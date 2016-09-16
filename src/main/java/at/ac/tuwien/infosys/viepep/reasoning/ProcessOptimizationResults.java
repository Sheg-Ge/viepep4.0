package at.ac.tuwien.infosys.viepep.reasoning;

import java.util.Date;
import java.util.concurrent.Future;

import net.sf.javailp.Result;

/**
 * @author Gerta Sheganaku
 */
public interface ProcessOptimizationResults {
	public Future<Boolean> processResults(Result optimize, Date tau_t);
}
