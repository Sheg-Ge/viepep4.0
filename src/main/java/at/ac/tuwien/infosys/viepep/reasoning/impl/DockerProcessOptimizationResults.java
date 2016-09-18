package at.ac.tuwien.infosys.viepep.reasoning.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

import lombok.extern.slf4j.Slf4j;
import net.sf.javailp.Result;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;

import at.ac.tuwien.infosys.viepep.connectors.impl.ViePEPDockerControllerServiceImpl;
import at.ac.tuwien.infosys.viepep.database.entities.Element;
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

/**
 * @author Gerta Sheganaku
 */
@Slf4j
//@Component
@Scope("prototype")
public class DockerProcessOptimizationResults implements ProcessOptimizationResults {

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
        List<String> a = new ArrayList<>();

        StringBuilder stringBuilder2 = new StringBuilder();
        
        stringBuilder2.append("------------------------- VMs running ----------------------------\n");
        List<VirtualMachine> vMs = cacheVirtualMachineService.getAllVMs();
        for(VirtualMachine vm : vMs) {
            if(vm.isLeased() && vm.isStarted()) {
                stringBuilder2.append(vm.toString()).append("\n");
            }
        }
        
        stringBuilder2.append("------------------------- Dockers running ----------------------------\n");
        List<DockerContainer> containers = cacheDockerService.getAllDockerContainers();
        for(DockerContainer container : containers) {
            if(container.isRunning() && container.getVirtualMachine() != null) {
                stringBuilder2.append(container.toString()).append("\n");
            }
        }

        List<WorkflowElement> allRunningWorkflowInstances = cacheWorkflowService.getRunningWorkflowInstances();
        stringBuilder2.append("------------------------ Tasks running ---------------------------\n");
        List<ProcessStep> nextSteps = placementHelper.getNotStartedUnfinishedSteps();

        getRunningTasks(optimize, tau_t, vmsToStart, containersToDeploy, scheduledForExecution, y, x, a, stringBuilder2, vMs, allRunningWorkflowInstances, nextSteps);

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
        
        stringBuilder2.append("-------------------------- a results -----------------------------\n");
        for (String s : a) {
            stringBuilder2.append(s).append("=").append(optimize.get(s)).append("\n");
        }
        
        stringBuilder2.append("----------- VM should be used (running or has to be started): ----\n");
        for (VirtualMachine virtualMachine : vmsToStart) {
            stringBuilder2.append(virtualMachine).append("\n");
        }

        stringBuilder2.append("----------------------- Tasks to be started ----------------------\n");


        for (ProcessStep processStep : scheduledForExecution) {
            stringBuilder2.append("Task-TODO: ").append(processStep).append("\n");
        }
        stringBuilder2.append("------------------------------------------------------------------\n");

        for (DockerContainer container : containersToDeploy) {
            stringBuilder2.append("Containers To Deploy: ").append(container).append("\n");
        }
        stringBuilder2.append("------------------------------------------------------------------\n");

        cleanupContainers(optimize);
        
        stringBuilder2.append("----------- Container should be used (running or has to be started): ----\n");
        for (DockerContainer container : cacheDockerService.getAllDockerContainers()) {
            stringBuilder2.append(container).append("\n");
        }
        stringBuilder2.append("------------------------------------------------------------------\n");
        log.info(stringBuilder2.toString().replaceAll("(\r\n|\n)", "\r\n                                                                                                     "));

        serviceExecutionController.startInvocation(scheduledForExecution, containersToDeploy);

        cleanupVMs(tau_t);
        
