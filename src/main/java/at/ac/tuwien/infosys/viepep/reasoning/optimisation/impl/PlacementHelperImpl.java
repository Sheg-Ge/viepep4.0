package at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl;

import at.ac.tuwien.infosys.viepep.connectors.ViePEPClientService;
import at.ac.tuwien.infosys.viepep.connectors.impl.ViePEPDockerControllerServiceImpl;
import at.ac.tuwien.infosys.viepep.connectors.impl.exceptions.CouldNotStopDockerException;
import at.ac.tuwien.infosys.viepep.database.entities.*;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepep.database.services.ElementDaoService;
import at.ac.tuwien.infosys.viepep.database.services.ReportDaoService;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.PlacementHelper;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Created by philippwaibel on 18/05/16. edited by Gerta Sheganaku
 */
@Component
@Slf4j
public class PlacementHelperImpl implements PlacementHelper {

    @Autowired
    private ElementDaoService elementDaoService;
    @Autowired
    private ViePEPClientService viePEPClientService;
    @Autowired
    private ReportDaoService reportDaoService;
    @Autowired
    private ViePEPDockerControllerServiceImpl dockerControllerService;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;

    @Value("${simulate}")
    private boolean simulate;

    @Override
    public void setFinishedWorkflows() {
        List<WorkflowElement> nextWorkflows = cacheWorkflowService.getRunningWorkflowInstances();

        for (WorkflowElement workflow : nextWorkflows) {

            List<Element> flattenWorkflowList = getFlattenWorkflow(new ArrayList<>(), workflow);

            boolean workflowDone = true;
            Date finishedDate = new Date(0);
            for (Element element : flattenWorkflowList) {

                if (element instanceof ProcessStep && element.isLastElement()) {
                    if (((ProcessStep) element).isHasToBeExecuted()) {
                        if (element.getFinishedAt() == null) {
                            workflowDone = false;
                            break;
                        }
                        else {
                            if (element.getFinishedAt().after(finishedDate)) {
                                finishedDate = element.getFinishedAt();
                            }
                        }
                    }
                }
            }

            if (workflowDone) {
                for (Element element : flattenWorkflowList) {
                    if (element.getFinishedAt() != null) {
                        element.setFinishedAt(finishedDate);
                    }
                }
                workflow.setFinishedAt(finishedDate);
                cacheWorkflowService.deleteRunningWorkflowInstance(workflow);
            }

        }

    }

    @Override
    public List<ProcessStep> getNotStartedUnfinishedSteps() {

        List<ProcessStep> processSteps = new ArrayList<>();
        for(WorkflowElement workflowElement : cacheWorkflowService.getRunningWorkflowInstances()) {//.getAllWorkflowElements()) {
            List<Element> flattenWorkflowList = getFlattenWorkflow(new ArrayList<Element>(), workflowElement);
            for(Element element : flattenWorkflowList) {
                if(element instanceof ProcessStep && element.getFinishedAt() == null) {
                	if(((ProcessStep) element).getStartDate() == null && ((ProcessStep)element).getScheduledStartedAt() == null) {
                        processSteps.add((ProcessStep) element);                		
                	}
                }
            }
        }

        return processSteps;
    }

    @Override
    public List<Element> getFlattenWorkflow(List<Element> flattenWorkflowList, Element parentElement) {
        if (parentElement.getElements() != null) {
            for (Element element : parentElement.getElements()) {
                flattenWorkflowList.add(element);
                flattenWorkflowList = getFlattenWorkflow(flattenWorkflowList, element);
            }
        }
        return flattenWorkflowList;
    }

    @Override
    public List<ProcessStep> getNextSteps(String workflowInstanceId) {

        List<WorkflowElement> nextWorkflows = cacheWorkflowService.getRunningWorkflowInstances();

        for (Element workflow : nextWorkflows) {
            if (workflow.getName().equals(workflowInstanceId)) {
                List<ProcessStep> nextStepElements = new ArrayList<>();
                nextStepElements.addAll(getNextSteps(workflow));
                return nextStepElements;
            }
        }
        return new ArrayList<>();
    }

