package at.ac.tuwien.infosys.viepep.database.entities.docker;

import javax.persistence.Embeddable;

/**
 */
@Embeddable
public class DockerConfiguration {

//    MICRO_CORE(0.5, 512, 30),
    //SINGLE_CORE(1, 300, 30),
//  HEXA_CORE(8, 8 * 1024, 30);
    public static final DockerConfiguration SINGLE_CORE = new DockerConfiguration("SINGLE_CORE", 1, 1024, 30);
    public static final DockerConfiguration DUAL_CORE = new DockerConfiguration("DUAL_CORE", 2, 2 * 1024, 30);
    public static final DockerConfiguration QUAD_CORE = new DockerConfiguration("QUAD_CORE", 4, 4 * 1024, 30);

    private String name;
    public double cores; //amount of needed VCores
    public double ram; //amount of needed memory in mb
    public double disc; //amount of needed disc space in mb

    public DockerConfiguration() {}
    
    public DockerConfiguration(double cpuPoints, double ramPoints) {
    	this.cores = cpuPoints/100; //cpuPoints/0.9/100;
    	this.ram = ramPoints;
    	this.disc = 30;
    }
    
    DockerConfiguration(String name, double cores, double ram, double disc) {
    	this.name = name;
        this.cores = cores;
        this.ram = ram;
        this.disc = disc;
    }
    
    public double getCPUPoints(){
    	return (cores*100)*0.9;
    }

	public double getRAM() {
		return ram;
	}

	public String getName() {
		return name;//"c" + String.valueOf(cores);
	}
	
	public void setCPUPoints(double cpuPoints) {
    	this.cores = cpuPoints/100; //cpuPoints/0.9/100;
	}

	public void setRAMPoints(double ramPoints) {
		this.ram = ramPoints;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(cores);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(disc);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(ram);
		result = prime * result + (int) (temp ^ (temp >>> 32));
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
		DockerConfiguration other = (DockerConfiguration) obj;
		if (Double.doubleToLongBits(cores) != Double
				.doubleToLongBits(other.cores))
			return false;
		if (Double.doubleToLongBits(disc) != Double
				.doubleToLongBits(other.disc))
			return false;
		if (Double.doubleToLongBits(ram) != Double.doubleToLongBits(other.ram))
			return false;
		return true;
	}

}
