package at.ac.tuwien.infosys.viepep.reasoning;

/**
 * 
 * @author Gerta Sheganaku
 *
 */
public class ProblemNotSolvedException extends Exception {
	private static final long serialVersionUID = 1L;

	public ProblemNotSolvedException() { }

	public ProblemNotSolvedException(String msg) {
		super(msg);
	}
}
