package at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl;

import at.ac.tuwien.infosys.viepep.database.entities.*;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerImage;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheDockerService;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepep.reasoning.impl.ReasoningImpl;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.PlacementHelper;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.ProcessInstancePlacementProblemService;
import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import lombok.extern.slf4j.Slf4j;
import net.sf.javailp.*;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;


/**
 * @author Gerta Sheganaku
 */
@Slf4j
//@Component
public class DockerProcessInstancePlacementProblemServiceImpl extends NativeLibraryLoader implements ProcessInstancePlacementProblemService {

    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private CacheDockerService cacheDockerService;

//    @Value("${optimization.use.internVms.first}")
//    private boolean useInternVmsFirst;           // has to be true that the internal storage is filled first

    public static final Object SYNC_OBJECT = "Sync_Lock";

    private static final double EXTERNAL_CLOUD_FACTOR = 1; //not considered
    
    private static final double OMEGA_F_R_VALUE = 0.001; //0.0001
    private static final double OMEGA_F_C_VALUE = 0.01; //0.0001

    private static final double OMEGA_S_R_VALUE = 0.001;
    private static final double OMEGA_S_C_VALUE = 0.01;
//    private static final double TAU_T_1_WEIGHT = 0.00000000001;
    private static final double OMEGA_DEPLOY_D_VALUE = 0.001; //CHECK only a weight for actual deploy value
    private static final double DEADLINE_WEIGHT = 0.001;

    private Date tau_t;
    private static final long EPSILON = ReasoningImpl.MIN_TAU_T_DIFFERENCE_MS / 1000;
    private static final long TIMESLOT_DURATION = 60 * 1000 * 1; //timeslot duration is minimum 1 minute
//    public static final long LEASING_DURATION = 60 * 1000 * 5; //BTU timeslot duration is minimum 5 minutes

//    private int V = 0;
//    private int K = 0;
//    private int ST = 0;
//    private int C = 0;
//    private int internalTypes = 0; //not considered
    private long M = 10;
    private long N = 100;
    
    private static long CONTAINER_DEPLOY_TIME = 30000L; //30
    private static long VM_STARTUP_TIME = 60000L; //60

//    private Map<Integer, Integer> currentVMUsage = new HashMap<>();

//    private Map<VMType, List<VirtualMachine>> vmMap;
    private List<ProcessStep> allRunningSteps;
    private List<WorkflowElement> nextWorkflowInstances;
    private Map<String, List<ProcessStep>> nextSteps;
    private Map<String, List<ProcessStep>> runningSteps;
    private Problem problem;

    public void initializeParameters() {
//        V = cacheVirtualMachineService.getVMTypes().size();
//        for (VMType vmType : cacheVirtualMachineService.getVMTypes()) { //set K, max 1 iteration
//            K = cacheVirtualMachineService.getVMs(vmType).size();
            VM_STARTUP_TIME = cacheVirtualMachineService.getAllVMs().get(0).getStartupTime();
//            break;
//        }
        
//        ST = cacheDockerService.getDockerImages().size();
//        for (DockerImage dockerImage : cacheDockerService.getDockerImages()) { //set C, max 1 iteration
//            C = cacheDockerService.getDockerContainers(dockerImage).size();
            CONTAINER_DEPLOY_TIME = cacheDockerService.getAllDockerContainers().get(0).getDeployTime();
//            break;
//        }
    }

