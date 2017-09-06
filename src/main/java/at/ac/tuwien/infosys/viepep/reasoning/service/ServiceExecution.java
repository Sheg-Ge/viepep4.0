package at.ac.tuwien.infosys.viepep.reasoning.service;

import at.ac.tuwien.infosys.viepep.database.entities.Element;
import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElement;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepep.reasoning.service.dto.InvocationResultDTO;
import at.ac.tuwien.infosys.viepep.util.TimeUtil;
import at.ac.tuwien.infosys.viepep.reasoning.impl.ReasoningImpl;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.PlacementHelper;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Component
@Scope("prototype")
@Slf4j
public class ServiceExecution{

    @Autowired
    private ServiceInvoker serviceInvoker;
    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    @Lazy
    private ReasoningImpl reasoning;

    @Value("${simulate}")
    private boolean simulate;

    @Async
    public void startExecution(ProcessStep processStep, VirtualMachine virtualMachine) {
        log.info("Task-Start: " + processStep);

        synchronized(virtualMachine){
	    	if(virtualMachine.isServiceTypeChanged()){
	    		virtualMachine.setServiceTypeChanged(false);
	    		TimeUtil.sleep(virtualMachine.getDeployTime());
	    	}
        }
    	
        if (simulate) {
            TimeUtil.sleep(processStep.getExecutionTime());
        } else {
            InvocationResultDTO invoke = serviceInvoker.invoke(virtualMachine, processStep);
        }

        finaliseExecution(processStep);
    }

    @Async
	public void startExecution(ProcessStep processStep, DockerContainer container) {
		log.info("Task-Start: " + processStep);

        if (simulate) {
        	TimeUtil.sleep(processStep.getExecutionTime());
        } else {
            InvocationResultDTO invoke = serviceInvoker.invoke(container, processStep);
        }
        
        finaliseExecution(processStep);
        	
	}
	
	private void finaliseExecution(ProcessStep processStep) {
		Date finishedAt = TimeUtil.nowDate();
        processStep.setFinishedAt(finishedAt);

		log.info("Task-Done: " + processStep);

//        if (processStep.isLastElement()) {
//
//            List<ProcessStep> runningSteps = placementHelper.getRunningProcessSteps(processStep.getWorkflowName());
//            List<ProcessStep> nextSteps = placementHelper.getNextSteps(processStep.getWorkflowName());
//            if ((nextSteps == null || nextSteps.isEmpty()) && (runningSteps == null || runningSteps.isEmpty())) {
//            	// System.out.println("Process Step (last Element) Workflowname: " + processStep.getWorkflowName());
//                WorkflowElement workflowById = cacheWorkflowService.getWorkflowById(processStep.getWorkflowName());
//                if(workflowById == null) {
//                	log.warn("Unable to find workflow for ID: " + processStep.getWorkflowName());
//                }
//                System.out.println("setNumberOfFinishedLastElements " + processStep.getWorkflowName());
//                workflowById.setNumberOfFinishedLastElements(workflowById.getNumberOfFinishedLastElements() + 1);
//                if(workflowById.getNumberOfFinishedLastElements() == workflowById.getNumberOfLastElements()){
//                	workflowById.setFinishedAt(finishedAt);
//                	cacheWorkflowService.deleteRunningWorkflowInstance(workflowById);
//                	log.warn("Workflow done. Workflow: " + workflowById); // TODO reset to INFO
//                }
//            }
//        }


		reasoning.setNextOptimizeTimeNow();

	}

}
