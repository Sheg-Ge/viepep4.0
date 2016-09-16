package at.ac.tuwien.infosys.viepep.database.entities;

/**
 * Created by Philipp Hoenisch on 6/16/14.
 */
public enum ServiceType {

    Task1("service1", 45, 450, 1000 * 40, 2, false),
    Task2("service2", 75, 720, 1000 * 80, 4, false),
    Task3("service3", 75, 720, 1000 * 120, 6, true),
    Task4("service4", 100, 960, 1000 * 40, 2, false),
    Task5("service5", 120, 1150, 1000 * 100, 4, false),
    Task6("service6", 125, 1150, 1000 * 20, 6, true),
    Task7("service7", 150, 1440, 1000 * 40, 2, false),
    Task8("service8", 175, 1680, 1000 * 20, 4, false),
    Task9("service9", 250, 2400, 1000 * 60, 6, true),
    Task10("service10", 333, 3200, 1000 * 30, 2, false);
    ServiceType(String name, double cpuLoad, double memoryInByte, long makeSpan, double dataToTransfer, boolean onlyInternal) {
        this.name = name;
        this.cpuLoad = cpuLoad;
        this.memory = memoryInByte;
        this.makeSpan = makeSpan;
        this.dataToTransfer = dataToTransfer;
        this.onlyInternal = onlyInternal;
    }

    final double cpuLoad;
    final double memory;
    final long makeSpan;
    final String name;
    final double dataToTransfer;
    final boolean onlyInternal;

    public static ServiceType fromString(String serviceType) {
        switch (serviceType) {
            case "service1":
                return Task1;
            case "service2":
                return Task2;
            case "service3":
                return Task3;
            case "service4":
                return Task4;
            case "service5":
                return Task5;
            case "service6":
                return Task6;
            case "service7":
                return Task7;
            case "service8":
                return Task8;
            case "service9":
                return Task9;
            case "service10":
                return Task10;
            default:
                return Task1;
        }
    }

    public double getCpuLoad() {
        return cpuLoad;
    }
    
    public double getMemory() {
    	return memory;
    }

    public long getMakeSpan() {
        return makeSpan;
    }

    public String getName() {
        return name;
    }

    public double getDataToTransfer() {
        return dataToTransfer;
    }

    public boolean isOnlyInternal() {
        return onlyInternal;
    }
}