    public Result optimize(Date tau_t) {

        //cleanups
        synchronized (SYNC_OBJECT) {
            placementHelper.setFinishedWorkflows();

//            updateUsageMap();
            nextWorkflowInstances = null;
            nextSteps = new HashMap<>();
            allRunningSteps = null;
            runningSteps = new HashMap<>();
            
            getNextWorkflowInstances();
            getAllNextStepsAsList();

            getAllRunningSteps();
            getNextAndRunningSteps();
        }

        this.tau_t = tau_t;
//      M = 100000 / 1000;
        SolverFactory factory;
        System.out.println("useCPLEX " + useCPLEX);
        if (useCPLEX) {
        	factory = new SolverFactoryCPLEX();//use cplex
        	log.info("#### ---- Using CPLEX Solver");
        }
        else {
        	factory = new SolverFactoryLpSolve();//use lp solve
        	log.info("#### ---- Using LP Solver");

        }
//      factory.setParameter(Solver.POSTSOLVE, 2);
        factory.setParameter(Solver.VERBOSE, 0);
        factory.setParameter(Solver.TIMEOUT, 600); // set timeout to 600 seconds

        log.info(printCollections());

        problem = new Problem();
        addObjective_1(problem);
        addConstraint_2(problem);
        addConstraint_3(problem); 
        addConstraint_4_to_11(problem);
        addConstraint_12_16(problem);
        addConstraint_13_17(problem);
        addConstraint_14_18(problem);
        addConstraint_15_19(problem);
        addConstraint_20_to_25(problem);
//        addConstraint_20(problem);
        addConstraint_26(problem);
        addConstraint_27(problem);
        addConstraint_28_to_32(problem);
        addConstraint_33_to_36(problem);
        addConstraint_37(problem);
        addConstraint_38(problem);
        addConstraint_39(problem);
        addConstraint_40(problem);
        addConstraint_41(problem);
        addConstraint_42(problem);
        addConstraint_43(problem);
        addConstraint_44(problem);
        addConstraint_45(problem);
        addConstraint_46(problem);
        addConstraint_47(problem);
        addConstraint_48(problem);
        addConstraint_49(problem);



        Solver solver = new ViePEPSolverCPLEX(); // factory.get();
        //Solver solver = factory.get();
        if (useCPLEX) {
            ((SolverCPLEX) solver).addHook(new SolverCPLEX.Hook() {
                @Override
                public void call(IloCplex cplex, Map<Object, IloNumVar> varToNum) {
                    try {
                        cplex.setParam(IloCplex.DoubleParam.TiLim, 60); //(TIMESLOT_DURATION / 1000) - 10);  //60
                        // cplex.setParam(IloCplex.IntParam.RepeatPresolve, 3);
                        // cplex.setParam(IloCplex.LongParam.RepairTries, 20);

                        /* set optimality gap to ensure we get an optimal solution */
//                        System.out.println("cplex.getParam(IloCplex.DoubleParam.EpGap) " + cplex.getParam(IloCplex.DoubleParam.EpGap));
//                        System.out.println("cplex.getParam(IloCplex.DoubleParam.EpAGap) " + cplex.getParam(IloCplex.DoubleParam.EpAGap));
//                        System.out.println("cplex.getParam(IloCplex.IntParam.NodeLim) " + cplex.getParam(IloCplex.IntParam.NodeLim));
//                        System.out.println("cplex.getParam(IloCplex.IntParam.IntSolLim) " + cplex.getParam(IloCplex.IntParam.IntSolLim));
//                        System.out.println("cplex.getParam(IloCplex.DoubleParam.TreLim) " + cplex.getParam(IloCplex.DoubleParam.TreLim));
//                        System.out.println("cplex.getParam(IloCplex.DoubleParam.TiLim) " + cplex.getParam(IloCplex.DoubleParam.TiLim));
//                        System.out.println("cplex.getParam(IloCplex.DoubleParam.ItLim) " + cplex.getParam(IloCplex.IntParam.ItLim));
//                        System.out.println("cplex.getParam(IloCplex.DoubleParam.CutUp) " + cplex.getParam(IloCplex.DoubleParam.CutUp));
//                        System.out.println("cplex.getParam(IloCplex.DoubleParam.CutLo) " + cplex.getParam(IloCplex.DoubleParam.CutLo));
//                        System.out.println("cplex.getParam(IloCplex.IntParam.PopulateLim) " + cplex.getParam(IloCplex.IntParam.PopulateLim));
//                        System.out.println("cplex.getParam(IloCplex.DoubleParam.SolnPoolAGap) " + cplex.getParam(IloCplex.DoubleParam.SolnPoolAGap));
//                        System.out.println("cplex.getParam(IloCplex.DoubleParam.SolnPoolGap) " + cplex.getParam(IloCplex.DoubleParam.SolnPoolGap));
//                        System.out.println("cplex.getParam(IloCplex.IntParam.SolnPoolCapacity) " + cplex.getParam(IloCplex.IntParam.SolnPoolCapacity));
//                        System.out.println("cplex.getParam(IloCplex.IntParam.SolnPoolIntensity) " + cplex.getParam(IloCplex.IntParam.SolnPoolIntensity));
                        cplex.setParam(IloCplex.DoubleParam.EpGap, 0);
                        cplex.setParam(IloCplex.DoubleParam.EpAGap, 0);

//                        cplex.setParam(IloCplex.DoubleParam.SolnPoolAGap, 0.5);
//                        cplex.setParam(IloCplex.IntParam.PopulateLim, 1000);
//                        cplex.setParam(IloCplex.IntParam.SolnPoolCapacity, 10);
//                        cplex.setParam(IloCplex.IntParam.SolnPoolIntensity, 4);
                        // cplex.setParam(IloCplex.IntParam.NodeLim, 0);
                        // cplex.setParam(IloCplex.DoubleParam.TreLim, 0);
                        // cplex.setParam(IloCplex.IntParam.IntSolLim, -1);
                    } catch (IloException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        Result solved = solver.solve(problem);



        int i = 0;
        StringBuilder vars = new StringBuilder();

        if (solved != null) {
            log.info("------------------------- Solved  -------------------------");
          //  log.info(solved.toString());

//            log.info("------------------------- Variables -------------------------");
//            for (Object variable : problem.getVariables()) {
//                vars.append(i).append(": ").append(variable).append("=").append(solved.get(variable)).append(", ");
//                i++;
//            }
//            log.info(vars.toString());
//            log.info("-----------------------------------------------------------");
//            
            getAllObjectives(solved);
//            getAllSolvedConstraints(solved, problem);
        }


        if (solved == null) {
            log.error("-----------------------------------------------------------");
            Collection<Object> variables = problem.getVariables();
            i = 0;
            for (Object variable : variables) {
                log.error(i + " " + variable);
                i++;
            }

            log.error("-----------------------------------------------------------");
            log.error(problem.getConstraints().toString());
            log.error("-----------------------------------------------------------");
            log.error(problem.getObjective().toString());
            log.error("-----------------------------------------------------------");

        }
        return solved;

    }

    private String printCollections() {

        StringBuilder builder = new StringBuilder();

        builder.append("------- collections ---------\n");

        builder.append("\n--------- vmMap ---------");
        for (Map.Entry<VMType, List<VirtualMachine>> vmMapEntry : cacheVirtualMachineService.getVMMap().entrySet()) {

            builder.append("\n").append(vmMapEntry.getKey()).append(":");
            for (VirtualMachine vm : vmMapEntry.getValue()) {
                builder.append("\n").append("     ").append(vm.toString());
            }
        }

        builder.append("\n--------- dockerMap ---------");
        for (Map.Entry<DockerImage, List<DockerContainer>> dockerMapEntry : cacheDockerService.getDockerMap().entrySet()) {

            builder.append("\n").append(dockerMapEntry.getKey()).append(":");
            for (DockerContainer container : dockerMapEntry.getValue()) {
                builder.append("\n").append("     ").append(container.toString());
            }
        }
        
        builder.append("\n---- allRunningSteps ----");
        for (Element element : allRunningSteps) {
            builder.append("\n").append(element.toString());
        }

//        builder.append("\n- nextWorkflowInstances -");
//        for (WorkflowElement workflowElement : nextWorkflowInstances) {
//            builder.append("\n").append(workflowElement.toString());
//        }

        builder.append("\n------- nextSteps --------");
        for (Map.Entry<String, List<ProcessStep>> nextStepEntry : nextSteps.entrySet()) {
            builder.append("\n").append(nextStepEntry.getKey()).append(":");
            for (Element element : nextStepEntry.getValue()) {
                builder.append("\n").append("     ").append(element.toString());
            }
        }

        builder.append("\n------ runningSteps -------");
        for (Map.Entry<String, List<ProcessStep>> runningStepEntry : runningSteps.entrySet()) {
            builder.append("\n").append(runningStepEntry.getKey()).append(":");
            for (Element element : runningStepEntry.getValue()) {
                builder.append("\n").append("     ").append(element.toString());
            }
        }

        return builder.toString();
    }


    /**
     * the objective function, which is specified in (1), aims at
     * minimizing the total cost for leasing VMs. In addition, by
     * adding the amount of unused capacities
     * of leased VMs
     * to the total cost, the objective function also aims at minimiz-
     * ing unused capacities of leased VirtualMachine instances.
     *
     * @param problem to be solved
     */
    private void addObjective_1(Problem problem) {
        final Linear linear = new Linear();
        
//      String transfer = "transfercosts";
//      linear.add(1, transfer);

        //term 1
        for (VMType vmType : cacheVirtualMachineService.getVMTypes()) {
            String gamma = placementHelper.getGammaVariable(vmType);
            linear.add(vmType.getCosts(), gamma);
//            System.out.println("******************************** TERM 1 VMType: " + vmType + " gammaVar: "+gamma);
        }

        //DS: for penalty costs //term 2 and term 6
        for (WorkflowElement workflowInstance : getNextWorkflowInstances()) {
        	//Term2
        	String executionTimeViolation = placementHelper.getExecutionTimeViolationVariable(workflowInstance);
            linear.add(DEADLINE_WEIGHT * placementHelper.getPenaltyCostPerQoSViolationForProcessInstance(workflowInstance), executionTimeViolation);
//            System.out.println("******************************** TERM 2 penalty cost: " + placementHelper.getPenaltyCostPerQoSViolationForProcessInstance(workflowInstance) + " execTimeViolation: "+ executionTimeViolation);


            
            //Term 6
            long enactmentDeadline = placementHelper.getEnactmentDeadline(workflowInstance);//getDeadline() / 1000;
            double enactmentDeadlineSmall = enactmentDeadline / 1000;
            double tauSmall = tau_t.getTime() / 1000;
            double diffInSeconds = (enactmentDeadlineSmall - tauSmall);
            Double coefficient = 1.0 / diffInSeconds;
            if (Double.isInfinite(coefficient) || coefficient <= 0) {
                coefficient = 100.0 - diffInSeconds; 
            }
            
            Date enactDeadl = new Date(enactmentDeadline);
//            System.out.println("EnactmentDeadline: "+ enactDeadl + ", tau_t :" + tau_t + " of Workflow "+ workflowInstance.getName());
//    		System.out.println("******* Coefficient for Term 6 was: " + coefficient + " For diff: " + diffInMinutes + " For WorkflowDeadline: " + workflowInstance.getDeadline()+ " of Workflow "+ workflowInstance.getName());

            for (ProcessStep step : nextSteps.get(workflowInstance.getName())) {
//            	System.out.println("step: " + step);
//            	System.out.println(cacheDockerService.getDockerImage(step));
//            	System.out.println(cacheDockerService.getDockerImages());
//            	System.out.println(cacheDockerService.getDockerContainers(step));
                for(DockerContainer container : cacheDockerService.getDockerContainers(step)){
            		String decisionVariableX = placementHelper.getDecisionVariableX(step, container);
            		linear.add(-1 * coefficient, decisionVariableX);
//                    System.out.println("******************************** TERM 6 -1*coeff: "+ (-1 * coefficient) + " varX: "+ decisionVariableX + " Container: " + container.getName() + " step: " + step.getName());

                }
            }
        }
        
        //Term 3
		for (VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
			for (DockerContainer dockerContainer : cacheDockerService.getAllDockerContainers()) {
				if (placementHelper.imageForContainerEverDeployedOnVM(dockerContainer, vm) == 0) {
					String decisionVariableA = placementHelper.getDecisionVariableA(dockerContainer, vm);
					linear.add(dockerContainer.getDeployCost() * OMEGA_DEPLOY_D_VALUE, decisionVariableA);
//		            System.out.println("******************************** TERM 3 deployCostForContainer*Omega: "+ (dockerContainer.getDeployCost() * OMEGA_DEPLOY_D_VALUE) + " VarA "+ decisionVariableA);

				}
			}

		}
        
        //Term 4
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	String fValueC = placementHelper.getFValueCVariable(vm);
            String fValueR = placementHelper.getFValueRVariable(vm); //todo add me again if ram is considered
            linear.add(OMEGA_F_C_VALUE, fValueC);
            linear.add(OMEGA_F_R_VALUE, fValueR);
            problem.setVarUpperBound(fValueC, Double.MAX_VALUE);
            problem.setVarUpperBound(fValueR, Double.MAX_VALUE);
            problem.setVarLowerBound(fValueC, Double.MIN_VALUE);
            problem.setVarLowerBound(fValueR, Double.MIN_VALUE);
//            System.out.println("******************************** TERM 4 omegaFC: " + OMEGA_F_C_VALUE + " times variable fc: " + fValueC);
//            System.out.println("******************************** TERM 4 omegaFR: " + OMEGA_F_R_VALUE + " times variable fr: " + fValueR);

        }

        //Term 5
        for (VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
			for (DockerContainer dockerContainer : cacheDockerService.getAllDockerContainers()) {
				String decisionVariableA = placementHelper.getDecisionVariableA(dockerContainer, vm);
				double containerCPUSupply = placementHelper.getSuppliedCPUPoints(dockerContainer);
	            double containerRAMSupply = placementHelper.getSuppliedRAMPoints(dockerContainer); //todo add me again if ram is considered
	            
				linear.add(OMEGA_S_C_VALUE * containerCPUSupply, decisionVariableA);
	            linear.add(OMEGA_S_R_VALUE * containerRAMSupply, decisionVariableA);
//	            System.out.println("******************************** TERM 5 OmegaSC Value: "+ OMEGA_S_C_VALUE + " times container cpu supply: " + containerCPUSupply + " makes = " + (OMEGA_S_C_VALUE*containerCPUSupply) + " in varA: " + decisionVariableA);
//	            System.out.println("******************************** TERM 5 OmegaSR Value: "+ OMEGA_S_R_VALUE + " times container ram supply: " + containerRAMSupply + " makes = " + (OMEGA_S_R_VALUE*containerRAMSupply) + " in varA: " + decisionVariableA);
        
          }
      }
        
        //maximize tau_t_1
//        linear.add(-TAU_T_1_WEIGHT, "tau_t_1");
        
        problem.setObjective(linear, OptType.MIN);
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_2(Problem problem) {
        final List<WorkflowElement> nextWorkflowInstances = getNextWorkflowInstances();
        for (final WorkflowElement workflowInstance : nextWorkflowInstances) {
            Linear linear = new Linear();
            String executionTimeWorkflowVariable = placementHelper.getExecutionTimeVariable(workflowInstance);
            String executionTimeViolation = placementHelper.getExecutionTimeViolationVariable(workflowInstance);
            linear.add(1, "tau_t_1");
            linear.add(1, executionTimeWorkflowVariable);
            linear.add(-1, executionTimeViolation);
            
//            List<Element> runningStepsForWorkflow = getRunningStepsForWorkflow(workflowInstance.getName());
//            long maxRemainingExecutionTime = 0;
//            for (Element runningStep : runningStepsForWorkflow) {
//                maxRemainingExecutionTime = Math.max(maxRemainingExecutionTime, getRemainingExecutionTimeAndDeployTimes(runningStep));
//            }

            long rhs = workflowInstance.getDeadline() / 1000; //- maxRemainingExecutionTime / 1000;
            problem.add(linear, "<=", rhs);
            
//            System.out.println("******************************** CONSTRAINT 2 for workflowelement: " + workflowInstance.getName() +" :: ");
//            System.out.println("LHS: 1*tau_t_1 + 1*"+executionTimeWorkflowVariable +" <= 1*"+executionTimeViolation +" + "+rhs );


        }
    }

    /**
     * next optimization step has to be bigger than the last one
     *
     * @param problem to be solved
     */
    private void addConstraint_3(Problem problem) {
        Linear linear = new Linear();
        linear.add(1, "tau_t_1");
        problem.add(linear, ">=", tau_t.getTime() / 1000 + EPSILON); //+ TIMESLOT_DURATION / 1000);
        problem.setVarUpperBound("tau_t_1", Integer.MAX_VALUE);
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_4_to_11(Problem problem) {
        for (WorkflowElement workflowInstance : getNextWorkflowInstances()) {
            List<String> nextStepIds = new ArrayList<>();
            for (Element element : nextSteps.get(workflowInstance.getName())) {
                nextStepIds.add(element.getName());
            }

            String executionTimeWorkflowVariable = placementHelper.getExecutionTimeVariable(workflowInstance);
            Linear linear = new Linear();
            linear.add(1, executionTimeWorkflowVariable);

            Element rootElement = workflowInstance.getElements().get(0);
            //this method realizes constraints (4)-(11)
            generateConstraintsForCalculatingExecutionTime(rootElement, linear, problem, -1, nextStepIds);
            problem.add(linear, "=", 0);
            
//            for(Term record : linear) {
//            	System.out.println("******************************** CONSTRAINT 4 to 11 TERMS for workflowinstance:: " + workflowInstance.getName() +" :: ");
//                System.out.println(record.getVariable());
//
//            }
        }
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_12_16(Problem problem) {
    	
    	List<ProcessStep> steps = getNextAndRunningSteps();
        if (steps.isEmpty()) {//can be ignored if no steps are running
            return;
        }
        for(DockerContainer container : cacheDockerService.getAllDockerContainers()){
        	Linear linear = new Linear();
            Linear linear2 = new Linear(); //add me if ram is considered
            boolean stepsToAdd = false;

            for (ProcessStep step : steps) {
            	if(step.getServiceType().getName().equals(container.getDockerImage().getServiceType().getName())) {
            		stepsToAdd = true;
            		String decisionVariableX = placementHelper.getDecisionVariableX(step, container);
            		
            		double requiredCPUPoints = placementHelper.getRequiredCPUPoints(step);
            		linear.add(requiredCPUPoints, decisionVariableX);

            		double requiredRAMPoints = placementHelper.getRequiredRAMPoints(step);
            		linear2.add(requiredRAMPoints, decisionVariableX);
            	}
            }
            
            if(stepsToAdd) {
            	problem.add(linear, "<=", placementHelper.getSuppliedCPUPoints(container));
            	problem.add(linear2, "<=",placementHelper.getSuppliedRAMPoints(container));
            
//            	System.out.println("******************************** CONSTRAINT 12 for container: " + container.getName() +" :: ");
//            	System.out.print("LHS CPU:");
//            	for(Term record : linear) {
//            		System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//            	}
//            	System.out.print(" <= " + placementHelper.getSuppliedCPUPoints(container) + "\n\n");
//            
//            	System.out.println("******************************** CONSTRAINT 16 for container: " + container.getName() +" :: ");
//            	System.out.print("LHS RAM:");
//            	for(Term record : linear2) {
//                	System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//            	}
//            	System.out.print(" <= " + placementHelper.getSuppliedRAMPoints(container) + "\n\n");
            }
        }
    }

    private void addConstraint_13_17(Problem problem) {
    	for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
    		Linear linear = new Linear();
            Linear linear2 = new Linear(); //add me if ram is considered

            for (DockerContainer dockerContainer : cacheDockerService.getAllDockerContainers()) {
            	String decisionVariableA = placementHelper.getDecisionVariableA(dockerContainer, vm);
                double requiredCPUPoints = placementHelper.getSuppliedCPUPoints(dockerContainer);
                linear.add(requiredCPUPoints, decisionVariableA);

                double requiredRAMPoints = placementHelper.getSuppliedRAMPoints(dockerContainer);
                linear2.add(requiredRAMPoints, decisionVariableA);        
            }

            //TODO: check if necessary to consider GVariable:
            linear.add(-placementHelper.getSuppliedCPUPoints(vm), placementHelper.getGVariable(vm));
            linear2.add(-placementHelper.getSuppliedRAMPoints(vm), placementHelper.getGVariable(vm));
            problem.add(linear, "<=", 0);
            problem.add(linear2, "<=", 0);
            
//            System.out.println("******************************** CONSTRAINT 13 for VM: " + vm.getName() +" :: ");
//        	System.out.print("CPU containers demand - supply of vm:");
//        	for(Term record : linear) {
//        		System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//        	}
//        	System.out.print(" <= 0 \n\n");
//        
//        	System.out.println("******************************** CONSTRAINT 17 for VM: " + vm.getName() +" :: ");
//        	System.out.print("RAM containers demand - supply of vm:");
//        	for(Term record : linear2) {
//            	System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//        	}
//        	System.out.print(" <= 0 \n\n");
            
//            problem.add(linear, "<=", placementHelper.getSuppliedCPUPoints(vm));
//            problem.add(linear2, "<=",placementHelper.getSuppliedRAMPoints(vm));
        }
    }
    
    private void addConstraint_14_18(Problem problem) {
    	for (DockerImage dockerImage : cacheDockerService.getDockerImages()) {
            Linear linear = new Linear();
            Linear linear2 = new Linear(); //add me if ram is considered

            for (DockerContainer dockerContainer : cacheDockerService.getDockerContainers(dockerImage)) {
                for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
                	String decisionVariableA = placementHelper.getDecisionVariableA(dockerContainer, vm);
                    double suppliedCPUPoints = placementHelper.getSuppliedCPUPoints(dockerContainer);
                    linear.add(suppliedCPUPoints, decisionVariableA);
                    
                    double suppliedRAMPoints = placementHelper.getSuppliedRAMPoints(dockerContainer);
                    linear2.add(suppliedRAMPoints, decisionVariableA);
                }
            }
            
            List<ProcessStep> steps = getNextAndRunningSteps();
            if (!steps.isEmpty()) {
            	for(DockerContainer container : cacheDockerService.getDockerContainers(dockerImage)){
                    for (ProcessStep step : steps) {
                    	if(step.getServiceType().getName().equals(container.getDockerImage().getServiceType().getName())) {
                    		String decisionVariableX = placementHelper.getDecisionVariableX(step, container);
                        	
                    		double requiredCPUPoints = placementHelper.getRequiredCPUPoints(step);
                        	linear.add(-requiredCPUPoints, decisionVariableX);

                        	double requiredRAMPoints = placementHelper.getRequiredRAMPoints(step);
                        	linear2.add(-requiredRAMPoints, decisionVariableX);                    
                        }
                    }
                 }
            }

            problem.add(linear, ">=", 0);
            problem.add(linear2, ">=", 0);
            
//            System.out.println("******************************** CONSTRAINT 14 for Service type: " + dockerImage.getServiceType().getName() +" :: ");
//        	System.out.print("CPU containers supply - steps demand:");
//        	for(Term record : linear) {
//        		System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//        	}
//        	System.out.print(" >= 0 \n\n");
//        
//        	System.out.println("******************************** CONSTRAINT 18 for Service type: " + dockerImage.getServiceType().getName() +" :: ");
//        	System.out.print("RAM containers supply - steps demand:");
//        	for(Term record : linear2) {
//            	System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//        	}
//        	System.out.print(" >= 0 \n\n");
        }
    }

    private void addConstraint_15_19(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	Linear linear = new Linear();
        	Linear linear2 = new Linear(); //add me if ram is considered
            String helperVariableG = placementHelper.getGVariable(vm);
            double suppliedCPUPoints = placementHelper.getSuppliedCPUPoints(vm);
            linear.add(suppliedCPUPoints, helperVariableG);
            
            double suppliedRAMPoints = placementHelper.getSuppliedRAMPoints(vm);
            linear2.add(suppliedRAMPoints, helperVariableG);

            for (DockerContainer dockerContainer : cacheDockerService.getAllDockerContainers()) {
            	String decisionVariableA = placementHelper.getDecisionVariableA(dockerContainer, vm);
            	double requiredCPUPoints = placementHelper.getSuppliedCPUPoints(dockerContainer);
                linear.add(-requiredCPUPoints, decisionVariableA);       

                double requiredRAMPoints = placementHelper.getSuppliedRAMPoints(dockerContainer);
                linear2.add(-requiredRAMPoints, decisionVariableA);
            }
            
            String fValueC = placementHelper.getFValueCVariable(vm);
            linear.add(-1, fValueC);

            String fValueR = placementHelper.getFValueRVariable(vm); //add me if ram is considered
            linear2.add(-1, fValueR);

            problem.add(linear, Operator.LE, 0);
            problem.add(linear2, Operator.LE, 0);
                
//                System.out.println("******************************** CONSTRAINT 15 for vm: " + vm.getName() +" :: ");
//            	System.out.print("CPU vm supply - container supply - free vm resource : ");
//            	for(Term record : linear) {
//            		System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//            	}
//            	System.out.print(" <= 0 \n\n");
//            
//            	System.out.println("******************************** CONSTRAINT 19 for vm: " + vm.getName() +" :: ");
//            	System.out.print("RAM vm supply - container supply - free vm resource : ");
//            	for(Term record : linear2) {
//                	System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//            	}
//            	System.out.print(" <= 0 \n\n");
            
        }
    }

    //TODO temp
    private void addConstraint_20(Problem problem) {
    	
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	String g_v_k = placementHelper.getGVariable(vm);
            Linear linear = new Linear();
            linear.add(1, g_v_k);
            problem.add(linear, Operator.GE, placementHelper.getBeta(vm));
            
            String y_v_k = placementHelper.getDecisionVariableY(vm);
            Linear linear2 = new Linear();
            linear2.add(1, g_v_k);
            linear2.add(-1, y_v_k);
            problem.add(linear2, Operator.GE, 0);
            
            Linear linear3 = new Linear();
            linear3.add(1, g_v_k);
            linear3.add(-1, y_v_k);
            problem.add(linear3, Operator.GE, 0);
            
            Linear linear4 = new Linear();
            linear4.add(1, g_v_k);
            linear4.add(-1, y_v_k);
            problem.add(linear4, Operator.LE, placementHelper.getBeta(vm));
        }
    }

//    /**
//     * @param problem to be solved
//     */
//    private void addConstraint_20_to_25(Problem problem) {
//        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
//        	String g_v_k = placementHelper.getGVariable(vm);
//        	String y_v_k = placementHelper.getDecisionVariableY(vm);
//        	String g_times_y = placementHelper.getGYVariable(vm);
//        	int b_v_k = placementHelper.getBeta(vm);
//        	int y_upperBound = Integer.MAX_VALUE;
//
//        	//Constraint 20
//            Linear linear = new Linear();
//            linear.add(1, g_v_k);
//            linear.add(-1, y_v_k);
//            problem.add(linear, Operator.LE, b_v_k);
//            
//            System.out.println("******************************** CONSTRAINT 20 for vm: " + vm.getName() +" :: ");
//        	System.out.print("g - y : ");
//        	for(Term record : linear) {
//            	System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//        	}
//        	System.out.print(" <= b " + b_v_k + " \n\n");
//            
//            //Constraint 21
//            Linear linear1 = new Linear();
//            linear1.add(1, y_v_k);
//            linear1.add(-1 * b_v_k, g_v_k);
//            linear1.add(-1, g_times_y);
//            problem.add(linear1, Operator.LE, -1*b_v_k);
//            
//            System.out.println("******************************** CONSTRAINT 21 for vm: " + vm.getName() +" :: ");
//        	System.out.print("y - b*g - g*y : ");
//        	for(Term record : linear1) {
//            	System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//        	}
//        	System.out.print(" <= -1*b " + (-1)*b_v_k + " \n\n");
//            
//            //Constraint 22
//            Linear linear2 = new Linear();
//            linear2.add(1, g_times_y);
//            linear2.add(-1 * y_upperBound, g_v_k);
//            problem.add(linear2, Operator.LE, 0);
//            
//            //Constraint 23
//            Linear linear3 = new Linear();
//            linear3.add(1, g_times_y);
//            linear3.add(-1, y_v_k);
//            problem.add(linear3, Operator.LE, 0);
//            
//            //Constraint 24
//            Linear linear4 = new Linear();
//            linear4.add(1, g_times_y);
//            linear4.add(-1, y_v_k);
//            linear4.add(-1 * y_upperBound, g_v_k);
//            problem.add(linear4, Operator.GE, -1 * y_upperBound);
//            
//            //Constraint 25
//            Linear linear5 = new Linear();
//            linear5.add(1, g_times_y);
//            problem.add(linear5, Operator.GE, 0);
//        }
//    }
    
    private void addConstraint_20_to_25(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	String g_v_k = placementHelper.getGVariable(vm);
        	String y_v_k = placementHelper.getDecisionVariableY(vm);
        	int b_v_k = placementHelper.getBeta(vm);

        	//Constraint 20
            Linear linear = new Linear();
            linear.add(1, g_v_k);
            linear.add(-1, y_v_k);
            problem.add(linear, Operator.LE, b_v_k);
            
            //Constraint 21
            Linear linear1 = new Linear();
            linear1.add(1, y_v_k);
            linear1.add(-1 * Integer.MAX_VALUE, g_v_k);
            problem.add(linear1, Operator.LE, -1*b_v_k);
            
        }
    }
    
    private void addConstraint_26(Problem problem) {
//    	for (DockerImage dockerImage : cacheDockerService.getDockerImages()) { //TODO: say wrong in 4Fold 
		for (VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
			Linear linear = new Linear();
			for (DockerContainer dockerContainer : cacheDockerService.getAllDockerContainers()) {
				String decisionVariableA = placementHelper.getDecisionVariableA(dockerContainer, vm);
				linear.add(1, decisionVariableA);
			}
			String helperVariableG = placementHelper.getGVariable(vm);
			linear.add(-M, helperVariableG);
			problem.add(linear, "<=", 0);
			
//			System.out.println("******************************** CONSTRAINT 26 for vm: " + vm.getName() +" :: ");
//        	System.out.print("all containers to place on a vm - M * will the vm be leased? : ");
//        	for(Term record : linear) {
//        		System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//        	}
//        	System.out.print(" <= 0 \n\n");
        
		}
//        }
    }
    
    /**
     * @param problem to be solved 
     */
//    private void addConstraint_27(Problem problem) {
//    	List<ProcessStep> steps = getNextAndRunningSteps();
//    	System.out.println("steps (c27): " + steps);
//       
//        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
//        	for(DockerContainer container : cacheDockerService.getAllDockerContainers()) {
//        		Linear linear = new Linear();
//        		
//                for (Element step : steps) {
//                	String decisionVariableX = placementHelper.getDecisionVariableX(step, container);
//                	linear.add(1, decisionVariableX);
//                }
//                
//                String decisionVariableA = placementHelper.getDecisionVariableA(container, vm);
//                linear.add(-N, decisionVariableA);
//                problem.add(linear, "<=",  0);
//        	}
//        }
//    }
    

    /**
     * @param problem to be solved
     */
    private void addConstraint_27(Problem problem) {
    	List<ProcessStep> steps = getNextAndRunningSteps(); 

    	for(DockerContainer container : cacheDockerService.getAllDockerContainers()) {
    		Linear linear = new Linear();

            for (ProcessStep step : steps) {
            	if(step.getServiceType().getName().equals(container.getDockerImage().getServiceType().getName())) {
            		String decisionVariableX = placementHelper.getDecisionVariableX(step, container);
            		linear.add(1, decisionVariableX);
            	}
            }
            for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
	            String decisionVariableA = placementHelper.getDecisionVariableA(container, vm);
	            String decisionVariableG = placementHelper.getGVariable(vm);
	            String a_times_g = placementHelper.getATimesG(vm, container);

            	linear.add(-N, a_times_g);
	            
                //Constraint 
                Linear linear2 = new Linear();
                linear2.add(1, a_times_g);
                linear2.add(-1, decisionVariableA);
                problem.add(linear2, Operator.LE, 0);

                //Constraint 
                Linear linear3 = new Linear();
                linear3.add(1, a_times_g);
                linear3.add(-1, decisionVariableG);
                problem.add(linear3, Operator.LE, 0);

                //Constraint 
                Linear linear4 = new Linear();
                linear4.add(1, a_times_g);
                linear4.add(-1, decisionVariableA);
                linear4.add(-1, decisionVariableG);
                problem.add(linear4, Operator.GE, -1);
                        
                //Constraint 
                Linear linear5 = new Linear();
                linear5.add(1, a_times_g);
                problem.add(linear5, Operator.GE, 0);
	    	}            
            problem.add(linear, Operator.LE,  0);

//			System.out.println("******************************** CONSTRAINT 27 for container: " + container.getName() +" :: ");
//        	System.out.print("all services to place on a container - N * will the container be leased? : ");
//        	for(Term record : linear) {
//        		System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//        	}
//        	System.out.print(" <= 0 \n\n");
        }
    }
    

    /**
     * @param problem to be solved 
     */
    private void addConstraint_28_to_32(Problem problem) {
    	for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
    		for(DockerContainer container : cacheDockerService.getAllDockerContainers()) {
    			String decisionVariableA = placementHelper.getDecisionVariableA(container, vm);
    			String tau_t_1 = "tau_t_1";
    			int tau_t_1_UpperBound = Integer.MAX_VALUE;
    			String a_times_t1 = placementHelper.getATimesT1(container, vm); 
                String decisionVariableY = placementHelper.getDecisionVariableY(vm);
    			long tau_t_0 = tau_t.getTime() / 1000;
    			long d_v_k = placementHelper.getRemainingLeasingDuration(tau_t, vm) / 1000;
            	int b_v_k = placementHelper.getBeta(vm);
            	long BTU = placementHelper.getBTU(vm) / 1000;

                //Constraint 28
                Linear linear = new Linear();
                linear.add(1, a_times_t1);
                linear.add(-1 * tau_t_0, decisionVariableA);
                linear.add(-1 * BTU, decisionVariableY);
                problem.add(linear, "<=", d_v_k * b_v_k);
                
//                System.out.println("******************************** CONSTRAINT 28 for container: " + container.getName() +" on VM : " + vm.getName() + " :: ");
//            	System.out.print("if container is scheduled on a VM, for how long (t1-now)? - btu*lease vm? : ");
//            	for(Term record : linear) {
//            		System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//            	}
//            	System.out.print(" <= remaining time kv is leased d: " + d_v_k + " b: " + b_v_k + " = " + d_v_k*b_v_k + " \n\n");

                //Constraint 29
                Linear linear2 = new Linear();
                linear2.add(1, a_times_t1);
                linear2.add(-1 * tau_t_1_UpperBound, decisionVariableA);
                problem.add(linear2, Operator.LE, 0);

                //Constraint 30
                Linear linear3 = new Linear();
                linear3.add(1, a_times_t1);
                linear3.add(-1, tau_t_1);
                problem.add(linear3, Operator.LE, 0);

                //Constraint 31
                Linear linear4 = new Linear();
                linear4.add(1, a_times_t1);
                linear4.add(-1, tau_t_1);
                linear4.add(-1 * tau_t_1_UpperBound, decisionVariableA);
                problem.add(linear4, Operator.GE, -1 * tau_t_1_UpperBound);
                        
                //Constraint 32
                Linear linear5 = new Linear();
                linear5.add(1, a_times_t1);
                problem.add(linear5, Operator.GE, 0);
            }
            
        }
    }
    
