package at.ac.tuwien.infosys.viepep.reasoning.optimisation;

import at.ac.tuwien.infosys.viepep.database.entities.Element;
import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.VMType;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElement;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;

import java.util.Date;
import java.util.List;

/**
 * Created by philippwaibel on 18/05/16. edited by Gerta Sheganaku
 */
public interface PlacementHelper {


    void setFinishedWorkflows();

    List<ProcessStep> getNotStartedUnfinishedSteps();

    List<Element> getFlattenWorkflow(List<Element> flattenWorkflowList, Element parentElement);

    List<ProcessStep> getNextSteps(String workflowInstanceId);

    List<ProcessStep> getRunningProcessSteps(String workflowInstanceId);

    long getRemainingSetupTime(VirtualMachine vm, Date now);

    List<Element> getRunningSteps();

    List<ProcessStep> getRunningProcessSteps(List<Element> elements);

    void terminateVM(VirtualMachine virtualMachine);

    List<ProcessStep> getNextSteps(Element workflow);

    void resetChildren(List<Element> elementList);

	String getGammaVariable(VMType vmType);

	String getExecutionTimeViolationVariable(WorkflowElement workflowInstance);
	
	String getExecutionTimeVariable(WorkflowElement workflowInstance);

	double getPenaltyCostPerQoSViolationForProcessInstance(WorkflowElement workflowInstance);

	String getDecisionVariableX(Element step, VirtualMachine vm);

	String getFValueCVariable(VirtualMachine vm);
	
	String getFValueRVariable(VirtualMachine vm);

	int getBeta(VirtualMachine vm);

	String getDecisionVariableY(VirtualMachine vm);

	int getZ(String type, VirtualMachine vm);

	long getLeasingDuration(VirtualMachine vm);

	long getRemainingLeasingDuration(Date tau_t, VirtualMachine vm);

	String getGVariable(VirtualMachine vm);

	double getSuppliedCPUPoints(VirtualMachine vm);

	double getSuppliedRAMPoints(VirtualMachine vm);

	double getRequiredCPUPoints(ProcessStep step);

	double getRequiredRAMPoints(ProcessStep step);

	long getEnactmentDeadline(WorkflowElement workflowInstance);

	String getDecisionVariableX(Element step, DockerContainer container);

	String getDecisionVariableA(DockerContainer dockerContainer, VirtualMachine vm);

	double getSuppliedCPUPoints(DockerContainer dockerContainer);

	double getSuppliedRAMPoints(DockerContainer dockerContainer);

	String getGYVariable(VirtualMachine vm);

	long getBTU(VirtualMachine vm);

	int imageForStepEverDeployedOnVM(ProcessStep step, VirtualMachine vm);

	void stopDockerContainer(DockerContainer container);

	int imageForContainerEverDeployedOnVM(DockerContainer dockerContainer, VirtualMachine vm);

	long getRemainingSetupTime(DockerContainer scheduledAtContainer, Date tau_t);

	String getATimesG(VirtualMachine vm, DockerContainer container);

	String getATimesT1(DockerContainer container, VirtualMachine vm);

	String getAtimesX(ProcessStep step, DockerContainer container, VirtualMachine vm);

}
