package at.ac.tuwien.infosys.viepep.reasoning;

import java.util.Date;

import net.sf.javailp.Result;

/**
 * @author Gerta Sheganaku
 */
public interface ProcessOptimizationResults {
	public void processResults(Result optimize, Date tau_t);
}
