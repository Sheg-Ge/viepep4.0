package at.ac.tuwien.infosys.viepep.database.entities.docker;

import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;

import javax.persistence.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 */

@Entity
@Table(name = "DockerContainer")
@Getter
@Setter
public class DockerContainer {

    @Id
    @GeneratedValue
    private Long id;

    @Enumerated(EnumType.STRING)
    private DockerConfiguration containerConfiguration;

    @ManyToOne
    private DockerImage dockerImage;
    
    private int number;
    
    @ManyToOne
    private VirtualMachine virtualMachine;
    
    private Date startedAt;
    private boolean running = false;

    private long startupTime;
    private long deployTime;
    private long deployCost;
    
    private String containerID;

    private DockerContainer() {
    }

    public DockerContainer(DockerImage dockerImage, DockerConfiguration containerConfiguration, int number) {
        this.containerConfiguration = containerConfiguration;
        this.dockerImage = dockerImage;
        this.number = number;
    }

    @Deprecated
    // TODO remove?
    public DockerContainer(DockerImage dockerImage, long executionTime, DockerConfiguration containerConfiguration) {
        this.containerConfiguration = containerConfiguration;
        this.startupTime = executionTime;
        this.dockerImage = dockerImage;
    }

    public String getName() {
        return containerConfiguration.name() + "_" + this.dockerImage.getServiceName()+"_"+number;
    }

	public DockerConfiguration getContainerConfiguration() {
        return containerConfiguration;
    }

    public String getAppID() {
        return this.dockerImage.getServiceName();
    }

    public long getDeployTime() {
        return deployTime;
    }

    public long getDeployCost() {
        return deployCost;
    }

    public DockerImage getDockerImage() {
        return dockerImage;
    }

    public String getContainerID() {
        return containerID;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setContainerConfiguration(DockerConfiguration containerConfiguration) {
        this.containerConfiguration = containerConfiguration;
    }

    public void setDockerImage(DockerImage dockerImage) {
        this.dockerImage = dockerImage;
    }

    public void setDeployTime(long deployTime) {
        this.deployTime = deployTime;
    }

    public void setDeployCost(long deployCost) {
        this.deployCost = deployCost;
    }

    public void setContainerID(String containerID) {
        this.containerID = containerID;
    }

    public VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }

    public void setVirtualMachine(VirtualMachine virtualMachine) {
        this.virtualMachine = virtualMachine;
    }

    public void shutdownContainer() {
    	virtualMachine = null;
    	running = false;
    	startedAt = null;
    }
    
    @Override
    public String toString() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String startString = startedAt == null ? "NOT_YET" : simpleDateFormat.format(startedAt);
        String vmString = virtualMachine == null ? "NOT_YET" : virtualMachine.getName();
        return "DockerContainer{" +
                "id=" + id +
                ", name='" + getName() + '\'' +
                ", running=" + running +
                ", startedAt=" + startString +
                ", running on VM =" + vmString +
                '}';
    }
    
    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = getName().hashCode();
		result += prime * result
				+ ((containerID == null) ? 0 : containerID.hashCode());
		result = prime * result
				+ ((dockerImage == null) ? 0 : dockerImage.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DockerContainer other = (DockerContainer) obj;
		if (containerID == null) {
			if (other.containerID != null)
				return false;
		} else if (!containerID.equals(other.containerID))
			return false;
		if (dockerImage == null) {
			if (other.dockerImage != null)
				return false;
		} else if (!dockerImage.equals(other.dockerImage))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;

		//  also consider the name here:
		String otherName = other.getName();
		String thisName = this.getName();
		if (thisName == null) {
			if (otherName != null)
				return false;
		} else if (!thisName.equals(otherName))
			return false;
		return true;
	}
}
