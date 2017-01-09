package at.ac.tuwien.infosys.viepep.reasoning.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

import lombok.extern.slf4j.Slf4j;
import net.sf.javailp.Result;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.AsyncResult;

import at.ac.tuwien.infosys.viepep.connectors.impl.ViePEPDockerControllerServiceImpl;
import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElement;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheDockerService;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepep.reasoning.ProcessOptimizationResults;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.PlacementHelper;
import at.ac.tuwien.infosys.viepep.reasoning.service.ServiceExecutionController;
import at.ac.tuwien.infosys.viepep.util.TimeUtil;

/**
 * @author Gerta Sheganaku
 */
@Slf4j
//@Component
@Scope("prototype")
public class DockerLightProcessOptimizationResults implements ProcessOptimizationResults {

    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private ServiceExecutionController serviceExecutionController;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheDockerService cacheDockerService;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private ViePEPDockerControllerServiceImpl dockerControllerService;

    //@Async
    public Future<Boolean> processResults(Result optimize, Date tau_t) {

        //start VMs
        List<VirtualMachine> vmsToStart = new ArrayList<>();
        //deploy Containers
        List<DockerContainer> containersToDeploy = new ArrayList<>();
        //set steps to be scheduled
        List<ProcessStep> scheduledForExecution = new ArrayList<>();
        List<String> y = new ArrayList<>();
        List<String> x = new ArrayList<>();

        StringBuilder stringBuilder2 = new StringBuilder();
        
        stringBuilder2.append("------------------------- VMs running ----------------------------\n");
        List<VirtualMachine> vMs = cacheVirtualMachineService.getAllVMs();
        for(VirtualMachine vm : vMs) {
            if(vm.isLeased() && vm.isStarted()) {
                stringBuilder2.append(vm.toString()).append("\n");
                
                stringBuilder2.append("------------------------- Dockers running ----------------------------\n");
                List<DockerContainer> containers = vm.getContainers();
                for(DockerContainer container : containers) {
                    if(container.isRunning()) {
                        stringBuilder2.append(container.toString()).append("\n");
                    }
                }
            }
        }

        stringBuilder2.append("------------------------ Tasks running ---------------------------\n");
        List<WorkflowElement> allRunningWorkflowInstances = cacheWorkflowService.getRunningWorkflowInstances();
        for (WorkflowElement workflow : allRunningWorkflowInstances) {
            List<ProcessStep> runningSteps = placementHelper.getRunningProcessSteps(workflow.getName());
            for (ProcessStep runningStep : runningSteps) {
                if(runningStep.getScheduledAtContainer() != null) {
                	if(runningStep.getScheduledAtContainer().isRunning()) {
                		stringBuilder2.append("Task-Running: ").append(runningStep).append("\n");
                	}
                }
            }
        }
        
        processResults(optimize, tau_t, vmsToStart, containersToDeploy, scheduledForExecution, y, x, stringBuilder2, vMs, allRunningWorkflowInstances);

        stringBuilder2.append("-------------------------- y results -----------------------------\n");
        for (String s : y) {
            stringBuilder2.append(s).append("=").append(optimize.get(s)).append("\n");
        }
        
        stringBuilder2.append("-------------------------- x results -----------------------------\n");
        for (String s : x) {
        	Number num = optimize.get(s);
        	if(num != null && num.intValue() > 0) {
                stringBuilder2.append(s).append("=").append(optimize.get(s)).append("\n");
        	}
        }
        
        stringBuilder2.append("----------- VM should be used (has to be started): ----\n");
        for (VirtualMachine virtualMachine : vmsToStart) {
            stringBuilder2.append(virtualMachine).append("\n");
        }

        stringBuilder2.append("----------------------- Tasks to be started ----------------------\n");

        for (ProcessStep processStep : scheduledForExecution) {
            stringBuilder2.append("Task-TODO: ").append(processStep).append("\n");
        }
        stringBuilder2.append("----------------------------- Containers to deploy -------------------------------------\n");

        for (DockerContainer container : containersToDeploy) {
            stringBuilder2.append(container).append("\n");
        }
        stringBuilder2.append("------------------------------------------------------------------\n");

        cleanupContainers(containersToDeploy);
        
//        stringBuilder2.append("----------- Container should be used (running or has to be started): ----\n");
//        for (DockerContainer container : cacheDockerService.getAllDockerContainers()) {
//        		stringBuilder2.append(container).append("\n");
//        	
//        }
//        stringBuilder2.append("------------------------------------------------------------------\n");
//        log.info(stringBuilder2.toString().replaceAll("(\r\n|\n)", "\r\n                                                                                                     "));

        serviceExecutionController.startInvocation(scheduledForExecution, containersToDeploy);

        cleanupVMs(tau_t);
        
        return new AsyncResult<Boolean>(true);
    }


	


