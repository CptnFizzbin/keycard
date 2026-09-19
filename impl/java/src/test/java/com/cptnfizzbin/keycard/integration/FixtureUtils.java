package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.policy.WildcardToken;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import org.semver4j.Semver;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Shared helpers for every compliance-fixture-driven integration suite -
 * {@link PolicyFixtures} (the fixtures under test/fixtures/policies) and
 * {@link Fixtures} (the spec-native fixtures under test/fixtures/v1)
 * today, and any future fixture set. Not a test class itself.
 * <p>
 * Factors out the parts that don't depend on a fixture format's on-disk
 * shape: discovering `*.yaml` files, parsing the v1 `rules`/`meta` shape
 * (SPEC_V0.md §3) shared by every fixture format, the `{ action,
 * subject, subjectData?, expected }` shape every format's individual
 * cases boil down to once parsed, resolving one such case against a
 * {@link Policy}, and filtering fixtures by the SemVer `version` they
 * declare - so each format-specific loader only has to own parsing its
 * own document's outer shape into that common {@link TestCase}, not the
 * discovery/resolution/filtering mechanics around it.
 * <p>
 * KeyCard itself never reads or writes policy.yaml text; parsing it into a
 * plain PolicyDefinition (via SnakeYaml, a test-only dependency) is this
 * test suite's job, mirroring what an application would do.
 */
final class FixtureUtils {
    private FixtureUtils() {
    }

    /**
     * Shared SnakeYaml instance for every fixture loader - construction isn't free, and it's stateless/reusable.
     */
    static final Yaml YAML = new Yaml();

    /**
     * One `{ action, subject, subjectData?, expected }` case, common to
     * every compliance fixture format regardless of how its surrounding
     * document is shaped.
     */
    record TestCase(String name, String action, String subject, Map<String, Object> subjectData, boolean expected) {
    }

    /**
     * `*.yaml` files under {@code dir} recursively for which {@code filter} holds, sorted by path.
     */
    static List<Path> discoverYamlFiles(Path dir, Predicate<Path> filter) throws IOException {
        try (var stream = Files.walk(dir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".yaml"))
                .filter(filter)
                .sorted()
                .collect(Collectors.toList());
        }
    }

    /**
     * Parses a raw `rules:` list of `[effect, action, subject, conditions?]` tuples into `Rule`s (SPEC_V0.md §3.3).
     */
    @SuppressWarnings("unchecked")
    static List<PolicyDefinition.Rule> toRules(List<?> rawRules) {
        List<PolicyDefinition.Rule> rules = new ArrayList<>();
        for (Object o : rawRules) {
            List<?> tuple = (List<?>) o;
            String effect = String.valueOf(tuple.get(0));
            String action = String.valueOf(tuple.get(1));
            String subjectName = String.valueOf(tuple.get(2));
            Map<String, Object> conditions = tuple.size() > 3 ? (Map<String, Object>) tuple.get(3) : null;
            rules.add(new PolicyDefinition.Rule(effect, action, subjectName, conditions));
        }
        return rules;
    }

    /**
     * Parses a raw `meta:` map into a {@link PolicyDefinition.Meta},
     * preserving the "not declared" vs. "explicitly declared" distinction
     * for anyAction/anySubject (SPEC_V0.md §3.2.1) via {@code
     * containsKey}, since a SnakeYaml-parsed map can tell the two apart
     * where a plain nullable field can't. Whatever raw value SnakeYaml
     * parsed for `anyAction`/`anySubject` (a string, {@code null}, {@code
     * false}, or anything else) is passed straight through to {@code
     * Meta.Builder}, which applies §3.2.1's four-way dispatch itself (see
     * {@link com.cptnfizzbin.keycard.policy.WildcardToken#of}).
     */
    static PolicyDefinition.Meta toMeta(Map<String, Object> rawMeta) {
        if (rawMeta == null) return null;
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta();

        if (rawMeta.containsKey("anyAction")) {
            meta.anyAction(WildcardToken.of(rawMeta.get("anyAction")));
        }

        if (rawMeta.containsKey("anySubject")) {
            meta.anySubject(WildcardToken.of(rawMeta.get("anySubject")));
        }

        if (rawMeta.get("actions") != null) {
            meta.actions(toStringList((List<?>) rawMeta.get("actions")));
        }

        if (rawMeta.get("subjects") != null) {
            meta.subjects(toStringList((List<?>) rawMeta.get("subjects")));
        }

        if (rawMeta.get("operators") != null) {
            meta.operators(toStringList((List<?>) rawMeta.get("operators")));
        }

        if (rawMeta.containsKey("application")) {
            meta.application(rawMeta.get("application"));
        }

        return meta;
    }

    private static List<String> toStringList(List<?> raw) {
        List<String> result = new ArrayList<>();
        for (Object o : raw) result.add(String.valueOf(o));
        return result;
    }

    /**
     * Resolves one {@link TestCase} against a {@link Policy} the same way
     * every fixture-driven suite does: a bare Subject (no instance) when
     * there's no instance data (EC-7/EC-9), or one wrapping
     * {@code subjectData} as its instance when there is.
     */
    static boolean resolve(Policy policy, TestCase testCase) {
        Action action = ActionFactory.create(testCase.action());
        Subject<Map<String, Object>> subject = SubjectFactory.<Map<String, Object>>create(testCase.subject());
        if (testCase.subjectData() != null) {
            subject = subject.wrap(testCase.subjectData());
        }
        return policy.can(action, subject);
    }

    /**
     * True when a fixture declaring {@code fixtureVersion} is compatible
     * with an implementation targeting {@code maxSupportedVersion}, per
     * SPEC_V0.md §2: the same MAJOR, and a MINOR no higher than what's
     * supported. PATCH never affects compatibility. Parsing/comparison is
     * delegated to semver4j - the same library {@link
     * com.cptnfizzbin.keycard.version.KeyCardVersion} uses - rather than
     * hand-rolled MAJOR.MINOR.PATCH parsing.
     */
    static boolean isCompatible(String fixtureVersion, String maxSupportedVersion) {
        Semver fixture = Objects.requireNonNull(Semver.coerce(fixtureVersion));
        Semver max = Objects.requireNonNull(Semver.coerce(maxSupportedVersion));
        return fixture.getMajor() == max.getMajor() && fixture.getMinor() <= max.getMinor();
    }

    /**
     * System property that overrides a suite's baked-in
     * {@code compliantVersion} for one run (e.g.
     * {@code mvn test -Dkeycard.fixtures.maxVersion=1.0.0}) - useful for
     * deliberately narrowing or widening the cap without editing code.
     * Unset (the common case) means "use whatever version the compliance
     * suite itself bakes in".
     */
    static final String MAX_VERSION_PROPERTY = "keycard.fixtures.maxVersion";

    /**
     * True when a fixture declaring {@code fixtureVersion} should run
     * against a compliance suite that bakes in {@code compliantVersion} as
     * the highest version its adapter is written against - see e.g.
     * {@code V1ConformanceFixtureTest.COMPLIANT_VERSION}. Every compliance
     * suite bakes in its own version rather than defaulting to "run
     * everything", so a suite whose adapter hasn't caught up to a newer
     * MINOR version's fixtures skips them automatically, with no external
     * configuration required; {@link #MAX_VERSION_PROPERTY} overrides that
     * baked-in default when set.
     */
    static boolean isIncluded(String fixtureVersion, String compliantVersion) {
        String override = System.getProperty(MAX_VERSION_PROPERTY);
        return isCompatible(fixtureVersion, override != null ? override : compliantVersion);
    }
}