    /**
     * @param problem to be solved
     */
    private void addConstraint_33_to_36(Problem problem) {
		List<ProcessStep> steps = getAllNextStepsAsList();

    	for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
			String decisionVariableY = placementHelper.getDecisionVariableY(vm);
			long d_v_k = placementHelper.getRemainingLeasingDuration(tau_t, vm) / 1000;
        	int b_v_k = placementHelper.getBeta(vm);
        	long BTU = placementHelper.getBTU(vm) / 1000;
			long vmStartupTime = vm.getStartupTime() / 1000;

    		for(DockerContainer container : cacheDockerService.getAllDockerContainers()) {
    			String decisionVariableA = placementHelper.getDecisionVariableA(container, vm);
    			
    			for (ProcessStep step : steps) {
                	if(step.getServiceType().getName().equals(container.getDockerImage().getServiceType().getName())) {
	
	    				String decisionVariableX = placementHelper.getDecisionVariableX(step, container);
	    				String a_times_x = placementHelper.getAtimesX(step, container, vm); 
	    				int variableZ = placementHelper.imageForStepEverDeployedOnVM((ProcessStep)step, vm);
	    				long remainingExecutionTime = step.getRemainingExecutionTime(tau_t) / 1000;
	    				long serviceDeployTime = container.getDeployTime() / 1000;
    				
    					//Constraint 33
	    				Linear linear = new Linear();
	    				linear.add(remainingExecutionTime + serviceDeployTime * (1 - variableZ) + vmStartupTime * (1 - b_v_k), a_times_x);
	    				linear.add(- BTU, decisionVariableY);
	    				problem.add(linear, "<=", d_v_k * b_v_k);
	    				
//	    				System.out.println("******************************** CONSTRAINT 33 for container: " + container.getName() +" on VM : " + vm.getName() + " :: ");
//	    	            System.out.print("remaining execution time incl service deploy  and startup time * deploy? - btu*lease vm? : ");
//	    	            	for(Term record : linear) {
//	    	            		System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
//	    	            	}
//	    	            System.out.print(" <= remaining time kv is leased d: " + d_v_k + " b: " + b_v_k + " = " + d_v_k*b_v_k + " \n\n");

	    				
	    				//Constraint 34
	    				Linear linear2 = new Linear();
	    				linear2.add(1, a_times_x);
	    				linear2.add(-1, decisionVariableA);
	    				problem.add(linear2, Operator.LE, 0);
	    				
	    				//Constraint 35
	    				Linear linear3 = new Linear();
	    				linear3.add(1, a_times_x);
	    				linear3.add(-1, decisionVariableX);
	    				problem.add(linear3, Operator.LE, 0);  

	    				//Constraint 36
	    				Linear linear4 = new Linear();
	    				linear4.add(1, a_times_x);
	    				linear4.add(-1, decisionVariableA);
	    				linear4.add(-1, decisionVariableX);
	    				problem.add(linear4, Operator.GE, -1);
	    				
	    				//Constraint 37
	    				Linear linear5 = new Linear();
	    				linear5.add(1, a_times_x);
	    				problem.add(linear5, Operator.GE, 0);
                	}
    			}
            }
        }
    }

    /**
     * @param problem to be solved 
     */
    private void addConstraint_37(Problem problem) {
      List<ProcessStep> steps = getAllRunningSteps();

      for (ProcessStep step : steps) {
    	  DockerContainer container = step.getScheduledAtContainer();
          VirtualMachine virtualMachine = container.getVirtualMachine();
          String decisionVariableY = placementHelper.getDecisionVariableY(virtualMachine);
          long d_v_k = placementHelper.getRemainingLeasingDuration(tau_t, virtualMachine) / 1000;
          int b_v_k = placementHelper.getBeta(virtualMachine);
          long BTU = placementHelper.getBTU(virtualMachine) / 1000;
          long remainingExecutionTimeAndDeployTimes = getRemainingExecutionTimeAndDeployTimes(step) / 1000;
          
          Linear linear = new Linear();
          linear.add(- BTU, decisionVariableY);
          problem.add(linear, "<=",  d_v_k*b_v_k - remainingExecutionTimeAndDeployTimes);
          
         
          System.out.println("******************************** CONSTRAINT 37 for step " + step + "scheduled on container: " + container.getName() +" on VM : " + virtualMachine.getName() + " :: ");
          System.out.print("r- btu*lease vm? : ");
          	for(Term record : linear) {
          		System.out.print(record.getCoefficient() +" * "+ record.getVariable() + "     +     ");
          	}
          System.out.print(" <= remaining time kv is leased d: " + d_v_k + " b: " + b_v_k + " = " + d_v_k*b_v_k + " - remainingExecAndDeployTime " + remainingExecutionTimeAndDeployTimes + " \n\n");

      }
    }
    
