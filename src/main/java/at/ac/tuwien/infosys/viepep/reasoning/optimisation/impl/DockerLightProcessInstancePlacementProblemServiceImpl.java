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
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloLinearNumExprIterator;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloRange;
import ilog.cplex.CpxNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.BooleanParam;
import ilog.cplex.IloCplex.DoubleParam;
import ilog.cplex.IloCplex.IntParam;
import ilog.cplex.IloCplex.LongParam;
import ilog.cplex.IloCplex.StringParam;
import lombok.extern.slf4j.Slf4j;
import net.sf.javailp.*;
import static at.ac.tuwien.infosys.viepep.Constants.START_EPOCH;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;


/**
 * @author Gerta Sheganaku
 */
@Slf4j
//@Component
public class DockerLightProcessInstancePlacementProblemServiceImpl extends NativeLibraryLoader implements ProcessInstancePlacementProblemService {

    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;

//    @Value("${optimization.use.internVms.first}")
//    private boolean useInternVmsFirst;           // has to be true that the internal storage is filled first

    public static final Object SYNC_OBJECT = "Sync_Lock";

    private static final double EXTERNAL_CLOUD_FACTOR = 1; //not considered
    
    private static final double OMEGA_F_R_VALUE = 0.001; //0.0001
    private static final double OMEGA_F_C_VALUE = 0.01; //0.0001
    private static final double PREFER_LONGER_LEASED_VMS = 0.001; // 0.000001;

    private static final double OMEGA_DEPLOY_D_VALUE = 1; // 0.001; //CHECK only a weight for actual deploy value

    private Date tau_t;
    private Date tau_t_startepoch;
    
    private static final long EPSILON = ReasoningImpl.MIN_TAU_T_DIFFERENCE_MS / 1000;

    private long M = 10;

    @Value("${dockercontainer.deploy.time}")
    private long CONTAINER_DEPLOY_TIME; //30000L

    @Value("${dockercontainer.deploy.cost}")
    private long CONTAINER_DEPLOY_COST; //30000L
    
    @Value("${virtualmachine.startup.time}")
    private long VM_STARTUP_TIME; //60000L

    private List<ProcessStep> allRunningSteps;
    private List<WorkflowElement> nextWorkflowInstances;
    private Map<String, List<ProcessStep>> nextSteps;
    private Map<String, List<ProcessStep>> runningSteps;
    private Problem problem;

