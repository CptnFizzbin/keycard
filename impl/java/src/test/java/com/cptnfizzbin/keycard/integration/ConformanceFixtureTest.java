package com.cptnfizzbin.keycard.integration;

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

/**
 * Metaprogrammed: every case in every suite in every fixture file under
 * test/fixtures/v1 is discovered at test-run time (via {@link Parameterized})
 * and becomes its own case below. Dropping a new suite, or a new case into
 * an existing suite, adds coverage automatically - no new test code
 * required. See {@link Fixtures} and {@link FixtureUtils} for the
 * fixture format this suite parses each case from.
 * <p>
 * A fixture whose declared `version` isn't covered by
 * {@link #COMPLIANT_VERSION} - this suite's own baked-in ceiling, per
 * SPEC_V0.md §2's compatibility rule - is skipped (not failed) via
 * {@link org.junit.Assume}; see {@link FixtureUtils#isIncluded} for
 * the mechanics and {@link FixtureUtils#MAX_VERSION_PROPERTY} for the
 * knob that overrides it for a single run.
 */
@RunWith(Parameterized.class)
public class ConformanceFixtureTest {
    @Parameters(name = "{0} > {1} > {2}")
    public static Collection<Object[]> cases() throws IOException {
        List<Object[]> params = new ArrayList<>();
        for (Path fixtureFile : Fixtures.discoverFixtureFiles()) {
            for (Fixtures.Suite suite : Fixtures.loadSuites(fixtureFile)) {
                for (FixtureUtils.TestCase testCase : suite.cases()) {
                    params.add(new Object[]{
                        fixtureFile.getFileName().toString(), suite.name(), testCase.name(), suite, testCase
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
            "suite version " + suite.version() + " exceeds this suite's compliant version " + KeyCardVersion.KEYCARD_POLICY_SUPPORTED_VERSIONS,
            Objects.requireNonNull(Semver.coerce(suite.version())).satisfies(KeyCardVersion.KEYCARD_POLICY_SUPPORTED_VERSIONS)
        );

        Policy policy = Policy.from(suite.definition(), Fixtures.operatorsFor(fixtureName));

        assertEquals(testCase.name(), testCase.expected(), FixtureUtils.resolve(policy, testCase));
    }
}
