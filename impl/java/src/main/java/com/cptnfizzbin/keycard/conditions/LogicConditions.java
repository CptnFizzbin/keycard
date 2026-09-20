package com.cptnfizzbin.keycard.conditions;

import java.util.List;

/**
 * Pure combining logic for $or/$and/$not (SPEC_V0.md) -
 * type-checking the operand and the required diagnostic on failure
 * (including the vacuous-empty-array case) is the caller's job
 * (ConditionResolver), so {@link #or}/{@link #and} assume an
 * already-validated {@link List}. An empty list naturally falls out
 * correct here with no special-casing: zero iterations of `or` never
 * finds a match (false), zero iterations of `and` never finds a
 * counterexample (true).
 */
public final class LogicConditions {
    private LogicConditions() {}

    public static boolean or(OperatorContext ctx, Object subject, List<?> conditions) {
        for (Object cond : conditions) {
            if (ctx.resolveSubcondition(subject, cond)) {
                return true;
            }
        }
        return false;
    }

    public static boolean and(OperatorContext ctx, Object subject, List<?> conditions) {
        for (Object cond : conditions) {
            if (!ctx.resolveSubcondition(subject, cond)) {
                return false;
            }
        }
        return true;
    }

    public static boolean not(OperatorContext ctx, Object subject, Object condition) {
        return !ctx.resolveSubcondition(subject, condition);
    }
}