//TODO: check if necessary for Docker!

//    /**
//     * DS: Makes sure that services with different types are not place on the same VM
//     *
//     * @param problem to be solved
//     */
//    private void addConstraint_50(Problem problem) {
//
//        List<ProcessStep> steps = getNextAndRunningSteps();
//        List<ProcessStep> steps2 = new ArrayList<>(steps);
//
//        for (ProcessStep step1 : steps) {
//            for (ProcessStep step2 : steps2) {
//                if (!step1.getName().equals(step2.getName())) { //save some iterations, only take the different step
//                    //DS: we only need this constraint if the service types are different
//                    if (!step1.getServiceType().name().equals(step2.getServiceType().name())) {
//                    	for(DockerContainer container : cacheDockerService.getAllDockerContainers()){
//                            String decisionVariable1 = placementHelper.getDecisionVariableX(step1, container);
//                            String decisionVariable2 = placementHelper.getDecisionVariableX(step2, container);
//                            Linear linear = new Linear();
//                            linear.add(1, decisionVariable1);
//                            linear.add(1, decisionVariable2);
//                            problem.add(linear, "<=", 1);
//                            
//                        }
//                    }
//                }
//            }
//            steps2.remove(step1);
//        }
//    }
    
    
    
    private void addConstraint_38(Problem problem) {
    	for (VMType vmType : cacheVirtualMachineService.getVMTypes()) {
            Linear linear = new Linear();
            String gamma = placementHelper.getGammaVariable(vmType);
            
            for (VirtualMachine vm : cacheVirtualMachineService.getVMs(vmType)) {
                String variableY = placementHelper.getDecisionVariableY(vm);
                linear.add(1, variableY);
            }
            linear.add(-1, gamma);
            problem.add(linear, "<=", 0);
        }

    }
    
    private void addConstraint_39(Problem problem) {
//    	System.out.println("getAllNextStepsAsList(): " + getAllNextStepsAsList());
    	for (ProcessStep step : getAllNextStepsAsList()) {
    		Linear linear = new Linear();
//            System.out.println("getDockerContainers for step : " + " - " + step + " - " + cacheDockerService.getDockerContainers(step));
            for(DockerContainer container : cacheDockerService.getDockerContainers(step)){
                String variableX = placementHelper.getDecisionVariableX(step, container);
                linear.add(1, variableX);
            }
            problem.add(linear, "<=", 1);
        }
    }
    
    private void addConstraint_40(Problem problem) {
    	for (DockerImage image : cacheDockerService.getDockerImages()) {
            for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
            	Linear linear = new Linear();
                for (DockerContainer dockerContainer : cacheDockerService.getDockerContainers(image)) {
                	String decisionVariableA = placementHelper.getDecisionVariableA(dockerContainer, vm);
                    linear.add(1, decisionVariableA);
                }
                problem.add(linear, "<=", 1);
            }
        }
    }
    
    private void addConstraint_41(Problem problem) {
        for (ProcessStep step : getAllRunningSteps()) {
            String containerName = step.getScheduledAtContainer().getName();
            
            for(DockerContainer container : cacheDockerService.getDockerContainers(step)){
                String variableX = placementHelper.getDecisionVariableX(step, container);
                Linear linear = new Linear();
                linear.add(1, variableX);
                
                boolean runsAt = containerName.equals(container.getName());
                if (runsAt) {
                	problem.add(linear, Operator.EQ, 1);
                	
                	for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
                		String variableA = placementHelper.getDecisionVariableA(container, vm);

                		Linear linear2 = new Linear();
                		linear2.add(1, variableA);

                		boolean deployedAt = container.getVirtualMachine().getName().equals(vm.getName());
                		if(deployedAt) {
                			problem.add(linear2, Operator.EQ, 1);
                		} else {
                			problem.add(linear2, Operator.EQ, 0);
                		}
                		problem.setVarUpperBound(variableA, 1);
                        problem.setVarLowerBound(variableA, 0);
                	}
                }
                else {
                    problem.add(linear, Operator.EQ, 0);
                }
                problem.setVarUpperBound(variableX, 1);
                problem.setVarLowerBound(variableX, 0);
            }
        }
    }

    private void addConstraint_42(Problem problem) {
    	for (ProcessStep step : getAllNextStepsAsList()) {
            for(DockerContainer container : cacheDockerService.getDockerContainers(step)){
                String variableX = placementHelper.getDecisionVariableX(step, container);
                Linear linear = new Linear();
                linear.add(1, variableX);
                problem.add(linear, "<=", 1);
                problem.add(linear, ">=", 0);
                problem.setVarType(variableX, VarType.INT);
                problem.setVarLowerBound(variableX, 0);
                problem.setVarUpperBound(variableX, 1);                
            }
        }
    }

    private void addConstraint_43(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
            for(DockerContainer container : cacheDockerService.getAllDockerContainers()){
                String variableA = placementHelper.getDecisionVariableA(container, vm);
                Linear linear = new Linear();
                linear.add(1, variableA);
                problem.add(linear, "<=", 1);
                problem.add(linear, ">=", 0);
                problem.setVarType(variableA, VarType.INT);
                problem.setVarLowerBound(variableA, 0);
                problem.setVarUpperBound(variableA, 1);                
            }
        }
    }
    
    /**
     * @param problem to add the variable
     */
    private void addConstraint_44(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
            String variableG = placementHelper.getGVariable(vm);
            Linear linear = new Linear();
            linear.add(1, variableG);
            problem.add(linear, "<=", 1);
            problem.add(linear, ">=", 0);
            problem.setVarType(variableG, VarType.INT);
            problem.setVarLowerBound(variableG, 0);
            problem.setVarUpperBound(variableG, 1);
        }
    }
    
    /**
     * @param problem to add the variable
     */
    private void addConstraint_45(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
            String variableY = placementHelper.getDecisionVariableY(vm);
            Linear linear = new Linear();
            linear.add(1, variableY);
            problem.add(linear, ">=", 0);
            problem.setVarType(variableY, VarType.INT);
            problem.setVarUpperBound(variableY, Integer.MAX_VALUE);
            problem.setVarLowerBound(variableY, 0);
        }
    }

    /**
     * @param problem to add the variable
     */
    private void addConstraint_46(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	for(DockerContainer container : cacheDockerService.getAllDockerContainers()) {
	            String variableATimesG = placementHelper.getATimesG(vm, container);
	            Linear linear = new Linear();
	            linear.add(1, variableATimesG);
	            problem.add(linear, ">=", 0);
	            problem.setVarType(variableATimesG, VarType.INT);
	            problem.setVarUpperBound(variableATimesG, 1);
	            problem.setVarLowerBound(variableATimesG, 0);
        	}
        }
    }

    private void addConstraint_47(Problem problem) {
		List<ProcessStep> steps = getAllNextStepsAsList();

        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	for(DockerContainer container : cacheDockerService.getAllDockerContainers()) {
        		for (ProcessStep step : steps) {
                	if(step.getServiceType().getName().equals(container.getDockerImage().getServiceType().getName())) {
			            String variableATimesX = placementHelper.getAtimesX(step, container, vm);
			            Linear linear = new Linear();
			            linear.add(1, variableATimesX);
			            problem.add(linear, ">=", 0);
			            problem.setVarType(variableATimesX, VarType.INT);
			            problem.setVarUpperBound(variableATimesX, 1);
			            problem.setVarLowerBound(variableATimesX, 0);
                	}
        		}
        	}
        }
    }
    
    private void addConstraint_48(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	for(DockerContainer container : cacheDockerService.getAllDockerContainers()) {
	            String variableATimesT1 = placementHelper.getATimesT1(container, vm);
	            Linear linear = new Linear();
	            linear.add(1, variableATimesT1);
	            problem.add(linear, ">=", 0);
	            problem.setVarType(variableATimesT1, VarType.INT);
	            problem.setVarUpperBound(variableATimesT1, Integer.MAX_VALUE);
	            problem.setVarLowerBound(variableATimesT1, 0);
        	}
        }
    }

    private void addConstraint_49(Problem problem) {
    	for (WorkflowElement workflowInstance : getNextWorkflowInstances()) {
            Linear linear = new Linear();
            String executionTimeViolation = placementHelper.getExecutionTimeViolationVariable(workflowInstance);
            linear.add(1, executionTimeViolation);
            problem.add(linear, ">=", Double.MIN_VALUE);
            problem.add(linear, "<=", Double.MAX_VALUE);
            problem.setVarLowerBound(executionTimeViolation, Double.MIN_VALUE);
            problem.setVarUpperBound(executionTimeViolation, Double.MAX_VALUE);
            problem.setVarType(executionTimeViolation, VarType.REAL);
        }
    }

    //Goal of this constraint is to eliminate the possibility that sensitive services are deployed on a vm with the type 5, 6 or 7
