package com.cptnfizzbin.keycard.version;

import lombok.NonNull;
import org.semver4j.Semver;
import org.semver4j.range.RangeList;
import org.semver4j.range.RangeListFactory;

import java.util.Objects;

/**
 * The v1 SemVer this implementation speaks - the
 * single source of truth {@link com.cptnfizzbin.keycard.policy.Policy}'s
 * {@code SUPPORTED_VERSION}, {@link
 * com.cptnfizzbin.keycard.builder.PolicyBuilder}'s {@code
 * BUILDER_VERSION}, and the compliance-fixture test suites' baked-in
 * compliant version all read from, so the three can never drift apart.
 */
public final class KeyCardVersion {
    private KeyCardVersion() {
    }

    @NonNull
    public static final Semver KEYCARD_POLICY_VERSION = Objects.requireNonNull(Semver.coerce("0.1"));
    @NonNull
    public static final RangeList KEYCARD_POLICY_SUPPORTED_VERSIONS = RangeListFactory.create("<=0.1.*");
}
