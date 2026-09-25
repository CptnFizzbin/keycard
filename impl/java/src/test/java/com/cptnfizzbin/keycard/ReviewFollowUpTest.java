package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionCatalog;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.conditions.ConditionResolver;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.conditions.OperatorCatalog;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.policy.WildcardToken;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectCatalog;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/**
 * Coverage for the follow-up review changes: field access, record accessor
 * names, immutable rules, catalog/operator registration checks, explicit
 * wildcard disabling, allow/deny symmetry, and logger-routed diagnostics.
 */
public class ReviewFollowUpTest {
    public record Flags(boolean isActive, String owner) {}

    public static class Base {
        private final String orgId = "acme";
    }

    public static class Derived extends Base {}

    /** No backing field for "fullName" - only a getter. */
    public static class Person {
        private final String first = "Ada";
        private final String last = "Lovelace";

        public String getFullName() {
            return first + " " + last;
        }
    }

    // --- field access ---

    @Test
    public void readsAFieldDeclaredOnASuperclass() {
        assertTrue(new ConditionResolver().evaluate(new Derived(), Map.of("orgId", "acme")));
    }

    @Test
    public void readsAGetterOnlyProperty() {
        assertTrue(new ConditionResolver().evaluate(new Person(), Map.of("fullName", "Ada Lovelace")));
    }

    @Test
    public void objectMethodsAreNeverSubjectFields() {
        // getClass() must not make "class" look like a field.
        // A missing field: $eq is false and a bare $ne is true.
        assertFalse(new ConditionResolver().evaluate(new Person(), Map.of("class", Map.of("$eq", Person.class))));
        assertTrue(new ConditionResolver().evaluate(new Person(), Map.of("class", Map.of("$ne", Person.class))));
    }

    @Test
    public void recordComponentStartingWithIsKeepsItsName() {
        assertEquals(Map.of("isActive", Map.of("$eq", true)), Condition.eq(Flags::isActive, true).toMap());

        Subject<Flags, ?> flags = new Subject<>("flags");
        Policy policy = new PolicyBuilder()
            .allow(new Action("read"), flags, Condition.eq(Flags::isActive, true))
            .build();
        assertTrue(policy.can(new Action("read"), flags.wrap(new Flags(true, "a"))));
        assertFalse(policy.can(new Action("read"), flags.wrap(new Flags(false, "a"))));
    }

    // --- immutable rules ---

    @Test
    public void ruleConditionsAreADeepUnmodifiableCopy() {
        Map<String, Object> inner = new java.util.HashMap<>(Map.of("$in", new ArrayList<>(List.of(1, 2))));
        Map<String, Object> conditions = new java.util.HashMap<>(Map.of("ownerId", inner));
        PolicyDefinition.Rule rule = new PolicyDefinition.Rule("allow", "read", "doc", conditions);

        inner.put("$in", List.of(3));
        assertEquals(Map.of("ownerId", Map.of("$in", List.of(1, 2))), rule.conditions());

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) rule.conditions().get("ownerId");
        assertThrows(UnsupportedOperationException.class, () -> nested.put("$eq", 1));
    }

    @Test
    public void rulesHaveValueEquality() {
        assertEquals(
            new PolicyDefinition.Rule("allow", "read", "doc", Map.of("a", 1)),
            new PolicyDefinition.Rule("allow", "read", "doc", Map.of("a", 1))
        );
    }

    // --- registration checks ---

    @Test
    public void operatorNamesMustStartWithDollar() {
        assertThrows(PolicyLoadException.class,
            () -> new OperatorCatalog().add(Operator.of("hasRole", (s, v, ctx) -> true)));
    }

    @Test
    public void catalogsRejectADifferentEntryUnderAnExistingKey() {
        ActionCatalog actions = new ActionCatalog();
        Action create = actions.set("create", new Action());
        actions.add("create", create); // same instance again: no-op
        assertThrows(PolicyArgumentException.class, () -> actions.add("create", new Action()));

        SubjectCatalog subjects = new SubjectCatalog();
        subjects.add(new Subject<>("doc"));
        assertThrows(PolicyArgumentException.class, () -> subjects.add(new Subject<>("doc")));
    }

    @Test
    public void catalogMapViewsAreReadOnly() {
        assertThrows(UnsupportedOperationException.class,
            () -> new ActionCatalog().asMap().put("x", new Action("x")));
        assertThrows(UnsupportedOperationException.class,
            () -> new SubjectCatalog().asMap().clear());
    }

    // --- explicit wildcard disabling ---

    @Test
    public void disableMethodsDeclareADisabledWildcard() {
        PolicyDefinition def = new PolicyBuilder(new KeycardConfig().disableAnyAction().disableAnySubject())
            .allow(new Action("read"), new Subject<>("doc"))
            .buildDef();

        assertEquals(WildcardToken.DISABLED, def.meta().anyAction());
        assertEquals(WildcardToken.DISABLED, def.meta().anySubject());

        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().disableAnyAction().disableAnySubject();
        assertEquals(WildcardToken.DISABLED, meta.anyAction());
        assertEquals(WildcardToken.DISABLED, meta.anySubject());
    }

    @Test
    public void nullNoLongerMeansDisabled() {
        assertThrows(PolicyArgumentException.class, () -> new KeycardConfig().anyAction(null));
        assertThrows(PolicyArgumentException.class, () -> new KeycardConfig().anySubject(null));
        assertThrows(NullPointerException.class, () -> new PolicyDefinition.Meta().anyAction((String) null));
    }

    // --- allow/deny symmetry ---

    @Test
    public void denyAcceptsSeveralActionsWithACondition() {
        Subject<Flags, ?> flags = new Subject<>("flags");
        Action read = new Action("read");
        Action write = new Action("write");

        Policy policy = new PolicyBuilder()
            .allow(List.of(read, write), flags)
            .deny(List.of(read, write), flags, Condition.eq(Flags::owner, "mallory"))
            .build();

        assertTrue(policy.can(write, flags.wrap(new Flags(true, "alice"))));
        assertFalse(policy.can(write, flags.wrap(new Flags(true, "mallory"))));
        assertFalse(policy.can(read, flags.wrap(new Flags(true, "mallory"))));
    }

    // --- diagnostics go to the configured logger ---

    @Test
    public void typeIssuesAreReportedToTheConfiguredLogger() {
        List<String> messages = new ArrayList<>();
        Subject<Flags, ?> flags = new Subject<>("flags");
        Policy policy = new PolicyBuilder(new KeycardConfig().logger(new CapturingLogger(messages)))
            .allow(new Action("read"), flags, Condition.op(Flags::owner, "$gt", 5))
            .build();

        assertFalse(policy.can(new Action("read"), flags.wrap(new Flags(true, "alice"))));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("$gt"));
    }

    private record CapturingLogger(List<String> messages) implements System.Logger {
        @Override
        public String getName() {
            return "capture";
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            messages.add(msg);
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            messages.add(format);
        }

        @Override
        public void log(Level level, Supplier<String> msgSupplier) {
            messages.add(msgSupplier.get());
        }
    }
}
