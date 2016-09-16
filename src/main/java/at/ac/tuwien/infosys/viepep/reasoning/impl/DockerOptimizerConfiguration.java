package at.ac.tuwien.infosys.viepep.reasoning.impl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

import at.ac.tuwien.infosys.viepep.reasoning.ProcessOptimizationResults;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.ProcessInstancePlacementProblemService;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl.DockerProcessInstancePlacementProblemServiceImpl;

/**
 * @author Gerta Sheganaku
 */

@Configuration
@Profile("docker")
@PropertySource(value = "application-docker.properties")
public class DockerOptimizerConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblemService initializeParameters() {
		System.out.println("Profile docker!!");
		return new DockerProcessInstancePlacementProblemServiceImpl();
	}
	
	@Bean
	public ProcessOptimizationResults processResults() {
		System.out.println("Profile docker!!");
		return new DockerProcessOptimizationResults();
	}

}
