package at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl;

import at.ac.tuwien.infosys.viepep.database.entities.*;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Created by Philipp Hoenisch on 4/15/14. edited by Gerta Sheganaku
 */
@Slf4j
//@Component
public class BasicProcessInstancePlacementProblemServiceImpl extends NativeLibraryLoader implements ProcessInstancePlacementProblemService {

    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;

//    @Value("${optimization.use.internVms.first}")
//    private boolean useInternVmsFirst = false;           //IGNORE // has to be true that the internal storage is filled first


    public static final Object SYNC_OBJECT = "Sync_Lock";

    private static final double EXTERNAL_CLOUD_FACTOR = 1; //not considered
    
    private static final double OMEGA_F_R_VALUE = 0.001;
    private static final double OMEGA_F_C_VALUE = 0.001;
//    private static final double TAU_T_1_WEIGHT = 0.00000000001;


    private Date tau_t;
    private static final long EPSILON = ReasoningImpl.MIN_TAU_T_DIFFERENCE_MS / 1000;
    
//    private static final long TIMESLOT_DURATION = 20 * 1000 * 1; //timeslot duration is minimum 1 minute
//    public static final long LEASING_DURATION = 60 * 1000 * 5; //timeslot duration is minimum 5 minutes

//    private int V = 0;
//    private int K = 0;
//    private int internalTypes = 0;
    private long M;

    private static long SERVICE_DEPLOY_TIME = 30000L;
    private static long VM_STARTUP_TIME = 60000L;
    
//    private Map<Integer, Integer> currentVMUsage = new HashMap<>();

    private List<Element> allRunningSteps;
    private List<WorkflowElement> nextWorkflowInstances;
    private Map<String, List<Element>> nextSteps;
    private Map<String, List<Element>> runningSteps;
    private Problem problem;

