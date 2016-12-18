package at.ac.tuwien.infosys.viepep.configuration;

import org.springframework.boot.orm.jpa.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Created by philippwaibel on 10/03/16.
 */
@Configuration
@EntityScan(basePackages = {"at.ac.tuwien.infosys.viepep.database.entities"})
@EnableJpaRepositories(basePackages = {"at.ac.tuwien.infosys.viepep.database.repositories"})
@EnableTransactionManagement
@Profile("!test")
public class DatabaseConfiguration {
}
