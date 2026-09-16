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
 * Shared loading helpers for the fixture-driven integration tests. Not a
 * test class itself - see PolicyFixtureReadTest and PolicyFixtureCaseTest.
 *
 * KeyCard itself never reads or writes policy.yaml text; parsing it into a
 * plain PolicyDefinition (via SnakeYaml, a test-only dependency) is this
 * test suite's job, mirroring what an application would do. The on-disk
 * shape is the v1 rules/meta schema, per SPEC_V1-0.md §3.
 *
 * File discovery, YAML parsing, and the per-case shape are shared with
 * every other compliance suite via {@link ComplianceFixtures}.
 */
final class PolicyFixtures {
    private PolicyFixtures() {}

    static final Path FIXTURES_DIR = Paths.get("../../test/fixtures/policies");

    /**
     * Some fixture policies exercise a custom condition operator, which -
     * per SPEC_V1-0.md §7.4.12 - only the host application (here, this
     * test suite) can implement; declaring it in meta.operators documents
     * it but doesn't wire up behavior. Keyed by fixture file name.
     */
    static List<Operator> operatorsFor(String fixtureFileName) {
        if ("policy-05-advanced.yaml".equals(fixtureFileName)) {
            return List.of(Operator.of("$startsWithUpper", (subject, value, ctx) ->
                subject instanceof String && !((String) subject).isEmpty()
                    && Character.isUpperCase(((String) subject).charAt(0))));
        }
        return List.of();
    }

    /** All `policy-*.yaml` fixtures (excluding their `.test.yaml` companions), sorted by name. */
    static List<Path> discoverPolicyFiles() throws IOException {
        return ComplianceFixtures.discoverYamlFiles(FIXTURES_DIR, p -> !p.getFileName().toString().endsWith(".test.yaml"));
    }

    /** The `*.test.yaml` companion path for a given `*.yaml` policy fixture. */
    static Path testFileFor(Path policyFile) {
        String name = policyFile.getFileName().toString();
        String testName = name.substring(0, name.length() - ".yaml".length()) + ".test.yaml";
        return policyFile.resolveSibling(testName);
    }

    @SuppressWarnings("unchecked")
    static PolicyDefinition loadPolicyDefinition(Path yamlFile) throws IOException {
        String content = Files.readString(yamlFile);
        Map<String, Object> raw = ComplianceFixtures.YAML.load(content);

        String version = String.valueOf(raw.get("version"));
        String name = (String) raw.get("name");
        String description = (String) raw.get("description");
        PolicyDefinition.Meta meta = ComplianceFixtures.toMeta((Map<String, Object>) raw.get("meta"));
        List<PolicyDefinition.Rule> rules = ComplianceFixtures.toRules((List<?>) raw.getOrDefault("rules", List.of()));

        return new PolicyDefinition(version, name, description, meta, rules);
    }

    @SuppressWarnings("unchecked")
    static List<ComplianceFixtures.TestCase> loadTestCases(Path testYamlFile) throws IOException {
        String content = Files.readString(testYamlFile);
        Map<String, Object> raw = ComplianceFixtures.YAML.load(content);
        List<Map<String, Object>> rawCases = (List<Map<String, Object>>) raw.get("tests");

        List<ComplianceFixtures.TestCase> cases = new ArrayList<>();
        for (Map<String, Object> rc : rawCases) {
            cases.add(new ComplianceFixtures.TestCase(
                (String) rc.get("name"),
                (String) rc.get("action"),
                (String) rc.get("subject"),
                (Map<String, Object>) rc.get("subjectData"),
                Boolean.TRUE.equals(rc.get("expected"))
            ));
        }
        return cases;
    }
}
