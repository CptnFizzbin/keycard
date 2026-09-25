package com.cptnfizzbin.keycard.lib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.cptnfizzbin.keycard.errors.PolicyArgumentException;

/**
 * Resolves a {@code KeycardConfig}'s {@code actions}/{@code subjects}
 * catalog into the reverse {@code raw name -> catalog key} map {@code
 * PolicyBuilder}/{@code Policy} use to resolve a dynamic Action/Subject's
 * random name into its real, serializable one, plus the full list of names
 * to fold into {@code meta.actions}/{@code meta.subjects}.
 */
public final class Catalog {
    private Catalog() {}

    /**
     * @param reverseMap raw name (a dynamic Action/Subject's random id, or a
     *   named one's own name) -> catalog key.
     * @param names every catalog key, in registration order.
     */
    public record Resolution(Map<String, String> reverseMap, List<String> names) {}

    /**
     * @param catalog the keyed catalog (e.g. {@code
     *   KeycardConfig.actions().asMap()}) - each key becomes the serialized
     *   name for its entry.
     * @param nameOf reads an entry's own (possibly dynamic/random) name.
     * @param kind used only to name the vocabulary ("action"/"subject") in
     *   a duplicate-registration error message.
     */
    public static <T> Resolution build(Map<String, ? extends T> catalog, Function<T, String> nameOf, String kind) {
        List<String> names = new ArrayList<>();
        Map<String, String> reverseMap = new LinkedHashMap<>();
        if (catalog != null) {
            for (Map.Entry<String, ? extends T> entry : catalog.entrySet()) {
                String key = entry.getKey();
                String rawName = nameOf.apply(entry.getValue());
                String existingKey = reverseMap.get(rawName);
                if (existingKey != null && !existingKey.equals(key)) {
                    throw new PolicyArgumentException(
                        "KeycardConfig " + kind + " catalog error: the same " + kind + " is registered under both \""
                            + existingKey + "\" and \"" + key
                            + "\" - a single Action/Subject can only be registered under one catalog key."
                    );
                }
                reverseMap.put(rawName, key);
                names.add(key);
            }
        }

        return new Resolution(reverseMap, names);
    }

    /** Resolves {@code rawName} (an Action/Subject's own name, dynamic or not) to its catalog key, or returns it unchanged when it isn't a registered catalog entry. */
    public static String resolveName(Map<String, String> reverseMap, String rawName) {
        String resolved = reverseMap.get(rawName);
        return resolved != null ? resolved : rawName;
    }
}
