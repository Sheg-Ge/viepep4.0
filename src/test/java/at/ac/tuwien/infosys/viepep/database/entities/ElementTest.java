package at.ac.tuwien.infosys.viepep.database.entities;

import org.junit.Test;

import java.util.Date;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;


public class ElementTest {

    @Test
    public void testGetLastElementSeq() throws Exception {
        Element workflow = createSequenceWorkflow(new Date(), "100");
        ProcessStep lastExecutedElement = workflow.getLastExecutedElement();
        assertThat(lastExecutedElement.getName(), equalTo("100.2"));
    }

    @Test
    public void testGetLastElementAnd() throws Exception {
        Element workflow = createAndWorkflow(new Date(), "100", true, true, true, true);
        ProcessStep lastExecutedElement = workflow.getLastExecutedElement();
        assertThat(lastExecutedElement.getName(), equalTo("100.3"));
    }

    @Test
    public void testGetLastElementXor() throws Exception {
        Element workflow = createXORWorkflow(new Date(), "100");
        ProcessStep lastExecutedElement = workflow.getLastExecutedElement();
        assertThat(lastExecutedElement.getName(), equalTo("100.3"));

    }

    private Element createSequenceWorkflow(Date tau_t, String id) {
        Element workflow = new WorkflowElement(id, tau_t.getTime() + 1000 * 200, 200);
        Sequence seq = new Sequence(id + "-seq");
        ProcessStep step1 = new ProcessStep(id + ".0", true, ServiceType.Task1, workflow.getName());
        seq.addElement(step1);

        ProcessStep step2 = new ProcessStep(id + ".1", true, ServiceType.Task2, workflow.getName());
        seq.addElement(step2);

        ProcessStep step3 = new ProcessStep(id + ".2", true, ServiceType.Task3, workflow.getName());
        seq.addElement(step3);

        ProcessStep step4 = new ProcessStep(id + ".3", true, ServiceType.Task4, workflow.getName());
        seq.addElement(step4);
        
        workflow.addElement(seq);

        step1.setFinishedAt(tau_t);
        step2.setFinishedAt(new Date(tau_t.getTime() + 10000L));
        step3.setFinishedAt(new Date(tau_t.getTime() + 20000L));
        
        return workflow;
    }

    private Element createAndWorkflow(Date tau_t, String id, boolean first, boolean second1, boolean second2, boolean third) {
        Element workflow = new WorkflowElement(id + "", tau_t.getTime() + 1000 * 120, 200);
        Sequence seq = new Sequence(id + "-seq");
        ProcessStep step0 = new ProcessStep(id + ".0", first, ServiceType.Task1, workflow.getName());
        step0.setFinishedAt(new Date(tau_t.getTime() + 10000L));
        seq.addElement(step0);

        ANDConstruct and = new ANDConstruct(id + "-AND");
        ProcessStep step1 = new ProcessStep(id + ".1", second1, ServiceType.Task2, workflow.getName());
        step1.setFinishedAt(new Date(tau_t.getTime() + 20000L));
        and.addElement(step1);

        ProcessStep step2 = new ProcessStep(id + ".2", second2, ServiceType.Task3, workflow.getName());
        step2.setFinishedAt(new Date(tau_t.getTime() + 30000L));
        and.addElement(step2);

        seq.addElement(and);

        ProcessStep step3 = new ProcessStep(id + ".3", third, ServiceType.Task4, workflow.getName());
        step3.setFinishedAt(new Date(tau_t.getTime() + 40000L));
        seq.addElement(step3);

        workflow.addElement(seq);
        return workflow;
    }

    private Element createXORWorkflow(Date tau_t, String id) {
        Element workflow = new WorkflowElement(id + "", new Date().getTime() + 1000 * 120, 200);

        Sequence seq = new Sequence(id + "-seq");
        ProcessStep step0 = new ProcessStep(id + ".0", true, ServiceType.Task1, workflow.getName());
        step0.setFinishedAt(new Date(tau_t.getTime() + 10000L));
        seq.addElement(step0);

        XORConstruct xor = new XORConstruct(id + "-XOR");
        ProcessStep step1 = new ProcessStep(id + ".1.1", true, ServiceType.Task2, workflow.getName());
        step1.setFinishedAt(new Date(tau_t.getTime() + 20000L));
        xor.addElement(step1);

        ProcessStep step2 = new ProcessStep(id + ".1.2", true, ServiceType.Task3, workflow.getName());
        step2.setFinishedAt(new Date(tau_t.getTime() + 30000L));
        xor.addElement(step2);

        seq.addElement(xor);

        ProcessStep step3 = new ProcessStep(id + ".3", true, ServiceType.Task4, workflow.getName());
        step3.setFinishedAt(new Date(tau_t.getTime() + 40000L));
        seq.addElement(step3);

        workflow.addElement(seq);
        return workflow;
    }

}