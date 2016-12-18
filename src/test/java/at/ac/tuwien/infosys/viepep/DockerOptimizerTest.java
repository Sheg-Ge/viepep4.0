package at.ac.tuwien.infosys.viepep;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
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
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl.ViePEPSolverCPLEX;
import at.ac.tuwien.infosys.viepep.rest.impl.WorkflowRestServiceImpl;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ilog.cplex.IloCplex;
import lombok.extern.slf4j.Slf4j;

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

    private void disableLogging() {
		Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.WARN);
    }
    
	//@Ignore
	@Test
	public void testOptimization() throws Exception {
		disableLogging();

		ViePEPSolverCPLEX.CPLEX_PARAMS.put(IloCplex.IntParam.SolnPoolCapacity, 210000);
		ViePEPSolverCPLEX.CPLEX_PARAMS.put(IloCplex.IntParam.SolnPoolIntensity, 3);
		// cplex.setParam(IloCplex.DoubleParam.SolnPoolAGap, 0.5);
		// cplex.setParam(IloCplex.IntParam.PopulateLim, 1000);
		// cplex.setParam(IloCplex.IntParam.NodeLim, 0);
		// cplex.setParam(IloCplex.DoubleParam.TreLim, 0);
		// cplex.setParam(IloCplex.IntParam.IntSolLim, -1);

		boolean breakOnFirstSuccess = false;
		int numIterations = 50;
		List<Integer> numsRequests = Arrays.asList(1, 3, 10);

		for(int numRequests : numsRequests) {
			System.out.println("Number of requests: " + numRequests);
			int currentIteration = 1;
			int numSuccessful = 0;
			double minObjective = Double.MAX_VALUE;
			double maxObjective = Double.MIN_VALUE;
			double totalDuration = 0;

			for(currentIteration = 0; currentIteration < numIterations; currentIteration ++) {

				/* initialize VM and container types */
	    		inMemoryCache.clear();
				vmService.initializeVMs();
				dockerService.initializeDockerContainers();
	
				/* define process type */
				Integer[] workflowTypeIDs = new Integer[]{1, 1, 1, 1, 1, 1, 1};
				WorkflowElements workflows1 = TestWorkflows.constructTestWorkflows(workflowTypeIDs);
	
				/* request enactment */
				for(int i = 0; i < numRequests; i ++) {
					workflowService.addWorkflow(workflows1);
				}
	
				/* perform optimization */
				long t1 = System.currentTimeMillis();
				long diff_secs = reasoning.performOptimisation() / 1000;
				long t2 = System.currentTimeMillis();
				long duration = t2 - t1;

				/* assertions, metrics.. */
				for(VirtualMachine vm : vmService.getAllVMs()) {
					log.debug(vm.toString());
				}
				Set<VirtualMachine> startedVMs = vmService.getStartedAndScheduledForStartVMs();

				double objective = ViePEPSolverCPLEX.LAST_RESULT.getObjective().doubleValue();
				maxObjective = Math.max(objective, maxObjective);
				minObjective = Math.min(objective, minObjective);
				totalDuration += duration;

				if(!startedVMs.isEmpty()) {
					numSuccessful++;
					if(breakOnFirstSuccess) {
						break;
					}
				}
			}

			System.out.println("Successful/executed iterations: " + numSuccessful + "/" + currentIteration);
			System.out.println("Min/max objective: " + minObjective + "/" + maxObjective);
			System.out.println("Average duration: " + (totalDuration / (double)currentIteration));
			Assert.assertFalse(vmService.getStartedAndScheduledForStartVMs().isEmpty());
		}

		/* finalize test */
		System.out.println("Done.");
	}

}
