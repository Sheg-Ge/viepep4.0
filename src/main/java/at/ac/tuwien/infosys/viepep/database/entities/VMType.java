package at.ac.tuwien.infosys.viepep.database.entities;

/**
 * Created by Philipp Hoenisch on 4/14/14.
 */

public enum VMType {
    //TODO only use 3 types of VMS, which have the same size on both clouds
    SINGLE_CORE(1, 1, 960, 10, "m1.small", "internal"),
    DUAL_CORE(2, 2, 3750, 18, "m1.medium", "internal"),
    QUAD_CORE(3, 4, 7680, 35,  "m1.large", "internal"),
//    AWS_SINGLE_CORE(4, 1, 2048, 9, "t2.small", "aws"),
//    AWS_DUAL_CORE(5, 2, 7680, 15, "m3.large", "aws"),
//    AWS_QUAD_CORE(6, 4, 15360, 30, "m3.xlarge", "aws");
    AWS_SINGLE_CORE(4, 1, 960, 9, "m1.small", "internal"),
    AWS_DUAL_CORE(5, 2, 3750, 15, "m1.medium", "internal"),
    AWS_QUAD_CORE(6, 4, 7680, 30, "m1.large", "internal");
    private final double costs;
    private final int identifier;
    public final int cores;
    private final Integer memorySize;
    private final String flavor;
    private double cpuPoints;
    private double ramPoints;
    private String location;

    VMType(int id, int cores, int memorySize, int costs, String flavor, String location) {
        this.identifier = id;
        this.cores = cores;
        this.memorySize = memorySize;
        this.costs = costs;
        this.ramPoints = memorySize; //use mega byte
        int i = cores * 100;
        this.cpuPoints = i - (i/10); //10% are used for the OS
        this.flavor = flavor;
        this.location = location;
    }

    public static VMType fromIdentifier(int identifier) throws Exception {
        switch (identifier) {
            case 1 : return SINGLE_CORE;
            case 2 : return DUAL_CORE;
            case 3 : return QUAD_CORE;
            case 4 : return AWS_SINGLE_CORE;
            case 5 : return AWS_DUAL_CORE;
            case 6 : return AWS_QUAD_CORE;
            default : throw new Exception("TYPE not found");
        }
    }

    public static VMType fromCore(int cores, String location) throws Exception {
        if ((cores == 1) && (location.equals("internal"))) {
            return SINGLE_CORE;
        }
        if ((cores == 2) && (location.equals("internal"))) {
            return DUAL_CORE;
        }
        if ((cores == 4) && (location.equals("internal"))) {
            return QUAD_CORE;
        }
        if ((cores == 1) && (location.equals("aws"))) {
            return AWS_SINGLE_CORE;
        }
        if ((cores == 2) && (location.equals("aws"))) {
            return AWS_DUAL_CORE;
        }
        if ((cores == 4) && (location.equals("aws"))) {
            return AWS_QUAD_CORE;
        }
        else throw new Exception("TYPE not found");
    }

    public double getCosts() {
        return costs;
    }

    public int getCores() {
        return cores;
    }

    public Integer getMemorySize() {
        return memorySize;
    }

    public double getCpuPoints() {
        return cpuPoints;
    }

    public double getRamPoints() {
        return ramPoints;
    }

    public String flavor() {
        return flavor;
    }

    public String getLocation() {
        return location;
    }
}
