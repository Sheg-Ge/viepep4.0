package at.ac.tuwien.infosys.viepep.connectors.impl;

import at.ac.tuwien.infosys.viepep.connectors.ViePEPOpenstackClientService;
import at.ac.tuwien.infosys.viepep.database.entities.VMType;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import com.woorea.openstack.base.client.OpenStackResponseException;
import com.woorea.openstack.connector.JerseyConnector;
import com.woorea.openstack.keystone.Keystone;
import com.woorea.openstack.keystone.model.Access;
import com.woorea.openstack.keystone.utils.KeystoneUtils;
import com.woorea.openstack.nova.Nova;
import com.woorea.openstack.nova.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * User: bonomat
 * Date: 12/14/12
 */
@Slf4j
@Component
public class ViePEPOpenstackClientServiceImpl implements ViePEPOpenstackClientService {

    private final int maxCPUs = 28;
    private static String VIE_PEP_BPMS;
    private static String KEYSTONE_AUTH_URL = "CHANGE_ME";
    private static String KEYSTONE_USERNAME = "CHANGE_ME";
    private static String KEYSTONE_PASSWORD = "CHANGE_ME";
    private String propertyFile;
    private Nova novaClient;
    private static String CREATE_BACKEND_VM = "";
    protected static String BACKEND_CONFIG = "";
    private static String OS_TENANT_NAME = " ";
    private static String OS_TENANT_ID = "";
    private static String IMAGE_ID = "";
    private static String FLAVOR = "";
    private static String IMAGE_REF = "";
    private static String CLOUD_INIT_DOCKER_START_TEMPL;
    private static String CLOUD_INIT;
    private static String DOCKER_RESIZE_SCRIPT;

    private static String FLAVOR_ID = "9112";
    private List<Flavor> possibleFlavors = new ArrayList<Flavor>();
    private List<VMType> flavors;
    private Map<String, Flavor> availableFlavorMap = new HashMap<>();
    private boolean enabled = false;

    
    private void loadSettings() {
        Properties prop = new Properties();

        try {
            propertyFile = System.getenv("OPENSTACK_PROPERTY_FILE");
            if (propertyFile != null) {
                prop.load(new FileInputStream(propertyFile));
            }
            else {
                propertyFile = "cloud-config/viepep4.0.properties";
                prop.load(getClass().getClassLoader().getResourceAsStream(propertyFile));
            }

            KEYSTONE_AUTH_URL = prop.getProperty("openstack_auth_url");
            KEYSTONE_USERNAME = prop.getProperty("openstack_username");
            KEYSTONE_PASSWORD = prop.getProperty("openstack_password");
            VIE_PEP_BPMS = prop.getProperty("server_ip");
            OS_TENANT_NAME = prop.getProperty("OS_TENANT_NAME");
            OS_TENANT_ID = prop.getProperty("OS_TENANT_ID");
            IMAGE_ID = prop.getProperty("IMAGE_ID");
            FLAVOR = prop.getProperty("FLAVOR");


            FileLoader fileLoader = FileLoader.getInstance();
            CLOUD_INIT = fileLoader.CLOUD_INIT;
            CLOUD_INIT_DOCKER_START_TEMPL = fileLoader.CLOUD_INIT_DOCKER_START;
            DOCKER_RESIZE_SCRIPT = fileLoader.DOCKER_RESIZE_SCRIPT;

            if (KEYSTONE_AUTH_URL.isEmpty() || KEYSTONE_PASSWORD.isEmpty() || KEYSTONE_USERNAME.isEmpty()) {
                throw new Exception("Could not find variables ");
            }
        } catch (Exception e) {
            this.enabled = false;
        }
        log.info("-------- openstack properties loaded ----------------------");
    }

    @Override
    public void initialize() {
        loadSettings();
        JerseyConnector connector = new JerseyConnector();

        try {

            Keystone keystone = new Keystone(KEYSTONE_AUTH_URL, connector);
            Access access = keystone.
                    tokens().
                    authenticate().
                    withUsernamePassword(KEYSTONE_USERNAME, KEYSTONE_PASSWORD).
                    withTenantName(OS_TENANT_NAME).
                    execute();


            //use the token in the following requests
            keystone.token(access.getToken().getId());

            List<Access.Service> serviceCatalog = access.getServiceCatalog();

            String url = KeystoneUtils.findEndpointURL(serviceCatalog, "compute", null, "public");
            novaClient = new Nova(url, connector);
            novaClient.token(access.getToken().getId());

            Flavors flavors = novaClient.flavors().list(true).execute();
            FLAVOR_ID = "9112";
            for (Flavor flavor : flavors) {
                if (flavor.getName().equals(FLAVOR)) {
                    FLAVOR_ID = flavor.getId();
                }
                if (flavor.getName().startsWith("m")) {
                    possibleFlavors.add(flavor);
                    availableFlavorMap.put(flavor.getName(), flavor);
                }
            }
            enabled = true;
            log.info("autostartPEP file loaded");
        } catch (OpenStackResponseException openstackException) {
            enabled = false;
            printErrorMessage();
        }
    }