    @Override
    public List<ProcessStep> getRunningProcessSteps(String workflowInstanceId) {
        List<WorkflowElement> workflowInstances = cacheWorkflowService.getRunningWorkflowInstances();
        for (Element workflowInstance : workflowInstances) {
            if (workflowInstance.getName().equals(workflowInstanceId)) {
                List<Element> workflowElement = new ArrayList<>();
                workflowElement.add(workflowInstance);
                return getRunningProcessSteps(workflowElement);
            }
        }
        return new ArrayList<>();
	}

	@Override
	public long getRemainingSetupTime(VirtualMachine vm, Date now) {
		Date startedAt = vm.getStartedAt();
		if (vm.isLeased() && startedAt != null && !vm.isStarted()) {
			long startupTime = vm.getStartupTime();
			long serviceDeployTime = vm.getDeployTime();
			long nowTime = now.getTime();
			long startedAtTime = startedAt.getTime();
			long remaining = (startedAtTime + startupTime + serviceDeployTime) - nowTime;

			if (remaining > 0) { // should never be < 0
				return remaining;
			} else {
				return startedAtTime;
			}
		}
		if (vm.isStarted()) {
			return 0;
		}

		return 0;
	}

	@Override
	public long getRemainingSetupTime(DockerContainer container, Date now) {
		VirtualMachine vm = container.getVirtualMachine();
		if(vm==null){
			log.error("VM not set for scheduled service on container: " + container);
			return 0;
		} else if(!vm.isLeased()) {
			log.error("VM " + vm + " not leased for scheduled service on container: " + container);
			return 0;
		}
		
		Date vmStartedAt = vm.getStartedAt();
		if (vm.isLeased() && vmStartedAt != null && !vm.isStarted()) {
			long vmStartupTime = vm.getStartupTime();
			long containerDeployTime = container.getDeployTime();
			long containerStartupTime = container.getStartupTime();
			long nowTime = now.getTime();
			long startedAtTime = vmStartedAt.getTime();
			
			long remaining = (startedAtTime + vmStartupTime + containerDeployTime + containerStartupTime) - nowTime;

			if (remaining > 0) { // should never be < 0
				return remaining;
			} else {
				return startedAtTime;
			}
		}
		if (vm.isStarted()) {
			Date containerStartedAt = container.getStartedAt();
			if (containerStartedAt != null && !container.isRunning()) {
				long containerDeployTime = container.getDeployTime();
				long containerStartupTime = container.getStartupTime();
				long nowTime = now.getTime();
				long startedAtTime = containerStartedAt.getTime();
				long remaining = (startedAtTime + containerDeployTime + containerStartupTime) - nowTime;

				if (remaining > 0) { // should never be < 0
					return remaining;
				} else {
					return 0;//startedAtTime;
				}
			}
			if (container.isRunning()) {
				return 0;
			}		}

		return 0;
	}
	
    @Override
    public List<Element> getRunningSteps() {
        List<Element> running = new ArrayList<>();
        for (WorkflowElement allWorkflowInstance : cacheWorkflowService.getRunningWorkflowInstances()) {//cacheWorkflowService.getAllWorkflowElements()) {     // TODO use getNextWorkflowInstances?
            running.addAll(getRunningProcessSteps(allWorkflowInstance.getElements()));
        }

        return running;
    }

    @Override
    public List<ProcessStep> getRunningProcessSteps(List<Element> elements) {
        List<ProcessStep> steps = new ArrayList<>();
        for (Element element : elements) {
            if (element instanceof ProcessStep) {
                if ((((ProcessStep) element).getStartDate() != null || ((ProcessStep) element).getScheduledStartedAt() != null)&& ((ProcessStep) element).getFinishedAt() == null) {
                	if (!steps.contains(element)) {
                        steps.add((ProcessStep) element);
                    }
                }
                // ignore else
            }
            else {
                steps.addAll(getRunningProcessSteps(element.getElements()));
            }

        }
        return steps;
    }

