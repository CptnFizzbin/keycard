package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.conditions.Conditions;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Unit tests for the shared helpers in {@link ComplianceFixtures}. */
public class ComplianceFixturesTest {

    @After
    public void clearVersionCap() {
        System.clearProperty(ComplianceFixtures.MAX_VERSION_PROPERTY);
    }

    @Test
    public void parsesAFullMajorMinorPatchString() {
        assertEquals(new ComplianceFixtures.SemVer(1, 2, 3), ComplianceFixtures.SemVer.parse("1.2.3"));
    }

    @Test
    public void defaultsMinorAndPatchToZeroWhenOmitted() {
        assertEquals(new ComplianceFixtures.SemVer(1, 0, 0), ComplianceFixtures.SemVer.parse("1"));
        assertEquals(new ComplianceFixtures.SemVer(1, 5, 0), ComplianceFixtures.SemVer.parse("1.5"));
    }

    @Test
    public void sameVersionIsAlwaysCompatibleWithItself() {
        assertTrue(ComplianceFixtures.isCompatible("1.0.0", "1.0.0"));
    }

    @Test
    public void aLowerMinorThanWhatsSupportedIsCompatible() {
        assertTrue(ComplianceFixtures.isCompatible("1.0.0", "1.5.0"));
    }

    @Test
    public void aHigherMinorThanWhatsSupportedIsNotCompatible() {
        assertFalse(ComplianceFixtures.isCompatible("1.5.0", "1.0.0"));
    }

    @Test
    public void patchNeverAffectsCompatibility() {
        assertTrue(ComplianceFixtures.isCompatible("1.0.9", "1.0.0"));
        assertTrue(ComplianceFixtures.isCompatible("1.0.0", "1.0.9"));
    }

    @Test
    public void aDifferentMajorIsNeverCompatibleEitherDirection() {
        assertFalse(ComplianceFixtures.isCompatible("2.0.0", "1.9.0"));
        assertFalse(ComplianceFixtures.isCompatible("1.0.0", "2.0.0"));
    }

    @Test
    public void aFixtureAtOrBelowTheBakedInCompliantVersionIsIncluded() {
        assertTrue(ComplianceFixtures.isIncluded("1.0.0", "1.0.0"));
        assertTrue(ComplianceFixtures.isIncluded("1.0.0", "1.5.0"));
    }

    @Test
    public void aFixtureAboveTheBakedInCompliantVersionIsExcluded() {
        assertFalse(ComplianceFixtures.isIncluded("1.5.0", "1.0.0"));
    }

    @Test
    public void theSystemPropertyOverridesTheBakedInCompliantVersion() {
        System.setProperty(ComplianceFixtures.MAX_VERSION_PROPERTY, "1.5.0");
        // Would be excluded against a baked-in "1.0.0", but the override widens it.
        assertTrue(ComplianceFixtures.isIncluded("1.2.0", "1.0.0"));
    }

    @Test
    public void theSystemPropertyCanNarrowTheBakedInCompliantVersionToo() {
        System.setProperty(ComplianceFixtures.MAX_VERSION_PROPERTY, "1.0.0");
        // Would be included against a baked-in "1.5.0", but the override narrows it.
        assertFalse(ComplianceFixtures.isIncluded("1.2.0", "1.5.0"));
    }

    @Test
    public void resolveUsesTheBareOverloadWhenThereIsNoInstance() {
        Policy policy = Policy.from(new PolicyDefinition(
            "1.0.0", List.of(new PolicyDefinition.Rule("allow", "Read", "Article", null))
        ));
        ComplianceFixtures.TestCase testCase = new ComplianceFixtures.TestCase("n", "Read", "Article", null, true);

        assertTrue(ComplianceFixtures.resolve(policy, testCase));
    }

    @Test
    public void resolveTagsInstanceDataWithDunderName() {
        Policy policy = Policy.from(new PolicyDefinition(
            "1.0.0",
            List.of(new PolicyDefinition.Rule("allow", "Update", "Article", Conditions.op("owner_id", 1)))
        ));
        ComplianceFixtures.TestCase matching = new ComplianceFixtures.TestCase(
            "n", "Update", "Article", Map.of("owner_id", 1), true
        );
        ComplianceFixtures.TestCase notMatching = new ComplianceFixtures.TestCase(
            "n", "Update", "Article", Map.of("owner_id", 2), false
        );

        assertTrue(ComplianceFixtures.resolve(policy, matching));
        assertFalse(ComplianceFixtures.resolve(policy, notMatching));
    }
}
