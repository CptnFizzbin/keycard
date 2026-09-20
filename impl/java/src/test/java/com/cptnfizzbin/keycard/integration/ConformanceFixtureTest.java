package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.conditions.OperatorCatalog;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.version.KeyCardVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.semver4j.Semver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class ConformanceFixtureTest {
    @Parameters(name = "{0} > {1} > {2}")
    public static Collection<Object[]> cases() throws IOException {
        List<Object[]> params = new ArrayList<>();
        for (Path fixtureFile : Fixtures.discoverFixtureFiles()) {
            for (Fixtures.Suite suite : Fixtures.loadSuites(fixtureFile)) {
                for (FixtureUtils.TestCase testCase : suite.cases()) {
                    params.add(new Object[]{
                        fixtureFile.getFileName().toString(), suite.definition().name(), testCase.name(), suite, testCase
                    });
                }
            }
        }
        return params;
    }

    private final String fixtureName;
    private final Fixtures.Suite suite;
    private final FixtureUtils.TestCase testCase;

    public ConformanceFixtureTest(
        String fixtureName, String suiteName, String caseName, Fixtures.Suite suite, FixtureUtils.TestCase testCase
    ) {
        this.fixtureName = fixtureName;
        this.suite = suite;
        this.testCase = testCase;
    }

    @Test
    public void resolvesExpectedResult() {
        assumeTrue(
            "suite version " + suite.definition().version() + " exceeds this suite's compliant version " + KeyCardVersion.KEYCARD_POLICY_SUPPORTED_VERSIONS,
            Objects.requireNonNull(Semver.coerce(suite.definition().version())).satisfies(KeyCardVersion.KEYCARD_POLICY_SUPPORTED_VERSIONS)
        );

        KeycardConfig config = new KeycardConfig()
            .operators(new OperatorCatalog().addAll(Fixtures.operatorsFor(fixtureName)));

        Policy policy = new Policy(suite.definition(), config);

        assertEquals(testCase.name(), testCase.expected(), FixtureUtils.resolve(policy, testCase));
    }
}