//    private void addConstraint_30(Problem problem) {
//        List<Element> steps = getAllNextStepsAsList();//todo check if duplicated steps
//
//        for (Element step : steps) {
//            for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
//                    //vm types 4,5 and 6 are aws types
//
//                    //TODO currently only services 3 6 and 9 are forbidden to be executed on the public cloud
//
//
////                    if (v >= internalTypes) {// steps can also be restricted for interna VMs
//                ProcessStep processStep = (ProcessStep) step;
//                List<Integer> restrictedVMs = processStep.getRestrictedVMs();
//                if (restrictedVMs != null && restrictedVMs.contains(vm.getVmType().getIdentifier())) {
//                	String variable = placementHelper.getDecisionVariableX(step, vm);
//                    Linear linear = new Linear();
//                    linear.add(1, variable);
//                    problem.add(linear, "=", 0);
//                }
////                        if (("task3".equals(processStep.getType().getName())) ||
////                                ("task6".equals(processStep.getType().getName())) ||
////                                ("task9".equals(processStep.getType().getName()))) {
////                            String variable = "x_" + step.getName() + "," + v + "_" + k;
////                            Linear linear = new Linear();
////                            linear.add(1, variable);
////                            problem.add(linear, "=", 0);
////                        }
////                    }
//
//                
//            }
//        }
//    }

    // the goal of this contraint is to implement the colocation of tasks (implicit) and minimal data transfer costs