    public Result optimize(Date tau_t) {
    	tau_t_startepoch = new Date(((tau_t.getTime() / 1000) - START_EPOCH) * 1000);

    	//cleanups
        synchronized (SYNC_OBJECT) {
        	placementHelper.setFinishedWorkflows();

            nextWorkflowInstances = null;
            nextSteps = new HashMap<>();
            allRunningSteps = null;
            runningSteps = new HashMap<>();
            
            getRunningWorkflowInstances();
            getAllNextStepsAsList();

            getAllRunningSteps();
            getNextAndRunningSteps();
        }

        this.tau_t = tau_t;

        SolverFactory factory;
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

        Solver solver = new ViePEPSolverCPLEX(); // factory.get();

        log.info(printCollections());

        problem = new Problem();
        addObjective_1(problem);
        addConstraint_2(problem);
        addConstraint_3(problem); 
        addConstraint_4_to_11(problem);
        addConstraint_12_16(problem);
        addConstraint_15_19(problem);
        
        addConstraint_20_to_25(problem);
//        addConstraint_18(problem);
//        addConstraint_19(problem);
//        addConstraint_20(problem);
        
//        addConstraint_27(problem);
        addConstraint_33_to_36(problem);
        addConstraint_37(problem);
        addConstraint_38(problem);
        addConstraint_39(problem);
        addConstraint_41(problem);
        addConstraint_42(problem);
        addConstraint_44(problem);
        addConstraint_45(problem);
        addConstraint_49(problem);
        addConstraint_50(problem);
      

        //Solver solver = factory.get();
        if (useCPLEX) {
        	// add hook for config properties
            ((SolverCPLEX) solver).addHook(new SolverCPLEX.Hook() {
                @Override
                public void call(IloCplex cplex, Map<Object, IloNumVar> varToNum) {
                    try {
                        cplex.setParam(IloCplex.DoubleParam.TiLim, 60); //(TIMESLOT_DURATION / 1000) - 10);  //60
                        cplex.setParam(IloCplex.IntParam.RepeatPresolve, 3);
                        cplex.setParam(IloCplex.LongParam.RepairTries, 20);
                        /* set optimality gap to ensure we get an optimal solution */
                        cplex.setParam(IloCplex.DoubleParam.EpGap, 0);
                        cplex.setParam(IloCplex.DoubleParam.EpAGap, 0);
                        cplex.setParam(IloCplex.Param.Emphasis.MIP, 2);
                        // cplex.setParam(IloCplex.Param.Simplex.Tolerances.Feasibility, 0.000000001);
                        cplex.setParam(IloCplex.Param.Simplex.Tolerances.Optimality, 0.000000001);

                        //defaults:
//                        cplex.getParam(IloCplex.DoubleParam.EpGap) 0.0
//                        cplex.getParam(IloCplex.DoubleParam.EpAGap) 0.0
//                        cplex.getParam(IloCplex.IntParam.NodeLim) 2147483647
//                        cplex.getParam(IloCplex.IntParam.IntSolLim) 2147483647
//                        cplex.getParam(IloCplex.DoubleParam.TreLim) 1.0E75
//                        cplex.getParam(IloCplex.DoubleParam.TiLim) 60.0
//                        cplex.getParam(IloCplex.DoubleParam.ItLim) 2147483647
//                        cplex.getParam(IloCplex.DoubleParam.CutUp) 1.0E75
//                        cplex.getParam(IloCplex.DoubleParam.CutLo) -1.0E75
//                        cplex.getParam(IloCplex.IntParam.PopulateLim) 20
//                        cplex.getParam(IloCplex.DoubleParam.SolnPoolAGap) 1.0E75
//                        cplex.getParam(IloCplex.DoubleParam.SolnPoolGap) 1.0E75
//                        cplex.getParam(IloCplex.IntParam.SolnPoolCapacity) 2100000000
//                        cplex.getParam(IloCplex.IntParam.SolnPoolIntensity) 0

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
            System.out.println(getAllObjectives(solved));
//            getAllSolvedConstraints(solved, problem);
            
            for(int j=0; j<10; j++){
            	getSolvedConstraint(solved, problem, j);
            }
        }


        if (solved == null) {
//            log.error("-----------------------------------------------------------");
//            Collection<Object> variables = problem.getVariables();
//            i = 0;
//            for (Object variable : variables) {
//                log.error(i + " " + variable);
//                i++;
//            }
//
//            log.error("-----------------------------------------------------------");
//            log.error(problem.getConstraints().toString());
//            log.error("-----------------------------------------------------------");
//            log.error(problem.getObjective().toString());
//            log.error("-----------------------------------------------------------");

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

//        builder.append("\n--------- dockerMap ---------");
//        for (Map.Entry<DockerImage, List<DockerContainer>> dockerMapEntry : cacheDockerService.getDockerMap().entrySet()) {
//
//            builder.append("\n").append(dockerMapEntry.getKey()).append(":");
//            for (DockerContainer container : dockerMapEntry.getValue()) {
//                builder.append("\n").append("     ").append(container.toString());
//            }
//        }
//        
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
        
        //term 1
        for (VMType vmType : cacheVirtualMachineService.getVMTypes()) {
            String gamma = placementHelper.getGammaVariable(vmType);
            linear.add(vmType.getCosts(), gamma);
//            System.out.println("******************************** TERM 1 VMType: " + vmType + " gammaVar: "+gamma);
        }

        //DS: for penalty costs //term 2, 3 and term 5
        for (WorkflowElement workflowInstance : getRunningWorkflowInstances()) {
        	//Term 2
        	String executionTimeViolation = placementHelper.getExecutionTimeViolationVariable(workflowInstance);
            linear.add(placementHelper.getPenaltyCostPerQoSViolationForProcessInstance(workflowInstance), executionTimeViolation);
//            System.out.println("******************************** TERM 2 penalty cost: " + placementHelper.getPenaltyCostPerQoSViolationForProcessInstance(workflowInstance) + " execTimeViolation: "+ executionTimeViolation);

            //Term 5
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
                for(VirtualMachine virtualMachine : cacheVirtualMachineService.getAllVMs()){
            		String decisionVariableX = placementHelper.getDecisionVariableX(step, virtualMachine);
            		linear.add(-1 * coefficient, decisionVariableX);
//                    System.out.println("******************************** TERM 6 -1*coeff: "+ (-1 * coefficient) + " varX: "+ decisionVariableX + " Container: " + container.getName() + " step: " + step.getName());
            		
            		//TERM 3:
                    if (!placementHelper.imageForStepEverDeployedOnVM(step, virtualMachine)) {
    					linear.add(CONTAINER_DEPLOY_COST * OMEGA_DEPLOY_D_VALUE, decisionVariableX);
//    		            System.out.println("******************************** TERM 3 deployCostForContainer*Omega: "+ (dockerContainer.getDeployCost() * OMEGA_DEPLOY_D_VALUE) + " VarA "+ decisionVariableA);
    				}
                    
                    long d_v_k = placementHelper.getRemainingLeasingDuration(tau_t, virtualMachine) / 1000;
                    // System.out.println("?!?!?!?! " + (-1.0 * PREFER_LONGER_LEASED_VMS * (double)d_v_k));
                    linear.add(-1.0 * PREFER_LONGER_LEASED_VMS * (double)d_v_k, decisionVariableX);
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
        
        //maximize tau_t_1
//        linear.add(-TAU_T_1_WEIGHT, "tau_t_1");
        
        problem.setObjective(linear, OptType.MIN);
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_2(Problem problem) {
        final List<WorkflowElement> nextWorkflowInstances = getRunningWorkflowInstances();
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

            long rhs = (workflowInstance.getDeadline() / 1000) - START_EPOCH; //- maxRemainingExecutionTime / 1000;
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
        problem.add(linear, ">=", tau_t_startepoch.getTime() / 1000 + EPSILON); //+ TIMESLOT_DURATION / 1000);
        problem.setVarUpperBound("tau_t_1", Integer.MAX_VALUE);
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_4_to_11(Problem problem) {
        for (WorkflowElement workflowInstance : getRunningWorkflowInstances()) {
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
    private void addConstraint_12_16(Problem problem) { //rename constraint
    	List<ProcessStep> steps = getNextAndRunningSteps();
        if (steps.isEmpty()) {//can be ignored if no steps are running
            return;
        }
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	Linear linear = new Linear();
            Linear linear2 = new Linear(); //add me if ram is considered

            for (ProcessStep step : steps) {
            	String decisionVariableX = placementHelper.getDecisionVariableX(step, vm);
            	double requiredCPUPoints = placementHelper.getRequiredCPUPoints(step);
            	linear.add(requiredCPUPoints, decisionVariableX);
            	double requiredRAMPoints = placementHelper.getRequiredRAMPoints(step);
            	linear2.add(requiredRAMPoints, decisionVariableX);
            }
            problem.add(linear, "<=", placementHelper.getSuppliedCPUPoints(vm));
            problem.add(linear2, "<=",placementHelper.getSuppliedRAMPoints(vm));
        }
    }
    
    private void addConstraint_15_19(Problem problem) { //rename constraint
    	List<ProcessStep> steps = getNextAndRunningSteps();
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
            Linear linear = new Linear();
            Linear linear2 = new Linear(); //add me if ram is considered
            for (ProcessStep step : steps) {
            	String decisionVariableX = placementHelper.getDecisionVariableX(step, vm);
                linear.add(-placementHelper.getRequiredCPUPoints((ProcessStep) step), decisionVariableX);
                linear2.add(-placementHelper.getRequiredRAMPoints((ProcessStep) step), decisionVariableX); //add me if ram is considered
            }

            if (!steps.isEmpty()) {
	            double suppliedCPUPoints = placementHelper.getSuppliedCPUPoints(vm);
	            double suppliedRAMPoints = placementHelper.getSuppliedRAMPoints(vm);

	            String helperVariableG = placementHelper.getGVariable(vm);
	            linear.add(suppliedCPUPoints, helperVariableG);
	            linear2.add(suppliedRAMPoints, helperVariableG); //add me if ram is considered

	            String fValueC = placementHelper.getFValueCVariable(vm);
	            linear.add(-1, fValueC);
	            String fValueR = placementHelper.getFValueRVariable(vm); //add me if ram is considered
	            linear2.add(-1, fValueR); //add me if ram is considered
	            
	            problem.add(linear, Operator.LE, 0);
	            problem.add(linear2, Operator.LE, 0); //add me if ram is considered  
	        }
        }
    }

//    //TODO temp
//    private void addConstraint_20(Problem problem) {
//    	
//        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
//        	String g_v_k = placementHelper.getGVariable(vm);
//            Linear linear = new Linear();
//            linear.add(1, g_v_k);
//            problem.add(linear, Operator.GE, placementHelper.getBeta(vm));
//            
//            String y_v_k = placementHelper.getDecisionVariableY(vm);
//            Linear linear2 = new Linear();
//            linear2.add(1, g_v_k);
//            linear2.add(-1, y_v_k);
//            problem.add(linear2, Operator.GE, 0);
//            
//            Linear linear3 = new Linear();
//            linear3.add(1, g_v_k);
//            linear3.add(-1, y_v_k);
//            problem.add(linear3, Operator.GE, 0);
//            
//            Linear linear4 = new Linear();
//            linear4.add(1, g_v_k);
//            linear4.add(-1, y_v_k);
//            problem.add(linear4, Operator.LE, placementHelper.getBeta(vm));
//        }
//    }

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
    
    
    /**
     * @param problem to be solved
     */
    private void addConstraint_18(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	String g_v_k = placementHelper.getGVariable(vm);
            Linear linear = new Linear();
            linear.add(1, g_v_k);
            problem.add(linear, Operator.GE, placementHelper.isLeased(vm));
            
        }
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_19(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
            String g_v_k = placementHelper.getGVariable(vm);
            String y_v_k = placementHelper.getDecisionVariableY(vm);
            Linear linear = new Linear();
            linear.add(1, g_v_k);
            linear.add(-1, y_v_k);
            problem.add(linear, Operator.GE, 0);
            
        }
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_20(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
            String g_v_k = placementHelper.getGVariable(vm);
            String y_v_k = placementHelper.getDecisionVariableY(vm);
            Linear linear = new Linear();
            linear.add(1, g_v_k);
            linear.add(-1, y_v_k);
            problem.add(linear, Operator.LE, placementHelper.isLeased(vm));
            
        }
    }
    
    private void addConstraint_20_to_25(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	String g_v_k = placementHelper.getGVariable(vm);
        	String y_v_k = placementHelper.getDecisionVariableY(vm);
        	int b_v_k = placementHelper.isLeased(vm);

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
    
    
    private void addConstraint_27a(Problem problem){
    	List<ProcessStep> steps = getNextAndRunningSteps();
        
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	Linear linear = new Linear();
        	
            for (Element step : steps) {
            	String decisionVariable = placementHelper.getDecisionVariableX(step, vm);
            	linear.add(1, decisionVariable);
           }
            
           String decisionVariableY = placementHelper.getDecisionVariableY(vm);
           linear.add(-M, decisionVariableY);
           int beta = placementHelper.isLeased(vm);
           problem.add(linear, "<=", beta * M);
        }
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_27(Problem problem) { //+constraint_28
    	List<ProcessStep> steps = getNextAndRunningSteps(); 
        
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
//        	Linear linear = new Linear();
        	
            for (ProcessStep step : steps) {
            	String decisionVariableX = placementHelper.getDecisionVariableX(step, vm);            	
    			String tau_t_1 = "tau_t_1";
    			int tau_t_1_UpperBound = Integer.MAX_VALUE;
    			String x_times_t1 = placementHelper.getXTimesT1(step, vm); 
                String decisionVariableY = placementHelper.getDecisionVariableY(vm);
    			long tau_t_0 = tau_t_startepoch.getTime() / 1000;
    			long d_v_k = placementHelper.getRemainingLeasingDuration(tau_t, vm) / 1000;
            	int b_v_k = placementHelper.isLeased(vm);
            	long BTU = placementHelper.getBTU(vm) / 1000;

                //Constraint 28
                Linear linear = new Linear();
                linear.add(1, x_times_t1);
                linear.add(-1 * tau_t_0, decisionVariableX);
                linear.add(-1 * BTU, decisionVariableY);
                problem.add(linear, "<=", d_v_k * b_v_k);
                
                //Constraint 29
                Linear linear2 = new Linear();
                linear2.add(1, x_times_t1);
                linear2.add(-1 * tau_t_1_UpperBound, decisionVariableX);
                problem.add(linear2, Operator.LE, 0);

                //Constraint 30
                Linear linear3 = new Linear();
                linear3.add(1, x_times_t1);
                linear3.add(-1, tau_t_1);
                problem.add(linear3, Operator.LE, 0);

                //Constraint 31
                Linear linear4 = new Linear();
                linear4.add(1, x_times_t1);
                linear4.add(-1, tau_t_1);
                linear4.add(-1 * tau_t_1_UpperBound, decisionVariableX);
                problem.add(linear4, Operator.GE, -1 * tau_t_1_UpperBound);
                        
                //Constraint 32
                Linear linear5 = new Linear();
                linear5.add(1, x_times_t1);
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
			int b_v_k = placementHelper.isLeased(vm);
			long BTU = placementHelper.getBTU(vm) / 1000;
			long vmStartupTime = vm.getStartupTime() / 1000;

			for (ProcessStep step : steps) {
				String decisionVariableX = placementHelper.getDecisionVariableX(step, vm);
				int variableZ = placementHelper.imageForStepEverDeployedOnVM((ProcessStep) step, vm) ? 1 : 0;
				long remainingExecutionTime = step.getRemainingExecutionTime(tau_t) / 1000;
				long serviceDeployTime = CONTAINER_DEPLOY_TIME / 1000;

				// Constraint 33
				Linear linear = new Linear();
				linear.add(remainingExecutionTime + serviceDeployTime * (1 - variableZ) + vmStartupTime * (1 - b_v_k), decisionVariableX);
				linear.add(-BTU, decisionVariableY);
				problem.add(linear, "<=", d_v_k * b_v_k);
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
          int b_v_k = placementHelper.isLeased(virtualMachine);
          long BTU = placementHelper.getBTU(virtualMachine) / 1000;
          long remainingExecutionTimeAndDeployTimes = getRemainingExecutionTimeAndDeployTimes(step) / 1000;
          
          Linear linear = new Linear();
          linear.add(- BTU, decisionVariableY);
          problem.add(linear, "<=",  d_v_k*b_v_k - remainingExecutionTimeAndDeployTimes);
      }
    }
    

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
    	for (ProcessStep step : getAllNextStepsAsList()) {
    		Linear linear = new Linear();
            for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
                String variableX = placementHelper.getDecisionVariableX(step, vm);
                linear.add(1, variableX);
            }
            problem.add(linear, "<=", 1);
        }
    }
    

	private void addConstraint_41(Problem problem) {
		for (ProcessStep step : getAllRunningSteps()) {
            String vmName = ((ProcessStep) step).getScheduledAtContainer().getVirtualMachine().getName();
            for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
                String variable = placementHelper.getDecisionVariableX(step, vm);
                Linear linear = new Linear();
                linear.add(1, variable);
                boolean runsAt = vmName.equals(vm.getName());
                if (runsAt) {
                	problem.add(linear, Operator.EQ, 1);
                }
                else {
                    problem.add(linear, Operator.EQ, 0);
                }
                problem.setVarUpperBound(variable, 1);
                problem.setVarLowerBound(variable, 0);
                
            }
        }

//        for (ProcessStep step : getAllRunningSteps()) {
//            String containerName = step.getScheduledAtContainer().getName();
//            
//            for(DockerContainer container : cacheDockerService.getDockerContainers(step)){
//                String variableX = placementHelper.getDecisionVariableX(step, container);
//                Linear linear = new Linear();
//                linear.add(1, variableX);
//                
//                boolean runsAt = containerName.equals(container.getName());
//                if (runsAt) {
//                	problem.add(linear, Operator.EQ, 1);
//                	
//                	for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
//                		String variableA = placementHelper.getDecisionVariableA(container, vm);
//
//                		Linear linear2 = new Linear();
//                		linear2.add(1, variableA);
//
//                		boolean deployedAt = container.getVirtualMachine().getName().equals(vm.getName());
//                		if(deployedAt) {
//                			problem.add(linear2, Operator.EQ, 1);
//                		} else {
//                			problem.add(linear2, Operator.EQ, 0);
//                		}
//                		problem.setVarUpperBound(variableA, 1);
//                        problem.setVarLowerBound(variableA, 0);
//                	}
//                }
//                else {
//                    problem.add(linear, Operator.EQ, 0);
//                }
//                problem.setVarUpperBound(variableX, 1);
//                problem.setVarLowerBound(variableX, 0);
//            }
//        }
    }

    private void addConstraint_42(Problem problem) {
    	for (ProcessStep step :  getNextAndRunningSteps()) {
            for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
                String variableX = placementHelper.getDecisionVariableX(step, vm);
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

    private void addConstraint_49(Problem problem) {
    	for (VMType vmType : cacheVirtualMachineService.getVMTypes()) {
            String gamma = placementHelper.getGammaVariable(vmType);
            Linear linear = new Linear();
            linear.add(1, gamma);
            problem.add(linear, ">=", 0);
            problem.setVarType(gamma, VarType.INT);
            problem.setVarUpperBound(gamma, Integer.MAX_VALUE);
            problem.setVarLowerBound(gamma, 0);            
    	}
    }
    private void addConstraint_50(Problem problem) {
    	for (WorkflowElement workflowInstance : getRunningWorkflowInstances()) {
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
    public List<WorkflowElement> getRunningWorkflowInstances() {
        if (nextWorkflowInstances == null) {
            nextWorkflowInstances = Collections.synchronizedList(new ArrayList<WorkflowElement>(cacheWorkflowService.getRunningWorkflowInstances()));
        }
        return nextWorkflowInstances;
    }

	private List<ProcessStep> getAllNextStepsAsList() {
    	List<ProcessStep> allNextStepsAsList = new ArrayList<>();

        if(nextSteps.isEmpty()){
        	for (WorkflowElement workflow : getRunningWorkflowInstances()) {
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
            List<WorkflowElement> nextWorkflowInstances = getRunningWorkflowInstances();
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
            }
            else {
                long remainingExecutionTimeAndDeployTimes = getRemainingExecutionTimeAndDeployTimes((ProcessStep) elem);
                if (nextStepIds.contains(elem.getName())) {
                    for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
                        String decisionVariableX = placementHelper.getDecisionVariableX(elem, vm);
                        linearProcessStep.add(remainingExecutionTimeAndDeployTimes / 1000, decisionVariableX);
                    }
                }
                problem.add(linearProcessStep, "=", remainingExecutionTimeAndDeployTimes / 1000);
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
        }

    }

//    @Override
    public Collection<Object> getVariables() {
        return this.problem.getVariables();
    }
    
	private String getAllObjectives(Result optimize) {
		System.out.println("\n Term 1: vm leasing costs");
		double sum1 = 0;

		for (VMType vmType : cacheVirtualMachineService.getVMTypes()) {
			String gamma = placementHelper.getGammaVariable(vmType);
			double c = optimize.get(gamma).doubleValue();
			sum1 += vmType.getCosts() * c;
		}

		System.out.println("Value: " + sum1);

		double sum2 = 0;
		double sum3 = 0;
		double sum4 = 0;
		double sum6 = 0;
	
		for (WorkflowElement workflowInstance : getRunningWorkflowInstances()) {
        	//Term 2
        	String executionTimeViolation = placementHelper.getExecutionTimeViolationVariable(workflowInstance);
			double cp = optimize.get(executionTimeViolation).doubleValue();
			sum2 += placementHelper.getPenaltyCostPerQoSViolationForProcessInstance(workflowInstance) * cp;

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

            for (ProcessStep step : nextSteps.get(workflowInstance.getName())) {
                for(VirtualMachine virtualMachine : cacheVirtualMachineService.getAllVMs()){
            		String decisionVariableX = placementHelper.getDecisionVariableX(step, virtualMachine);
					int x = toInt(optimize.get(decisionVariableX));
					sum6 += -1 * coefficient * x;
            		
            		//TERM 3:
                    if (!placementHelper.imageForStepEverDeployedOnVM(step, virtualMachine)) {
    					sum3 += CONTAINER_DEPLOY_COST * OMEGA_DEPLOY_D_VALUE * (double)x;    				
    				}
                    
                    //TERM 4:
                    long d_v_k = placementHelper.getRemainingLeasingDuration(tau_t, virtualMachine) / 1000;
                    sum4 += -1.0 * PREFER_LONGER_LEASED_VMS * (double)d_v_k * (double)x;
                }
            }
        }
		
		System.out.println("\n Term 2: penalty costs");
		System.out.println("Value: " + sum2);
		
		System.out.println("\n Term 3: costs for container deployment");
		System.out.println("Value: " + sum3);
		
		System.out.println("\n Term 4: preference for longer leased vms");
		System.out.println("Value: " + sum4);
		
		double sum5 = 0;

		// Term 5
		for (VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
			String fValueC = placementHelper.getFValueCVariable(vm);
			double fc = optimize.get(fValueC).doubleValue();
			String fValueR = placementHelper.getFValueRVariable(vm);
			double fr = optimize.get(fValueR).doubleValue();
			sum5 += OMEGA_F_C_VALUE * fc;
			sum5 += OMEGA_F_R_VALUE * fr;
			
			String decVarY = placementHelper.getDecisionVariableY(vm);
			double y = optimize.get(decVarY).doubleValue();
			long d_v_k = placementHelper.getRemainingLeasingDuration(tau_t, vm) / 1000;
        	System.out.println("Remaining leasing duration: " +d_v_k+ " seconds, for VM " + vm.getName() +", startedAt: "+vm.getStartedAt() + ", scheduled for shutdown: "+vm.getToBeTerminatedAt() + ", additional BTUs to Lease: " + y);
        	
		}

		System.out.println("\n Term 5: free VM Ressources");
		System.out.println("Value: " + sum5);

		System.out.println("\n Term 6: preference for steps closer to Deadline");
		System.out.println("Value: " + sum6);
//
//		
////		System.out.println("_______________ ALL CONSTRAINTS ______________________");
////		
////		System.out.println("Constraint 27:");
////	    List<ProcessStep> steps = getNextAndRunningSteps();
////    	for(DockerContainer container : cacheDockerService.getAllDockerContainers()) {
////    		int sumC27 = 0;
////    		for (ProcessStep step : steps) {
////	           	if(step.getServiceType().getName().equals(container.getDockerImage().getServiceType().getName())) {
////	           		String decisionVariableX = placementHelper.getDecisionVariableX(step, container);
////	           		int x = toInt(optimize.get(decisionVariableX));
////	            	sumC27 += 1 * x;
////	            	if(x != 0) {
////	            		System.out.println("x!=0 for :" + decisionVariableX + " Value="+x);
////	            	}
////	            }
////	         }
////	         for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
////	        	 String decisionVariableA = placementHelper.getDecisionVariableA(container, vm);
////		         String decisionVariableG = placementHelper.getGVariable(vm);
////		         String a_times_g = placementHelper.getATimesG(vm, container); 
////		         int a = toInt(optimize.get(decisionVariableA));
////		         int g = toInt(optimize.get(decisionVariableG));
////		         int a_t_g = toInt(optimize.get(a_times_g));
////		         
////	             sumC27 += (-N * a_t_g);
////	             
////	             
////	             System.out.print("a = " + decisionVariableA + " Value="+a);
////	             System.out.print("  //  g = " + decisionVariableG + " Value="+g);
////	             System.out.print("  //  a_times_g = " + a_times_g + " Value="+a_t_g);
////	             System.out.println("    Alltogether a (" + a + ") * g (" + g + ") = " + a_t_g );
////		            
////	             
////		    }
////
////			System.out.print("For Container " + container + " Sum is " + sumC27 + " <= 0 \n\n");
////	        
////	    }
//    	
//    	//maximize tau_t_1
////    	double sum7 = -TAU_T_1_WEIGHT * optimize.get("tau_t_1").doubleValue();
////    	System.out.println("tau_t_1 term: " + sum7);
//		
		return "\n Sum : "+ (sum1+sum2+sum3+sum4+sum5+sum6+"\n");
	}
	
	private int toInt(Number n) {
		return (int)Math.round(n.doubleValue());
	}
	
	private String getAllSolvedConstraints(Result result, Problem problem) {
		for(Constraint constraint : problem.getConstraints()) {
//			System.out.println("LHS Variables : ");
			double lhsSum = 0;
			for(int i = 0; i< constraint.getLhs().size(); i++) {
				double coefficient = constraint.getLhs().get(i).getCoefficient().doubleValue();
				Object variable = constraint.getLhs().get(i).getVariable();
				
//				System.out.print(coefficient + " * " + variable + " (" + result.get(variable) + ") + ");
				lhsSum+=(coefficient * result.get(variable).doubleValue());
			}
			String operator = constraint.getOperator().toString();
			
//			System.out.println("//// RHS result = " + constraint.getRhs());
//			System.out.println("    ********************************************************** Alltogether: " + lhsSum + operator + constraint.getRhs());
		}
		return "";
	}
	
	private String getSolvedConstraint(Result result, Problem problem, int constraintNumber) {
		Constraint constraint = problem.getConstraints().get(constraintNumber);
//		System.out.println("LHS Variables : ");
		double lhsSum = 0;
		for (int i = 0; i < constraint.getLhs().size(); i++) {
			double coefficient = constraint.getLhs().get(i).getCoefficient()
					.doubleValue();
			Object variable = constraint.getLhs().get(i).getVariable();

//			System.out.print(coefficient + " * " + variable + " ("
//					+ result.get(variable) + ") + ");
			lhsSum += (coefficient * result.get(variable).doubleValue());
		}
		String operator = constraint.getOperator().toString();

//		System.out.println("//// RHS result = " + constraint.getRhs());
//		System.out.println("    ********************************************************** Alltogether: "
//				+ lhsSum + operator + constraint.getRhs());

		return "";
	}
}