	private void processResults(Result optimize, Date tau_t, List<VirtualMachine> vmsToStart,
			List<DockerContainer> containersToDeploy, List<ProcessStep> scheduledForExecution,
			List<String> y, List<String> x, StringBuilder stringBuilder2, List<VirtualMachine> vMs, 
			List<WorkflowElement> allRunningWorkflowInstances) {

        for (WorkflowElement workflow : allRunningWorkflowInstances) {
            List<ProcessStep> runningSteps = placementHelper.getRunningProcessSteps(workflow.getName());
            for (ProcessStep runningStep : runningSteps) {
                if(runningStep.getScheduledAtContainer() != null) {
                	if(runningStep.getScheduledAtContainer().isRunning()) {
                		stringBuilder2.append("Task-Running: ").append(runningStep).append("\n");
                	}
                }
            }

            for (ProcessStep processStep : placementHelper.getRunningOrNotStartedSteps()) {
                if (!processStep.getWorkflowName().equals(workflow.getName())) {
                    continue;
                }
                processXYValues(optimize, tau_t, vmsToStart, containersToDeploy, scheduledForExecution, y, x, vMs, processStep);
            }

        }
    }

    
    private void processXYValues(Result optimize, Date tau_t, List<VirtualMachine> vmsToStart, 
    		List<DockerContainer> containersToDeploy, List<ProcessStep> scheduledForExecution, 
    		List<String> y, List<String> x, List<VirtualMachine> vMs, ProcessStep processStep) {
    	
    	for (VirtualMachine virtualMachine: vMs) {
        	String x_s_v = placementHelper.getDecisionVariableX(processStep, virtualMachine);
            String y_v_k = placementHelper.getDecisionVariableY(virtualMachine);

            Number x_s_v_number = optimize.get(x_s_v);
            Number y_v_k_number = optimize.get(y_v_k);

            if (!y.contains(y_v_k)) {
                y.add(y_v_k);
                if (toInt(y_v_k_number) >= 1) {
                    vmsToStart.add(virtualMachine);
                    Date date = TimeUtil.nowDate();
                    if (virtualMachine.getToBeTerminatedAt() != null) {
                        date = virtualMachine.getToBeTerminatedAt();
                    }
//                    if(virtualMachine.getStartedAt() == null){
//                    	virtualMachine.setStartedAt(TimeUtil.nowDate());
//                    }
                    virtualMachine.setLeased(true);
                    virtualMachine.setToBeTerminatedAt(new Date(date.getTime() + (placementHelper.getLeasingDuration(virtualMachine) * toInt(y_v_k_number))));
                }
            }
            
        	DockerContainer container = virtualMachine.getContainer(processStep);
            
            if (!x.contains(x_s_v)) {
                x.add(x_s_v);
                if(x_s_v_number != null) {
                	int x_s_v_number_int = toInt(x_s_v_number);
	                if (x_s_v_number_int == 1) {
	                	if(!containersToDeploy.contains(container)){
	                		containersToDeploy.add(container);	              
	                	}
	                }
                }
            }

            if (x_s_v_number == null || toInt(x_s_v_number) == 0) {
                continue;
            }

            if (toInt(x_s_v_number) == 1 && !scheduledForExecution.contains(processStep)){ 
            	if(processStep.getStartDate() != null || processStep.getScheduledStartedAt() != null) {
            		// System.out.println("Reschedule: \nProcessStep: " + processStep +"\nContainer: "+container+"\nOld Container: "+processStep.getScheduledAtContainer());
            		processStep.rescheduledExecution(container);
                    scheduledForExecution.add(processStep);
                    if (!containersToDeploy.contains(container)) {
                        containersToDeploy.add(container);
                    }
            	}else{
            		processStep.setScheduledForExecution(true, tau_t, container);
                    scheduledForExecution.add(processStep);
                    if (!containersToDeploy.contains(container)) {
                        containersToDeploy.add(container);
                    }
            	}       
            }
        }
    }
    
    private void cleanupContainers(List<DockerContainer> containersToDeploy) {
        List<DockerContainer> containers = cacheDockerService.getAllDockerContainers();

        for (DockerContainer container : containers) {
        	if(!containersToDeploy.contains(container)){
        		log.debug("CONTAINER IS CLOSED: "+ container + " it was on VM: " + container.getFixedVirtualMachine());
				placementHelper.stopDockerContainer(container);
        	}
        }
    }
    
    private void cleanupVMs(Date tau_t_0) {
        List<VirtualMachine> vMs = cacheVirtualMachineService.getAllVMs();
        for (VirtualMachine vM : vMs) {
            if (vM.getToBeTerminatedAt() != null && vM.getToBeTerminatedAt().before((tau_t_0))) {
                placementHelper.terminateVM(vM);
            }
        }
    }

	private int toInt(Number n) {
		return (int)Math.round(n.doubleValue());
	}
	
}