//    private void addConstraint_31(Problem problem) {
//        List<Element> steps = getNextSteps();//todo check if duplicated steps
//
//        Linear linearTransfer = new Linear();
//        linearTransfer.add(1, "transfercosts");
//        problem.add(linearTransfer, ">=", 0);
//        problem.setVarType(linearTransfer, VarType.INT);
//
//
//        Linear linear = new Linear();
//
//        for (Element stepId : steps) {
//            String location = "";
//
//            if (stepId.getLastExecutedElement() == null) {
//                location = "internal";
//            }
//            else {
//                if (stepId.getLastExecutedElement().getScheduledAtVM() != null) {
//                    location = stepId.getLastExecutedElement().getScheduledAtVM().getLocation();
//                }
//                else {
//                    location = "internal";
//                }
//            }
//
//            for (int v = 0; v < V; v++) {
//                for (int k = 0; k < K; k++) {
//                    String variable = "x_" + stepId.getName() + "," + v + "_" + k;
//
//                    if ("internal".equals(location)) {
//                        if (v < internalTypes) {
//                            linear.add(0, variable);
//                        }
//                        else {
//                            linear.add(stepId.getLastExecutedElement().getServiceType().getDataToTransfer(), variable);
//                        }
//
//                    }
//                    else {
//                        if (v >= internalTypes) {
//                            linear.add(0, variable);
//                        }
//                        else {
//                            linear.add(stepId.getLastExecutedElement().getServiceType().getDataToTransfer(), variable);
//                        }
//                    }
//                }
//            }
//        }
//
//        String transfer = "transfercosts";
//        linear.add(-1, transfer);
//        problem.add(linear, ">=", 0);
//
//    }


    //##################################################################################################################
    //################################################# Helper Methods #################################################
    //##################################################################################################################

