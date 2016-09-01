package at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl;

import at.ac.tuwien.infosys.viepep.database.entities.*;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerImage;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheDockerService;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheWorkflowService;
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

    @Value("${optimization.use.internVms.first}")
    private boolean useInternVmsFirst;           // has to be true that the internal storage is filled first

    public static final Object SYNC_OBJECT = "Sync_Lock";

    private static final double EXTERNAL_CLOUD_FACTOR = 1; //not considered
    
    private static final double OMEGA_F_R_VALUE = 0.001; //0.0001
    private static final double OMEGA_F_C_VALUE = 0.001; //0.0001

    private static final double OMEGA_S_R_VALUE = 0.01;
    private static final double OMEGA_S_C_VALUE = 0.01;
        
    private Date tau_t;
    private static final long TIMESLOT_DURATION = 30 * 1000 * 1; //timeslot duration is minimum 1 minute
    public static final long LEASING_DURATION = 60 * 1000 * 5; //BTU timeslot duration is minimum 5 minutes

    private int V = 0;
    private int K = 0;
    private int ST = 0;
    private int C = 0;
    private int internalTypes = 0; //not considered
    private long M;
    
    private static long CONTAINER_DEPLOY_TIME = 40000L;
    private static long VM_STARTUP_TIME = 40000L;

    private Map<Integer, Integer> currentVMUsage = new HashMap<>();

