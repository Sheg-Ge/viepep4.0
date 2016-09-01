package at.ac.tuwien.infosys.viepep.reasoning.service;

import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Slf4j
@Component
public class ServiceExecutionControllerBasic {

    @Autowired
    private LeaseVMAndStartExecutionBasic leaseVMAndStartExecution;

    @Async("serviceProcessExecuter")
    public void startInvocation(List<ProcessStep> processSteps) {

        final Map<VirtualMachine, List<ProcessStep>> vmProcessStepsMap = new HashMap<>();
        for (final ProcessStep processStep : processSteps) {

            processStep.setStartDate(new Date());
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
                virtualMachine.setStartedAt(new Date());

                leaseVMAndStartExecution.leaseVMAndStartExecution(virtualMachine, processSteps1);

            } else {
                leaseVMAndStartExecution.startExecutions(vmProcessStepsMap.get(virtualMachine), virtualMachine);
            }
        }
    }
}
