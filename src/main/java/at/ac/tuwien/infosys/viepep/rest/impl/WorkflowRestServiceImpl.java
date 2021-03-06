package at.ac.tuwien.infosys.viepep.rest.impl;

import at.ac.tuwien.infosys.viepep.database.entities.*;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl.ProcessInstancePlacementProblemServiceImpl;
import at.ac.tuwien.infosys.viepep.rest.WorkflowRestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Created by philippwaibel on 17/05/16.
 */
@RestController
@Slf4j
public class WorkflowRestServiceImpl implements WorkflowRestService {

    @Autowired
    private CacheWorkflowService cacheWorkflowService;

    @RequestMapping( value="/", method = RequestMethod.GET, consumes = MediaType.APPLICATION_XML_VALUE)
    public List<WorkflowElement> getWorkflows() throws Exception {
        return cacheWorkflowService.getAllWorkflowElements();
    }

    @Override
    @RequestMapping( value="/addWorkflowRequest", method = RequestMethod.POST, consumes = MediaType.APPLICATION_XML_VALUE)
    public void addWorkflow(@RequestBody WorkflowElement workflowElement) {
        cacheWorkflowService.addWorkflowInstance(workflowElement);
    }

    @Override
    @RequestMapping( value="/addWorkflowRequests", method = RequestMethod.POST, consumes = MediaType.APPLICATION_XML_VALUE)
    public void addWorkflow(@RequestBody WorkflowElements workflowElement) {

        synchronized (ProcessInstancePlacementProblemServiceImpl.SYNC_OBJECT) {

            try {
                Date date = new Date();
                log.info("Recieved new WorkflowElements: " + workflowElement.getWorkflowElements().size());
                for (WorkflowElement element : workflowElement.getWorkflowElements()) {

                    element.setArrivedAt(date);
                    update(element);
                    log.info("add new WorkflowElement: " + element.toString());
                    cacheWorkflowService.addWorkflowInstance(element);
                    log.info("Done: Add new WorkflowElement: " + element.toString());
                }
            } catch (Exception ex) {
                log.error("EXCEPTION", ex);
            }
        }
    }

    private void update(Element parent) {
        if (parent == null) {
            return;
        }
        List<Element> elements = parent.getElements();
        if (elements == null || elements.size() == 0) {
            return;
        }
        for (Element element : elements) {
            element.setParent(parent);
            if (element instanceof XORConstruct) {
                XORConstruct element2 = (XORConstruct) element;
                int size = element2.getElements().size();
                Random random = new Random();
                int i = random.nextInt(size);
                Element subelement1 = element2.getElements().get(i);
                setAllOthersToNotExecuted(element2.getElements(), subelement1);
                element.getParent().setNextXOR(subelement1);
            } else if (element instanceof LoopConstruct) {
                ((LoopConstruct) element).setNumberOfIterationsInWorstCase(1);
                ((LoopConstruct) element).setIterations(1);
            }
            update(element);
        }

    }

    private void setAllOthersToNotExecuted(List<Element> elements, Element ignore) {
        if (elements == null) {
            return;
        }
        for (Element element : elements) {
            if (!element.getName().equals(ignore.getName())) {
                if (element instanceof ProcessStep) {
                    ((ProcessStep) element).setHasToBeExecuted(false);
                } else {
                    setAllOthersToNotExecuted(element.getElements(), ignore);
                }
            }
        }
    }

}
