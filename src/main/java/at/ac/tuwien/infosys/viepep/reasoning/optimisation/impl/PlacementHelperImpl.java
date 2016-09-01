package at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl;

import at.ac.tuwien.infosys.viepep.connectors.ViePEPClientService;
import at.ac.tuwien.infosys.viepep.database.entities.*;
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
    public List<ProcessStep> getUnfinishedSteps() {

        List<ProcessStep> processSteps = new ArrayList<>();
        for(WorkflowElement workflowElement : cacheWorkflowService.getAllWorkflowElements()) {
            List<Element> flattenWorkflowList = getFlattenWorkflow(new ArrayList<Element>(), workflowElement);
            for(Element element : flattenWorkflowList) {
                if(element instanceof ProcessStep && element.getFinishedAt() == null && ((ProcessStep) element).getStartDate() == null) {
                    processSteps.add((ProcessStep) element);
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
    public List<Element> getNextSteps(String workflowInstanceId) {

        List<WorkflowElement> nextWorkflows = cacheWorkflowService.getRunningWorkflowInstances();

        for (Element workflow : nextWorkflows) {
            if (workflow.getName().equals(workflowInstanceId)) {
                List<Element> nextStepElements = new ArrayList<>();
                nextStepElements.addAll(getNextSteps(workflow));
                return nextStepElements;
            }
        }
        return new ArrayList<>();
    }

    @Override
    public List<Element> getRunningProcessSteps(String workflowInstanceId) {
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
    public long getRemainingSetupTime(String vmId, Date now) {
        for (VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
            if (vm.getName().equals(vmId)) {
                Date startedAt = vm.getStartedAt();
                if (vm.isLeased() && startedAt != null && !vm.isStarted()) {
                    long startupTime = vm.getStartupTime();
                    long serviceDeployTime = vm.getDeployTime();
                    long nowTime = now.getTime();
                    long startedAtTime = startedAt.getTime();
                    long remaining = (startedAtTime + startupTime + serviceDeployTime) - nowTime;

                    if (remaining > 0) {         //should never be < 0
                        return remaining;
                    }
                    else {
                        return startedAtTime;
                    }
                }
                if (vm.isStarted()) {
                    return 0;
                }
            }
        }
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
    public List<Element> getRunningProcessSteps(List<Element> elements) {
        List<Element> steps = new ArrayList<>();
        for (Element element : elements) {
            if (element instanceof ProcessStep) {
                if (((ProcessStep) element).getStartDate() != null && ((ProcessStep) element).getFinishedAt() == null) {
                    if (!steps.contains(element)) {
                        steps.add(element);
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

        ReportingAction report = new ReportingAction(new Date(), virtualMachine.getName(), VMAction.STOPPED);
        reportDaoService.save(report);
    }

    @Override
    public List<Element> getNextSteps(Element workflow) {
        List<Element> nextSteps = new ArrayList<>();
        if (workflow instanceof ProcessStep) {
            if (!((ProcessStep) workflow).hasBeenExecuted() && ((ProcessStep) workflow).getStartDate() == null) {
                nextSteps.add(workflow);
            }
            return nextSteps;
        }
        for (Element element : workflow.getElements()) {
            if (element instanceof ProcessStep) {
                if ((!((ProcessStep) element).hasBeenExecuted()) && (((ProcessStep) element).getStartDate() == null)) {
                    nextSteps.add(element);
                    return nextSteps;
                }
                else if ((((ProcessStep) element).getStartDate() != null) && ((ProcessStep) element).getFinishedAt() == null) {
                    //Step is still running, ignore next step
                    return nextSteps;
                }
            }
            else {
                List<Element> elementList = element.getElements();
                if (element instanceof ANDConstruct) {
                    for (Element subElement : elementList) {
                        nextSteps.addAll(getNextSteps(subElement));
                    }
                }
                else if (element instanceof XORConstruct) {
                    int size = elementList.size();
                    if (element.getParent().getNextXOR() == null) {
                        Random random = new Random();
                        int i = random.nextInt(size);
                        Element subelement1 = elementList.get(i);
                        element.getParent().setNextXOR(subelement1);
                        nextSteps.addAll(getNextSteps(subelement1));
                        elementDaoService.update(element.getParent());

                    }
                    else {
                        Element subelement1 = element.getParent().getNextXOR();
                        nextSteps.addAll(getNextSteps(subelement1));
                    }
                }
                else if (element instanceof LoopConstruct) {
                    LoopConstruct loopConstruct = (LoopConstruct) element;
                    for (Element subElement : elementList) {
                        if (subElement instanceof ProcessStep) {
                            ProcessStep processStep = (ProcessStep) subElement;

                            if (!(processStep).hasBeenExecuted() && (processStep).getStartDate() == null) {
                                nextSteps.add(processStep);
                                return nextSteps;
                            }
                            else {
                                boolean lastElement = subElement.equals(elementList.get(elementList.size() - 1));
                                Random random = new Random();
                                boolean rand = random.nextInt(2) == 1;
                                if (lastElement && loopConstruct.getNumberOfIterationsInWorstCase() > loopConstruct.getIterations() && rand) {
                                    loopConstruct.setIterations(loopConstruct.getIterations() + 1);
                                    nextSteps.add(elementList.get(0));
                                    resetChildren(elementList);
                                    
                                    elementDaoService.update(workflow);
                                    return nextSteps;
                                }
                            }
                        }
                        else {
                            nextSteps.addAll(getNextSteps(subElement));
                        }
                    }
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
}
