package at.ac.tuwien.infosys.viepep;

import net.sf.javailp.Linear;
import net.sf.javailp.OptType;
import net.sf.javailp.Problem;
import net.sf.javailp.Result;
import net.sf.javailp.Solver;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl.NativeLibraryLoader;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl.ViePEPSolverCPLEX;

public class SimpleSolverTest {

	@Test
	@Ignore
	public void solveTest() {

		Problem p = new Problem();
		NativeLibraryLoader.extractNativeResources();
		Solver solver = new ViePEPSolverCPLEX();
		Linear linear = new Linear();
		linear.add(1, "a");
		linear.add(2, "b");
		Linear linear2 = new Linear();
		linear2.add(1, "a");
		p.add(linear2, ">=", 1);
		Linear linear3 = new Linear();
		linear3.add(1, "b");
		p.add(linear3, ">=", 1);
		//p.add(linear);
        p.setObjective(linear, OptType.MIN);
		Result result = solver.solve(p);
		System.out.println("RESULT " + result);
		Assert.assertNotNull(result);
	}

}
