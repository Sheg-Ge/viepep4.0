package at.ac.tuwien.infosys.viepep.database.inmemory.database;

import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElement;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerImage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by philippwaibel on 10/06/16.
 */
@Component
public class InMemoryCacheImpl {
    private List<WorkflowElement> runningWorkflows = new ArrayList<>();
    private List<WorkflowElement> allWorkflowInstances = new ArrayList<>();
    private List<VirtualMachine> virtualMachines = new ArrayList<>();
    private Map<DockerContainer, List<DockerContainer>> dockerMap = new HashMap<>();
    private List<DockerImage> dockerImageList = new ArrayList<>();


    public void clear() {
        virtualMachines = new ArrayList<>();
        runningWorkflows = new ArrayList<>();
        allWorkflowInstances = new ArrayList<>();
        dockerMap = new HashMap<>();
        dockerImageList = new ArrayList<>();
    }

    public List<VirtualMachine> getVMs() {
        return virtualMachines;
    }

    public void addVM(VirtualMachine vm) {
        virtualMachines.add(vm);
    }

    public List<WorkflowElement> getRunningWorkflows() {
        return runningWorkflows;
    }

    public List<WorkflowElement> getAllWorkflowInstances() {
        return allWorkflowInstances;
    }

    public void addRunningWorkflow(WorkflowElement workflowElement) {
        runningWorkflows.add(workflowElement);
    }

    public void addToAllWorkflows(WorkflowElement workflowElement) {
        allWorkflowInstances.add(workflowElement);
    }

    public Map<DockerContainer, List<DockerContainer>> getDockerMap() {
        return dockerMap;
    }

    public List<DockerImage> getDockerImageList() {
        return dockerImageList;
    }

    public void addToDockerImageList(DockerImage dockerImage) {
        dockerImageList.add(dockerImage);
    }

    public void addToDockerMap(DockerContainer key, List<DockerContainer> dockerContainerList) {
        dockerMap.put(key, dockerContainerList);
    }
}
