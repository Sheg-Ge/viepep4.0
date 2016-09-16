package at.ac.tuwien.infosys.viepep;

import at.ac.tuwien.infosys.viepep.connectors.ViePEPAwsClientService;
import at.ac.tuwien.infosys.viepep.connectors.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepep.connectors.ViePEPOpenstackClientService;
import at.ac.tuwien.infosys.viepep.database.entities.*;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerConfiguration;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerImage;
import at.ac.tuwien.infosys.viepep.database.repositories.WorkflowElementRepository;
import at.ac.tuwien.infosys.viepep.database.services.WorkflowDaoService;
import com.spotify.docker.client.messages.ContainerInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.Date;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ViePepApplication.class)
@WebAppConfiguration
@IntegrationTest("server.port:0")
@TestPropertySource(properties = {"simulation = true", "autostart = false"})
@ActiveProfiles({"test", "docker"})
@Slf4j
public class ViePepApplicationTests {

	@Value("${local.server.port}")
	int port;

	@Autowired
	private ViePEPOpenstackClientService viePEPOpenstackClient;
	@Autowired
	private ViePEPAwsClientService viePEPAwsClientService;
	@Autowired
	private ViePEPDockerControllerService dockerControllerService;
	@Autowired
	private WorkflowDaoService workflowDaoService;
	@Autowired
	private WorkflowElementRepository workflowElementRepository;

	@Ignore
	@Test
	public void testStartNewOpenStackVM_AddContainer_ResizeContainer_Terminate() throws Exception {
//		viePEPOpenstackClient.initialize();
//		dockerControllerService.initialize();
//		//starting a new VM instance
//		//exampleService can be ignored
//		String testVMIp = viePEPOpenstackClient.startNewVM("testVM", VMType.DUAL_CORE.flavor(), "exampleService");
//		assertThat(testVMIp, notNullValue());
//
//		//starting a new docker container
//		DockerImage dockerImage = new DockerImage("exampleApp", "bonomat", "nodejs-hello-world", 8090, 3000);
//		//DockerImage dockerImage = new DockerImage("exampleApp", "bonomat", "viepep-backend-services", 8080, 8080);
//		DockerConfiguration dockerConfiguration = DockerConfiguration.SINGLE_CORE;
//
//		DockerContainer dockerContainer = new DockerContainer(dockerImage, dockerConfiguration);
//
//		VirtualMachine virtualMachine = new VirtualMachine("dummyVM", VMType.DUAL_CORE);
//		virtualMachine.setIpAddress(testVMIp);
//		//  virtualMachine.setIpAddress("128.130.172.226"); //TODO change this manualy if run from outside of the cloud,
//		// but make sure to assign this IP to the VM you just created
//
//		Thread.sleep(30 * 1000);
//
//		log.info("VM running, start docker...");
//		dockerContainer = dockerControllerService.startDocker(virtualMachine, dockerContainer);
//
//		//collect some docker information
//		ContainerInfo dockerInfo = dockerControllerService.getDockerInfo(virtualMachine, dockerContainer);
//		assertThat(dockerInfo, notNullValue());
//
//		log.info("Docker running, change docker config...");
//		//change docker configuration
//		dockerContainer.setContainerConfiguration(DockerConfiguration.DUAL_CORE);
//		dockerContainer = dockerControllerService.resizeContainer(virtualMachine, dockerContainer);
//
//		log.info("Docker config changed, stop docker...");
//		//stop docker
//		boolean b = dockerControllerService.stopDocker(virtualMachine, dockerContainer);
//		assertTrue(b);
	}

	@Test
	public void testStartNewAWSVM_AddContainer_ResizeContainer_Terminate() throws Exception {
		log.info("TEST testStartNewAWSVM_AddContainer_ResizeContainer_Terminate started...");

		viePEPAwsClientService.initialize();
		dockerControllerService.initialize();

		//starting a new VM instance
		//exampleService can be ignored
		String testVMIp = viePEPAwsClientService.startNewVM("testVM", VMType.AWS_SINGLE_CORE.flavor(), "exampleService", "eu-central-1");
		assertThat(testVMIp, notNullValue());

		//starting a new docker container
		//DockerImage dockerImage = new DockerImage("exampleApp", "bonomat", "nodejs-hello-world", 8090, 3000);
		DockerImage dockerImage = new DockerImage("exampleApp", "shegge", "viepep-docker-1", 8080, 8080);
		//DockerImage dockerImage = new DockerImage("exampleApp", "bonomat", "viepep-backend-services", 8080, 8080);
		DockerConfiguration dockerConfiguration = DockerConfiguration.SINGLE_CORE;

		DockerContainer dockerContainer = new DockerContainer(dockerImage, dockerConfiguration);

		VirtualMachine virtualMachine = new VirtualMachine("dummyVM", VMType.AWS_SINGLE_CORE);
		virtualMachine.setIpAddress(testVMIp);

		Thread.sleep(40 * 1000);

		log.info("VM running, start docker...");
		dockerContainer = dockerControllerService.startDocker(virtualMachine, dockerContainer);

		//collect some docker information
		ContainerInfo dockerInfo = dockerControllerService.getDockerInfo(virtualMachine, dockerContainer);
		assertThat(dockerInfo, notNullValue());

		log.info("Docker running, change docker config...");
		//change docker configuration
		dockerContainer.setContainerConfiguration(DockerConfiguration.DUAL_CORE);
		dockerContainer = dockerControllerService.resizeContainer(virtualMachine, dockerContainer);

		log.info("Docker config changed, stop docker...");
		//stop docker
		boolean b = dockerControllerService.stopDocker(virtualMachine, dockerContainer);
		assertTrue(b);
		log.info("Docker stopped, stop VM...");
		boolean c = viePEPAwsClientService.terminateInstanceByIP(testVMIp);
		assertTrue(c);
		log.info("VM stopped.");
	}

	@Ignore
	@Test
	public void persistWorkflow_ShouldPersistWorkflow() {

		WorkflowElement workflowElement = createFinishedWorkflow();
		workflowElement.setFinishedAt(new Date());
		workflowElement = workflowDaoService.finishWorkflow(workflowElement);
		WorkflowElement workflowFromDatabase = workflowElementRepository.findOne(workflowElement.getId());
		assertNotNull(workflowFromDatabase);
	}

	private WorkflowElement createFinishedWorkflow() {
		String name = "finishedWorkflow";
		WorkflowElement workflow = new WorkflowElement(name, (new Date()).getTime() + 10000, 200);
		Sequence seq = new Sequence(name + "-seq");
		ProcessStep elem1 = new ProcessStep(name + ".1", ServiceType.Task1, workflow.getName());
		seq.addElement(elem1);
		ProcessStep elem2 = new ProcessStep(name + ".2", ServiceType.Task2, workflow.getName());
		seq.addElement(elem2);
		ProcessStep elem = new ProcessStep(name + ".3", ServiceType.Task3, workflow.getName());
		elem.setLastElement(true);
		elem.setFinishedAt(new Date());
		seq.addElement(elem);
		workflow.addElement(seq);

		return workflow;
	}

}
