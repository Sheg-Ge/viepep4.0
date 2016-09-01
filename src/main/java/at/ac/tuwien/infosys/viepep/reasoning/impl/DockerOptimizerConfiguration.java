package at.ac.tuwien.infosys.viepep.reasoning.impl;

import java.util.Date;

import net.sf.javailp.Result;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

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
