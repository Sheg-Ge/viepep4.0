package at.ac.tuwien.infosys.viepep;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElements;
import at.ac.tuwien.infosys.viepep.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheDockerService;
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
//@ActiveProfiles({"test", "basic"})
public class DockerOptimizerTest {

	@Autowired
	CacheVirtualMachineService vmService;
	@Autowired
	CacheDockerService dockerService;
	@Autowired
	ReasoningImpl reasoning;
	@Autowired
	WorkflowRestServiceImpl workflowService;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;

	@Ignore
	@Test
	public void testOptimization() throws Exception {

		boolean breakOnFirstSuccess = false;
		int numIterations = 100;
		int currentIteration = 1;
		int numSuccessful = 0;

		for(currentIteration = 0; currentIteration < numIterations; currentIteration ++) {
			/* initialize VM and container types */
    		inMemoryCache.clear();
			vmService.initializeVMs();
			dockerService.initializeDockerContainers();

			/* define process type */
			Integer[] workflowTypeIDs = new Integer[]{1, 1, 1, 1, 1, 1, 1};
			WorkflowElements workflows1 = TestWorkflows.constructTestWorkflows(workflowTypeIDs);

			/* request enactment */
			workflowService.addWorkflow(workflows1);

			/* start optimization */
			long diff_secs = reasoning.performOptimisation() / 1000;
			System.out.println(diff_secs);

			/* assertions, metrics.. */
			for(VirtualMachine vm : vmService.getAllVMs()) {
				System.out.println(vm);
			}
			Set<VirtualMachine> startedVMs = vmService.getStartedAndScheduledForStartVMs();

			if(!startedVMs.isEmpty()) {
				numSuccessful++;
				if(breakOnFirstSuccess) {
					break;
				}
			}
		}

		System.out.println("Executed iterations: " + currentIteration);
		System.out.println("Successful iterations: " + numSuccessful);
		Assert.assertFalse(vmService.getStartedAndScheduledForStartVMs().isEmpty());

		/* finalize test */
		System.out.println("Done.");
	}

}
