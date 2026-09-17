package com.cptnfizzbin.keycard.lib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.cptnfizzbin.keycard.errors.PolicyArgumentException;

/**
 * Resolves a {@code KeycardConfig}'s plain {@code actions}/{@code
 * subjects} list alongside its keyed {@code actionCatalog}/{@code
 * subjectCatalog} map into the reverse {@code id -> catalog key} map
 * {@code PolicyBuilder}/{@code Policy} use to resolve a dynamic Action/
 * Subject's random name into its real, serializable one, plus the full
 * list of names to fold into {@code meta.actions}/{@code meta.subjects}.
 */
public final class Catalog {
    private Catalog() {}

    /**
     * @param reverseMap raw name (a dynamic Action/Subject's random id) ->
     *   catalog key. Empty when no keyed catalog was given - there are no
     *   keys to resolve from.
     * @param names every resolved name: a plain list entry's own name, or a
     *   keyed catalog entry's key.
     */
    public record Resolution(Map<String, String> reverseMap, List<String> names) {}

    /**
     * @param list the plain, non-keyed vocabulary declaration (e.g.
     *   {@code KeycardConfig.getActions()}) - declares vocabulary only,
     *   each entry's own name used as-is.
     * @param catalog the keyed catalog (e.g. {@code
     *   KeycardConfig.getActionCatalog()}) - each key becomes the
     *   serialized name for its entry.
     * @param nameOf reads an entry's own (possibly dynamic/random) name.
     * @param kind used only to name the vocabulary ("action"/"subject") in
     *   a duplicate-registration error message.
     */
    public static <T> Resolution build(List<T> list, Map<String, T> catalog, Function<T, String> nameOf, String kind) {
        List<String> names = new ArrayList<>();
        if (list != null) {
            for (T entry : list) names.add(nameOf.apply(entry));
        }

        Map<String, String> reverseMap = new LinkedHashMap<>();
        if (catalog != null) {
            for (Map.Entry<String, T> entry : catalog.entrySet()) {
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