        return new AsyncResult<Boolean>(true);
    }


	


	private void getRunningTasks(Result optimize, Date tau_t, List<VirtualMachine> vmsToStart, List<DockerContainer> containersToDeploy, List<ProcessStep> scheduledForExecution, List<String> y, List<String> c, List<String> a, StringBuilder stringBuilder2, List<VirtualMachine> vMs, List<WorkflowElement> allWorkflowInstances, List<ProcessStep> nextSteps) {
        for (Element workflow : allWorkflowInstances) {
            List<ProcessStep> runningSteps = placementHelper.getRunningProcessSteps(workflow.getName());
            for (ProcessStep runningStep : runningSteps) {
                if(runningStep.getScheduledAtContainer() != null) {
                	if(runningStep.getScheduledAtContainer().isRunning()) {
                		stringBuilder2.append("Task-Running: ").append(runningStep).append("\n");
                	}
                }
            }

            for (ProcessStep processStep : nextSteps) {
                if (!processStep.getWorkflowName().equals(workflow.getName())) {
                    continue;
                }
                List<DockerContainer> containers = cacheDockerService.getDockerContainers(processStep);
                processXValues(optimize, tau_t, containersToDeploy, scheduledForExecution, c, containers, processStep);
            }

        }
        
        
        for (DockerContainer container : containersToDeploy) {
        	System.out.println("PROCESSING A VALUES FOR CONTAINER: "+container);
            processAYValues(optimize, tau_t, vmsToStart, scheduledForExecution, y, a, vMs, container);
        }
    }

    
    private void processXValues(Result optimize, Date tau_t, List<DockerContainer> containersToDeploy, List<ProcessStep> scheduledForExecution, List<String> c, List<DockerContainer> containers, ProcessStep processStep) {
    	for (DockerContainer container : containers) {
        	String x_s_c = placementHelper.getDecisionVariableX(processStep, container);
        	
            Number x_s_c_number = optimize.get(x_s_c);

            if (!c.contains(x_s_c)) {
                c.add(x_s_c);
                if(x_s_c_number != null) {
                	int x_s_c_number_int = toInt(x_s_c_number);
	                if (x_s_c_number_int == 1) {
	                	containersToDeploy.add(container);
//	                	System.out.println("X S C WAS NOT NULL for " + x_s_c + " CONTAINERS TO DEPLOY WAS UPDATED");
	
	                }
//	            	System.out.println("X S C WAS NOT NULL for " + x_s_c + "Value was: " + x_s_c_number_int);

                } else {
//                	System.out.println("X S C WAS NULL for " + x_s_c);
                }
            }

            if (x_s_c_number == null || toInt(x_s_c_number) == 0) {
                continue;
            }

            if (toInt(x_s_c_number) == 1 && !scheduledForExecution.contains(processStep) && processStep.getStartDate() == null) {
                processStep.setScheduledForExecution(true, tau_t, container);
                scheduledForExecution.add(processStep);
                if (!containersToDeploy.contains(container)) {
                    containersToDeploy.add(container);
                }
            }
        }
    }
    
    private void processAYValues(Result optimize, Date tau_t, List<VirtualMachine> vmsToStart, List<ProcessStep> scheduledForExecution, List<String> y, List<String> a, List<VirtualMachine> vMs, DockerContainer container) {
    	for (VirtualMachine virtualMachine : vMs) {
        	String a_c_v = placementHelper.getDecisionVariableA(container, virtualMachine);
            String y_v_k = placementHelper.getDecisionVariableY(virtualMachine);

            Number a_c_v_number = optimize.get(a_c_v);
            Number y_v_k_number = optimize.get(y_v_k);

            if (!y.contains(y_v_k)) {
                y.add(y_v_k);
                if (toInt(y_v_k_number) >= 1) {
                    vmsToStart.add(virtualMachine);
                    Date date = new Date();
                    if (virtualMachine.getToBeTerminatedAt() != null) {
                        date = virtualMachine.getToBeTerminatedAt();
                    }

                    virtualMachine.setToBeTerminatedAt(new Date(date.getTime() +
                    		(placementHelper.getLeasingDuration(virtualMachine) * toInt(y_v_k_number))));
                }
            }

            if (!a.contains(a_c_v)) {
            	a.add(a_c_v);
            }
            
            if (a_c_v_number == null || toInt(a_c_v_number) == 0) {
                continue;
            }

            if (toInt(a_c_v_number) == 1) {
            	container.setVirtualMachine(virtualMachine);
                if (!vmsToStart.contains(virtualMachine)) {
                	vmsToStart.add(virtualMachine);
                }
            }
        }
    }

    private void cleanupContainers(Result optimize) {
        List<DockerContainer> containers = cacheDockerService.getAllDockerContainers();
        List<VirtualMachine> vms = cacheVirtualMachineService.getAllVMs();

//        for(VirtualMachine vm : vms) {
//	        for (DockerContainer container : containers) {
//	        	String decisionVariableA = placementHelper.getDecissionVariableA(container, vm);
//	        	int a = toInt(optimize.get(decisionVariableA));
//	        	
//	            if (a!=1 && (container.getVirtualMachine()!=null)) {
//					System.out.println("CONTAINER IS CLOSED: "+ container + " it was on VM: " + container.getVirtualMachine() + " VarA " + decisionVariableA);
//	            	System.out.println("VM    :" +vm);
//					placementHelper.stopDockerContainer(container);
//					
//	            }
//	        }
//        }
        

        for (DockerContainer container : containers) {
        	VirtualMachine vm = container.getVirtualMachine();
        	if(vm != null) {
            	String decisionVariableA = placementHelper.getDecisionVariableA(container, vm);
            	int a = toInt(optimize.get(decisionVariableA));
            	
                if (a!=1) {
    				System.out.println("CONTAINER IS CLOSED: "+ container + " it was on VM: " + container.getVirtualMachine() + " VarA " + decisionVariableA);
                	System.out.println("VM    :" +vm);
    				placementHelper.stopDockerContainer(container);
    				
                }
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
