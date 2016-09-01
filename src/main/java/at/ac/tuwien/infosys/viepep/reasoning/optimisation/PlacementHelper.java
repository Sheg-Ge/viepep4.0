package at.ac.tuwien.infosys.viepep.reasoning.optimisation;

import at.ac.tuwien.infosys.viepep.database.entities.Element;
import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;

import java.util.Date;
import java.util.List;

/**
 * Created by philippwaibel on 18/05/16.
 */
public interface PlacementHelper {


    void setFinishedWorkflows();

    List<ProcessStep> getUnfinishedSteps();

    List<Element> getFlattenWorkflow(List<Element> flattenWorkflowList, Element parentElement);

    List<Element> getNextSteps(String workflowInstanceId);

    List<Element> getRunningProcessSteps(String workflowInstanceId);

    long getRemainingSetupTime(String vmId, Date now);

    List<Element> getRunningSteps();

    List<Element> getRunningProcessSteps(List<Element> elements);

    void terminateVM(VirtualMachine virtualMachine);

    List<Element> getNextSteps(Element workflow);

    void resetChildren(List<Element> elementList);
}
