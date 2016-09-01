package at.ac.tuwien.infosys.viepep.connectors.impl;


import at.ac.tuwien.infosys.viepep.connectors.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepep.connectors.ViePEPSSHConnector;
import at.ac.tuwien.infosys.viepep.connectors.impl.exceptions.ContainerNotFoundException;
import at.ac.tuwien.infosys.viepep.connectors.impl.exceptions.CouldNotStartDockerException;
import at.ac.tuwien.infosys.viepep.connectors.impl.exceptions.CouldNotStopDockerException;
import at.ac.tuwien.infosys.viepep.connectors.impl.exceptions.CouldResizeDockerException;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerConfiguration;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerImage;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheDockerService;
import com.spotify.docker.client.*;
import com.spotify.docker.client.messages.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.net.URI;
import java.util.*;

/**
 * This Docker controller makes use of the standard Docker REST API using the Spotify Docker Library
 */
@Component
@Slf4j
public class ViePEPDockerControllerServiceImpl implements ViePEPDockerControllerService {

    private static String DOCKER_RESIZE_SCRIPT ;

    private Map<String, DefaultDockerClient> dockerClientMap = new HashMap<>();

    private Map<VirtualMachine, List<DockerContainer>> dockersPerVM = new HashMap<>();
    private String defaultPort;

    @Autowired
    private CacheDockerService cacheDockerService;
    @Autowired
    private ViePEPSSHConnector sshConnector;


    @Override
    public void initialize() {
        loadProperties();
        sshConnector.initialize();
    }

    @Override
    public Map<VirtualMachine, List<DockerContainer>> getDockersPerVM(boolean running) {
        return this.dockersPerVM;
    }

    @Override
    public List<DockerContainer> getDockers(VirtualMachine virtualMachine) {
        List<DockerContainer> dockers = new ArrayList<>();
        DockerClient.ListContainersParam params = DockerClient.ListContainersParam.allContainers(false);

        DefaultDockerClient dockerClient = getDockerClient(virtualMachine);
        List<Container> containers = new ArrayList<>();
        try {
            containers = dockerClient.listContainers(params);
        } catch (Exception ex) {
            log.error("Could not get Docker containers: " + ex.getLocalizedMessage());
        }
        if (containers == null) {
            containers = new ArrayList<>();
        }
        for (Container container : containers) {
            String image = container.image();
            if (image.contains("swarm")) {
                continue;
            }
            List<String> names = container.names();

            DockerImage dockerImage = cacheDockerService.parseByServiceTypeId(names.get(0));
            if (dockerImage == null) {
                log.info(image + " Unknown image ID ");
                continue;
            }
            DockerContainer con = new DockerContainer(dockerImage, DockerConfiguration.SINGLE_CORE);
            dockers.add(con);
        }
        return dockers;
    }

    @Override
    public DockerContainer startDocker(VirtualMachine virtualMachine, DockerContainer dockerContainer) throws CouldNotStartDockerException {

        DefaultDockerClient dockerClient = getDockerClient(virtualMachine);

        try {
            dockerClient.pull(dockerContainer.getDockerImage().getFullName(), new ProgressHandler() {
                @Override
                public void progress(ProgressMessage message) throws DockerException {
                    if (message != null && message.progress() != null) {
                        log.info(message.progress());
                    }
                }
            });


            final Map<String, List<PortBinding>> portBindings = new HashMap<>();
            List<PortBinding> hostPorts = new ArrayList<>();
            String externPort = String.valueOf(dockerContainer.getDockerImage().getExternPort());

            String internPort = String.valueOf(dockerContainer.getDockerImage().getInternPort());
            hostPorts.add(PortBinding.of("0.0.0.0", externPort));
            portBindings.put(internPort, hostPorts);

            final HostConfig hostConfig = HostConfig.builder().portBindings(portBindings).build();

            double vmCores = virtualMachine.getVmType().cores;
            double containerCores = dockerContainer.getContainerConfiguration().cores;
            long cpuShares = 1024 / (long) Math.ceil(vmCores / containerCores);

            long memory = (long) dockerContainer.getContainerConfiguration().ram * 1024 * 1024;

            final ContainerConfig config = ContainerConfig.builder()
                    .hostConfig(hostConfig)
                    .image(dockerContainer.getDockerImage().getFullName())
                    .cpuShares(cpuShares)
                    .exposedPorts(String.valueOf(dockerContainer.getDockerImage().getInternPort()))
                    .memory(memory)
                    .env(String.format("WP_URL=\"%s:%s\"", virtualMachine.getIpAddress(),
                            dockerContainer.getDockerImage().getExternPort()))
                    .build();

            String id;
            final String containerName = getContainerName(dockerContainer);

            try {
                final ContainerCreation creation = dockerClient.createContainer(config, containerName);
                id = creation.id();
            } catch (DockerRequestException ex) {
                if (ex.message().contains("already in use")) {
                    ContainerInfo dockerInfo = getDockerInfo(virtualMachine, dockerContainer);
                    dockerContainer.setContainerID(dockerInfo.id());
                    if (!dockerInfo.state().running()) { //if not running, terminate
                        log.info("Could not create, already existing, let's remove it first");
                        stopDocker(virtualMachine, dockerContainer);
                        ContainerCreation creation = dockerClient.createContainer(config, containerName);
                        id = creation.id();
                    } else {
                        return resizeContainer(virtualMachine, dockerContainer);
                    }
                } else {
                    throw new Exception("Could not start container " + containerName, ex);
                }
            }

            dockerClient.startContainer(id);
            dockerContainer.setContainerID(id);


        } catch (Exception e) {
            //ignore
            log.error("could not start container " + virtualMachine.getName(), e);
        }

        return dockerContainer;
    }

