package at.ac.tuwien.infosys.viepep.database.inmemory.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerConfiguration;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerImage;
import at.ac.tuwien.infosys.viepep.database.inmemory.database.InMemoryCacheImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 13/06/16. edited by Gerta Sheganaku
 */
@Component
public class CacheDockerService {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    @Value("${docker.repo.name}")
    private String repoName;
    @Value("${docker.image.name}")
    private String imageNamePrefix;

    private Integer SERVICE_TYPES = 10; // how many docker images (mapping one service types)
    private Integer CONTAINERS_PER_IMAGE = 4; //different configurations per Image/Service Type
    
    public void initializeDockerContainers() {
        for (int st = 1; st <= SERVICE_TYPES; st++) {
            DockerImage dockerImage = parseByServiceTypeId("service" + st);            
            
            for(int c=1; c<=CONTAINERS_PER_IMAGE; c++) {
            	DockerConfiguration configuration = null;
            	switch (c) {
            	case 1:
                    configuration = DockerConfiguration.MICRO_CORE;
                    break;
                case 2:
                    configuration = DockerConfiguration.SINGLE_CORE;
                    break;
                case 3:
                    configuration = DockerConfiguration.DUAL_CORE;
                    break;
                case 4:
                    configuration = DockerConfiguration.QUAD_CORE;
                    break;
//                case 5:
//                    configuration = DockerConfiguration.HEXA_CORE;
//                    break;
            	}
            	
            	if(dockerImage.getServiceType().getCpuLoad() <= configuration.getCPUPoints() && dockerImage.getServiceType().getMemory() <= configuration.getRAM()){
    				inMemoryCache.addDockerContainer(new DockerContainer(dockerImage, configuration));
                }
            }
        }
    }
    
    public Set<DockerImage> getDockerImages() {
        return inMemoryCache.getDockerMap().keySet();
    }
    
    public DockerImage getDockerImage(ProcessStep step) {
    	for(DockerImage image : getDockerImages()) {
    		if(image.getServiceName().equals(step.getServiceType().getName())) {
    			return image;
    		}
    	}
    	return null;
    }
    
    public List<DockerContainer> getDockerContainers(DockerImage dockerImage) {
        return inMemoryCache.getDockerMap().get(dockerImage);
    }
    
    public List<DockerContainer> getDockerContainers(ProcessStep step) {
//    	System.out.println("docker image for step: " + step + " - " + getDockerImage(step).getFullName());
		return getDockerContainers(getDockerImage(step));
	}
    
    public List<DockerContainer> getAllDockerContainers() {
    	List<DockerContainer> allContainers = new ArrayList<DockerContainer>();
    	for(DockerImage dockerImage : getDockerImages()) {
    		allContainers.addAll(getDockerContainers(dockerImage));
    	}
    	return allContainers;
    }
    

    public Map<DockerImage, List<DockerContainer>> getDockerMap() {
    	return inMemoryCache.getDockerMap();
    }

    private DockerImage parseByImageName(String imageFullName) {
    	for(int st=1; st<=SERVICE_TYPES; st++) {
    		if (imageFullName.equals("service"+st)) {
                return new DockerImage("service"+st, repoName, imageNamePrefix+st, (8080+st), 8080);
            }
    	}
        return null;
    }

    public DockerImage parseByServiceTypeId(String serviceType) {
    	for(int st=1; st<=SERVICE_TYPES; st++) {
    		if(serviceType.equals("service"+st)) {
    			return parseByImageName("service"+st);
    		}
    	}
        return parseByImageName("service1");
    }
}