//    private void updateUsageMap() {
//
//        for (int v = 0; v < V; v++) {
//            Integer usedVMs = 0;
//            for (int k = 0; k < K; k++) {
//                if (getBeta(v, k) == 1) {
//                    usedVMs++;
//                }
//            }
//            currentVMUsage.put(v, usedVMs);
//        }
//    }



    /**
     * @return a list of workflow instances
     */
    public List<WorkflowElement> getNextWorkflowInstances() {
        if (nextWorkflowInstances == null) {
            nextWorkflowInstances = Collections.synchronizedList(new ArrayList<WorkflowElement>(cacheWorkflowService.getRunningWorkflowInstances()));
        }
        return nextWorkflowInstances;
    }

	private List<ProcessStep> getAllNextStepsAsList() {
    	List<ProcessStep> allNextStepsAsList = new ArrayList<>();

        if(nextSteps.isEmpty()){
        	for (WorkflowElement workflow : getNextWorkflowInstances()) {
        		List<ProcessStep> nextStepsOfWorkflow = Collections.synchronizedList(new ArrayList<ProcessStep>(placementHelper.getNextSteps(workflow.getName())));
                nextSteps.put(workflow.getName(), nextStepsOfWorkflow);
            }
        }
        
        for(String workflowName : nextSteps.keySet()){
        	allNextStepsAsList.addAll(nextSteps.get(workflowName));
        }
        return allNextStepsAsList;
    }


    /**
     * @param workflowInstanceID of the running steps
     * @return a list of currently running steps
     */
    public List<ProcessStep> getRunningStepsForWorkflow(String workflowInstanceID) {
        if (!runningSteps.containsKey(workflowInstanceID)) {
            List<ProcessStep> runningProcessSteps = Collections.synchronizedList(new ArrayList<ProcessStep>(placementHelper.getRunningProcessSteps(workflowInstanceID)));
            runningSteps.put(workflowInstanceID, runningProcessSteps);
        }
        return runningSteps.get(workflowInstanceID);
    }

    public List<ProcessStep> getAllRunningSteps() {
        if (allRunningSteps == null) {
            allRunningSteps = new ArrayList<>();
            List<WorkflowElement> nextWorkflowInstances = getNextWorkflowInstances();
            for (Element workflowInstance : nextWorkflowInstances) {
                List<ProcessStep> runningStepsForWorkflowInstanceID = getRunningStepsForWorkflow(workflowInstance.getName());
                allRunningSteps.addAll(runningStepsForWorkflowInstanceID);
            }
        }
        return allRunningSteps;
    }

    public List<ProcessStep> getNextAndRunningSteps() {
        List<ProcessStep> steps = getAllNextStepsAsList();
        List<ProcessStep> runningSteps = getAllRunningSteps();
        for (ProcessStep step : runningSteps) {
            if (!steps.contains(step)) {
                steps.add(step);
            }
        }
        return steps;
    }
    
    /**
     * @param v needed to identify a vm type
     * @return the costs for that VM
     */
//    public double getCostForVM(int v) {
//        if (!useInternVmsFirst) {
//            return getVMType(v).getCosts();
//        }
        //only works under the assumption that there are the same vm types on the public cloud

        /**
         * k=2
         *
         * v=0 -> single core intern
         * v=1 -> dual core intern
         * v=2 -> quad core intern
         *
         * v=3 -> single core public
         * v=4 -> dual core public
         * v=5 -> quad core public
         * TODO Fix Me, check cost for external VMs
         *
         */

//        if (v < internalTypes) {
//            //is a private VM
//            return getVMType(v + 1).getCosts();
//        }
//        else {
//            if (currentVMUsage.get(v - internalTypes) < K) {
//                //there are the same instances available on the private cloud
//                return getVMType(v + 1).getCosts() * EXTERNAL_CLOUD_FACTOR;
//            }
//            else {
//
//                for (int i = 0; i < V - v; i++) {
//                    if (currentVMUsage.get(i) < K) {
//                        return getVMType(v + 1).getCosts() * EXTERNAL_CLOUD_FACTOR;
//                    }
//                }
//
//                //check if there are larger VMs on the private cloud available
//                Integer amountOfLargerVMTypes = v - internalTypes - 1;
//                for (int i = v + 1; i <= amountOfLargerVMTypes; i++) {
//                    if (currentVMUsage.get(i - internalTypes) < K) {
//                        return getVMType(v + 1).getCosts() * EXTERNAL_CLOUD_FACTOR;
//                    }
//                }
//                return getVMType(v + 1).getCosts();
//            }
//        }
//    }


    /**
     * @param step to find out rest execution time
     * @return the remaining execution time for step
     */
    public long getRemainingExecutionTimeAndDeployTimes(ProcessStep processStep) {
        long remainingExecutionTime = processStep.getRemainingExecutionTime(tau_t);
        if (processStep.isScheduled()) {
//            log.info("getRemainingExecutionTimeAndDeployTimes finishWorkflow");
            remainingExecutionTime += placementHelper.getRemainingSetupTime(processStep.getScheduledAtContainer(), tau_t);
        }
        else {
            remainingExecutionTime += CONTAINER_DEPLOY_TIME + VM_STARTUP_TIME;
        }
        if (remainingExecutionTime < 0) {
            remainingExecutionTime = 0;
        }
        return remainingExecutionTime;

    }

    /**
     * @param elem        current element
     * @param linear      the linear function for the problem
     * @param problem     the lp problem
     * @param factor      what factor
     * @param nextStepIds next step ids of the whole workflow
     */
    public void generateConstraintsForCalculatingExecutionTime(Element elem, Linear linear, Problem problem, int factor,
                                                               List<String> nextStepIds) {
        if (elem instanceof ProcessStep) {
            String processStepVariable = "e_p_" + elem.getName();
            linear.add(factor, processStepVariable);

            Linear linearProcessStep = new Linear();
            linearProcessStep.add(1, processStepVariable);
            if (((ProcessStep) elem).hasBeenExecuted()) {
                problem.add(linearProcessStep, "=", 0);
//                for(Term record : linearProcessStep) {
//                	System.out.println("******************************** CONSTRAINT 5 to 11 TERMS for processSteps:hasbeenexecuted :: ");
//                    System.out.println(record.getVariable() + " = " + 0);
//                }
            }
            else {
                long remainingExecutionTimeAndDeployTimes = getRemainingExecutionTimeAndDeployTimes((ProcessStep) elem);
                if (nextStepIds.contains(elem.getName())) {
                    for(DockerContainer container : cacheDockerService.getDockerContainers((ProcessStep) elem)){
                        String decisionVariableX = placementHelper.getDecisionVariableX(elem, container);
                        linearProcessStep.add(remainingExecutionTimeAndDeployTimes / 1000, decisionVariableX);
                    }
                }
                problem.add(linearProcessStep, "=", remainingExecutionTimeAndDeployTimes / 1000);
                
//                for(Term record : linearProcessStep) {
//                	System.out.println("******************************** CONSTRAINT 5 to 11 TERMS for processSteps :: ");
//                    System.out.println(record.getVariable() + " = " + remainingExecutionTimeAndDeployTimes / 1000);
//
//                }
                //e_p +  QoS + DeployTime + VMStartUp)*x  = QoS + DeployTime + VMStartUp
            }
        }
        else if (elem instanceof Sequence) {
            String elementVariable = "e_s_" + elem.getName();
            linear.add(factor, elementVariable);
            List<Element> subElements = elem.getElements();
            Linear linearForSubElements = new Linear();
            linearForSubElements.add(1, elementVariable);
            for (Element subElement : subElements) {
                generateConstraintsForCalculatingExecutionTime(subElement, linearForSubElements, problem, -1, nextStepIds);
            }
            problem.add(linearForSubElements, "=", 0);
//            for(Term record : linearForSubElements) {
//            	System.out.println("******************************** CONSTRAINT 5 to 11 TERMS for subElements - sequence :: ");
//                System.out.println(record.getVariable());
//
//            }
        }
        else if (elem instanceof ANDConstruct) {
            String elementVariable = "e_a_" + elem.getName();
            linear.add(factor, elementVariable);
            List<Element> subElements = elem.getElements();
            for (Element subElement : subElements) {
                Linear linearForSubElements = new Linear();
                linearForSubElements.add(1, elementVariable);
                generateConstraintsForCalculatingExecutionTime(subElement, linearForSubElements, problem, -1, nextStepIds);
                problem.add(linearForSubElements, ">=", 0);
                
//                for(Term record : linearForSubElements) {
//                	System.out.println("******************************** CONSTRAINT 5 to 11 TERMS for subElements - and :: ");
//                    System.out.println(record.getVariable());
//
//                }
            }

        }
        else if (elem instanceof XORConstruct) {
            String elementVariable = "e_x_" + elem.getName();
            linear.add(factor, elementVariable);
            List<Element> subElements = elem.getElements();
            Element maxSubElement = null;
            for (Element subElement : subElements) {
                if (maxSubElement == null) {
                    maxSubElement = subElement;
                }
                else if (subElement.calculateQoS() / 1000 > maxSubElement.calculateQoS() / 1000) {
                    maxSubElement = subElement;
                }
            }
            Linear linearForSubElements = new Linear();
            linearForSubElements.add(1, elementVariable);
            generateConstraintsForCalculatingExecutionTime(maxSubElement, linearForSubElements, problem, -1, nextStepIds);
            problem.add(linearForSubElements, ">=", 0);
            
//            for(Term record : linearForSubElements) {
//            	System.out.println("******************************** CONSTRAINT 5 to 11 TERMS for subElements - xor :: ");
//                System.out.println(record.getVariable());
//
//            }
        }
        else if (elem instanceof LoopConstruct) {
            String elementVariable = "e_lo_" + elem.getName();
            linear.add(factor, elementVariable);
            Element subElement = elem.getElements().get(0);
            Linear linearForSubElement = new Linear();
            linearForSubElement.add(1, elementVariable);
            generateConstraintsForCalculatingExecutionTime(subElement, linearForSubElement, problem,
                    -((LoopConstruct) elem).getNumberOfIterationsInWorstCase(), nextStepIds);
            problem.add(linearForSubElement, ">=", 0);
            
//            for(Term record : linearForSubElement) {
//            	System.out.println("******************************** CONSTRAINT 5 to 11 TERMS for subElements - loop :: ");
//                System.out.println(record.getVariable());
//
//            }
        }

    }

