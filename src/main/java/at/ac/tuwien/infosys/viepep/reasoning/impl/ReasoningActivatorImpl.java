package at.ac.tuwien.infosys.viepep.reasoning.impl;

import at.ac.tuwien.infosys.viepep.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheDockerService;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepep.reasoning.ReasoningActivator;
import at.ac.tuwien.infosys.viepep.util.ProfileUtil;
import at.ac.tuwien.infosys.viepep.util.TimeUtil;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@Component
@Scope("prototype")
@Slf4j
public class ReasoningActivatorImpl implements ReasoningActivator {

    @Autowired
    private ReasoningImpl reasoning;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheDockerService cacheDockerService;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;
    @Autowired
    private ProfileUtil profileUtil;
    
    @Value("${reasoner.autoTerminate}")
    private boolean autoTerminate;

    @Value("${use.docker}")
    private boolean useDocker;

    @Override
    public void initialize() {
        log.info("ReasoningActivator initialized");

        inMemoryCache.clear();

        if(profileUtil.isProfile("docker")) {
            cacheDockerService.initializeDockerContainers();
            cacheVirtualMachineService.initializeVMs();
        }else if(profileUtil.isProfile("dockerLight")){
            cacheVirtualMachineService.initializeVMs(cacheDockerService);
        }else if(profileUtil.isProfile("basic")){
            cacheVirtualMachineService.initializeVMs();
        }
    }

    @Override
    public Future<Boolean> start() throws Exception {
    	return reasoning.runReasoning(TimeUtil.nowDate(), autoTerminate);
    }

    @Override
    public void stop() {
        reasoning.stop();
    }
}
