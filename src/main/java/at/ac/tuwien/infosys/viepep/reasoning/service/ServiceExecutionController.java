package at.ac.tuwien.infosys.viepep.reasoning.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerConfiguration;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.util.ProfileUtil;
import at.ac.tuwien.infosys.viepep.util.TimeUtil;

/**
 * Created by philippwaibel on 18/05/16. edited by Gerta Sheganaku
 */
@Slf4j
@Component
public class ServiceExecutionController{

    @Autowired
    private LeaseVMAndStartExecution leaseVMAndStartExecution;

    @Autowired
    private ProfileUtil profileUtil;

    @Async("serviceProcessExecuter")
    public void startInvocation(List<ProcessStep> processSteps) {

        final Map<VirtualMachine, List<ProcessStep>> vmProcessStepsMap = new HashMap<>();
        for (final ProcessStep processStep : processSteps) {

//            processStep.setStartDate(TimeUtil.nowDate());
            VirtualMachine scheduledAt = processStep.getScheduledAtVM();
            List<ProcessStep> processStepsOnVm = new ArrayList<>();
            if (vmProcessStepsMap.containsKey(scheduledAt)) {
                processStepsOnVm.addAll(vmProcessStepsMap.get(scheduledAt));
            }
            processStepsOnVm.add(processStep);
            vmProcessStepsMap.put(scheduledAt, processStepsOnVm);
        }

        for (final VirtualMachine virtualMachine : vmProcessStepsMap.keySet()) {

            final List<ProcessStep> processSteps1 = vmProcessStepsMap.get(virtualMachine);
            if (!virtualMachine.isLeased()) {
                virtualMachine.setLeased(true);
                virtualMachine.setStartedAt(TimeUtil.nowDate());

                leaseVMAndStartExecution.leaseVMAndStartExecution(virtualMachine, processSteps1);

            } else {
                leaseVMAndStartExecution.startExecutions(vmProcessStepsMap.get(virtualMachine), virtualMachine);
            }
        }
    }
    
    @Async("serviceProcessExecuter")
    public void startInvocation(List<ProcessStep> processSteps, List<DockerContainer> containers) {

    	final Map<VirtualMachine, Map<DockerContainer, List<ProcessStep>>> vmContainerProcessStepMap = new HashMap<>();
    	final Map<DockerContainer, List<ProcessStep>> containerProcessStepsMap = new HashMap<>();

        for (final ProcessStep processStep : processSteps) {
//        	if(processStep.getStartDate()==null){
//        		processStep.setStartDate(TimeUtil.nowDate());   
//        	}
        	DockerContainer scheduledAt = processStep.getScheduledAtContainer();
            if (!containerProcessStepsMap.containsKey(scheduledAt)) {
            	containerProcessStepsMap.put(scheduledAt, new ArrayList<>());
            }
            containerProcessStepsMap.get(scheduledAt).add(processStep);
        }
        
        for (final DockerContainer container : containerProcessStepsMap.keySet()) {
            
        	//set container configuration
        	if(profileUtil.isProfile("dockerLight")){
        		double ram = 0;
        		double cpu = 0;
        		// System.out.println(container + " - " + containerProcessStepsMap.get(container));
        		for(ProcessStep step : containerProcessStepsMap.get(container)){
        			cpu += step.getServiceType().getCpuLoad();
        			ram += step.getServiceType().getMemory();
        		}
        		if(container.getContainerConfiguration() == null){
        			container.setContainerConfiguration(new DockerConfiguration(cpu, ram));
        		}else{
        			container.getContainerConfiguration().setCPUPoints(cpu);
        			container.getContainerConfiguration().setRAMPoints(ram);
        		}
        	}
        	
            VirtualMachine scheduledAt = container.getVirtualMachine();
            if(scheduledAt == null) {
            	log.error("SCHEDULED AT (VM) NULL .  NO GOOD for container "+container);
            }
            if(!vmContainerProcessStepMap.containsKey(scheduledAt)) {
            	vmContainerProcessStepMap.put(scheduledAt, new HashMap<DockerContainer, List<ProcessStep>>());
            }
            vmContainerProcessStepMap.get(scheduledAt).put(container, containerProcessStepsMap.get(container));
        }

        for (final VirtualMachine virtualMachine : vmContainerProcessStepMap.keySet()) {
            final Map<DockerContainer, List<ProcessStep>> containerProcessSteps = vmContainerProcessStepMap.get(virtualMachine);
            try {
                if (!virtualMachine.isLeased() || virtualMachine.getStartedAt() == null) {
                    virtualMachine.setLeased(true);
                    virtualMachine.setStartedAt(TimeUtil.nowDate());
                    leaseVMAndStartExecution.leaseVMAndStartExecution(virtualMachine, containerProcessSteps);
                } else {
                    leaseVMAndStartExecution.startExecutions(vmContainerProcessStepMap.get(virtualMachine), virtualMachine);
                }
			} catch (Exception e) {
				if(!(e.getCause() instanceof InterruptedException)) {
					log.error("Unable start invocation: " + e);
				}
			}
        }
    }
}
