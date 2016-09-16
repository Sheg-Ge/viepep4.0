package at.ac.tuwien.infosys.viepep.database.repositories;

import at.ac.tuwien.infosys.viepep.database.entities.DockerReportingAction;
import at.ac.tuwien.infosys.viepep.database.entities.ReportingAction;

import org.springframework.data.repository.CrudRepository;

/**
 * Created by philippwaibel on 17/05/16.
 */
public interface DockerReportRepository extends CrudRepository<DockerReportingAction, Long> {

}
