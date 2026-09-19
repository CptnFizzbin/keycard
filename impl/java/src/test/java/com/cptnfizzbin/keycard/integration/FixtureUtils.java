package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.semver4j.Semver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * shape: discovering `*.yaml` files, the `{ action, subject,
 * subjectData?, expected }` shape every format's individual cases boil
 * down to once parsed, resolving one such case against a {@link Policy},
 * and filtering fixtures by the SemVer `version` they declare - so each
 * format-specific loader only has to own parsing its own document's
 * outer shape into that common {@link TestCase}, not the
 * discovery/resolution/filtering mechanics around it. Parsing a
 * document's `rules`/`meta` shape isn't this class's job
 * any more either - {@code PolicyDefinition}/{@code Rule}/{@code Meta}
 * are Jackson-annotated and bind straight from a document themselves.
 * <p>
 * KeyCard itself never reads or writes policy.yaml text; parsing one
 * (via jackson-dataformat-yaml, a test-only dependency) is this test
 * suite's job, mirroring what an application would do.
 */
final class FixtureUtils {
    private FixtureUtils() {
    }

    /**
     * Shared Jackson YAML mapper for every fixture loader - reads a
     * fixture's YAML and binds it directly to typed Java types (a
     * {@code PolicyDefinition} for the policy document shape; see
     * {@link Fixtures.SuiteDoc}), rather than a raw Map/List tree that
     * then has to be walked and cast by hand. Construction isn't free,
     * and it's stateless/reusable. Unknown fields (e.g. an informational
     * `description:`) are tolerated, since a fixture isn't required to
     * stick to only the fields a loader happens to model.
     */
    static final YAMLMapper YAML = YAMLMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

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
     * Every `---`-separated YAML document in {@code yamlFile}, bound directly to {@code type}.
     */
    static <T> List<T> loadYamlDocuments(Path yamlFile, Class<T> type) throws IOException {
        try (var parser = YAML.createParser(yamlFile.toFile())) {
            return YAML.readValues(parser, type).readAll();
        }
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
     * SPEC_V0.md: the same MAJOR, and a MINOR no higher than what's
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
