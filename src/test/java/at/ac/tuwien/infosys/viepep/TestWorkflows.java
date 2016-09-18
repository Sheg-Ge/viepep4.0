package at.ac.tuwien.infosys.viepep;

import java.util.Date;
import java.util.UUID;

import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.Sequence;
import at.ac.tuwien.infosys.viepep.database.entities.ServiceType;
import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElement;
import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElements;

public class TestWorkflows {

	private static final double DEADLINE_FACTOR = 1.5;

	public static WorkflowElements constructTestWorkflows(Integer ... processTypeIDs) {
		WorkflowElements result = new WorkflowElements();
		for(int processTypeID : processTypeIDs) {
			Date deadline = new Date();
	        double penalty = 200+(processTypeID*10);
	        WorkflowElement processInstance = TestWorkflows.getProcess1(processTypeID, deadline, penalty);
	        long execDuration = processInstance.calculateQoS();
	        processInstance.setDeadline((long) ((new Date().getTime()) + execDuration * DEADLINE_FACTOR));
	        result.getWorkflowElements().add(processInstance);
		}
        return result;
	}

	public static WorkflowElement getProcess1(Object identifier, Date deadline, double penalty) {
		if(!(identifier instanceof String)) {
			identifier = generateProcessInstanceID(identifier);
		}
        WorkflowElement workflow = new WorkflowElement(identifier.toString(), deadline.getTime(), penalty);
        Sequence seq = new Sequence(identifier + "-seq");
        ProcessStep elem1 = new ProcessStep(identifier + ".1", ServiceType.Task1, workflow.getName());
        seq.addElement(elem1);
        ProcessStep elem2 = new ProcessStep(identifier + ".2", ServiceType.Task2, workflow.getName());
        seq.addElement(elem2);
        ProcessStep elem = new ProcessStep(identifier + ".3", ServiceType.Task3, workflow.getName());
        elem.setLastElement(true);
        seq.addElement(elem);
        workflow.addElement(seq);

        return workflow;
    }

    public static String generateProcessInstanceID(Object ProcessTypeID) {
    	return UUID.randomUUID().toString().substring(0, 8) + "pr"+ProcessTypeID;
	}

}
