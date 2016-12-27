package at.ac.tuwien.infosys.viepep.reasoning.optimisation.impl;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.BooleanParam;
import ilog.cplex.IloCplex.DoubleParam;
import ilog.cplex.IloCplex.IntParam;
import ilog.cplex.IloCplex.LongParam;
import ilog.cplex.IloCplex.Status;
import ilog.cplex.IloCplex.StringParam;
import net.sf.javailp.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Created by Philipp Hoenisch on 8/20/14. Modified by Gerta Sheganaku
 */
public class ViePEPSolverCPLEX extends SolverCPLEX {

    public static final Map<Object,Object> CPLEX_PARAMS = new HashMap<>();
    // TODO for testing only
	public static Result LAST_RESULT;

    private IloCplex cplex;

    public ViePEPSolverCPLEX() {
    	try {
			cplex = new IloCplex();
		} catch (IloException e) {
			throw new RuntimeException(e);
		}
	}

    /*
     * (non-Javadoc)
     *
     * @see net.sf.javailp.Solver#solve(net.sf.javailp.Problem)
     */
    @Override
    public Result solve(Problem problem) {
        Map<IloNumVar, Object> numToVar = new HashMap<IloNumVar, Object>();
        Map<Object, IloNumVar> varToNum = new HashMap<Object, IloNumVar>();

        try {

            // disable console logging
            cplex.setOut(null);

            initWithParameters(cplex);
            
            // set custom parameters
            for(Object key : CPLEX_PARAMS.keySet()) {
            	if(key instanceof DoubleParam) {
                    cplex.setParam((DoubleParam)key, (double) CPLEX_PARAMS.get(key));
            	} else if(key instanceof IntParam) {
                    cplex.setParam((IntParam)key, (int) CPLEX_PARAMS.get(key));
            	} else if(key instanceof LongParam) {
                    cplex.setParam((LongParam)key, (long) CPLEX_PARAMS.get(key));
            	} else if(key instanceof LongParam) {
                    cplex.setParam((StringParam)key, (String) CPLEX_PARAMS.get(key));
            	} else if(key instanceof LongParam) {
                    cplex.setParam((BooleanParam)key, (boolean) CPLEX_PARAMS.get(key));
            	} else {
            		throw new RuntimeException("Unexpected parameter type: " + key);
            	}
            }

            for (Object variable : problem.getVariables()) {
                VarType varType = problem.getVarType(variable);
                Number lowerBound = problem.getVarLowerBound(variable);
                Number upperBound = problem.getVarUpperBound(variable);

                double lb = (lowerBound != null ? lowerBound.doubleValue() : Double.NEGATIVE_INFINITY);
                double ub = (upperBound != null ? upperBound.doubleValue() : Double.POSITIVE_INFINITY);

                final IloNumVarType type;
                switch (varType) {
                    case BOOL:
                        type = IloNumVarType.Bool;
                        break;
                    case INT:
                        type = IloNumVarType.Int;
                        break;
                    default: // REAL
                        type = IloNumVarType.Float;
                        break;
                }

                IloNumVar num = cplex.numVar(lb, ub, type, (String) variable);

                numToVar.put(num, variable);
                varToNum.put(variable, num);
            }

            for (Constraint constraint : problem.getConstraints()) {
                IloLinearNumExpr lin = cplex.linearNumExpr();
                Linear linear = constraint.getLhs();
                convert(linear, lin, varToNum);

                double rhs = constraint.getRhs().doubleValue();

                switch (constraint.getOperator()) {
                    case LE:
                        cplex.addLe(lin, rhs);
                        break;
                    case GE:
                        cplex.addGe(lin, rhs);
                        break;
                    default: // EQ
                        cplex.addEq(lin, rhs);
                }
            }

            if (problem.getObjective() != null) {
                IloLinearNumExpr lin = cplex.linearNumExpr();
                Linear objective = problem.getObjective();
                convert(objective, lin, varToNum);

                if (problem.getOptType() == OptType.MIN) {
                    cplex.addMinimize(lin);
                } else {
                    cplex.addMaximize(lin);
                }
            }

            for (Hook hook : hooks) {
                hook.call(cplex, varToNum);
            }
            cplex.setParam(IloCplex.BooleanParam.PreInd, false);

            if (!cplex.solve()) {
                cplex.end();
                return null;
            }

            final Result result;
            if (problem.getObjective() != null) {
                Linear objective = problem.getObjective();
                result = new ResultImpl(objective);
            } else {
                result = new ResultImpl();
            }

            for (Entry<Object, IloNumVar> entry : varToNum.entrySet()) {
                Object variable = entry.getKey();
                IloNumVar num = entry.getValue();
                VarType varType = problem.getVarType(variable);

                double value = cplex.getValue(num);
                if (varType.isInt()) {
                    int v = (int) Math.round(value);
                    result.putPrimalValue(variable, v);
                } else {
                    result.putPrimalValue(variable, value);
                }
            }

            if(cplex.getStatus() != Status.Optimal) {
                System.out.println("\n##### ----- CPLEX SOLVER STATUS: "+ cplex.getStatus()+"\n");
            }
            //System.out.println("........ Cplex status: "+ cplex.getCplexStatus());
            //System.out.println("........ Cplex substatus: " + cplex.getCplexSubStatus());
            //System.out.println("........ solver status: "+ cplex.getStatus());

            cplex.end();

            LAST_RESULT = result;

            return result;

        } catch (IloException e) {
            e.printStackTrace();
        }

        return null;
    }

	public IloCplex getCplex() {
		return cplex;
	}


}
