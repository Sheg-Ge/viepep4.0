package at.ac.tuwien.infosys.viepep;

import lombok.extern.slf4j.Slf4j;
import net.sf.javailp.Linear;
import net.sf.javailp.OptType;
import net.sf.javailp.Problem;
import net.sf.javailp.Result;
import net.sf.javailp.Solver;
import net.sf.javailp.SolverFactoryLpSolve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl.NativeLibraryLoader;

@SpringBootApplication
@Slf4j
public class ViePepApplication {

	private static void solveTest() {
		Problem p = new Problem();
		NativeLibraryLoader.extractNativeResources();
		SolverFactoryLpSolve factory = new SolverFactoryLpSolve();
		Solver solver = factory.get();
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
	}

	public static void main(String[] args) {

		// solveTest();

		SpringApplication.run(ViePepApplication.class, args);
	}
}