    @Override
    public boolean terminateInstanceByID(String instanceId) {
        if (instanceId == null) {
            return false;
        }
        if (!enabled) {
            printErrorMessage();
            return false;
        }
        novaClient.servers().delete(instanceId).execute();
        return true;
    }

    /**
     * @return a list of active server IPs
     */
    @Override
    public List<String> getServerIpList() {
        if (!enabled) {
            printErrorMessage();
            return new ArrayList<>();
        }
        List<String> serverList = new ArrayList<String>();
        Servers servers = novaClient.servers().list(true).execute();
        for (Server server : servers) {
            String status = server.getStatus();
            boolean error = !"error".equalsIgnoreCase(status);

            if (error && !"error".equalsIgnoreCase(status) && !server.getName()
                    .contains("bpms") && !"deleting".equalsIgnoreCase(server.getTaskState())) {
                serverList.add(server.getAddresses().getAddresses().get("private").get(0).getAddr());
            }
        }
        return serverList;
    }

    public String getVMIdByIP(String ip) {
        try {
            if (!enabled) {
                printErrorMessage();
                return null;
            }
            Servers servers = novaClient.servers().list(true).execute();
            for (Server server : servers) {
                Server.Addresses addresses = server.getAddresses();
                Map<String, List<Server.Addresses.Address>> addresses1 = addresses.getAddresses();
                List<Server.Addresses.Address> aPrivate = addresses1.get("private");
                if (aPrivate == null) {
                    continue;
                }
                if (ip.equals(aPrivate.get(0).getAddr())) {
                    return server.getId();
                }
            }
        } catch (NullPointerException ex) {
            return null;
        }
        return null;
    }

    public String getBPMSIp() {
        return VIE_PEP_BPMS;
    }


    /**
     * @return the list of registered services
     */
    public List<Server> getServerList() {
        if (!enabled) {
            printErrorMessage();
            return new ArrayList<>();
        }
        Servers servers = novaClient.servers().list(true).execute();
        return servers.getList();
    }


    public String getImageId() {
        return FLAVOR;
    }

    @Override
    public boolean terminateInstanceByIP(String localUrl) {
        if (!enabled) {
            printErrorMessage();
            return false;
        }
        String vmIdByIP = getVMIdByIP(localUrl);
        return terminateInstanceByID(vmIdByIP);
    }