    @Override
    public void terminateVM(VirtualMachine virtualMachine) {
        if (!simulate) {
            log.info("Terminate: " + virtualMachine);
            viePEPClientService.terminateInstanceByIP(virtualMachine.getIpAddress());
        }
        virtualMachine.terminate();

        ReportingAction report = new ReportingAction(new Date(), virtualMachine.getName(), Action.STOPPED);
        reportDaoService.save(report);
    }
    
    public void terminateVM(VirtualMachine virtualMachine, Date date) {
        if (!simulate) {
            log.info("Terminate: " + virtualMachine);
            viePEPClientService.terminateInstanceByIP(virtualMachine.getIpAddress());
        }
        virtualMachine.terminate();

        ReportingAction report = new ReportingAction(date, virtualMachine.getName(), Action.STOPPED);
        reportDaoService.save(report);
    }
    
    public void stopDockerContainer(DockerContainer container) {
    	VirtualMachine vm = container.getVirtualMachine();
    	if(!simulate) {
    		log.info("Stop Container: " + container + " on VM: " + vm);
			try {
				dockerControllerService.stopDocker(vm, container);
			} catch (CouldNotStopDockerException e) {
				e.printStackTrace();
			}
    	}

    	container.shutdownContainer();

    	DockerReportingAction report = new DockerReportingAction(new Date(), container.getName(), vm.getName(), Action.STOPPED);
        reportDaoService.save(report);
    }

    @Override
    public List<ProcessStep> getNextSteps(Element workflow) {
        List<ProcessStep> nextSteps = new ArrayList<>();
        if (workflow instanceof ProcessStep) {
            if (!((ProcessStep) workflow).hasBeenExecuted() && ((ProcessStep) workflow).getStartDate() == null && ((ProcessStep) workflow).getScheduledStartedAt() == null) {
                nextSteps.add((ProcessStep) workflow);
            }
            return nextSteps;
        }
        for (Element element : workflow.getElements()) {
            if (element instanceof ProcessStep) {
                if ((!((ProcessStep) element).hasBeenExecuted()) && (((ProcessStep) element).getStartDate() == null) && ((ProcessStep) element).getScheduledStartedAt() == null) {
                    nextSteps.add((ProcessStep) element);
                    return nextSteps;
                }
                else if ((((ProcessStep) element).getStartDate() != null || ((ProcessStep) element).getScheduledStartedAt() != null) && ((ProcessStep) element).getFinishedAt() == null) {
                    //Step is still running, ignore next step
                    return nextSteps;
                }
            }
            else {
                List<Element> subElementList = element.getElements();
                if (element instanceof ANDConstruct) {
                    for (Element subElement : subElementList) {
                        nextSteps.addAll(getNextSteps(subElement));
                    }
                }
                else if (element instanceof XORConstruct) {
//                    int size = subElementList.size();
//                    if (element.getParent().getNextXOR() == null) {
//                        Random random = new Random();
//                        int i = random.nextInt(size);
//                        Element subelement1 = subElementList.get(i);
//                        element.getParent().setNextXOR(subelement1);
//                        nextSteps.addAll(getNextSteps(subelement1));
//         //INVALID due to in memory cache only               elementDaoService.update(element.getParent());
//
//                    }
//                    else {
                        nextSteps.addAll(getNextSteps(element.getParent().getNextXOR()));
//                    }
                }
                else if (element instanceof LoopConstruct) {
                	System.out.println("*********** ***** number of Executions: " + element.getNumberOfExecutions() + " Number of Iterations: "+ ((LoopConstruct) element).getNumberOfIterationsToBeExecuted());
                	if((element.getNumberOfExecutions() < ((LoopConstruct) element).getNumberOfIterationsToBeExecuted())) {
                		for(Element subElement : subElementList) {
                			nextSteps.addAll(getNextSteps(subElement));
                		}
                		
//                		int futureIterations = ((LoopConstruct) element).getNumberOfIterationsToBeExecuted() - element.getNumberOfExecutions() - 1;
//                		if(futureIterations >= 1){
//                			for(int i = 0; i<futureIterations; i++) {
//                				
//                			}
//                		}
                		
                		//	((LoopConstruct) element).setIterations(((LoopConstruct) element).getIterations()+1);
                		
                	}
//                    LoopConstruct loopConstruct = (LoopConstruct) element;
//                    for (Element subElement : subElementList) {
//                        if (subElement instanceof ProcessStep) {
//                            ProcessStep processStep = (ProcessStep) subElement;
//
//                            if (!(processStep).hasBeenExecuted() && (processStep).getStartDate() == null) {
//                                nextSteps.add(processStep);
//                                return nextSteps;
//                            }
//                            else {
//                                boolean lastElement = subElement.equals(subElementList.get(subElementList.size() - 1));
//                                Random random = new Random();
//                                boolean rand = random.nextInt(2) == 1;
//                                if (lastElement && loopConstruct.getNumberOfIterationsInWorstCase() > loopConstruct.getIterations() && rand) {
//                                    loopConstruct.setIterations(loopConstruct.getIterations() + 1);
//                                    nextSteps.add(subElementList.get(0));
//                                    resetChildren(subElementList);
//                                    
//         //INVALID due to in memory cache only                           elementDaoService.update(workflow);
//                                    return nextSteps;
//                                }
//                            }
//                        }
//                        else {
//                            nextSteps.addAll(getNextSteps(subElement));
//                        }
//                    }
                }
                else { //sequence
                    nextSteps.addAll(getNextSteps(element));
                }
            }
            if (nextSteps.size() > 0) {
                return nextSteps;
            }
        }
        return nextSteps;
    }

