package at.ac.tuwien.infosys.viepep;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElement;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepep.reasoning.impl.ReasoningImpl;
import at.ac.tuwien.infosys.viepep.rest.impl.WorkflowRestServiceImpl;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ViePepApplication.class)
@WebAppConfiguration
@IntegrationTest("server.port:0")
@TestPropertySource(properties = {"simulate = true", "autostart = false"})
@Slf4j
@ActiveProfiles({"test", "docker"})
public class DockerOptimizerTest {
	
	@Autowired
	CacheVirtualMachineService vmService;
	@Autowired
	ReasoningImpl reasoning;
	@Autowired
	WorkflowRestServiceImpl workflowService;

	// @Ignore
	@Test
	public void testOptimization() throws Exception {
		/* define VM types */
		vmService.initializeVMs();
		
		/* define container types */

		/* define process type */

		/* request enactment */
		System.out.println(workflowService);
		workflowService.addWorkflow(TestWorkflows.constructTestWorkflows(1));

		/* ... */

		/* start optimization */
		long diff_secs = reasoning.performOptimisation() / 1000;
		System.out.println(diff_secs);

		/* ... (wait) */

		/* assertions, metrics.. */
	}

}
