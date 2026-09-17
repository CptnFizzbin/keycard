/**
 * The v1 SemVer this implementation speaks (SPEC_V1-0.md §2) - the
 * single source of truth `Policy`'s internal `SUPPORTED_VERSION`,
 * `PolicyBuilder`'s `BUILDER_VERSION`, and the compliance-fixture test
 * suites' baked-in compliant version all read from, so the three can
 * never drift apart.
 */
export const KEYCARD_POLICY_VERSION = "1.0"
