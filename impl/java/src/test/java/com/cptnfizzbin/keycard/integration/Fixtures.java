package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared loading helpers for the v1 conformance fixtures under
 * test/fixtures/v1 (see the README there) - the shared, spec-derived
 * fixtures every implementation MUST read, per SPEC_V0.md §6. Not a
 * test class itself - see V1ConformanceFixtureTest.
 * <p>
 * KeyCard itself never reads or writes policy.yaml text; parsing it into a
 * plain PolicyDefinition (via SnakeYaml, a test-only dependency) is this
 * test suite's job, mirroring what an application would do.
 * <p>
 * impl/java now natively implements the v1 rules/meta schema (see {@link
 * PolicyDefinition}), so each parsed suite's `rules`/`meta` are handed
 * straight to a real {@link PolicyDefinition} - no adapter needed.
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
     * which - per SPEC_V0.md §7.4.12 - only the host application (here,
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
    record Suite(String version, String name, PolicyDefinition definition, List<FixtureUtils.TestCase> cases) {
    }

    @SuppressWarnings("unchecked")
    static List<Suite> loadSuites(Path yamlFile) throws IOException {
        String content = Files.readString(yamlFile);
        List<Suite> suites = new ArrayList<>();

        for (Object rawDoc : FixtureUtils.YAML.loadAll(content)) {
            Map<String, Object> raw = (Map<String, Object>) rawDoc;
            String version = String.valueOf(raw.get("version"));
            String name = (String) raw.get("name");

            PolicyDefinition.Meta meta = FixtureUtils.toMeta((Map<String, Object>) raw.get("meta"));
            List<PolicyDefinition.Rule> rules = FixtureUtils.toRules((List<?>) raw.get("rules"));
            PolicyDefinition definition = new PolicyDefinition()
                .version(version)
                .name(name)
                .meta(meta)
                .rules(rules);

            List<FixtureUtils.TestCase> cases = new ArrayList<>();
            for (Map<String, Object> rc : (List<Map<String, Object>>) raw.get("tests")) {
                List<?> check = (List<?>) rc.get("check");
                String action = String.valueOf(check.get(0));
                String subject = String.valueOf(check.get(1));
                Map<String, Object> subjectData = check.size() == 3
                    ? (Map<String, Object>) check.get(2)
                    : null;

                boolean expected = (Boolean) rc.get("expected");
                String caseName = String.valueOf(rc.get("name"));

                cases.add(new FixtureUtils.TestCase(
                    caseName,
                    action,
                    subject,
                    subjectData,
                    expected
                ));
            }

            suites.add(new Suite(version, name, definition, cases));
        }

        return suites;
    }
}
