package at.ac.tuwien.infosys.viepep.reasoning.optimisation.docker;


import at.ac.tuwien.infosys.viepep.connectors.ViePEPClientService;
import at.ac.tuwien.infosys.viepep.connectors.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElement;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepep.reasoning.service.ServiceInvoker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 *
 */
@Component
public class DockerOptimizer {

    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private ViePEPClientService viePEPClientService;
    @Autowired
    private ViePEPDockerControllerService viePEPDockerControllerService;
    @Autowired
    private ServiceInvoker serviceInvoker;


    private DockerOptimizer() {
    }

    public void startOptimation() {
        //to test: create simple sequential process consisting out of 3 steps with different type

        // check database for queued process executions

        List<WorkflowElement> workflowElementList = cacheWorkflowService.getAllWorkflowElements();


        for (WorkflowElement workflowElement : workflowElementList) {
            // List<Element> elements = workflowElement.getElements();
            //  long deadline = workflowElement.getDeadline();


            //find out which process step types exist


            // count NEXT invocations per type #see PlacementDaoHelperImpl.getNextSteps()
        }


        // compute resources demand per type
        // each processType has a ServiceType which defines resource demand
        // sum up resources demand

        // start virtual machines

        // deploy docker container
        // viePEPDockerControllerService


        //start invocation
        // serviceInvoker

    }
}
