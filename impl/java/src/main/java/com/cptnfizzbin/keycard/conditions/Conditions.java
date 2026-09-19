package com.cptnfizzbin.keycard.conditions;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Conditions<S> {
    private final Map<String, Object> condition;

    private Conditions(String field, String operator, Object value) {
        this.condition = Map.of(field, Map.of(operator, value));
    }

    private Conditions(String operator, Conditions<S> conditions) {
        this.condition = Map.of(operator, conditions.toMap());
    }

    private Conditions(String operator, String value) {
        this.condition = Map.of(operator, value);
    }

    private Conditions(String operator, Boolean value) {
        this.condition = Map.of(operator, value);
    }

    private Conditions(String operator, Number value) {
        this.condition = Map.of(operator, value);
    }

    private Conditions(String operator, List<Conditions<S>> conditions) {
        this.condition = Map.of(
            operator,
            conditions.stream().map(Conditions::toMap).toList()
        );
    }

    public Map<String, Object> toMap() {
        return this.condition;
    }

    @FunctionalInterface
    public interface FieldGetter<T, R> extends Serializable {
        R get(T obj);
    }

    public static <T, R> Conditions<T> eq(FieldGetter<T, R> getter, R value) {
        return new Conditions<>(extractFieldName(getter), "$eq", value);
    }

    public static <T, R> Conditions<T> ne(FieldGetter<T, R> getter, R value) {
        return new Conditions<>(extractFieldName(getter), "$ne", value);
    }

    public static <T> Map<String, Object> gt(FieldGetter<T, ? extends Number> getter, Number value) {
        String fieldName = extractFieldName(getter);
        Map<String, Object> condition = new HashMap<>();
        condition.put(fieldName, Map.of("$gt", value));
        return condition;
    }

    public static <T> Map<String, Object> gte(FieldGetter<T, ? extends Number> getter, Number value) {
        String fieldName = extractFieldName(getter);
        Map<String, Object> condition = new HashMap<>();
        condition.put(fieldName, Map.of("$gte", value));
        return condition;
    }

    public static <T> Map<String, Object> lt(FieldGetter<T, ? extends Number> getter, Number value) {
        String fieldName = extractFieldName(getter);
        Map<String, Object> condition = new HashMap<>();
        condition.put(fieldName, Map.of("$lt", value));
        return condition;
    }

    public static <T> Map<String, Object> lte(FieldGetter<T, ? extends Number> getter, Number value) {
        String fieldName = extractFieldName(getter);
        Map<String, Object> condition = new HashMap<>();
        condition.put(fieldName, Map.of("$lte", value));
        return condition;
    }

    public static <T, R> Map<String, Object> in(FieldGetter<T, R> getter, Object collection) {
        String fieldName = extractFieldName(getter);
        Map<String, Object> condition = new HashMap<>();
        condition.put(fieldName, Map.of("$in", collection));
        return condition;
    }

    /**
     * §7.4.5: $has - the field itself is the array; matches when it contains value.
     */
    public static <T, R> Map<String, Object> has(FieldGetter<T, R> getter, Object value) {
        String fieldName = extractFieldName(getter);
        Map<String, Object> condition = new HashMap<>();
        condition.put(fieldName, Map.of("$has", value));
        return condition;
    }

    /**
     * §7.4.6: $substr - a small, non-regex substring pattern language.
     */
    public static <T, R> Map<String, Object> substr(FieldGetter<T, R> getter, String pattern) {
        String fieldName = extractFieldName(getter);
        Map<String, Object> condition = new HashMap<>();
        condition.put(fieldName, Map.of("$substr", pattern));
        return condition;
    }

    /**
     * §7.4.11: $field (explicit field access) - the long form for testing a
     * subject field whose name itself starts with "$", since a bare key
     * starting with "$" is always parsed as an operator (§7.5). Use this
     * instead of {@link #field} only for such dollar-prefixed field names.
     */
    public static <S> Conditions<S> field(String fieldName, Conditions<S> condition) {
        return new Conditions<>(fieldName, condition);
    }

    @SafeVarargs
    public static <S> Conditions<S> and(Conditions<S>... conditions) {
        return new Conditions<>("$and", Arrays.stream(conditions).toList());
    }

    @SafeVarargs
    public static <S> Conditions<S> or(Conditions<S>... conditions) {
        return new Conditions<>("$or", Arrays.stream(conditions).toList());
    }

    public static <S> Conditions<S> not(Conditions<S> condition) {
        return new Conditions<>("$not", condition);
    }

    private static String extractFieldName(FieldGetter<?, ?> getter) {
        try {
            Method writeReplaceMethod = getter.getClass().getDeclaredMethod("writeReplace");
            writeReplaceMethod.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplaceMethod.invoke(getter);
            String methodName = lambda.getImplMethodName();

            // Convert getter method name to field name
            // e.g., "getOwnerId" -> "ownerId"
            String fieldName;
            if (methodName.startsWith("get")) {
                fieldName = methodName.substring(3);
                fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
            } else if (methodName.startsWith("is")) {
                fieldName = methodName.substring(2);
                fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
            } else {
                fieldName = methodName;
            }

            return fieldName;
        } catch (Exception e) {
            throw new RuntimeException("Could not extract field name from method reference", e);
        }
    }
}