    @Override
    public void resetChildren(List<Element> elementList) {
        if (elementList != null) {
            for (Element element : elementList) {
                if (element instanceof ProcessStep) {
                    ((ProcessStep) element).reset();

                }
                else {
                    resetChildren(element.getElements());
                }
            }
        }
    }
    
    
	// ******************** Helpers FOR VMs

    /**
     * indicates if a VM with ID v_k is leased
     * @return 0 if not leased, 1 if leased
     */
    public int getBeta(VirtualMachine vm) {
        return vm.isLeased() ? 1 : 0;
    }
    
    /**
     * indicates if a specific service (serviceType) runs on a virtual machine (v, k)
     * @return 0 if false, 1 if true
     */
    public int getZ(String serviceType, VirtualMachine vm) {
    	if (vm.isLeased() && vm.getServiceType() != null && vm.getServiceType().name().equals(serviceType)) {
    		   return 1;   
        }
        return 0;
    }
    
    /**
     * @return the general leasing duration for a particular vm (v,k) vm
     */
    public long getLeasingDuration(VirtualMachine vm) {
    	return vm.getVmType().getLeasingDuration();
    }
    
    /**
     * @return the remaining leasing duration for a particular vm (v,k) starting from tau_t
     */
    public long getRemainingLeasingDuration(Date tau_t, VirtualMachine vm) {
        Date startedAt = vm.getStartedAt();
        if (startedAt == null) {
            return 0;
        }
        Date toBeTerminatedAt = vm.getToBeTerminatedAt();
        if (toBeTerminatedAt == null) {
            toBeTerminatedAt = new Date(startedAt.getTime() + vm.getVmType().getLeasingDuration());
        }
        long remainingLeasingDuration = toBeTerminatedAt.getTime() - tau_t.getTime();
        if (remainingLeasingDuration < 0) {
            remainingLeasingDuration = 0;
        }
        return remainingLeasingDuration;
        
    }
    
    @Override
    public long getBTU(VirtualMachine vm) {
    	return vm.getVmType().getLeasingDuration();
    }
    
    public double getSuppliedCPUPoints(VirtualMachine vm) {
    	return vm.getVmType().getCpuPoints();
    }
    
    public double getSuppliedRAMPoints(VirtualMachine vm) {
    	return vm.getVmType().getRamPoints();
    }

    public String getDecisionVariableY(VirtualMachine vm) {
		return "y_" + vm.getName();
	}
	
