/**
 * The v1 SemVer this implementation speaks - the
 * single source of truth `Policy`'s internal `SUPPORTED_VERSION`,
 * `PolicyBuilder`'s `BUILDER_VERSION`, and the compliance-fixture test
 * suites' baked-in compliant version all read from, so the three can
 * never drift apart.
 */
export const KEYCARD_POLICY_VERSION = "0.1"
export const KEYCARD_POLICY_SUPPORTED_VERSIONS = "<=0.1.*"