    @Override
    public boolean stopDocker(VirtualMachine virtualMachine, DockerContainer dockerContainer) throws CouldNotStopDockerException {


        DefaultDockerClient dockerClient = getDockerClient(virtualMachine);
        try {
            String containerID = getDockerID(dockerClient, dockerContainer);

            if (containerID == null) {
                log.debug("Container was not found, expected behaviour");
                return true;
            }
            dockerClient.stopContainer(containerID, 30);

            dockerClient.removeContainer(containerID);
        } catch (Exception e) {
            throw new CouldNotStopDockerException(e);
        }
        return true;
    }

    @Override
    public ContainerInfo getDockerInfo(VirtualMachine virtualMachine, DockerContainer dockerContainer) throws ContainerNotFoundException {
        DefaultDockerClient dockerClient = getDockerClient(virtualMachine);
        try {
            String containerID = getDockerID(dockerClient, dockerContainer);

            return dockerClient.inspectContainer(containerID);

        } catch (Exception e) {
            throw new ContainerNotFoundException(e.getMessage());
        }
    }

    @Override
    public DockerContainer resizeContainer(VirtualMachine virtualMachine, DockerContainer dockerContainer) throws CouldResizeDockerException {
        try {
            String currentScript = DOCKER_RESIZE_SCRIPT;

            double vmCores = virtualMachine.getVmType().cores;
            double containerCores = dockerContainer.getContainerConfiguration().cores;
            long cpuShares = 1024 / (long) Math.ceil(vmCores / containerCores);

            currentScript = currentScript.replace("#{DOCKER_NAME}", dockerContainer.getDockerImage().getServiceName());
            currentScript = currentScript.replace("#{CPU_SHARE}", String.valueOf(cpuShares));

            String[] strings = sshConnector.execSSHCommand(virtualMachine, currentScript);
            if (strings[1].length() > 0) {
                return null;
            }
            return dockerContainer;
        } catch (Exception e) {
            throw new CouldResizeDockerException(e);
        }
    }

    private String getDockerID(DefaultDockerClient dockerClient, DockerContainer dockerContainer) throws DockerException, InterruptedException {
        if (dockerContainer.getContainerID() != null) {
            return dockerContainer.getContainerID();
        }
        DockerClient.ListContainersParam params = DockerClient.ListContainersParam.allContainers(true);

        List<Container> containers = dockerClient.listContainers(params);
        for (Container container : containers) {
            if (container.names().toString().contains(getContainerName(dockerContainer))) {
                return container.id();
            }
        }
        return null;
    }

    private String getContainerName(DockerContainer dockerContainer) {
        return String.format("%s", dockerContainer.getDockerImage().getServiceName());
    }

    private DefaultDockerClient getDockerClient(VirtualMachine virtualMachine) {
        DefaultDockerClient dockerClient = dockerClientMap.get(virtualMachine.getIpAddress());
        if (dockerClient == null) {
            String url = String.format("http://%s:%s", virtualMachine.getIpAddress(), defaultPort);
            dockerClient = DefaultDockerClient.builder().uri(URI.create(url)).build();
            dockerClientMap.put(virtualMachine.getIpAddress(), dockerClient);
        }
        return dockerClient;
    }

    private void loadProperties() {
        Properties prop = new Properties();
        String openstack_properties = System.getenv("DOCKER_PROPERTY_FILE");
        try {
            if (openstack_properties != null) {
                log.info("DOCKER VARIABLE SET -- Loading from Environment Variable: ");
                log.info(openstack_properties);
                prop.load(new FileInputStream(openstack_properties));
            } else {
                openstack_properties = "docker-config/docker-swarm.properties";
                prop.load(getClass().getClassLoader().getResourceAsStream(openstack_properties));
            }
            defaultPort = prop.getProperty("DOCKER_PORT");
            DOCKER_RESIZE_SCRIPT = FileLoader.getInstance().DOCKER_RESIZE_SCRIPT;

        } catch (Exception e) {
            log.error("-----------------------------------------------------------" +
                      "------------ docker properties not loaded -----------------" +
                      "-----------------------------------------------------------" +
                      "---- /src/resources/docker-swarm.properties is missing ----" +
                      "-----------------------------------------------------------");

        }
        log.info("------------------------------------------------------------" +
                 "---------- docker properties properties loaded -------------" +
                 "------------------------------------------------------------");
    }

}
