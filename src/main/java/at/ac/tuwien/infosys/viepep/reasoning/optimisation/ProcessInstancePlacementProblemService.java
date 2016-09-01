package at.ac.tuwien.infosys.viepep.reasoning.optimisation;

import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import net.sf.javailp.Result;

import java.util.Date;

/**
 * Created by Philipp Hoenisch on 5/5/14. modified by Gerta Sheganaku
 */
public interface ProcessInstancePlacementProblemService {

    /**
     * optimizes the process instance placement problem
     * @return the result
     */
    Result optimize(Date tau_t);

    void initializeParameters();

//    VirtualMachine getVMById(String vmID);

//    int getZ(String processStepType, int v, int k);

//    java.util.Collection<Object> getVariables();
}