    public void initializeParameters() {
//        V = cacheVirtualMachineService.getVMTypes().size();
//        for (VMType vmType : cacheVirtualMachineService.getVMTypes()) { //set K, max 1 iteration
//            K = cacheVirtualMachineService.getVMs(vmType).size();
            VM_STARTUP_TIME = cacheVirtualMachineService.getAllVMs().get(0).getStartupTime();
            SERVICE_DEPLOY_TIME = cacheVirtualMachineService.getAllVMs().get(0).getDeployTime();
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
//        M = 100000 / 1000;
        M = 10000000;
        SolverFactory factory;
        if (useCPLEX) {
            factory = new SolverFactoryCPLEX();//use cplex
            log.info("#### ---- Using CPLEX Solver");
        }
        else {
            factory = new SolverFactoryLpSolve();//use lp solve
            log.info("#### ---- Using LP Solver");

        }
//        factory.setParameter(Solver.POSTSOLVE, 2);
        factory.setParameter(Solver.VERBOSE, 0);
        factory.setParameter(Solver.TIMEOUT, 600); // set timeout to 600 seconds

        log.info(printCollections());
        
        problem = new Problem();
        addObjective_1(problem);
        addConstraint_2(problem);
        addConstraint_3(problem); //DS: constraints 4-7 are realized implicitly by the recursive method generateConstraintsForCalculatingExecutionTime
        addConstraint_4(problem);
        addConstraint_12(problem);
        addConstraint_13(problem);
        addConstraint_14_16(problem);
        addConstraint_15_17(problem);
        
//        addConstraint_18(problem);
//        addConstraint_19(problem);
//        addConstraint_20(problem);
        addConstraint_20_to_25(problem);
        
        addConstraint_21(problem);
        addConstraint_22(problem);
        addConstraint_23(problem);
        addConstraint_24(problem);
        addConstraint_25(problem);
        addConstraint_26(problem);
        addConstraint_27(problem);
        addConstraint_28(problem);
        addConstraint_29(problem);
        
//        addConstraint_30(problem);
//        addConstraint_31(problem);

        Solver solver = new ViePEPSolverCPLEX(); // factory.get();
        //Solver solver = factory.get();
        if (useCPLEX) {
            ((SolverCPLEX) solver).addHook(new SolverCPLEX.Hook() {
                @Override
                public void call(IloCplex cplex, Map<Object, IloNumVar> varToNum) {
                    try {
                        cplex.setParam(IloCplex.DoubleParam.TiLim, 60);  //(TIMESLOT_DURATION / 1000) - 10
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
            log.info(solved.toString());

            log.info("------------------------- Variables -------------------------");
            for (Object variable : problem.getVariables()) {
                vars.append(i).append(": ").append(variable).append("=").append(solved.get(variable)).append(", ");
                i++;
            }
            log.info(vars.toString());
            log.info("-----------------------------------------------------------");
            
            System.out.println(getAllObjectives(solved));
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

//  private void addConstraint_20_to_25(Problem problem) {
//  for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
//  	String g_v_k = placementHelper.getGVariable(vm);
//  	String y_v_k = placementHelper.getDecisionVariableY(vm);
//  	String g_times_y = placementHelper.getGYVariable(vm);
//  	int b_v_k = placementHelper.getBeta(vm);
//  	int y_upperBound = Integer.MAX_VALUE;
//
//  	  //Constraint 20
//      Linear linear = new Linear();
//      linear.add(1, g_v_k);
//      linear.add(-1, y_v_k);
//      problem.add(linear, Operator.LE, b_v_k);
//      
//      //Constraint 21
//      Linear linear1 = new Linear();
//      linear1.add(1, y_v_k);
//      linear1.add(-1 * b_v_k, g_v_k);
//      linear1.add(-1, g_times_y);
//      problem.add(linear1, Operator.LE, -1*b_v_k);
//      
//      //Constraint 22
//      Linear linear2 = new Linear();
//      linear2.add(1, g_times_y);
//      linear2.add(-1 * y_upperBound, g_v_k);
//      problem.add(linear2, Operator.LE, 0);
//      
//      //Constraint 23
//      Linear linear3 = new Linear();
//      linear3.add(1, g_times_y);
//      linear3.add(-1, y_v_k);
//      problem.add(linear3, Operator.LE, 0);
//      
//      //Constraint 24
//      Linear linear4 = new Linear();
//      linear4.add(1, g_times_y);
//      linear4.add(-1, y_v_k);
//      linear4.add(-1 * y_upperBound, g_v_k);
//      problem.add(linear4, Operator.GE, -1 * y_upperBound);
//      
//      //Constraint 25
//      Linear linear5 = new Linear();
//      linear5.add(1, g_times_y);
//      problem.add(linear5, Operator.GE, 0);
//  }
//}

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

        builder.append("\n---- allRunningSteps ----");
        for (Element element : allRunningSteps) {
            builder.append("\n").append(element.toString());
        }

        builder.append("\n- nextWorkflowInstances -");
        for (WorkflowElement workflowElement : nextWorkflowInstances) {
            builder.append("\n").append(workflowElement.toString());
        }

        builder.append("\n------- nextSteps --------");
        for (Map.Entry<String, List<Element>> nextStepEntry : nextSteps.entrySet()) {
            builder.append("\n").append(nextStepEntry.getKey()).append(":");
            for (Element element : nextStepEntry.getValue()) {
                builder.append("\n").append("     ").append(element.toString());
            }
        }

        builder.append("\n------ runningSteps -------");
        for (Map.Entry<String, List<Element>> runningStepEntry : runningSteps.entrySet()) {
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
        
        //Term 1:
        for (VMType vmType : cacheVirtualMachineService.getVMTypes()) {
            String gamma = placementHelper.getGammaVariable(vmType);
            linear.add(vmType.getCosts(), gamma);
        }

//        String transfer = "transfercosts";
//        linear.add(1, transfer);


        //DS: for penalty costs
        for (WorkflowElement workflowInstance : getNextWorkflowInstances()) {
        	//Term 2:
            String executionTimeViolation = placementHelper.getExecutionTimeViolationVariable(workflowInstance);
            linear.add(placementHelper.getPenaltyCostPerQoSViolationForProcessInstance(workflowInstance), executionTimeViolation);

            //Term 4:
            long enactmentDeadline = placementHelper.getEnactmentDeadline(workflowInstance);//getDeadline() / 1000;
            double enactmentDeadlineSmall = enactmentDeadline / 1000;
            double tauSmall = tau_t.getTime() / 1000;
            double diffInMinutes = (enactmentDeadlineSmall - tauSmall); //diff in secs!? (((deadlineSmall - 60 - tauSmall)));
            Double coefficient = 1.0 / diffInMinutes;
            if (Double.isInfinite(coefficient) || coefficient <= 0) {
                coefficient = 100.0-diffInMinutes; 
            }
            
            Date enactDeadl = new Date(enactmentDeadline);
            System.out.println("EnactmentDeadline: "+ enactDeadl + ", tau_t :" + tau_t + " of Workflow "+ workflowInstance.getName());
    		System.out.println("******* Coefficient for Term 6 was: " + coefficient + " For diff: " + diffInMinutes + " For WorkflowDeadline: " + workflowInstance.getDeadline()+ " of Workflow "+ workflowInstance.getName());

    		for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
            	for (Element step : nextSteps.get(workflowInstance.getName())) {
            		String decisionVariableX = placementHelper.getDecisionVariableX(step, vm);
            		linear.add(-1 * coefficient, decisionVariableX);
                }
            }

        }
        //Term 3        
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	String fValueC = placementHelper.getFValueCVariable(vm);
            String fValueR = placementHelper.getFValueRVariable(vm); //todo add me again if ram is considered
            linear.add(OMEGA_F_C_VALUE, fValueC);
            linear.add(OMEGA_F_R_VALUE, fValueR);
            problem.setVarUpperBound(fValueC, Double.MAX_VALUE);
            problem.setVarUpperBound(fValueR, Double.MAX_VALUE);
            problem.setVarLowerBound(fValueC, Double.MIN_VALUE);
            problem.setVarLowerBound(fValueR, Double.MIN_VALUE);
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
    private void addConstraint_4(Problem problem) {
        for (WorkflowElement workflowInstance : getNextWorkflowInstances()) {
            List<String> nextStepIds = new ArrayList<>();
            for (Element element : nextSteps.get(workflowInstance.getName())) {
                nextStepIds.add(element.getName());
            }

            String executionTimeWorkflowVariable = placementHelper.getExecutionTimeVariable(workflowInstance);
            Linear linear = new Linear();
            linear.add(1, executionTimeWorkflowVariable);

            Element rootElement = workflowInstance.getElements().get(0);
            //DS: this method realizes constraints (4)-(7)
            generateConstraintsForCalculatingExecutionTime(rootElement, linear, problem, -1, nextStepIds);
            problem.add(linear, "=", 0);

        }
    }


    /**
     * @param problem to be solved
     */
    private void addConstraint_12(Problem problem) {
        List<Element> steps = getNextAndRunningSteps();
        
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	Linear linear = new Linear();
        	
            for (Element step : steps) {
            	String decisionVariable = placementHelper.getDecisionVariableX(step, vm);
            	linear.add(1, decisionVariable);
           }
            
           String decisionVariableY = placementHelper.getDecisionVariableY(vm);
           linear.add(-M, decisionVariableY);
           int beta = placementHelper.getBeta(vm);
           problem.add(linear, "<=", beta * M);
        }
    }

    /**
     * DS: Makes sure that services with different types are not place on the same VM
     *
     * @param problem to be solved
     */
    private void addConstraint_13(Problem problem) {

        List<Element> steps = getNextAndRunningSteps();
        List<Element> steps2 = new ArrayList<>(steps);

        for (Element step1 : steps) {
            for (Element step2 : steps2) {
                if (!step1.getName().equals(step2.getName())) { //save some iterations, only take the different step
                    //DS: we only need this constraint if the service types are different
                    if (!((ProcessStep) step1).getServiceType().name().equals(((ProcessStep) step2).getServiceType().name())) {
                        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
                            String decisionVariable1 = placementHelper.getDecisionVariableX(step1, vm);
                            String decisionVariable2 = placementHelper.getDecisionVariableX(step2, vm);
                            Linear linear = new Linear();
                            linear.add(1, decisionVariable1);
                            linear.add(1, decisionVariable2);
                            problem.add(linear, "<=", 1);
                            
                        }
                    }
                }
            }
            steps2.remove(step1);
        }
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_14_16(Problem problem) {
        List<Element> steps = getNextAndRunningSteps();
        if (steps.isEmpty()) {//can be ignored if no steps are running
            return;
        }
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	Linear linear = new Linear();
            Linear linear2 = new Linear(); //add me if ram is considered

            for (Element step : steps) {
            	String decisionVariableX = placementHelper.getDecisionVariableX(step, vm);
                double requiredCPUPoints = placementHelper.getRequiredCPUPoints((ProcessStep) step);
                linear.add(requiredCPUPoints, decisionVariableX);

                double requiredRAMPoints = placementHelper.getRequiredRAMPoints((ProcessStep) step);
                linear2.add(requiredRAMPoints, decisionVariableX);
            }
            problem.add(linear, "<=", placementHelper.getSuppliedCPUPoints(vm));
            problem.add(linear2, "<=",placementHelper.getSuppliedRAMPoints(vm));
        }
    }

    private void addConstraint_15_17(Problem problem) {
        List<Element> steps = getNextAndRunningSteps();
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
            Linear linear = new Linear();
            Linear linear2 = new Linear(); //add me if ram is considered
            for (Element step : steps) {
            	String decisionVariableX = placementHelper.getDecisionVariableX(step, vm);
                linear.add(-placementHelper.getRequiredCPUPoints((ProcessStep) step), decisionVariableX);
                linear2.add(-placementHelper.getRequiredRAMPoints((ProcessStep) step), decisionVariableX); //add me if ram is considered
            }
            String fValueC = placementHelper.getFValueCVariable(vm);
            String fValueR = placementHelper.getFValueRVariable(vm); //add me if ram is considered

            if (!steps.isEmpty()) {
	            double suppliedCPUPoints = placementHelper.getSuppliedCPUPoints(vm);
	            double suppliedRAMPoints = placementHelper.getSuppliedRAMPoints(vm);
	
	

	
	            linear.add(-1, fValueC);
	            linear2.add(-1, fValueR); //add me if ram is considered
	            
	            String g_v_k = placementHelper.getGVariable(vm);
	            linear.add(1 * suppliedCPUPoints, g_v_k);
	            linear2.add(1 * suppliedRAMPoints, g_v_k); //add me if ram is considered
	
	            problem.add(linear, Operator.LE, 0);
	            problem.add(linear2, Operator.LE, 0); //add me if ram is considered
	            
	        }
        }
    }


    /**
     * @param problem to be solved
     */
    private void addConstraint_18(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	String g_v_k = placementHelper.getGVariable(vm);
            Linear linear = new Linear();
            linear.add(1, g_v_k);
            problem.add(linear, Operator.GE, placementHelper.getBeta(vm));
            
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
            problem.add(linear, Operator.LE, placementHelper.getBeta(vm));
            
        }
    }


    /**
     * @param problem to be solved
     */
    private void addConstraint_21(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	List<Element> steps = getAllNextStepsAsList();
            String decisionVariableY = placementHelper.getDecisionVariableY(vm);
            for (Element step : steps) {
            	String decisionVariableX = placementHelper.getDecisionVariableX(step, vm);
                Linear linear = new Linear();
                long remainingExecutionTime = ((ProcessStep) step).getRemainingExecutionTime(tau_t);
                long serviceDeployTime = SERVICE_DEPLOY_TIME;
                serviceDeployTime = vm.getDeployTime();
                int i = 1 - placementHelper.getBeta(vm);
                remainingExecutionTime = remainingExecutionTime / 1000;
                serviceDeployTime = serviceDeployTime / 1000;
                String type = ((ProcessStep) step).getServiceType().name();
                int z1 = placementHelper.getZ(type, vm);
                linear.add(remainingExecutionTime + serviceDeployTime * (1 - z1) + vm.getStartupTime() / 1000 * i, decisionVariableX);
                linear.add(- (placementHelper.getLeasingDuration(vm) / 1000), decisionVariableY);
                problem.add(linear, "<=", placementHelper.getRemainingLeasingDuration(tau_t, vm) / 1000);//see constraint 13 --> d_v_k,t
            }
        }
    }


    private void addConstraint_22(Problem problem) {
//        for (int v = 0; v < V; v++) {
//            for (int k = 0; k < K; k++) {
        List<Element> steps = getAllRunningSteps();

        for (Element step : steps) {
            VirtualMachine virtualMachine = ((ProcessStep) step).getScheduledAtVM();
            String decisionVariableY = placementHelper.getDecisionVariableY(virtualMachine);
            Linear linear = new Linear();
            long remainingExecutionTimeAndDeployTimes = getRemainingExecutionTimeAndDeployTimes(step);
            linear.add(-(placementHelper.getLeasingDuration(virtualMachine) / 1000), decisionVariableY);
            long remainingLeasingDuration = placementHelper.getRemainingLeasingDuration(tau_t, virtualMachine) / 1000;
            long remainingExecutionandDeployTime = remainingExecutionTimeAndDeployTimes / 1000;
            problem.add(linear, "<=", remainingLeasingDuration - remainingExecutionandDeployTime);//see constraint 14 --> d_v_k,t
        }
//            }
//        }
    }


    private void addConstraint_23(Problem problem) {
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

    private void addConstraint_24(Problem problem) {
        for (Element step : getAllNextStepsAsList()) {
            Linear linear = new Linear();
            for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
                String variable = placementHelper.getDecisionVariableX(step, vm);
                linear.add(1, variable);
            }
            problem.add(linear, "<=", 1);
        //    problem.add(linear, ">=", 0);
        }
    }

    private void addConstraint_25(Problem problem) {
        for (Element step : getAllRunningSteps()) {
            String vmName = ((ProcessStep) step).getScheduledAtVM().getName();
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
    }


    private void addConstraint_26(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
        	for (Element step : getAllNextStepsAsList()) {
                String variable = placementHelper.getDecisionVariableX(step, vm);
                Linear linear = new Linear();
                linear.add(1, variable);
                problem.add(linear, "<=", 1);
                problem.add(linear, ">=", 0);
                problem.setVarType(variable, VarType.INT);
                problem.setVarLowerBound(variable, 0);
                problem.setVarUpperBound(variable, 1);
                //DS: potentially easier to use Boolean
                
            }
        }
    }

    /**
     * @param problem to add the variable
     */
    private void addConstraint_27(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
            String variableG = placementHelper.getGVariable(vm);
            Linear linear = new Linear();
            linear.add(1, variableG);
            problem.add(linear, "<=", 1);
            problem.add(linear, ">=", 0);
            problem.setVarType(variableG, VarType.INT);
            problem.setVarLowerBound(variableG, 0);
            problem.setVarUpperBound(variableG, 1);
            //DS: potentially easier to use Boolean
        }
    }

