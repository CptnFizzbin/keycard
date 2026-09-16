package com.cptnfizzbin.keycard.conditions;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class Conditions {
    private Conditions() {}

    @FunctionalInterface
    public interface FieldGetter<T, R> extends Serializable {
        R get(T obj);
    }

    public static <T, R> Map<String, Object> field(FieldGetter<T, R> getter, R value) {
        String fieldName = extractFieldName(getter);
        Map<String, Object> condition = new HashMap<>();
        condition.put(fieldName, value);
        return condition;
    }

    public static <T, R> Map<String, Object> eq(FieldGetter<T, R> getter, R value) {
        String fieldName = extractFieldName(getter);
        Map<String, Object> condition = new HashMap<>();
        condition.put(fieldName, Map.of("$eq", value));
        return condition;
    }

    public static <T, R> Map<String, Object> ne(FieldGetter<T, R> getter, R value) {
        String fieldName = extractFieldName(getter);
        Map<String, Object> condition = new HashMap<>();
        condition.put(fieldName, Map.of("$ne", value));
        return condition;
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

    /** §7.4.5: $has - the field itself is the array; matches when it contains value. */
    public static <T, R> Map<String, Object> has(FieldGetter<T, R> getter, Object value) {
        String fieldName = extractFieldName(getter);
        Map<String, Object> condition = new HashMap<>();
        condition.put(fieldName, Map.of("$has", value));
        return condition;
    }

    /** §7.4.6: $substr - a small, non-regex substring pattern language. */
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
    public static Map<String, Object> field(String fieldName, Object condition) {
        Map<String, Object> result = new HashMap<>();
        result.put("$field", java.util.List.of(fieldName, condition));
        return result;
    }

    /**
     * §7.4.12: a single-key condition for any registered operator, built-in
     * or custom (e.g. {@code Conditions.op("$hasRole", "admin")}) - the
     * escape hatch for operators with no dedicated helper above.
     */
    public static Map<String, Object> op(String operatorName, Object value) {
        Map<String, Object> condition = new HashMap<>();
        condition.put(operatorName, value);
        return condition;
    }

    public static Map<String, Object> and(Map<String, Object>... conditions) {
        Map<String, Object> result = new HashMap<>();
        result.put("$and", java.util.Arrays.asList(conditions));
        return result;
    }

    public static Map<String, Object> or(Map<String, Object>... conditions) {
        Map<String, Object> result = new HashMap<>();
        result.put("$or", java.util.Arrays.asList(conditions));
        return result;
    }

    public static Map<String, Object> not(Map<String, Object> condition) {
        Map<String, Object> result = new HashMap<>();
        result.put("$not", condition);
        return result;
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

