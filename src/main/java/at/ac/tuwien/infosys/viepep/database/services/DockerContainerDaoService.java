package at.ac.tuwien.infosys.viepep.database.services;

import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.repositories.DockerContainerRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 13/06/16. edited by Gerta Sheganaku.
 */
@Component
public class DockerContainerDaoService {

    @Autowired
    private DockerContainerRepository dockerContainerRepository;

    public DockerContainer update(DockerContainer dockerContainer) {
        return dockerContainerRepository.save(dockerContainer);
    }

    public DockerContainer getDockerContainer(DockerContainer dockerContainer) {
        return dockerContainerRepository.findOne(dockerContainer.getId());
    }
}
