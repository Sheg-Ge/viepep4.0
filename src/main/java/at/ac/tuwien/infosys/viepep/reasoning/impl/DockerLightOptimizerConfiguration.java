package at.ac.tuwien.infosys.viepep.reasoning.impl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

import at.ac.tuwien.infosys.viepep.reasoning.ProcessOptimizationResults;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.ProcessInstancePlacementProblemService;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl.DockerLightProcessInstancePlacementProblemServiceImpl;

/**
 * @author Gerta Sheganaku
 */

@Configuration
@Profile("dockerLight")
@PropertySource(value = "application-docker.properties")
public class DockerLightOptimizerConfiguration {

	@Bean
	public ProcessInstancePlacementProblemService initializeParameters() {
		System.out.println("Profile DockerLight!!");
		return new DockerLightProcessInstancePlacementProblemServiceImpl();
	}

	@Bean
	public ProcessOptimizationResults processResults() {
		return new DockerLightProcessOptimizationResults();
	}

}
