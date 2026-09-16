package com.cptnfizzbin.keycard.policy;

/**
 * A parsed MAJOR.MINOR.PATCH SemVer string, per SPEC_V1-0.md §2.
 * {@code MINOR}/{@code PATCH} may be omitted - {@code "1"} and {@code
 * "1.0"} are valid shorthand for {@code "1.0.0"} (§2.1) - and default to
 * {@code 0}.
 */
public record SemVer(int major, int minor, int patch) {
    public static SemVer parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("version is required");
        }
        String[] parts = raw.trim().split("\\.", -1);
        if (parts.length == 0 || parts.length > 3) {
            throw new IllegalArgumentException("\"" + raw + "\" is not a valid SemVer version string.");
        }
        int major = parseComponent(raw, parts[0]);
        int minor = parts.length > 1 ? parseComponent(raw, parts[1]) : 0;
        int patch = parts.length > 2 ? parseComponent(raw, parts[2]) : 0;
        return new SemVer(major, minor, patch);
    }

    private static int parseComponent(String raw, String component) {
        if (!component.matches("\\d+")) {
            throw new IllegalArgumentException("\"" + raw + "\" is not a valid SemVer version string.");
        }
        return Integer.parseInt(component);
    }

    /**
     * True when a document declaring `this` version is compatible with an
     * implementation supporting `supported` - SPEC_V1-0.md §2: the same
     * MAJOR, and a MINOR no higher than what's supported (i.e. `supported`
     * MUST be at or above `this`, within the same MAJOR). PATCH never
     * affects compatibility.
     */
    public boolean isCompatibleWith(SemVer supported) {
        return major == supported.major && minor <= supported.minor;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
