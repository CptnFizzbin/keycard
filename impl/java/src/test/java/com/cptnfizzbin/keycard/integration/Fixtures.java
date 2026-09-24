package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared loading helpers for the v1 conformance fixtures under
 * test/fixtures/v1 (see the README there) - the shared, spec-derived
 * fixtures every implementation MUST read. Not a
 * test class itself - see V1ConformanceFixtureTest.
 * <p>
 * KeyCard itself never reads or writes policy.yaml text; parsing one
 * (via jackson-dataformat-yaml, a test-only dependency) is this test
 * suite's job, mirroring what an application would do.
 * <p>
 * impl/java now natively implements the v1 rules/meta schema (see {@link
 * PolicyDefinition}), whose own Jackson annotations bind a document's
 * `version`/`name`/`meta`/`rules` straight into a real {@link
 * PolicyDefinition} - {@code @JsonIgnoreProperties(ignoreUnknown = true)}
 * on {@link PolicyDefinition} itself means the `tests:` list a fixture
 * document also carries (which PolicyDefinition has no field for) is
 * simply skipped on that pass. {@link #loadSuites} reads each document a
 * second time into {@link TestsDoc}, a minimal wrapper for just that
 * list, rather than a DTO that re-declares PolicyDefinition's own shape
 * alongside it (Jackson can't combine {@code @JsonUnwrapped} with a
 * record/constructor-based property as of jackson-databind 2.17).
 * <p>
 * File discovery, YAML parsing, the per-case shape, and can()-resolution
 * are shared with every other compliance suite via {@link FixtureUtils}.
 */
final class Fixtures {
    private Fixtures() {
    }

    static final Path FIXTURES_DIR = Paths.get("../../test/fixtures");

    /**
     * All `*.yaml` fixture files under test/fixtures/v1, sorted by name.
     */
    static List<Path> discoverFixtureFiles() throws IOException {
        return FixtureUtils.discoverYamlFiles(FIXTURES_DIR, p -> true);
    }

    /**
     * Some v1 conformance suites exercise a custom condition operator,
     * which only the host application (here,
     * this test suite) can implement; declaring it in meta.operators
     * documents it but doesn't wire up behavior. Keyed by fixture file name.
     */
    static List<Operator> operatorsFor(String fixtureFileName) {
        if ("11-worked-example.yaml".equals(fixtureFileName)) {
            // Mirrors the spec Appendix's own suggested implementation:
            // "one that checks subject.roles.includes('admin')".
            return List.of(Operator.of("$hasRole", (subject, value, ctx) -> {
                if (!(subject instanceof Map)) return false;
                Object roles = ((Map<?, ?>) subject).get("roles");
                return roles instanceof List && ((List<?>) roles).contains(value);
            }));
        }
        if ("policy-05-advanced.yaml".equals(fixtureFileName)) {
            // A custom operator checking whether a field's string value starts with an uppercase letter.
            return List.of(Operator.of("$startsWithUpper", (subject, value, ctx) -> {
                if (!(subject instanceof String) || !(value instanceof Boolean) || ((String) subject).isEmpty()) {
                    return false;
                }
                char first = ((String) subject).charAt(0);
                boolean isUpper = Character.toUpperCase(first) == first;
                return isUpper == (Boolean) value;
            }));
        }
        return List.of();
    }

    /**
     * One `---`-separated `{ version, name, meta?, rules, cases }` document from a fixture file.
     */
    public record Suite(PolicyDefinition definition, List<FixtureUtils.TestCase> cases) {
    }

    /**
     * `[action, subject, subjectData?]` - the `check:` tuple each `tests:`
     * entry declares, bound positionally the same way {@link
     * PolicyDefinition.Rule} binds a rule tuple.
     */
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record CheckDoc(String action, String subject, Map<String, Object> subjectData) {
    }

    record TestDoc(String name, CheckDoc check, Boolean expected) {
    }

    /**
     * The one field of a fixture document {@link PolicyDefinition} itself has no place for.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestsDoc(List<TestDoc> tests) {
    }

    static List<Suite> loadSuites(Path yamlFile) throws IOException {
        List<PolicyDefinition> definitions = FixtureUtils.loadYamlDocuments(yamlFile, PolicyDefinition.class);
        List<TestsDoc> testsDocs = FixtureUtils.loadYamlDocuments(yamlFile, TestsDoc.class);

        List<Suite> suites = new ArrayList<>();
        for (int i = 0; i < definitions.size(); i++) {
            List<FixtureUtils.TestCase> cases = new ArrayList<>();
            for (TestDoc test : testsDocs.get(i).tests()) {
                cases.add(new FixtureUtils.TestCase(
                    test.name(),
                    test.check().action(),
                    test.check().subject(),
                    test.check().subjectData(),
                    test.expected()
                ));
            }

            suites.add(new Suite(definitions.get(i), cases));
        }

        return suites;
    }
}
