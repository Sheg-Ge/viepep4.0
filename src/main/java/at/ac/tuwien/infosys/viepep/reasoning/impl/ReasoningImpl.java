package at.ac.tuwien.infosys.viepep.reasoning.impl;

import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElement;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepep.database.services.WorkflowDaoService;
import at.ac.tuwien.infosys.viepep.reasoning.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepep.reasoning.ProcessOptimizationResults;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.PlacementHelper;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.ProcessInstancePlacementProblemService;
import lombok.extern.slf4j.Slf4j;
import net.sf.javailp.Result;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@Component
@Scope("singleton")
@Slf4j
public class ReasoningImpl {

    @Autowired
    private ProcessOptimizationResults processOptimizationResults;
    @Autowired
    private ProcessInstancePlacementProblemService resourcePredictionService;
    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private WorkflowDaoService workflowDaoService;

    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

//    private Date tau_t;
    private boolean run = true;

    private AtomicLong lastTerminateCheckTime = new AtomicLong(0);
    private AtomicLong nextOptimizeTime = new AtomicLong(0);

    private static final long POLL_INTERVAL_MILLISECONDS = 1000;
    private static final long TERMINATE_CHECK_INTERVAL_MILLISECONDS = 10000;
	public static final long MIN_TAU_T_DIFFERENCE_MS = 10 * 1000;
	private static final long RETRY_TIMEOUT_MILLIS = 10 * 1000;


    @Async
    public Future<Boolean> runReasoning(Date date, boolean autoTerminate) throws InterruptedException {

        resourcePredictionService.initializeParameters();
        run = true;

        Date emptyTime = null;

        while (run) {
            synchronized (this) {
                try {
                	long now = System.currentTimeMillis();

                	//System.out.println("now " + now);
                	//System.out.println("next opt " + nextOptimizeTime.get());

                	if (now - lastTerminateCheckTime.get() > TERMINATE_CHECK_INTERVAL_MILLISECONDS) {
                		lastTerminateCheckTime.set(now);

                		List<WorkflowElement> workflows = cacheWorkflowService.getRunningWorkflowInstances();
                		log.info("  ***************** RunningWFL Instances (" + workflows.size() + "running) WAS EMPTY? : " + workflows.isEmpty());
//                        for(WorkflowElement workflow: workflows) {
//                        	System.out.println("\n running workflow: " + workflow);
//                        }

                        if(workflows.isEmpty()) {
                            if(emptyTime == null) {
                            	emptyTime = new Date();
                            };
                            log.info("Time first empty: " + emptyTime);
                        }
                        else {
                            emptyTime = null;
                        }
                        if (emptyTime != null && ((new Date()).getTime() - emptyTime.getTime()) >= (60 * 1000 * 1)) {
                        	if (autoTerminate) {
                        		run = false;
                        	}
                        }
                	}

                	if (now >= nextOptimizeTime.get()) {

                		 long difference = performOptimisation();

                		 nextOptimizeTime.set(now + difference);
                         // Thread.sleep(difference);
                	}
                   
                	Thread.sleep(POLL_INTERVAL_MILLISECONDS);
                	
                	
                } catch (ProblemNotSolvedException ex) {
                    log.error("An exception occurred, could not solve the problem", ex);
                    // try again in some time
           		 	nextOptimizeTime.set(System.currentTimeMillis() + RETRY_TIMEOUT_MILLIS);
                } catch (Exception ex) {
                    log.error("An unknown exception occurred. Terminating.", ex);
                    run = false;
                }
            }
        }

        waitUntilAllProcessDone();

        List<WorkflowElement> workflows = cacheWorkflowService.getAllWorkflowElements();
        int delayed = 0;
        for (WorkflowElement workflow : workflows) {
            log.info("workflow: " + workflow.getName() + " Deadline: " + formatter.format(new Date(workflow.getDeadline())));

            ProcessStep lastExecutedElement = workflow.getLastExecutedElement();
            if (lastExecutedElement != null) {
                Date finishedAt = lastExecutedElement.getFinishedAt();
                workflow.setFinishedAt(finishedAt);
                boolean ok = workflow.getDeadline() >= finishedAt.getTime();
                long delay = finishedAt.getTime() - workflow.getDeadline();
                String message = " LastDone: " + formatter.format(finishedAt);
                if (ok) {
                    log.info(message + " : was ok");
                } else {
                    log.info(message + " : delayed in seconds: " + delay / 1000);
                    delayed++;
                }
                cacheWorkflowService.deleteRunningWorkflowInstance(workflow);
            } else {
                log.info(" LastDone: not yet finished");
            }
        }
        log.info(String.format("From %s workflows, %s where delayed", workflows.size(), delayed));


        for(WorkflowElement workflowElement : cacheWorkflowService.getAllWorkflowElements()) {
            workflowDaoService.finishWorkflow(workflowElement);
        }

        return new AsyncResult<>(true);
    }

    private void waitUntilAllProcessDone() {
        int times = 0;
        int size = placementHelper.getRunningSteps().size();
        while (size != 0 && times <= 5) {
            log.info("there are still steps running waiting 1 minute: steps running: " + size);
            try {
                Thread.sleep(60000);//
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            size = placementHelper.getRunningSteps().size();
            times++;
        }
    }

    public long performOptimisation() throws Exception {

        Date tau_t_0 = new Date();
        log.info("---------tau_t_0 : " + tau_t_0 + " ------------------------");
        log.info("---------tau_t_0.time : " + tau_t_0.getTime() + " ------------------------");
        Result optimize = resourcePredictionService.optimize(tau_t_0);

        if (optimize == null) {
            throw new ProblemNotSolvedException("Could not solve the Problem");
        }

        log.info("Objective: " + optimize.getObjective());
        long tau_t_1 = optimize.get("tau_t_1").longValue() * 1000;//VERY IMPORTANT,
        System.out.println("tau_t_1 was calculted as: "+ new Date(tau_t_1) );
        System.out.println("Execution time ");
        
        
        Future<Boolean> processed = processOptimizationResults.processResults(optimize, tau_t_0);
        processed.get();
        
        long difference = tau_t_1 - new Date().getTime();
        if (difference < 0 || difference > 60*60*1000) {
            difference = MIN_TAU_T_DIFFERENCE_MS;
        }
        log.info("---------sleep for: " + difference / 1000 + " seconds-----------");
        log.info("---------next iteration: " + new Date(tau_t_1) + " -----------");
        
        
        return difference;
    }

    public void stop() {
        this.run = false;
    }

    public void setNextOptimizeTimeNow() {
    	setNextOptimizeTimeAfter(0);
    }

    public void setNextOptimizeTimeAfter(long millis) {
		nextOptimizeTime.set(System.currentTimeMillis() + millis);
	}
}
