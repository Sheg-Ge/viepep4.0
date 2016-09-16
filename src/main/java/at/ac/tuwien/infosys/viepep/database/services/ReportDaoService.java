package at.ac.tuwien.infosys.viepep.database.services;

import at.ac.tuwien.infosys.viepep.database.entities.DockerReportingAction;
import at.ac.tuwien.infosys.viepep.database.entities.ReportingAction;
import at.ac.tuwien.infosys.viepep.database.repositories.DockerReportRepository;
import at.ac.tuwien.infosys.viepep.database.repositories.ReportRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@Component
@Slf4j
public class ReportDaoService {

    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private DockerReportRepository dockerReportRepository;

    public void save(ReportingAction reportingAction) {
        reportRepository.save(reportingAction);
    }
    
    public void save(DockerReportingAction reportingAction) {
    	dockerReportRepository.save(reportingAction);
    }

}
