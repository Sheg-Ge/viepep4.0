package at.ac.tuwien.infosys.viepep.reasoning.impl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

import at.ac.tuwien.infosys.viepep.reasoning.ProcessOptimizationResults;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.ProcessInstancePlacementProblemService;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl.BasicProcessInstancePlacementProblemServiceImpl;

/**
 * @author Gerta Sheganaku
 */

@Configuration
@Profile("basic")
@PropertySource(value = "application-basic.properties")
public class BasicOptimizerConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblemService initializeParameters() {
		System.out.println("Profile basic!!");
		return new BasicProcessInstancePlacementProblemServiceImpl();
	}

	@Bean
	public ProcessOptimizationResults processResults() {
		System.out.println("Profile basic!!");
		return new BasicProcessOptimizationResults();
	}
	
}
