package com.cptnfizzbin.keycard.conditions;

import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import org.jspecify.annotations.NonNull;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Condition<S> {
    private final Map<String, Object> condition;

    private Condition(String field, String operator, Object value) {
        if (field.startsWith("$")) {
            this.condition = Map.of("$field", List.of(field, Collections.singletonMap(operator, value)));
        } else {
            this.condition = Collections.singletonMap(field, Collections.singletonMap(operator, value));
        }
    }

    private Condition(String operator, Condition<S> condition) {
        this.condition = Collections.singletonMap(operator, condition.toMap());
    }

    private Condition(String operator, Object value) {
        this.condition = Collections.singletonMap(operator, value);
    }

    private Condition(String operator, List<Condition<S>> conditions) {
        this.condition = Map.of(
            operator,
            conditions.stream().map(Condition::toMap).toList()
        );
    }

    public Map<String, Object> toMap() {
        return this.condition;
    }

    @FunctionalInterface
    public interface FieldGetter<T, R> extends Serializable {
        R get(T obj);
    }

    public static <T, R> Condition<T> eq(FieldGetter<T, R> getter, R value) {
        return new Condition<>(extractFieldName(getter), "$eq", value);
    }

    public static <T, R> Condition<T> ne(FieldGetter<T, R> getter, R value) {
        return new Condition<>(extractFieldName(getter), "$ne", value);
    }

    public static <T> Condition<T> gt(FieldGetter<T, ? extends Number> getter, Number value) {
        return new Condition<>(extractFieldName(getter), "$gt", value);
    }

    public static <T> Condition<T> gte(FieldGetter<T, ? extends Number> getter, Number value) {
        return new Condition<>(extractFieldName(getter), "$gte", value);
    }

    public static <T> Condition<T> lt(FieldGetter<T, ? extends Number> getter, Number value) {
        return new Condition<>(extractFieldName(getter), "$lt", value);
    }

    public static <T> Condition<T> lte(FieldGetter<T, ? extends Number> getter, Number value) {
        return new Condition<>(extractFieldName(getter), "$lte", value);
    }

    public static <T, R> Condition<T> in(FieldGetter<T, R> getter, Object collection) {
        return new Condition<>(extractFieldName(getter), "$in", collection);
    }

    public static <T, R extends Iterable<I>, I> Condition<T> has(FieldGetter<T, R> getter, Object value) {
        return new Condition<>(extractFieldName(getter), "$has", value);
    }

    /**
     * $substr - a small, non-regex substring pattern language.
     */
    public static <T> Condition<T> substr(FieldGetter<T, String> getter, String pattern) {
        return new Condition<>(extractFieldName(getter), "$substr", pattern);
    }

    @SafeVarargs
    public static <S> Condition<S> and(Condition<S>... conditions) {
        return new Condition<>("$and", Arrays.stream(conditions).toList());
    }

    @SafeVarargs
    public static <S> Condition<S> or(Condition<S>... conditions) {
        return new Condition<>("$or", Arrays.stream(conditions).toList());
    }

    public static <S> Condition<S> not(Condition<S> condition) {
        return new Condition<>("$not", condition);
    }

    public static <S> Condition<S> op(String operator, Object value) {
        return new Condition<>(operator, value);
    }

    /**
     * A custom (or built-in) operator scoped to a field, for an operator
     * with no dedicated helper above - e.g. {@code Condition.op(Claims::createdAt, "$withinDays", 30)}.
     */
    public static <T, R> Condition<T> op(FieldGetter<T, R> getter, String operator, Object value) {
        return new Condition<>(extractFieldName(getter), operator, value);
    }

    /**
     * Identity - returns {@code condition} unchanged. Purely for
     * readability at the top of an {@code allow}/{@code deny} call, so a
     * composite condition tree reads as "allow ... where &lt;condition&gt;".
     */
    public static <S> Condition<S> where(Condition<S> condition) {
        return condition;
    }

    private static String extractFieldName(FieldGetter<?, ?> getter) {
        SerializedLambda lambda;
        try {
            Method writeReplaceMethod = getter.getClass().getDeclaredMethod("writeReplace");
            writeReplaceMethod.setAccessible(true);
            lambda = (SerializedLambda) writeReplaceMethod.invoke(getter);
        } catch (Exception e) {
            throw new PolicyArgumentException("Could not extract field name from method reference", e);
        }
        return getFieldName(lambda);
    }

    private static @NonNull String getFieldName(SerializedLambda lambda) {
        String methodName = lambda.getImplMethodName();

        // A lambda body (e.g. c -> c.ownerId()) compiles to a synthetic
        // "lambda$..." method whose name says nothing about the field read.
        if (methodName.startsWith("lambda$")) {
            throw new PolicyArgumentException(
                "Conditions need a method reference (e.g. Claims::ownerId), not a lambda - a lambda's field name can't be recovered."
            );
        }

        // Convert a JavaBean getter name to its field name, e.g.
        // "getOwnerId" -> "ownerId" - but only when the prefix is followed by
        // an upper-case letter, so a plain accessor like "isbn()" or
        // "getaway()" is left as-is rather than mangled to "bn"/"away".
        String stripped = stripBeanPrefix(methodName, "get");
        if (stripped == null) stripped = stripBeanPrefix(methodName, "is");
        return stripped != null ? stripped : methodName;
    }

    private static String stripBeanPrefix(String methodName, String prefix) {
        if (methodName.length() <= prefix.length() || !methodName.startsWith(prefix)) return null;
        if (!Character.isUpperCase(methodName.charAt(prefix.length()))) return null;
        String rest = methodName.substring(prefix.length());
        return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
    }
}

