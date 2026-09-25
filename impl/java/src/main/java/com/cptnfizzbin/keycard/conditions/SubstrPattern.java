package com.cptnfizzbin.keycard.conditions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * a small, non-regex substring pattern language, implemented by
 * compiling it to a Java regex - the spec explicitly permits this
 * ("Implementations MAY implement $substr however they like internally
 * (including compiling it to the host language's native regex engine, e.g.
 * translating `*` to `.*` and escaping literal segments)"). {@link #parse}
 * returns {@code null} for a structurally invalid pattern (an unescaped
 * "^" anywhere but the first character, or an unescaped "$" anywhere but
 * the last).
 */
final class SubstrPattern {
    private final Pattern compiled;

    private static final int CACHE_SIZE = 256;

    /**
     * Parsed patterns, keyed by raw pattern text - bounded (LRU), since
     * patterns built from per-request claims could otherwise grow without
     * limit. {@code Optional.empty()} caches a malformed pattern too.
     */
    private static final Map<String, Optional<SubstrPattern>> CACHE = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Optional<SubstrPattern>> eldest) {
                return size() > CACHE_SIZE;
            }
        }
    );

    private SubstrPattern(Pattern compiled) {
        this.compiled = compiled;
    }

    /** {@link #parse}, memoized - returns {@code null} for a malformed pattern. */
    static SubstrPattern cached(String raw) {
        return CACHE.computeIfAbsent(raw, r -> Optional.ofNullable(parse(r))).orElse(null);
    }

    static SubstrPattern parse(String raw) {
        StringBuilder regex = new StringBuilder();
        int n = raw.length();

        for (int i = 0; i < n; i++) {
            char c = raw.charAt(i);

            switch (c) {
                case '\\':
                    // "\\" escapes the very next character, whatever it is,
                    // to a literal - a trailing "\\" with nothing following
                    // it is simply ignored.
                    if (i + 1 >= n) break;
                    regex.append(Pattern.quote(String.valueOf(raw.charAt(i + 1))));
                    i++; // skip the escaped character
                    break;
                case '*':
                    // Zero or more characters; a run of consecutive "*" is
                    // match-equivalent to a single one.
                    regex.append(".*");
                    break;
                case '^':
                    // Only meaningful as the pattern's first character -
                    // anywhere else it's a structurally invalid pattern.
                    if (i != 0) return null;
                    regex.append('^');
                    break;
                case '$':
                    // Only meaningful as the pattern's last character.
                    if (i != n - 1) return null;
                    regex.append('$');
                    break;
                default:
                    regex.append(Pattern.quote(String.valueOf(c)));
            }
        }

        // DOTALL so "." (from ".*") truly means "any character".
        return new SubstrPattern(Pattern.compile(regex.toString(), Pattern.DOTALL));
    }

    boolean matches(String subject) {
        return compiled.matcher(subject).find();
    }
}