    /**
     * @param problem to add the variable
     */
    private void addConstraint_28(Problem problem) {
        for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
            String variable = placementHelper.getDecisionVariableY(vm);
            Linear linear = new Linear();
            linear.add(1, variable);
            //DS: y may take the values {0; 1; 2; ...; Integer.MaxValue}
            problem.add(linear, ">=", 0);
            problem.setVarType(variable, VarType.INT);
            problem.setVarUpperBound(variable, Integer.MAX_VALUE);
            problem.setVarLowerBound(variable, 0);
        }
    }


    private void addConstraint_29(Problem problem) {
        for (WorkflowElement workflowInstance : getNextWorkflowInstances()) {
            Linear linear = new Linear();
            String executionTimeViolation = placementHelper.getExecutionTimeViolationVariable(workflowInstance);
            linear.add(1, executionTimeViolation);
            problem.add(linear, ">=", Double.MIN_VALUE);
            problem.add(linear, "<=", Double.MAX_VALUE);
            problem.setVarLowerBound(executionTimeViolation, Double.MIN_VALUE); //- 10000
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
//                if (cacheVirtualMachineService.getBeta(v, k) == 1) {
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

    private List<Element> getAllNextStepsAsList() {
    	List<Element> allNextStepsAsList = new ArrayList<Element>();

        if(nextSteps.isEmpty()){
        	for (WorkflowElement workflow : getNextWorkflowInstances()) {
        		List<Element> nextStepsOfWorkflow = Collections.synchronizedList(new ArrayList<Element>(placementHelper.getNextSteps(workflow.getName())));
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
    public List<Element> getRunningStepsForWorkflow(String workflowInstanceID) {
        if (!runningSteps.containsKey(workflowInstanceID)) {
            List<Element> runningProcessSteps = Collections.synchronizedList(new ArrayList<Element>(placementHelper.getRunningProcessSteps(workflowInstanceID)));
            runningSteps.put(workflowInstanceID, runningProcessSteps);
        }
        return runningSteps.get(workflowInstanceID);
    }
    
    public List<Element> getAllRunningSteps() {
        if (allRunningSteps == null) {
            allRunningSteps = new ArrayList<>();
            List<WorkflowElement> nextWorkflowInstances = getNextWorkflowInstances();
            for (Element workflowInstance : nextWorkflowInstances) {
                List<Element> runningStepsForWorkflowInstanceID = getRunningStepsForWorkflow(workflowInstance.getName());
                allRunningSteps.addAll(runningStepsForWorkflowInstanceID);
            }
        }
        return allRunningSteps;
    }
    

    public List<Element> getNextAndRunningSteps() {
        List<Element> steps = getAllNextStepsAsList();
        List<Element> runningSteps = getAllRunningSteps();
        for (Element step : runningSteps) {
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
    public long getRemainingExecutionTimeAndDeployTimes(Element step) {
        ProcessStep processStep = (ProcessStep) step;
        long remainingExecutionTime = processStep.getRemainingExecutionTime(tau_t);
        if (processStep.isScheduled()) {
//            log.info("getRemainingExecutionTimeAndDeployTimes finishWorkflow");
            remainingExecutionTime += placementHelper.getRemainingSetupTime(processStep.getScheduledAtVM(), tau_t);
        }
        else {
            remainingExecutionTime += SERVICE_DEPLOY_TIME + VM_STARTUP_TIME;
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
                long remainingExecutionTimeAndDeployTimes = getRemainingExecutionTimeAndDeployTimes(elem);
                if (nextStepIds.contains(elem.getName())) {
                    for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
                        String decisionVariableX = placementHelper.getDecisionVariableX(elem, vm);
                        linearProcessStep.add(remainingExecutionTimeAndDeployTimes / 1000, decisionVariableX);
                    }
                }
                problem.add(linearProcessStep, "=", remainingExecutionTimeAndDeployTimes / 1000);
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
		}

		System.out.println("Value: " + sum1);

		System.out.println("\n Term 2 \n");
		double sum2 = 0;
		double sum4 = 0;
		for (WorkflowElement workflowInstance : getNextWorkflowInstances()) {
			// Term2
			String executionTimeViolation = placementHelper
					.getExecutionTimeViolationVariable(workflowInstance);
			double cp = optimize.get(executionTimeViolation).doubleValue();
			sum2 += placementHelper
					.getPenaltyCostPerQoSViolationForProcessInstance(workflowInstance)
					* cp;

			// Term 4
			long enactmentDeadline = placementHelper
					.getEnactmentDeadline(workflowInstance);// getDeadline() /
															// 1000;
			double enactmentDeadlineSmall = enactmentDeadline / 1000;
			double tauSmall = tau_t.getTime() / 1000;
			double diffInMinutes = (enactmentDeadlineSmall - tauSmall); // diff
																		// in
																		// secs!?
																		// (((deadlineSmall
																		// - 60
																		// -
																		// tauSmall)));
			Double coefficient = 1.0 / diffInMinutes;
			if (Double.isInfinite(coefficient) || coefficient <= 0) {
				coefficient = 100.0 - diffInMinutes;
			}

    		for(VirtualMachine vm : cacheVirtualMachineService.getAllVMs()){
				for (Element step : nextSteps.get(workflowInstance.getName())) {
					String decisionVariableX = placementHelper
							.getDecisionVariableX(step, vm);
					int x = optimize.get(decisionVariableX).intValue();
					sum4 += -1 * coefficient * x;
				}
			}
		}

		System.out.println("Value: " + sum2);


		System.out.println("\n Term 3 \n");
		double sum3 = 0;

		// Term 3
		for (VirtualMachine vm : cacheVirtualMachineService.getAllVMs()) {
			String fValueC = placementHelper.getFValueCVariable(vm);
			double fc = optimize.get(fValueC).doubleValue();
			String fValueR = placementHelper.getFValueRVariable(vm); 
			double fr = optimize.get(fValueR).doubleValue();
			sum3 += OMEGA_F_C_VALUE * fc;
			sum3 += OMEGA_F_R_VALUE * fr;
		}

		System.out.println("Value: " + sum3);

		System.out.println("\n Term 4 \n");
		System.out.println("Value: " + sum4);
		
		return "\n Sum : "+ (sum1+sum2+sum3+sum4);
	}
}