    /**
     * gamma is the amount of all leased VMs
     *
     * @param vmType defines specific VMType
     * @return gamma variable for a particular VM Type
     */
    public String getGammaVariable(VMType vmType) {
        return "gamma_" + vmType.getIdentifier();
    }
	public String getFValueCVariable(VirtualMachine vm) {
		return "f_" + vm.getName() + "^C";
	}
	public String getFValueRVariable(VirtualMachine vm) {
		return "f_" + vm.getName() + "^R";
	}
	public String getGVariable(VirtualMachine vm) {
		return "g_" + vm.getName();
	}
	public String getGYVariable(VirtualMachine vm) {
		return "g_y_" + vm.getName();
	}
	
	
	// ******************** Helpers FOR CONTAINERs
	
	public double getSuppliedRAMPoints(DockerContainer dockerContainer) {
		return dockerContainer.getContainerConfiguration().getRAM();
	}
	
	public double getSuppliedCPUPoints(DockerContainer dockerContainer) {
		return dockerContainer.getContainerConfiguration().getCPUPoints();
	}
	
	public String getDecisionVariableA(DockerContainer dockerContainer, VirtualMachine vm) {
		return "a_" + dockerContainer.getName() + "," + vm.getName();
	}
	
	// ******************** Helpers FOR WORKFLOWs
	
    public double getPenaltyCostPerQoSViolationForProcessInstance(WorkflowElement workflowelement) {
        return workflowelement.getPenaltyPerViolation();
    }
    
    public long getEnactmentDeadline(WorkflowElement workflowInstance) {
    	return workflowInstance.getDeadline(); //- workflowInstance.getRemainingExecutionTime(); 
    }

    @Override
    public double getRequiredCPUPoints(ProcessStep step) {
    	return step.getServiceType().getCpuLoad();
    }
    
    @Override
    public double getRequiredRAMPoints(ProcessStep step) {
    	return step.getServiceType().getMemory();
    }
    
	public String getDecisionVariableX(Element step, VirtualMachine vm) {
		return "x_" + step.getName() + "," + vm.getName();
	}
	
	@Override
	public String getDecisionVariableX(Element step, DockerContainer container) {
		return "x_" + step.getName() + "," + container.getName();
	}

	public String getExecutionTimeViolationVariable(WorkflowElement workflowInstance) {
		return  "e_w_" + workflowInstance.getName() + "^p";
	}

	public String getExecutionTimeVariable(WorkflowElement workflowInstance) {
		return "e_w_" + workflowInstance.getName();
	}
	
	public String getATimesG(VirtualMachine vm, DockerContainer container) {
		String decisionVariableA = getDecisionVariableA(container, vm);
        String decisionVariableG = getGVariable(vm);
        return decisionVariableA + "_times_" + decisionVariableG;
	}

	public String getATimesT1(DockerContainer container, VirtualMachine vm) {
		String decisionVariableA = getDecisionVariableA(container, vm);
		return decisionVariableA + "_times_tau_t_1";
	}
	
	@Override
	public String getAtimesX(ProcessStep step, DockerContainer container, VirtualMachine vm) {
		String decisionVariableA = getDecisionVariableA(container, vm);
		String decisionVariableX = getDecisionVariableX(step, container);
		return decisionVariableA + "_times_" + decisionVariableX;
	}
	
	public int imageForStepEverDeployedOnVM(ProcessStep step, VirtualMachine vm) {
		for(DockerContainer container : vm.getDeployedContainers()){
			if(step.getServiceType().getName().equals(container.getDockerImage().getServiceName())) {
				return 1;
			}
		}	
		return 0;
	}
	
	public int imageForContainerEverDeployedOnVM(DockerContainer container, VirtualMachine vm) {
		for(DockerContainer containerOnVm : vm.getDeployedContainers()){
			if(container.getDockerImage().getServiceName().equals(containerOnVm.getDockerImage().getServiceName())) {
				return 1;
			}
		}
		return 0;
	}
}