    @Override
    public String startNewVM(String name, String flavorName, String serviceName) {

        if (!enabled) {
            printErrorMessage();
            return null;
        }
        Flavor flavor = availableFlavorMap.get(flavorName);

        //TODO: use this backend_config if you use the old way, not docker way
        BACKEND_CONFIG = StartBackendVMCommand.generateBackendConfig(VIE_PEP_BPMS, serviceName, 6549, "/home/ubuntu/viepep/backendVM/backend.properties");

        //TODO : change here to deploy containers on default
        //String dockerStartups = generateDockerStartupScripts(virtualMachine);
        //String nodeSpecificCloudInit = CLOUD_INIT.replace("#{DOCKER-UNITS}", dockerStartups);

        byte[] encodedBytes = Base64.encodeBase64(CLOUD_INIT.getBytes());

        ServerForCreate serverForCreate = new ServerForCreate();
        serverForCreate.setUserData(new String(encodedBytes));
        serverForCreate.setName(name);
        serverForCreate.setFlavorRef(flavor.getId());
        serverForCreate.setImageRef(IMAGE_ID);
        serverForCreate.setKeyName(KEYSTONE_USERNAME);
        serverForCreate.getSecurityGroups()
                .add(new ServerForCreate.SecurityGroup("default"));
        Server server = novaClient.servers().boot(serverForCreate).execute();
        log.info(String.format("Server created: %s name: %s", server.getId(), name));

        boolean isActive = "ACTIVE".equalsIgnoreCase(server.getStatus());
        int counter = 0;
        while (!isActive && counter < 5) {
            try {
                Thread.sleep(20000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            server = novaClient.servers().show(server.getId()).execute();
            String status = server.getStatus();
            if ("ERROR".equalsIgnoreCase(status)) {
                log.error("could not start VM");
                terminateInstanceByID(server.getId());
                server = novaClient.servers().boot(serverForCreate).execute();
                log.info(String.format("Server created: %s name: %s", server.getId(), name));
                counter = 0;
            }
            isActive = "ACTIVE".equals(status);
            counter++;
            if (counter == 10) {
                log.error("could not start VM");
                terminateInstanceByID(server.getId());
                server = novaClient.servers().boot(serverForCreate).execute();
                log.info(String.format("Server created: %s name: %s", server.getId(), name));
                counter = 0;
            }
        }
        Server.Addresses.Address aPrivate = server.getAddresses().getAddresses().get("private").get(0);
        return aPrivate.getAddr();
    }


    public int getFreeCPUs() {
        List<Server> serverList = getServerList();
        int sum = 0;
        for (Server server : serverList) {
            Flavor flavor = server.getFlavor();
            for (Flavor possibleFlavor : possibleFlavors) {
                if (possibleFlavor.getId().equals(flavor.getId())) {
                    sum += Integer.parseInt(possibleFlavor.getVcpus());
                    break;
                }
            }
        }
        return maxCPUs - sum;
    }

    public List<Server> getActiveVMs(String appId) {
        appId = appId.startsWith("/") ? appId : '/' + appId;
        List<Server> onlineResources = new ArrayList<Server>();
        List<Server> serverList = getServerList();
        for (Server server : serverList) {
            if (server.getName().contains(appId)) {
                onlineResources.add(server);
            }
        }
        return onlineResources;
    }

    public boolean isVMRunning(String ip) {
        if (!enabled) {
            printErrorMessage();
            return false;
        }
        Servers servers = novaClient.servers().list(true).execute();
        for (Server server : servers) {
            if ("deleting".equalsIgnoreCase(server.getTaskState())) {
                continue;
            }
            if ("error".equalsIgnoreCase(server.getStatus())) {
                novaClient.servers().delete(server.getId()).execute();
            }
            Server.Addresses addresses = server.getAddresses();
            Map<String, List<Server.Addresses.Address>> addresses1 = addresses.getAddresses();
            List<Server.Addresses.Address> aPrivate = addresses1.get("private");
            if (aPrivate == null) {
                continue;
            }
            if (ip.equals(aPrivate.get(0).getAddr())) {
                return true;
            }
        }
        return false;
    }

    public void cleanUpVMsNotRegistered(List<String> allRegisteredVMs) {
        SimpleDateFormat sfd = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Servers servers = novaClient.servers().list(true).execute();
        for (Server server : servers) {
            if (server.getName().contains("bpm")) {
                //ignore
                continue;
            }
            if ("deleting".equalsIgnoreCase(server.getTaskState())) {
                continue;
            }
            if ("error".equalsIgnoreCase(server.getStatus())) {
                terminateInstanceByID(server.getId());
            }
            Server.Addresses addresses = server.getAddresses();
            Map<String, List<Server.Addresses.Address>> addresses1 = addresses.getAddresses();
            List<Server.Addresses.Address> aPrivate = addresses1.get("private");
            if (aPrivate == null) {
                novaClient.servers().delete(server.getId()).execute();
                continue;
            }
            String addr = aPrivate.get(0).getAddr();
            if (!allRegisteredVMs.contains(addr)) {
                try {
                    if (sfd.parse(server.getCreated()).before(new Date(new Date().getTime() - 120000))) {
                        log.info("terminating VM: " + addr);
                        terminateInstanceByID(server.getId());
                    }
                } catch (ParseException e) {
                    //ignore
                }
            }
        }

    }

    /**
     *
     * @return a startup script to start docker containers on default
     * @param virtualMachine
     */
    private String generateDockerStartupScripts(VirtualMachine virtualMachine) {
        StringBuilder startups = new StringBuilder("");
        double vmCores = virtualMachine.getVmType().cores;

        for (DockerContainer deployedContainer : virtualMachine.getDeployedContainers()) {
            double containerCores = deployedContainer.getContainerConfiguration().cores;
            long cpuShares = 1024 / (long) Math.ceil(vmCores / containerCores);

            String replace = CLOUD_INIT_DOCKER_START_TEMPL.replace("#{name}", deployedContainer.getAppID());
            String runCmd = String.format("/usr/bin/docker run --restart=\"always\" --name %s -p %s:%s --cpu-shares=%s %s",
                    deployedContainer.getDockerImage().getServiceName(),
                    deployedContainer.getDockerImage().getExternPort(),
                    deployedContainer.getDockerImage().getInternPort(),
                    cpuShares,
                    deployedContainer.getDockerImage().getFullName());

            replace = replace.replace("#{RUN-CMD}", runCmd);
            startups.append(replace).append("\n");
        }
        return startups.toString();
    }

    private void printErrorMessage() {
        log.info("---------------------------------------------------------------------------------------------");
        log.info("------------ Could no connect to openstack cloud, Cloud connector disabled ------------------");
        log.info("---------------------------------------------------------------------------------------------");
    }
}