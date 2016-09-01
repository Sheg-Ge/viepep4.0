package at.ac.tuwien.infosys.viepep.database.inmemory.database;

import at.ac.tuwien.infosys.viepep.database.entities.VMType;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElement;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerImage;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by philippwaibel on 10/06/16. edited by Gerta Sheganaku
 */
@Component
public class InMemoryCacheImpl {
    private List<WorkflowElement> runningWorkflows = new ArrayList<>();
    private List<WorkflowElement> allWorkflowInstances = new ArrayList<>();
//    private List<VirtualMachine> virtualMachines = new ArrayList<>();
//    private Map<DockerContainer, List<DockerContainer>> dockerMap = new HashMap<>();
    private Map<VMType, List<VirtualMachine>> vmMap = new HashMap<>();
    private Map<DockerImage, List<DockerContainer>> dockerMap = new HashMap<>();
//    private List<DockerImage> dockerImageList = new ArrayList<>();


    public void clear() {
//        virtualMachines = new ArrayList<>();
        runningWorkflows = new ArrayList<>();
        allWorkflowInstances = new ArrayList<>();
        vmMap = new HashMap<>();
        dockerMap = new HashMap<>();
//        dockerImageList = new ArrayList<>();
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

//    public Map<DockerContainer, List<DockerContainer>> getDockerMap() {
//        return dockerMap;
//    }

    public Map<DockerImage, List<DockerContainer>> getDockerMap() {
    	return dockerMap;
    }
    
    public Map<VMType, List<VirtualMachine>> getVMMap() {
		return vmMap;
	}
    
//    public List<VirtualMachine> getVMs() {
//        return virtualMachines;
//    }

    
//    public List<DockerImage> getDockerImageList() {
//        return dockerImageList;
//    }
    
    public Set<DockerImage> getDockerImageList() {
    	return dockerMap.keySet();
    }

//    public void addToDockerImageList(DockerImage dockerImage) {
//        dockerImageList.add(dockerImage);
//    }
    
    private void addDockerImage(DockerImage dockerImage) {
    	dockerMap.put(dockerImage, new ArrayList<DockerContainer>());
    }
    
    private void addVMType(VMType vmType) {
        vmMap.put(vmType, new ArrayList<VirtualMachine>());
    }

//    public void addToDockerMap(DockerContainer key, List<DockerContainer> dockerContainerList) {
//        dockerMap.put(key, dockerContainerList);
//    }
    
    public void addDockerContainer(DockerContainer dockerContainer) {
    	if(!dockerMap.containsKey(dockerContainer.getDockerImage())) {
    		addDockerImage(dockerContainer.getDockerImage());
    	}
    	dockerMap.get(dockerContainer.getDockerImage()).add(dockerContainer);
    }
    
    public void addVirtualMachine(VirtualMachine vm) {
    	if(!vmMap.containsKey(vm.getVmType())) {
    		addVMType(vm.getVmType());
    	}
    	vmMap.get(vm.getVmType()).add(vm);
    }
    
    public void addDockerContainers(List<DockerContainer> dockerContainers) {
    	for(DockerContainer dockerContainer : dockerContainers) {
    		addDockerContainer(dockerContainer);
    	}
    }
    
    public void addVirtualMachines(List<VirtualMachine> virtualMachines) {
    	for(VirtualMachine vm : virtualMachines) {
    		addVirtualMachine(vm);
    	}
    }
}
