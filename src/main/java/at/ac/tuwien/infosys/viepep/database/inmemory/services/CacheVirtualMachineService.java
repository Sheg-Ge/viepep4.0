package at.ac.tuwien.infosys.viepep.database.inmemory.services;

import at.ac.tuwien.infosys.viepep.database.entities.VMType;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerConfiguration;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerImage;
import at.ac.tuwien.infosys.viepep.database.inmemory.database.InMemoryCacheImpl;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by philippwaibel on 10/06/16. modified by Gerta Sheganaku
 */
@Component
@Slf4j
public class CacheVirtualMachineService {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    private int V = 3; //Available types of VM's
    private int K = 3;

    public void initializeVMs() {
    	try {
    		for (int v = 1; v <= V; v++) {
    			VMType vmType = VMType.fromIdentifier(v);

    			for(int k = 1; k <= K; k++) {            	
    				inMemoryCache.addVirtualMachine(new VirtualMachine(v + "_" + k, vmType));
    			}
    		}
    	}catch(Exception e) {
    		e.printStackTrace();
    	}
    }
    public Set<VMType> getVMTypes() {
        return inMemoryCache.getVMMap().keySet();
    }
    
    public List<VirtualMachine> getVMs(VMType vmType) {
        return inMemoryCache.getVMMap().get(vmType);
    }
    
    public List<VirtualMachine> getAllVMs() {
    	List<VirtualMachine> allVMs = new ArrayList<VirtualMachine>();
    	for(VMType vmType : getVMTypes()) {
    		allVMs.addAll(getVMs(vmType));
    	}
    	return allVMs;
    }
    
    public Map<VMType, List<VirtualMachine>> getVMMap() {
    	return inMemoryCache.getVMMap();
    }
    
    public VirtualMachine getVMById(int v, int k) {
        for (VirtualMachine virtualMachine : getAllVMs()) {
        	if (virtualMachine.getName().equals(v + "_" + k)) {
        		return virtualMachine;
            }
        }
        return null;
    }

}