//    private Map<VMType, List<VirtualMachine>> vmMap;
    private List<Element> allRunningSteps;
    private List<WorkflowElement> nextWorkflowInstances;
    private Map<String, List<Element>> nextSteps;
    private Map<String, List<Element>> runningSteps;
    private Problem problem;

    public void initializeParameters() {
        V = cacheVirtualMachineService.getVMTypes().size();
        for (VMType vmType : cacheVirtualMachineService.getVMTypes()) { //set K, max 1 iteration
            K = cacheVirtualMachineService.getVMs(vmType).size();
            VM_STARTUP_TIME = cacheVirtualMachineService.getVMs(vmType).get(0).getStartupTime();
            break;
        }
        
        ST = cacheDockerService.getDockerImages().size();
        for (DockerImage dockerImage : cacheDockerService.getDockerImages()) { //set C, max 1 iteration
            C = cacheDockerService.getDockerContainers(dockerImage).size();
            CONTAINER_DEPLOY_TIME = cacheDockerService.getDockerContainers(dockerImage).get(0).getDeployTime();
            break;
        }
        //DS: we need to know which types of Services/ProcessSteps exist
        List<String> processStepTypes = new ArrayList<String>();
        for (Element workflowInstance : getNextWorkflowInstances()) {
            addProcessStepTypesFromElement(workflowInstance, processStepTypes);
        }


    }

    public Result optimize(Date tau_t) {

        //cleanups
        synchronized (SYNC_OBJECT) {
            placementHelper.setFinishedWorkflows();

            updateUsageMap();
            allRunningSteps = null;
            nextSteps = new HashMap<>();
            runningSteps = new HashMap<>();

            nextWorkflowInstances = null;
            nextWorkflowInstances = getNextWorkflowInstances();
            getAllRunningSteps();
            getNextAndRunningSteps();
            for (Element nextWorkflowInstance : nextWorkflowInstances) {
                getNextSteps(nextWorkflowInstance);
            }
        }

        this.tau_t = tau_t;
//        M = 100000 / 1000;
        M = 10000000;
        SolverFactory factory;
        if (useCPLEX) {
            factory = new SolverFactoryCPLEX();//use cplex
        }
        else {
            factory = new SolverFactoryLpSolve();//use lp solve
        }
//        factory.setParameter(Solver.POSTSOLVE, 2);
        factory.setParameter(Solver.VERBOSE, 0);
        factory.setParameter(Solver.TIMEOUT, 600); // set timeout to 600 seconds

        problem = new Problem();
        addObjective_1(problem);
        addConstraint_2(problem);
        addConstraint_3(problem); //DS: constraints 4-7 are realized implicitly by the recursive method generateConstraintsForCalculatingExecutionTime
        addConstraint_4(problem);
        addConstraint_12(problem);
        addConstraint_13(problem);
        addConstraint_14_16(problem);
        addConstraint_15_17(problem);
        addConstraint_18(problem);
        addConstraint_19(problem);
        addConstraint_20(problem);
        addConstraint_21(problem);
        addConstraint_22(problem);
        addConstraint_23(problem);
        addConstraint_24(problem);
        addConstraint_25(problem);
        addConstraint_26(problem);
        addConstraint_27(problem);
        addConstraint_28(problem);
        addConstraint_29(problem);
        addConstraint_30(problem);
        addConstraint_31(problem);

        Solver solver = new ViePEPSolverCPLEX(); // factory.get();
        if (useCPLEX) {
            ((SolverCPLEX) solver).addHook(new SolverCPLEX.Hook() {
                @Override
                public void call(IloCplex cplex, Map<Object, IloNumVar> varToNum) {
                    try {
                        cplex.setParam(IloCplex.DoubleParam.TiLim, (TIMESLOT_DURATION / 1000) - 10);  //60
                    } catch (IloException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        Result solve = solver.solve(problem);



        int i = 0;
        StringBuilder vars = new StringBuilder();

        if (solve != null) {
            log.info("------------------------- Solved  -------------------------");
            log.info(solve.toString());

            log.info("------------------------- Variables -------------------------");
            for (Object variable : problem.getVariables()) {
                vars.append(i).append(": ").append(variable).append("=").append(solve.get(variable)).append(", ");
                i++;
            }
            log.info(vars.toString());
            log.info("-----------------------------------------------------------");
        }


        if (solve == null) {
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
        return solve;

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
        
        //term 1
//        for (VMType vmType : cacheVirtualMachineService.getVMTypes()) {
//            String gamma = getGammaVariable(vmType);
//            linear.add(vmType.price, gamma);
//        }
//        
        for (int v = 0; v < V; v++) {
            String gamma = "gamma_" + v;
            linear.add(getCostForVM(v), gamma);
        }

        String transfer = "transfercosts";
        linear.add(1, transfer);


        //DS: for penalty costs
        for (Element workflowInstance : getNextWorkflowInstances()) {
            String workflowInstanceId = workflowInstance.getName();
            String executionTimeViolation = "e_w_" + workflowInstanceId + "^p";
            linear.add(getPenaltyCostPerQoSViolationForProcessInstance(workflowInstanceId), executionTimeViolation);

            List<Element> nextSteps = getNextSteps(workflowInstance);
            List<String> stepIds = new ArrayList<>();
            for (Element nextStep : nextSteps) {
                stepIds.add(nextStep.getName());
            }

            double deadlineSmall = workflowInstance.getDeadline() / 1000;
            double tauSmall = tau_t.getTime() / 1000;
            double diffInMinutes = (((deadlineSmall - 60 - tauSmall)));
            Double coefficient = 1.0 / diffInMinutes;
            if (coefficient <= 0 || Double.isInfinite(coefficient)) {
                coefficient = 100.0;
            }
            for (int v = 0; v < V; v++) {
                for (int k = 0; k < K; k++) {
                    for (String step : stepIds) {
                        String decisionVariableX = "x_" + step + "," + v + "_" + k;

                        linear.add(-1 * coefficient, decisionVariableX);
                    }
                }
            }

        }
        for (int v = 0; v < V; v++) {
            for (int k = 0; k < K; k++) {
                String fValueC = "f_" + v + "_" + k + "^C";
//                String fValueR = "f_" + v + "_" + k + "^R"; //todo add me again if ram is considered
                linear.add(OMEGA_F_C_VALUE, fValueC);
//                linear.add(OMEGA_F_R_VALUE, fValueR);
                problem.setVarUpperBound(fValueC, Double.MAX_VALUE);
//                problem.setVarUpperBound(fValueR, Double.MAX_VALUE);
                problem.setVarLowerBound(fValueC, Double.MIN_VALUE);
//                problem.setVarLowerBound(fValueR, Double.MIN_VALUE);
            }
        }

        problem.setObjective(linear, OptType.MIN);
        
//        
//
//        //term 2
//        for (VMType vmType : vmMap.keySet()) {
//            for (VirtualMachine vm : vmMap.get(vmType)) {
//                for (DockerContainer dockerContainerType : dockerMap.keySet()) {
//                    for (DockerContainer dockerContainer : dockerMap.get(dockerContainerType)) {
//                        if (!vm.isDockerDeployed(dockerContainer)) {
//                            String decisionVariableX = getDecisionVariableX(dockerContainer, vm);
//                            linear.add(dockerContainerType.getDeployCost() * OMEGA_DEPLOY_D_VALUE
//                                    , decisionVariableX);
//                        }
//                    }
//                }
//            }
//        }
//
//        //term 3
//        for (VMType vmType : vmMap.keySet()) {
//            for (VirtualMachine vm : vmMap.get(vmType)) {
//
//                String fValueC = getHelperVariableF(vm);
//                linear.add(OMEGA_F_C_VALUE, fValueC);
//                problem.setVarUpperBound(fValueC, Double.MAX_VALUE);
//                problem.setVarLowerBound(fValueC, Double.MIN_VALUE);
//            }
//        }
//
//        //term 4
//        for (VMType vmType : vmMap.keySet()) {
//            for (VirtualMachine vm : vmMap.get(vmType)) {
//
//                for (DockerContainer dockerContainerType : dockerMap.keySet()) {
//                    for (DockerContainer dockerContainer : dockerMap.get(dockerContainerType)) {
//                        String decisionVariableX = getDecisionVariableX(dockerContainer, vm);
//                        double sICDT = 1;
//                        linear.add(sICDT, decisionVariableX);
//                    }
//                }
//
//            }
//        }
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_2(Problem problem) {
        final List<WorkflowElement> nextWorkflowInstances = getNextWorkflowInstances();
        for (final Element workflowInstance : nextWorkflowInstances) {
            String workflowInstanceId = workflowInstance.getName();
            Linear linear = new Linear();
            String executionTimeWorkflowVariable = "e_w_" + workflowInstanceId;
            String executionTimeViolation = "e_w_" + workflowInstanceId + "^p";
            linear.add(1, "tau_t_1");
            linear.add(1, executionTimeWorkflowVariable);
            linear.add(-1, executionTimeViolation);

            List<Element> runningSteps = getRunningSteps(workflowInstance.getName());
            long maxRemainingExecutionTime = 0;
            for (Element runningStep : runningSteps) {
                maxRemainingExecutionTime = Math.max(maxRemainingExecutionTime, getRemainingExecutionTimeAndDeployTimes(runningStep));
            }

            long rhs = workflowInstance.getDeadline() / 1000 - maxRemainingExecutionTime / 1000;
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
        problem.add(linear, ">=", tau_t.getTime() / 1000 + TIMESLOT_DURATION / 1000);
        problem.setVarUpperBound("tau_t_1", Long.MAX_VALUE);
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_4(Problem problem) {
        for (Element workflowInstance : getNextWorkflowInstances()) {
            String workflowInstanceId = workflowInstance.getName();

            List<Element> nextSteps = getNextSteps(workflowInstance);
            List<String> nextStepIds = new ArrayList<>();
            for (Element element : nextSteps) {
                nextStepIds.add(element.getName());
            }

            String executionTimeWorkflowVariable = "e_w_" + workflowInstanceId;
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
        for (int v = 0; v < V; v++) {
            for (int k = 0; k < K; k++) {
                Linear linear = new Linear();
                for (Element step : steps) {
                    String decisionVariable = "x_" + step.getName() + "," + v + "_" + k;
                    linear.add(1, decisionVariable);
                }
                String decisionVariableY = "y_" + v + "_" + k;
                linear.add(-M, decisionVariableY);
                int beta = getBeta(v, k);
                problem.add(linear, "<=", beta * M);
            }
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
                        for (int v = 0; v < V; v++) {
                            for (int k = 0; k < K; k++) {
                                String decisionVariable1 = "x_" + step1.getName() + "," + v + "_" + k;
                                String decisionVariable2 = "x_" + step2.getName() + "," + v + "_" + k;
                                Linear linear = new Linear();
                                linear.add(1, decisionVariable1);
                                linear.add(1, decisionVariable2);
                                problem.add(linear, "<=", 1);
                            }
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
        for (int v = 0; v < V; v++) {
            for (int k = 0; k < K; k++) {
                Linear linear = new Linear();
//                Linear linear2 = new Linear(); //add me if ram is considered

                for (Element step : steps) {
                    String decisionVariableX = "x_" + step.getName() + "," + v + "_" + k;
                    double requiredCPUPoints = getRequiredCPUPoints(((ProcessStep) step).getServiceType(), v, k);
                    linear.add(requiredCPUPoints, decisionVariableX);

//                    double requiredRAMPoints = getRequiredRAMPoints(((ProcessStep) step).getType(), v, k);
//                    linear2.add(requiredRAMPoints, decisionVariableX);
                }
                problem.add(linear, "<=", getSuppliedCPUPoints(v, k));
//                problem.add(linear2, "<=", getSuppliedRAMPoints(v, k));
            }
        }
    }

    private void addConstraint_15_17(Problem problem) {
        List<Element> steps = getNextAndRunningSteps();
        for (int v = 0; v < V; v++) {
            for (int k = 0; k < K; k++) {
                Linear linear = new Linear();
//                Linear linear2 = new Linear(); //add me if ram is considered
                for (Element step : steps) {
                    String decisionVariableX = "x_" + step.getName() + "," + v + "_" + k;
                    linear.add(-getRequiredCPUPoints(((ProcessStep) step).getServiceType(), v, k), decisionVariableX);
//                    linear2.add(-getRequiredRAMPoints(((ProcessStep) step).getType(), v, k), decisionVariableX); //add me if ram is considered
                }
                String fValueC = "f_" + v + "_" + k + "^C";
//                String fValueR = "f_" + v + "_" + k + "^R"; //add me if ram is considered

//                if (!steps.isEmpty()) {
                double suppliedCPUPoints = getSuppliedCPUPoints(v, k);
//                double suppliedRAMPoints = getSuppliedRAMPoints(v, k);


//

                linear.add(-1, fValueC);
//                linear2.add(-1, fValueR); //add me if ram is considered

                linear.add(1 * suppliedCPUPoints, "g_" + v + "_" + k);
//                linear2.add(1 * suppliedRAMPoints, "g_" + v + "_" + k); //add me if ram is considered

                problem.add(linear, Operator.LE, 0);
//                problem.add(linear2, Operator.LE, 0); //add me if ram is considered
            }
        }
    }


    /**
     * @param problem to be solved
     */
    private void addConstraint_18(Problem problem) {
        for (int v = 0; v < V; v++) {
            for (int k = 0; k < K; k++) {
                String g_v_k = "g_" + v + "_" + k;
                Linear linear = new Linear();
                linear.add(1, g_v_k);
                problem.add(linear, Operator.GE, getBeta(v, k));
            }
        }
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_19(Problem problem) {
        for (int v = 0; v < V; v++) {
            for (int k = 0; k < K; k++) {
                String g_v_k = "g_" + v + "_" + k;
                String y_v_k = "y_" + v + "_" + k;
                Linear linear = new Linear();
                linear.add(1, g_v_k);
                linear.add(-1, y_v_k);
                problem.add(linear, Operator.GE, 0);
            }
        }
    }

    /**
     * @param problem to be solved
     */
    private void addConstraint_20(Problem problem) {
        for (int v = 0; v < V; v++) {
            for (int k = 0; k < K; k++) {
                String g_v_k = "g_" + v + "_" + k;
                String y_v_k = "y_" + v + "_" + k;
                Linear linear = new Linear();
                linear.add(1, g_v_k);
                linear.add(-1, y_v_k);
                problem.add(linear, Operator.LE, getBeta(v, k));
            }
        }
    }


    /**
     * @param problem to be solved
     */
    private void addConstraint_21(Problem problem) {
        for (int v = 0; v < V; v++) {
            for (int k = 0; k < K; k++) {
                List<Element> steps = getNextSteps();
                String decisionVariableY = "y_" + v + "_" + k;
                for (Element step : steps) {
                    String decisionVariableX = "x_" + step.getName() + "," + v + "_" + k;
                    Linear linear = new Linear();
                    long remainingExecutionTime = ((ProcessStep) step).getRemainingExecutionTime(tau_t);
                    VirtualMachine vmById = getVMById(v + "_" + k);
                    long serviceDeployTime = CONTAINER_DEPLOY_TIME;
                    if (vmById != null) {
                        serviceDeployTime = vmById.getDeployTime();
                    }
                    int i = 1 - getBeta(v, k);
                    remainingExecutionTime = remainingExecutionTime / 1000;
                    serviceDeployTime = serviceDeployTime / 1000;
                    String type = ((ProcessStep) step).getServiceType().name();
                    int z1 = getZ(type, v, k);
                    linear.add(remainingExecutionTime + serviceDeployTime * (1 - z1) + vmById.getStartupTime() / 1000 * i,
                            decisionVariableX);
                    linear.add(-LEASING_DURATION / 1000, decisionVariableY);
                    problem.add(linear, "<=", getRemainingLeasingDuration(v, k) / 1000);//see constraint 13 --> d_v_k,t
                }
            }
        }
    }


    private void addConstraint_22(Problem problem) {
//        for (int v = 0; v < V; v++) {
//            for (int k = 0; k < K; k++) {
        List<Element> steps = getAllRunningSteps();

        for (Element step : steps) {
            VirtualMachine virtualMachine = ((ProcessStep) step).getScheduledAtVM();
            String vmName = virtualMachine.getName();
            String decisionVariableY = "y_" + vmName;
            Linear linear = new Linear();
            long remainingExecutionTimeAndDeployTimes = getRemainingExecutionTimeAndDeployTimes(step);
            linear.add(-LEASING_DURATION / 1000, decisionVariableY);
            long remainingLeasingDuration = getRemainingLeasingDuration(virtualMachine) / 1000;
            long remainingExecutionandDeployTime = remainingExecutionTimeAndDeployTimes / 1000;
            problem.add(linear, "<=", remainingLeasingDuration - remainingExecutionandDeployTime);//see constraint 14 --> d_v_k,t
        }
//            }
//        }
    }


    private void addConstraint_23(Problem problem) {
        for (int v = 0; v < V; v++) {
            Linear linear = new Linear();
            String gamma = "gamma_" + v;
            for (int k = 0; k < K; k++) {
                String variableY = "y_" + v + "_" + k;
                linear.add(1, variableY);
            }
            linear.add(-1, gamma);
            problem.add(linear, "<=", 0);
        }

    }

    private void addConstraint_24(Problem problem) {
        List<Element> steps = getNextSteps();//todo check if duplicated steps

        for (Element stepId : steps) {
            Linear linear = new Linear();
            for (int v = 0; v < V; v++) {
                for (int k = 0; k < K; k++) {
                    String variable = "x_" + stepId.getName() + "," + v + "_" + k;
                    linear.add(1, variable);
                }
            }
            problem.add(linear, "<=", 1);
            problem.add(linear, ">=", 0);
        }
    }

    private void addConstraint_25(Problem problem) {

        List<Element> steps = getAllRunningSteps(); //todo check if duplicated steps
        for (Element step : steps) {
            String stepId = step.getName();
            String vmName = ((ProcessStep) step).getScheduledAtVM().getName();
            for (int v = 0; v < V; v++) {
                for (int k = 0; k < K; k++) {
                    String variable = "x_" + stepId + "," + v + "_" + k;
                    Linear linear = new Linear();
                    linear.add(1, variable);
                    boolean runsAt = vmName.equals(v + "_" + k);
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
    }


    private void addConstraint_26(Problem problem) {
        List<Element> steps = getNextSteps();
        for (int v = 0; v < V; v++) {
            for (int k = 0; k < K; k++) {
                for (Element step : steps) {
                    String variable = "x_" + step.getName() + "," + v + "_" + k;
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
    }

    /**
     * @param problem to add the variable
     */
    private void addConstraint_27(Problem problem) {
        for (int v = 0; v < V; v++) {
            for (int k = 0; k < K; k++) {
                String variableG = "g_" + v + "_" + k;

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
    }

    /**
     * @param problem to add the variable
     */
    private void addConstraint_28(Problem problem) {
        for (int v = 0; v < V; v++) {
            for (int k = 0; k < K; k++) {
                String variable = "y_" + v + "_" + k;
                Linear linear = new Linear();
                linear.add(1, variable);
                //DS: y may take the values {0; 1; 2; ...; Integer.MaxValue}
                problem.add(linear, ">=", 0);
                problem.setVarType(variable, VarType.INT);
                problem.setVarUpperBound(variable, Integer.MAX_VALUE);
                problem.setVarLowerBound(variable, 0);
            }
        }
    }


    private void addConstraint_29(Problem problem) {
        for (Element workflowInstance : getNextWorkflowInstances()) {
            String workflowInstanceId = workflowInstance.getName();
            Linear linear = new Linear();
            String executionTimeViolation = "e_w_" + workflowInstanceId + "^p";
            linear.add(1, executionTimeViolation);
            problem.add(linear, ">=", Double.MIN_VALUE);
            problem.add(linear, "<=", Double.MAX_VALUE);
            problem.setVarLowerBound(executionTimeViolation, -10000);
            problem.setVarUpperBound(executionTimeViolation, Double.MAX_VALUE);
            problem.setVarType(executionTimeViolation, VarType.REAL);
        }
    }

    //Goal of this constraint is to eliminate the possibility that sensitive services are deployed on a vm with the type 5, 6 or 7
    private void addConstraint_30(Problem problem) {
        List<Element> steps = getNextSteps();//todo check if duplicated steps

        for (Element step : steps) {
            for (int v = 0; v < V; v++) {
                for (int k = 0; k < K; k++) {


                    //vm types 4,5 and 6 are aws types

                    //TODO currently only services 3 6 and 9 are forbidden to be executed on the public cloud


//                    if (v >= internalTypes) {// steps can also be restricted for interna VMs
                    ProcessStep processStep = (ProcessStep) step;
                    List<Integer> restrictedVMs = processStep.getRestrictedVMs();
                    if (restrictedVMs != null && restrictedVMs.contains(v)) {
                        String variable = "x_" + step.getName() + "," + v + "_" + k;
                        Linear linear = new Linear();
                        linear.add(1, variable);
                        problem.add(linear, "=", 0);
                    }
//                        if (("task3".equals(processStep.getType().getName())) ||
//                                ("task6".equals(processStep.getType().getName())) ||
//                                ("task9".equals(processStep.getType().getName()))) {
//                            String variable = "x_" + step.getName() + "," + v + "_" + k;
//                            Linear linear = new Linear();
//                            linear.add(1, variable);
//                            problem.add(linear, "=", 0);
//                        }
//                    }

                }
            }
        }
    }

    // the goal of this contraint is to implement the colocation of tasks (implicit) and minimal data transfer costs
    private void addConstraint_31(Problem problem) {
        List<Element> steps = getNextSteps();//todo check if duplicated steps

        Linear linearTransfer = new Linear();
        linearTransfer.add(1, "transfercosts");
        problem.add(linearTransfer, ">=", 0);
        problem.setVarType(linearTransfer, VarType.INT);


        Linear linear = new Linear();

        for (Element stepId : steps) {
            String location = "";

            if (stepId.getLastExecutedElement() == null) {
                location = "internal";
            }
            else {
                if (stepId.getLastExecutedElement().getScheduledAtVM() != null) {
                    location = stepId.getLastExecutedElement().getScheduledAtVM().getLocation();
                }
                else {
                    location = "internal";
                }
            }

            for (int v = 0; v < V; v++) {
                for (int k = 0; k < K; k++) {
                    String variable = "x_" + stepId.getName() + "," + v + "_" + k;

                    if ("internal".equals(location)) {
                        if (v < internalTypes) {
                            linear.add(0, variable);
                        }
                        else {
                            linear.add(stepId.getLastExecutedElement().getServiceType().getDataToTransfer(), variable);
                        }

                    }
                    else {
                        if (v >= internalTypes) {
                            linear.add(0, variable);
                        }
                        else {
                            linear.add(stepId.getLastExecutedElement().getServiceType().getDataToTransfer(), variable);
                        }
                    }
                }
            }
        }

        String transfer = "transfercosts";
        linear.add(-1, transfer);
        problem.add(linear, ">=", 0);

    }


    //##################################################################################################################
    //################################################# Helper Methods #################################################
    //##################################################################################################################

    /**
     * gamma is the amount of all leased VMs
     *
     * @param vmType defines specific VMType
     * @return gamma variable for a particular VM Type
     */
//    private String getGammaVariable(VMType vmType) {
//        return "gamma_" + vmType.getReadableString();
//    }
    
    private void updateUsageMap() {

        for (int v = 0; v < V; v++) {
            Integer usedVMs = 0;
            for (int k = 0; k < K; k++) {
                if (getBeta(v, k) == 1) {
                    usedVMs++;
                }
            }
            currentVMUsage.put(v, usedVMs);
        }
    }

    //@Override
    public VirtualMachine getVMById(String vmID) {
        Set<VMType> vmTypes = cacheVirtualMachineService.getVMTypes();
        for (VMType vmType : vmTypes) {
            for (VirtualMachine virtualMachine : cacheVirtualMachineService.getVMs(vmType)) {
                if (virtualMachine.getName().equals(vmID)) {
                    return virtualMachine;
                }
            }
        }
        return null;
    }

    /**
     * @return a list of workflow instances
     */
    public List<WorkflowElement> getNextWorkflowInstances() {
        if (nextWorkflowInstances == null) {
            nextWorkflowInstances = Collections.synchronizedList(new ArrayList<WorkflowElement>(cacheWorkflowService.getRunningWorkflowInstances()));
        }
        return nextWorkflowInstances;
    }

    private List<Element> getNextSteps() {
        List<Element> list = new ArrayList<>();
        List<WorkflowElement> nextWorkflowInstances1 = getNextWorkflowInstances();
        for (Element workflow : nextWorkflowInstances1) {
            list.addAll(getNextSteps(workflow));
        }
        return list;
    }

    private List<Element> getNextSteps(Element workflow) {
        List<Element> list = new ArrayList<>();
        if (!nextSteps.containsKey(workflow.getName())) {
            List<Element> nextSteps1 = Collections.synchronizedList(new ArrayList<Element>(placementHelper.getNextSteps(workflow.getName())));
            nextSteps.put(workflow.getName(), nextSteps1);
        }
        list.addAll(nextSteps.get(workflow.getName()));
        return list;
    }

    /**
     * @param workflowInstanceID of the running steps
     * @return a list of currently running steps
     */
    public List<Element> getRunningSteps(String workflowInstanceID) {
        if (!runningSteps.containsKey(workflowInstanceID)) {
            List<Element> runningProcessSteps = Collections.synchronizedList(new ArrayList<Element>(placementHelper.getRunningProcessSteps(workflowInstanceID)));
            runningSteps.put(workflowInstanceID, runningProcessSteps);
        }
        return runningSteps.get(workflowInstanceID);
    }

    /**
     * @param v needed to identify a vm type
     * @return the costs for that VM
     */
    public double getCostForVM(int v) {
        if (!useInternVmsFirst) {
            return getVMType(v + 1).getCosts();
        }
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

        if (v < internalTypes) {
            //is a private VM
            return getVMType(v + 1).getCosts();
        }
        else {
            if (currentVMUsage.get(v - internalTypes) < K) {
                //there are the same instances available on the private cloud
                return getVMType(v + 1).getCosts() * EXTERNAL_CLOUD_FACTOR;
            }
            else {

                for (int i = 0; i < V - v; i++) {
                    if (currentVMUsage.get(i) < K) {
                        return getVMType(v + 1).getCosts() * EXTERNAL_CLOUD_FACTOR;
                    }
                }

                //check if there are larger VMs on the private cloud available
                Integer amountOfLargerVMTypes = v - internalTypes - 1;
                for (int i = v + 1; i <= amountOfLargerVMTypes; i++) {
                    if (currentVMUsage.get(i - internalTypes) < K) {
                        return getVMType(v + 1).getCosts() * EXTERNAL_CLOUD_FACTOR;
                    }
                }
                return getVMType(v + 1).getCosts();
            }
        }
    }


    /**
     * @param step to find out rest execution time
     * @return the remaining execution time for step
     */
    public long getRemainingExecutionTimeAndDeployTimes(Element step) {
        ProcessStep processStep = (ProcessStep) step;
        long remainingExecutionTime = processStep.getRemainingExecutionTime(tau_t);
        if (processStep.isScheduled()) {
//            log.info("getRemainingExecutionTimeAndDeployTimes finishWorkflow");
            remainingExecutionTime += placementHelper.getRemainingSetupTime(processStep.getScheduledAtVM().getName(), tau_t);
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
     * @param serviceType a service type id
     * @param v           needed to identify a vm type
     * @param k           needed to identify a vm type
     * @return the amount of CPU needed for that service type on a certain vm
     */
    public double getRequiredCPUPoints(ServiceType serviceType, int v, int k) {
        return serviceType.getCpuLoad();
    }

    /**
     * @param serviceType a service type
     * @param v           needed to identify a vm type
     * @param k           needed to identify a vm type
     * @return the amount of RAM needed for that service type on a certain vm
     */
    public double getRequiredRAMPoints(ServiceType serviceType, int v, int k) {
        return 180.0;
    }

    /**
     * @param v needed to identify a vm type
     * @param k needed to identify a vm type
     * @return the available cpu points on a certain vm
     */
    public double getSuppliedCPUPoints(int v, int k) {
        return getVMType(v + 1).getCpuPoints();
    }

    /**
     * @param v needed to identify a vm type
     * @param k needed to identify a vm type
     * @return the available ram points on a certain vm
     */
    public double getSuppliedRAMPoints(int v, int k) {
        return getVMType(v + 1).getRamPoints();
    }

    /**
     * @param v needed to identify a vm type
     * @param k needed to identify a vm type
     * @return the remaining leasing duration for a particular vm
     */
    public long getRemainingLeasingDuration(int v, int k) {
        VirtualMachine vmById = getVMById(v + "_" + k);
        return getRemainingLeasingDuration(vmById);
    }


    private long getRemainingLeasingDuration(VirtualMachine virtualMachine) {
        Date startedAt = virtualMachine.getStartedAt();
        if (startedAt == null) {
            return 0;
        }
        Date toBeTerminatedAt = virtualMachine.getToBeTerminatedAt();
        if (toBeTerminatedAt == null) {
            toBeTerminatedAt = new Date(startedAt.getTime() + LEASING_DURATION);
        }
        long remainingLeasingDuration = toBeTerminatedAt.getTime() - tau_t.getTime();
        if (remainingLeasingDuration < 0) {
            remainingLeasingDuration = 0;
        }
        return remainingLeasingDuration;
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
                    for (int v = 0; v < V; v++) {
                        for (int k = 0; k < K; k++) {
                            String decisionVariableX = "x_" + elem.getName() + "," + v + "_" + k;
                            linearProcessStep.add(remainingExecutionTimeAndDeployTimes / 1000, decisionVariableX);
                        }
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

    private VMType getVMType(int identifier) {
        try {
            return VMType.fromIdentifier(identifier);
        } catch (Exception e) {
            return null;
        }
    }

    public double getPenaltyCostPerQoSViolationForProcessInstance(String workflowInstanceId) {
        return 200.0; //TODO: return sensible costs according to a certain workflowInstanceId
    }

    public int getBeta(int v, int k) {
        VirtualMachine vmById = getVMById(v + "_" + k);
        return vmById.isLeased() ? 1 : 0;
    }

    /**
     * indicates if a specific service (serviceType) runs on a virtual machine (v, k)
     *
     * @param serviceType - service serviceType
     * @param v           - vm
     * @param k           - vm
     * @return 0 if false otherwise true
     */
    public int getZ(String serviceType, int v, int k) {
      /*  int[][] zArray = z.get(serviceType);
        int i = zArray[v][k];*/
//        vmMap.keySet();
        for (VMType vmType : cacheVirtualMachineService.getVMTypes()) {
            for (VirtualMachine virtualMachine : cacheVirtualMachineService.getVMs(vmType)) {
                if (virtualMachine.isLeased() && virtualMachine.getServiceType() != null && virtualMachine.getServiceType().name().equals(serviceType)) {
                    return 1;
                }
            }
        }
        return 0;
    }

//    @Override
    public Collection<Object> getVariables() {
        return this.problem.getVariables();
    }

    public List<Element> getAllRunningSteps() {
        if (allRunningSteps == null) {
            allRunningSteps = new ArrayList<>();
            List<WorkflowElement> nextWorkflowInstances = getNextWorkflowInstances();
            for (Element workflowInstance : nextWorkflowInstances) {
                List<Element> runningStepsForWorkflowInstanceID = getRunningSteps(workflowInstance.getName());
                allRunningSteps.addAll(runningStepsForWorkflowInstanceID);
            }
        }
        return allRunningSteps;
    }

    public List<Element> getNextAndRunningSteps() {
        List<Element> steps = getNextSteps();
        List<Element> runningSteps = getAllRunningSteps();
        for (Element step : runningSteps) {
            if (!steps.contains(step)) {
                steps.add(step);
            }
        }
        return steps;
    }

    public void addProcessStepTypesFromElement(Element elem, List<String> processStepTypes) {
        if (elem instanceof ProcessStep) {
            String typeOfP = ((ProcessStep) elem).getServiceType().name();
            if (!processStepTypes.contains(typeOfP)) {
                processStepTypes.add(typeOfP);
            }
        }
        else {
            for (Element subElement : elem.getElements())
                addProcessStepTypesFromElement(subElement, processStepTypes);
        }
    }
}
