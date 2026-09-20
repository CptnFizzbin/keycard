package com.cptnfizzbin.keycard.examples.vision.realbackend;

import com.cptnfizzbin.keycard.conditions.ConditionOperator;
import com.cptnfizzbin.keycard.conditions.OperatorCatalog;

import java.time.Duration;
import java.time.Instant;

public class AppOperators {
    public static final OperatorCatalog catalog = new OperatorCatalog();

    // "$withinDays" compares a subject's Instant field against a literal
    // number of days, resolved at evaluation time. A bare lambda has no
    // name of its own, so it always needs the two-arg set(name, operator).
    public static ConditionOperator WithinDays = catalog.set("$withinDays", (subjectValue, days) ->
        Duration.between((Instant) subjectValue, Instant.now()).toDays() <= ((Number) days).longValue());
}
