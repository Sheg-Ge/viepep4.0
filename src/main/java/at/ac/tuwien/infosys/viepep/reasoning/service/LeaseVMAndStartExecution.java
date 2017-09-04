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
import at.ac.tuwien.infosys.viepep.util.TimeUtil;

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
    private DockerExecutor dockerExecutor;
    @Autowired
    private ServiceExecution serviceExecution;

    @Value("${simulate}")
    private boolean simulate;
    @Value("${use.docker}")
    private boolean useDocker;
    @Value("${virtualmachine.startup.time}")
    private long startupTime;
    

    @Component
    public static class DockerExecutor {

        @Autowired
        private PlacementHelper placementHelper;
        @Autowired
        private ServiceExecution serviceExecution;
        @Autowired
        private ViePEPDockerControllerService dockerControllerService;
        @Autowired
        private ReportDaoService reportDaoService;
        @Value("${simulate}")
        private boolean simulate;

        @Async
        public void startExecution(Map<DockerContainer, List<ProcessStep>> containerProcessSteps, VirtualMachine virtualMachine, DockerContainer container) {
        	startContainer(virtualMachine, container);
    		for (final ProcessStep processStep : containerProcessSteps.get(container)) {
    			if(processStep.getStartDate() == null){
    				processStep.setStartDate(TimeUtil.nowDate());
    				serviceExecution.startExecution(processStep, container);
    			}
    		}
        }

        private void startContainer(VirtualMachine vm, DockerContainer container) {
        	if(container.isRunning()){
        		log.info("Container "+ container + " already running on vm "+ container.getVirtualMachine());
        		return;
        	}

        	if(simulate) {
        		if(!placementHelper.imageForContainerEverDeployedOnVM(container, vm)){
    				TimeUtil.sleep(container.getDeployTime());
    			}
    			TimeUtil.sleep(container.getStartupTime());
        	} else {
        		log.info("Start Container: " + container + " on VM: " + vm);
    			try {
    				dockerControllerService.startDocker(vm, container);
    			} catch (CouldNotStartDockerException e) {
    				e.printStackTrace();
    			}
        	}
        	container.setRunning(true);
        	container.setStartedAt(TimeUtil.nowDate());
    		vm.deployDockerContainer(container);

        	DockerReportingAction report =  new DockerReportingAction(TimeUtil.nowDate(), container.getName(), vm.getName(), Action.START);
            reportDaoService.save(report);
             
        }
    }
    
    @Async
    public void leaseVMAndStartExecution(VirtualMachine virtualMachine, List<ProcessStep> processSteps) {

        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String address = startVM(virtualMachine);

        ReportingAction report =  new ReportingAction(TimeUtil.nowDate(), virtualMachine.getName(), Action.START);
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

    @Async
    public void leaseVMAndStartExecution(VirtualMachine virtualMachine, Map<DockerContainer, List<ProcessStep>> containerProcessSteps) {
    	final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String address = startVM(virtualMachine);

        ReportingAction report =  new ReportingAction(TimeUtil.nowDate(), virtualMachine.getName(), Action.START);
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
            processStep.setStartDate(TimeUtil.nowDate());
        	serviceExecution.startExecution(processStep, virtualMachine);
        }
    }

	public void startExecutions(Map<DockerContainer, List<ProcessStep>> containerProcessSteps, VirtualMachine virtualMachine) {
		for (final DockerContainer container : containerProcessSteps.keySet()) {
			dockerExecutor.startExecution(containerProcessSteps, virtualMachine, container);
		}
	}

    private String startVM(VirtualMachine virtualMachine){
    	String address = null;
    	if (simulate) {
            address = "128.130.172.211";
            TimeUtil.sleep(virtualMachine.getStartupTime());
            /* if we are not in Docker mode, additionally sleep some time for deployment of the service */
            if (!useDocker) {
            	TimeUtil.sleep(virtualMachine.getDeployTime());
            }
        } else {
            address = viePEPClientService.startNewVM(virtualMachine.getName(), virtualMachine.getVmType().flavor(),
            		virtualMachine.getServiceType().getName(), virtualMachine.getVmType().getLocation());
            log.info("VM up and running with ip: " + address + " vm: " + virtualMachine);
            if(!useDocker){
            	TimeUtil.sleep(virtualMachine.getDeployTime()); //sleep 30 seconds, since as soon as it is up, it still has to deploy the services
            }
        }
    	return address;
    }


}
