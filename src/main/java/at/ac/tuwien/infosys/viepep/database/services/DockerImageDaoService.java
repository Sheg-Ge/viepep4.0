package at.ac.tuwien.infosys.viepep.database.services;

import java.util.List;

import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerImage;
import at.ac.tuwien.infosys.viepep.database.repositories.DockerImageRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 13/06/16. Edited by Gerta Sheganaku
 */
@Component
public class DockerImageDaoService {

    @Autowired
    private DockerImageRepository dockerImageRepository;

    public DockerImage save(DockerImage dockerImage) {
        return dockerImageRepository.save(dockerImage);
    }

    public DockerImage getDockerImage(DockerImage dockerImage) {
    	// full scan should be ok here, as we don't expect to have many images in the DB
        for(DockerImage img : dockerImageRepository.findAll()) {
        	if(img.getServiceName().equals(dockerImage.getServiceName())) {
        		return img;
        	}
        }
        return null;
    }
}
