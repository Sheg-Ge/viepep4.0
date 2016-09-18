package at.ac.tuwien.infosys.viepep.reasoning.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import at.ac.tuwien.infosys.viepep.connectors.ViePEPClientService;
import at.ac.tuwien.infosys.viepep.connectors.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepep.connectors.impl.exceptions.CouldNotStartDockerException;
import at.ac.tuwien.infosys.viepep.database.entities.Action;
import at.ac.tuwien.infosys.viepep.database.entities.DockerReportingAction;
import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.ReportingAction;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.services.ReportDaoService;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.PlacementHelper;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Component
@Scope("prototype")
@Slf4j
public class LeaseVMAndStartExecution {

    @Autowired
    private ReportDaoService reportDaoService;
    @Autowired
    private ViePEPClientService viePEPClientService;
    @Autowired
    private ServiceExecution serviceExecution;
    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private ViePEPDockerControllerService dockerControllerService;

    @Value("${simulate}")
    private boolean simulate;
    @Value("${use.docker}")
    private boolean useDocker;
    @Value("${virtualmachine.startup.time}")
    private long startupTime;
    

    @Async
    public void leaseVMAndStartExecution(VirtualMachine virtualMachine, List<ProcessStep> processSteps) {

        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String address = startVM(virtualMachine);

        ReportingAction report =  new ReportingAction(new Date(), virtualMachine.getName(), Action.START);
        reportDaoService.save(report);

        if (address == null) {
            log.error("VM " + virtualMachine.getName() + " was not started, reset task");
            for(ProcessStep processStep : processSteps) {
                processStep.setStartDate(null);
                processStep.setScheduled(false);
                processStep.setScheduledAtVM(null);
            }
            return;
        } else {
            long time = stopWatch.getTotalTimeMillis();
            stopWatch.stop();
            virtualMachine.setStartupTime(time);
            virtualMachine.setStarted(true);
            virtualMachine.setIpAddress(address);

            startExecutions(processSteps, virtualMachine);
        }
    }

    public void leaseVMAndStartExecution(VirtualMachine virtualMachine, Map<DockerContainer, List<ProcessStep>> containerProcessSteps) {
    	final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String address = startVM(virtualMachine);

        ReportingAction report =  new ReportingAction(new Date(), virtualMachine.getName(), Action.START);
        reportDaoService.save(report);

        if (address == null) {
            log.error("VM " + virtualMachine.getName() + " was not started, reset task");
            for(DockerContainer container : containerProcessSteps.keySet()){
            	for(ProcessStep processStep : containerProcessSteps.get(container)) {
            		processStep.setStartDate(null);
            		processStep.setScheduled(false);
            		processStep.setScheduledAtVM(null);
            	}
            	container.shutdownContainer();
            }
            return;
        } else {
            long time = stopWatch.getTotalTimeMillis();
            stopWatch.stop();
            virtualMachine.setStartupTime(time);
            virtualMachine.setStarted(true);
            virtualMachine.setIpAddress(address);

            startExecutions(containerProcessSteps, virtualMachine);

        }
	}

    public void startExecutions(final List<ProcessStep> processSteps, final VirtualMachine virtualMachine) {
        for (final ProcessStep processStep : processSteps) {
            processStep.setStartDate(new Date());
        	serviceExecution.startExecution(processStep, virtualMachine);

        }
    }

	public void startExecutions(Map<DockerContainer, List<ProcessStep>> containerProcessSteps, VirtualMachine virtualMachine) {
		for (final DockerContainer container : containerProcessSteps.keySet()) {
			startContainer(virtualMachine, container);
			for (final ProcessStep processStep : containerProcessSteps.get(container)) {
	            processStep.setStartDate(new Date());
				serviceExecution.startExecution(processStep, container);
			}	
		}
	}

    private String startVM(VirtualMachine virtualMachine){
    	String address = null;
    	if (simulate) {
            address = "128.130.172.211";
            try {
                Thread.sleep(virtualMachine.getStartupTime());
                /* if we are not in Docker mode, additionally sleep some time for deployment of the service */
                if (!useDocker) {
                    Thread.sleep(virtualMachine.getDeployTime());
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            address = viePEPClientService.startNewVM(virtualMachine.getName(), virtualMachine.getVmType().flavor(), virtualMachine.getServiceType().name(), virtualMachine.getVmType().getLocation());
            log.info("VM up and running with ip: " + address + " vm: " + virtualMachine);
            try {
                Thread.sleep(startupTime); //sleep 15 seconds, since as soon as it is up, it still has to deploy the services
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    	return address;
    }
    
    private void startContainer(VirtualMachine vm, DockerContainer container) {
    	if(container.isRunning()){
    		log.info("Container "+ container + " already running on vm "+ container.getVirtualMachine());
    		return;
    	}
    	
    	if(simulate) {
    		try {
    			if(placementHelper.imageForContainerEverDeployedOnVM(container, vm) == 0){
                    Thread.sleep(container.getDeployTime());
    			}
                Thread.sleep(container.getStartupTime()); 
            } catch (InterruptedException e) {
                e.printStackTrace();
                
            }
    	} else {
    		log.info("Start Container: " + container + " on VM: " + vm);
			try {
				dockerControllerService.startDocker(vm, container);
			} catch (CouldNotStartDockerException e) {
				e.printStackTrace();
			}
    	}
    	container.setRunning(true);
    	container.setStartedAt(new Date());
		vm.addDockerContainer(container);

    	DockerReportingAction report =  new DockerReportingAction(new Date(), container.getName(), vm.getName(), Action.START);
        reportDaoService.save(report);
         
    }

}