//    @Override
    public Collection<Object> getVariables() {
        return this.problem.getVariables();
    }
    
	private String getAllObjectives(Result optimize) {
		System.out.println("\n Term 1 \n");
		double sum1 = 0;

		for (VMType vmType : cacheVirtualMachineService.getVMTypes()) {
			String gamma = placementHelper.getGammaVariable(vmType);
			double c = optimize.get(gamma).doubleValue();
			sum1 += vmType.getCosts() * c;
			System.out.println("Sum just increased for vmType: " + vmType);
			System.out.println("vmCosts are: " + vmType.getCosts() + ", "+gamma +": " + c + " --> cost*gamma = "+ vmType.getCosts() * c);
		}

		System.out.println("Value: " + sum1);

		System.out.println("\n Term 2 \n");
		double sum2 = 0;
		double sum6 = 0;
		for (WorkflowElement workflowInstance : getNextWorkflowInstances()) {
			// Term2
			String executionTimeViolation = placementHelper
					.getExecutionTimeViolationVariable(workflowInstance);
			double cp = optimize.get(executionTimeViolation).doubleValue();
			sum2 += DEADLINE_WEIGHT * placementHelper.getPenaltyCostPerQoSViolationForProcessInstance(workflowInstance) * cp;

			// Term 6
			long enactmentDeadline = placementHelper.getEnactmentDeadline(workflowInstance);
			double enactmentDeadlineSmall = enactmentDeadline / 1000;
			double tauSmall = tau_t.getTime() / 1000;
			double diffInMinutes = (enactmentDeadlineSmall - tauSmall); 
			Double coefficient = 1.0 / diffInMinutes;
			if (Double.isInfinite(coefficient) || coefficient <= 0) {
				coefficient = 100.0 - diffInMinutes;
			}

			for (ProcessStep step : nextSteps.get(workflowInstance.getName())) {
                for(DockerContainer container : cacheDockerService.getDockerContainers(step)){
					String decisionVariableX = placementHelper.getDecisionVariableX(step, container);
					int x = toInt(optimize.get(decisionVariableX));
					sum6 += -1 * coefficient * x;
				}
			}
		}

		System.out.println("Value: " + sum2);

		System.out.println("\n Term 3 \n");
		double sum3 = 0;

		// Term 3
		for (VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
			for (DockerContainer dockerContainer : cacheDockerService.getAllDockerContainers()) {
				if (placementHelper.imageForContainerEverDeployedOnVM(dockerContainer, vm) == 0) {
					String decisionVariableA = placementHelper.getDecisionVariableA(dockerContainer, vm);
					int a = toInt(optimize.get(decisionVariableA));
					sum3 += dockerContainer.getDeployCost() * OMEGA_DEPLOY_D_VALUE * a;
				}
			}

		}

		System.out.println("Value: " + sum3);

		System.out.println("\n Term 4 \n");
		double sum4 = 0;

		// Term 4
		for (VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
			String fValueC = placementHelper.getFValueCVariable(vm);
			double fc = optimize.get(fValueC).doubleValue();
			String fValueR = placementHelper.getFValueRVariable(vm);
			double fr = optimize.get(fValueR).doubleValue();
			sum4 += OMEGA_F_C_VALUE * fc;
			sum4 += OMEGA_F_R_VALUE * fr;
		}

		System.out.println("Value: " + sum4);

		System.out.println("\nTerm 5 \n");
		double sum5 = 0;
		// Term 5
		for (VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
			for (DockerContainer dockerContainer : cacheDockerService.getAllDockerContainers()) {
				String decisionVariableA = placementHelper.getDecisionVariableA(dockerContainer, vm);
				int a = toInt(optimize.get(decisionVariableA));
				double containerCPUSupply = placementHelper.getSuppliedCPUPoints(dockerContainer);
				double containerResourceSupply = placementHelper.getSuppliedRAMPoints(dockerContainer);
				sum5 += OMEGA_S_C_VALUE * containerCPUSupply * a;
				sum5 += OMEGA_S_R_VALUE * containerResourceSupply * a;

			}
		}
		
		System.out.println("Value: " + sum5);

		System.out.println("\n Term 6 \n");
		System.out.println("Value: " + sum6);

		
//		System.out.println("_______________ ALL CONSTRAINTS ______________________");
//		
//		System.out.println("Constraint 27:");
//	    List<ProcessStep> steps = getNextAndRunningSteps();
//    	for(DockerContainer container : cacheDockerService.getAllDockerContainers()) {
//    		int sumC27 = 0;
//    		for (ProcessStep step : steps) {
//	           	if(step.getServiceType().getName().equals(container.getDockerImage().getServiceType().getName())) {
//	           		String decisionVariableX = placementHelper.getDecisionVariableX(step, container);
//	           		int x = toInt(optimize.get(decisionVariableX));
//	            	sumC27 += 1 * x;
//	            	if(x != 0) {
//	            		System.out.println("x!=0 for :" + decisionVariableX + " Value="+x);
//	            	}
//	            }
//	         }
//	         for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
//	        	 String decisionVariableA = placementHelper.getDecisionVariableA(container, vm);
//		         String decisionVariableG = placementHelper.getGVariable(vm);
//		         String a_times_g = placementHelper.getATimesG(vm, container); 
//		         int a = toInt(optimize.get(decisionVariableA));
//		         int g = toInt(optimize.get(decisionVariableG));
//		         int a_t_g = toInt(optimize.get(a_times_g));
//		         
//	             sumC27 += (-N * a_t_g);
//	             
//	             
//	             System.out.print("a = " + decisionVariableA + " Value="+a);
//	             System.out.print("  //  g = " + decisionVariableG + " Value="+g);
//	             System.out.print("  //  a_times_g = " + a_times_g + " Value="+a_t_g);
//	             System.out.println("    Alltogether a (" + a + ") * g (" + g + ") = " + a_t_g );
//		            
//	             
//		    }
//
//			System.out.print("For Container " + container + " Sum is " + sumC27 + " <= 0 \n\n");
//	        
//	    }
    	
    	//maximize tau_t_1
//    	double sum7 = -TAU_T_1_WEIGHT * optimize.get("tau_t_1").doubleValue();
//    	System.out.println("tau_t_1 term: " + sum7);
		
		return "\n Sum : "+ (sum1+sum2+sum3+sum4+sum5+sum6);
	}
	
	private int toInt(Number n) {
		return (int)Math.round(n.doubleValue());
	}
	
	private String getAllSolvedConstraints(Result result, Problem problem) {
		for(Constraint constraint : problem.getConstraints()) {
			System.out.println("LHS Variables : ");
			double lhsSum = 0;
			for(int i = 0; i< constraint.getLhs().size(); i++) {
				double coefficient = constraint.getLhs().get(i).getCoefficient().doubleValue();
				Object variable = constraint.getLhs().get(i).getVariable();
				
				System.out.print(coefficient + " * " + variable + " (" + result.get(variable) + ") + ");
				lhsSum+=(coefficient * result.get(variable).doubleValue());
			}
			String operator = constraint.getOperator().toString();
			
			System.out.println("//// RHS result = " + constraint.getRhs());
			System.out.println("    ********************************************************** Alltogether: " + lhsSum + operator + constraint.getRhs());
		}
		return "";
	}
}
