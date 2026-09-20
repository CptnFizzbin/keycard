package com.cptnfizzbin.keycard.conditions;

import org.jspecify.annotations.NonNull;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Condition<S> {
    private final Map<String, Object> condition;

    private Condition(String field, String operator, Object value) {
        if (field.startsWith("$")) {
            this.condition = Map.of("$field", List.of(field, Map.of(operator, value)));
        } else {
            this.condition = Map.of(field, Map.of(operator, value));
        }
    }

    private Condition(String operator, Condition<S> condition) {
        this.condition = Map.of(operator, condition.toMap());
    }

    private Condition(String operator, Object value) {
        this.condition = Map.of(operator, value);
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

    private static String extractFieldName(FieldGetter<?, ?> getter) {
        try {
            Method writeReplaceMethod = getter.getClass().getDeclaredMethod("writeReplace");
            writeReplaceMethod.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplaceMethod.invoke(getter);
            return getFieldName(lambda);
        } catch (Exception e) {
            throw new RuntimeException("Could not extract field name from method reference", e);
        }
    }

    private static @NonNull String getFieldName(SerializedLambda lambda) {
        String methodName = lambda.getImplMethodName();

        // Convert getter method name to field name
        // e.g., "getOwnerId" -> "ownerId"
        String fieldName;
        if (methodName.startsWith("get")) {
            fieldName = methodName.substring(3);
            return fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
        }

        if (methodName.startsWith("is")) {
            fieldName = methodName.substring(2);
            return fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
        }

        return methodName;
    }
}

