package at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl;

import at.ac.tuwien.infosys.viepep.connectors.ViePEPClientService;
import at.ac.tuwien.infosys.viepep.database.entities.*;
import at.ac.tuwien.infosys.viepep.database.services.ElementDaoService;
import at.ac.tuwien.infosys.viepep.database.services.ReportDaoService;
import at.ac.tuwien.infosys.viepep.database.services.VirtualMachineDaoService;
import at.ac.tuwien.infosys.viepep.database.services.WorkflowDaoService;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.PlacementHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Component
public class PlacementHelperImpl implements PlacementHelper {

    @Autowired
    private WorkflowDaoService workflowDaoService;
    @Autowired
    private ElementDaoService elementDaoService;
    @Autowired
    private VirtualMachineDaoService virtualMachineDaoService;
    @Autowired
    private ViePEPClientService viePEPClientService;
    @Autowired
    private ReportDaoService reportDaoService;

    @Value("${simulate}")
    private boolean simulate;

    private List<WorkflowElement> nextWorkflows = new ArrayList<>();
    private List<WorkflowElement> allWorkflowInstances = new ArrayList<>();
    private List<VirtualMachine> virtualMachines = new ArrayList<>();

    @Override
    public List<WorkflowElement> getNextWorkflowInstances(boolean cleanup) {
        List<WorkflowElement> workflows = nextWorkflows;
        if (nextWorkflows.isEmpty() || cleanup) {
            workflows = new ArrayList<>();
            List<WorkflowElement> list = workflowDaoService.getAllWorkflowElementsList();
            workflows.addAll(list);
        }

        List<WorkflowElement> newWorkflows = new ArrayList<>();
        for (WorkflowElement workflow : workflows) {
//            List<Element> nextSteps = getNextSteps(workflow.getName());
            if (workflow.getFinishedAt() == null) { //nextSteps != null && !nextSteps.isEmpty()) {
                newWorkflows.add(workflow);
            }
        }
        nextWorkflows = newWorkflows;
        return nextWorkflows;
    }

    public void setFinishedWorkflows() {
        List<WorkflowElement> nextWorkflows = getNextWorkflowInstances(false);

        for (WorkflowElement workflow : nextWorkflows) {

            List<Element> flattenWorkflowList = getFlattenWorkflow(new ArrayList<Element>(), workflow);

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
                workflowDaoService.update(workflow);
            }

        }

    }

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
//        if (nextWorkflows.isEmpty()) {
        List<WorkflowElement> nextWorkflows = getNextWorkflowInstances(false);
//        }
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
        List<WorkflowElement> workflowInstances = getNextWorkflowInstances(false);
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
        for (VirtualMachine vm : virtualMachines) {
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

//                    return remaining > 0 ? remaining : startupTime;  //should never be < 0
                }
                if (vm.isStarted()) {
                    return 0;
                }
            }
        }
        return 0;
    }

    @Override
    public List<Element> getRunningSteps(boolean update) {
        if (allWorkflowInstances.isEmpty() || update) {
            allWorkflowInstances = workflowDaoService.getAllWorkflowElementsList();             // TODO use getNextWorkflowInstances?
        }
        List<Element> running = new ArrayList<>();
        for (WorkflowElement allWorkflowInstance : allWorkflowInstances) {
            running.addAll(getRunningProcessSteps(allWorkflowInstance.getElements()));
        }

        return running;
    }

    @Override
    public void clear() {

        virtualMachines = new ArrayList<>();
        nextWorkflows = new ArrayList<>();
        allWorkflowInstances = new ArrayList<>();

    }

    private List<Element> getRunningProcessSteps(List<Element> elements) {
        List<Element> steps = new ArrayList<>();
        for (Element element : elements) {
            if (element instanceof ProcessStep) {
                if (((ProcessStep) element).getStartDate() != null && ((ProcessStep) element).getFinishedAt() == null) {
                    if (!steps.contains(element)) {
                        steps.add(element);
                    }
                }
                else {
                    //ignore
                }
            }
            else {
                steps.addAll(getRunningProcessSteps(element.getElements()));
            }

        }
        return steps;
    }

    @Override
    public List<VirtualMachine> getVMs(boolean update) {
        if (virtualMachines.isEmpty() || update) {
            virtualMachines = virtualMachineDaoService.getAllVms();
        }
        return virtualMachines;
    }

    @Override
    public WorkflowElement getWorkflowById(String workflowInstanceId) {
//            if (nextWorkflows.isEmpty()) {
        List<WorkflowElement> nextWorkflows = getNextWorkflowInstances(false);
//            }


        for (WorkflowElement nextWorkflow : nextWorkflows) {
            if (nextWorkflow.getName().equals(workflowInstanceId)) {
                return nextWorkflow;
            }
        }
        return null;
//        }
    }

    @Override
    public void terminateVM(VirtualMachine virtualMachine) {
        if (!simulate) {
            viePEPClientService.terminateInstanceByIP(virtualMachine.getIpAddress());
        }
        virtualMachine.terminate();

        virtualMachineDaoService.update(virtualMachine);
        ReportingAction report = new ReportingAction(new Date(), virtualMachine.getName(), VMAction.STOPPED);
        reportDaoService.save(report);
    }

    private List<Element> getNextSteps(Element workflow) {           // TODO split into several methods
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
                                    resetChilder(elementList);

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

    private void resetChilder(List<Element> elementList) {
        if (elementList != null) {
            for (Element element : elementList) {
                if (element instanceof ProcessStep) {
                    ((ProcessStep) element).reset();

                }
                else {
                    resetChilder(element.getElements());
                }
            }
        }
    }
}
