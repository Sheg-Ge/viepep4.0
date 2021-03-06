package at.ac.tuwien.infosys.viepep.database.entities;

import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: sauron
 * Date: 05.02.14
 * Time: 14:39
 * To change this template use File | Settings | File Templates.
 */
@Entity
@Getter
@Setter
public class VirtualMachine implements Serializable {

    /**
     * database id
     * important: this id is used to identify a vm in the program
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * name of this vm
     */
    private String name;

    /**
     * location of the vm: currently [internal|aws]
     */
    private String location;

    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;

    /**
     * indicates if this vm is currently leased
     */
    private boolean leased = false;

    private String ipAddress;

    @Enumerated(EnumType.STRING)
    private VMType vmType;

    private long startupTime = 60000L;
    private final long deployTime = 30000L;

    private Date startedAt;
    private boolean started;
    private Date toBeTerminatedAt;

    @OneToMany
    private List<DockerContainer> deployedContainers;

    public VirtualMachine(String name, Integer numberCores, ServiceType serviceType, String location) {
        this.name = name;
        this.serviceType = serviceType;
        this.location = location;
        try {
            this.vmType = VMType.fromCore(numberCores, location);
        } catch (Exception e) {
        }
    }

    public VirtualMachine(String name, VMType vmType, ServiceType serviceType) {
        this.name = name;
        this.location = vmType.getLocation();
        this.serviceType = serviceType;
    }

    public VirtualMachine(String name, Integer numberCores, String serviceType, String location) {
        this.name = name;
        this.serviceType = ServiceType.fromString(serviceType);
        this.location = location;
        try {
            this.vmType = VMType.fromCore(numberCores, location);
        } catch (Exception e) {
        }
    }

    public VirtualMachine(String name, VMType vmType) {
        this.name = name;
        this.location = vmType.getLocation();
        this.vmType = vmType;
    }

    public VirtualMachine() {
    }

    /*
    @Override
    public int hashCode() {
        return Math.toIntExact(id);
    }
*/

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        VirtualMachine other = (VirtualMachine) obj;

        return (this.id != null) && (other.id != null) && (this.id.intValue() == other.id.intValue());

    }

    @Override
    public String toString() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String startString = startedAt == null ? "NOT_YET" : simpleDateFormat.format(startedAt);
        String toBeTerminatedAtString = toBeTerminatedAt == null ? "NOT_YET" : simpleDateFormat.format(toBeTerminatedAt);
        return "VirtualMachine{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", serviceType=" + serviceType +
                ", leased=" + leased +
                ", startedAt=" + startString +
                ", terminateAt=" + toBeTerminatedAtString +
                ", location=" + location +
                ", ip adress=" + ipAddress +
                '}';
    }

    public String getURI() {
        return "http://" + this.ipAddress;
    }

    public void terminate() {
        this.setLeased(false);
        this.setStarted(false);
        this.setStartedAt(null);
        this.setToBeTerminatedAt(null);
    }
}
