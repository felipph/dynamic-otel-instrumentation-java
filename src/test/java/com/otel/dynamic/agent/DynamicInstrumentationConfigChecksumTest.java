package com.otel.dynamic.agent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for DynamicInstrumentationConfig checksum functionality.
 */
public class DynamicInstrumentationConfigChecksumTest {

    @Before
    @After
    public void clearSystemProperties() {
        DynamicInstrumentationConfig.clear();
    }

    @Test
    public void testComputeChecksumNullRules() {
        String checksum = DynamicInstrumentationConfig.computeChecksum(null);
        assertEquals("", checksum);
    }

    @Test
    public void testComputeChecksumEmptyRules() {
        List<DynamicInstrumentationConfig.AttributeRule> rules = new ArrayList<>();
        String checksum = DynamicInstrumentationConfig.computeChecksum(rules);
        assertEquals("", checksum);
    }

    @Test
    public void testComputeChecksumSingleRule() {
        List<DynamicInstrumentationConfig.AttributeRule> rules = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.id")
        );

        String checksum = DynamicInstrumentationConfig.computeChecksum(rules);

        assertNotNull(checksum);
        assertFalse(checksum.isEmpty());
        assertEquals(32, checksum.length()); // MD5 produces 32 hex characters
    }

    @Test
    public void testComputeChecksumMultipleRules() {
        List<DynamicInstrumentationConfig.AttributeRule> rules = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.id"),
                new DynamicInstrumentationConfig.AttributeRule(1, "getName", "app.name")
        );

        String checksum = DynamicInstrumentationConfig.computeChecksum(rules);

        assertNotNull(checksum);
        assertFalse(checksum.isEmpty());
        assertEquals(32, checksum.length());
    }

    @Test
    public void testComputeChecksumDeterministic() {
        List<DynamicInstrumentationConfig.AttributeRule> rules = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.id")
        );

        String checksum1 = DynamicInstrumentationConfig.computeChecksum(rules);
        String checksum2 = DynamicInstrumentationConfig.computeChecksum(rules);

        assertEquals("Same rules should produce same checksum", checksum1, checksum2);
    }

    @Test
    public void testComputeChecksumDifferentForDifferentRules() {
        List<DynamicInstrumentationConfig.AttributeRule> rules1 = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.id")
        );
        List<DynamicInstrumentationConfig.AttributeRule> rules2 = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.different_id")
        );

        String checksum1 = DynamicInstrumentationConfig.computeChecksum(rules1);
        String checksum2 = DynamicInstrumentationConfig.computeChecksum(rules2);

        assertNotEquals("Different rules should produce different checksums", checksum1, checksum2);
    }

    @Test
    public void testComputeChecksumDifferentForDifferentArgIndex() {
        List<DynamicInstrumentationConfig.AttributeRule> rules1 = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.id")
        );
        List<DynamicInstrumentationConfig.AttributeRule> rules2 = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(1, "getId", "app.id")
        );

        String checksum1 = DynamicInstrumentationConfig.computeChecksum(rules1);
        String checksum2 = DynamicInstrumentationConfig.computeChecksum(rules2);

        assertNotEquals("Different argIndex should produce different checksums", checksum1, checksum2);
    }

    @Test
    public void testComputeReturnChecksum() {
        List<DynamicInstrumentationConfig.ReturnValueRule> rules = Arrays.asList(
                new DynamicInstrumentationConfig.ReturnValueRule("getId", "app.return_id")
        );

        String checksum = DynamicInstrumentationConfig.computeReturnChecksum(rules);

        assertNotNull(checksum);
        assertFalse(checksum.isEmpty());
        assertEquals(32, checksum.length());
    }

    @Test
    public void testRegisterStoresChecksum() {
        List<DynamicInstrumentationConfig.AttributeRule> rules = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.id")
        );

        DynamicInstrumentationConfig.register("com.example.Service", "process", rules);

        String storedChecksum = DynamicInstrumentationConfig.getChecksum("com.example.Service", "process");
        String computedChecksum = DynamicInstrumentationConfig.computeChecksum(rules);

        assertEquals("Register should store the computed checksum", computedChecksum, storedChecksum);
    }

    @Test
    public void testGetAllChecksumsEmpty() {
        Map<String, String> checksums = DynamicInstrumentationConfig.getAllChecksums();
        assertTrue(checksums.isEmpty());
    }

    @Test
    public void testGetAllChecksumsAfterRegister() {
        List<DynamicInstrumentationConfig.AttributeRule> rules = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.id")
        );

        DynamicInstrumentationConfig.register("com.example.Service", "process", rules);
        DynamicInstrumentationConfig.register("com.example.Handler", "handle", rules);

        Map<String, String> checksums = DynamicInstrumentationConfig.getAllChecksums();

        assertEquals(2, checksums.size());
        assertTrue(checksums.containsKey("com.example.Service#process"));
        assertTrue(checksums.containsKey("com.example.Handler#handle"));
    }

    @Test
    public void testClearRemovesChecksums() {
        List<DynamicInstrumentationConfig.AttributeRule> rules = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.id")
        );

        DynamicInstrumentationConfig.register("com.example.Service", "process", rules);
        assertFalse(DynamicInstrumentationConfig.getAllChecksums().isEmpty());

        DynamicInstrumentationConfig.clear();

        assertTrue(DynamicInstrumentationConfig.getAllChecksums().isEmpty());
    }

    @Test
    public void testRegisterReturnCombinesChecksums() {
        List<DynamicInstrumentationConfig.AttributeRule> attrRules = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.id")
        );
        List<DynamicInstrumentationConfig.ReturnValueRule> returnRules = Arrays.asList(
                new DynamicInstrumentationConfig.ReturnValueRule("getStatus", "app.status")
        );

        DynamicInstrumentationConfig.register("com.example.Service", "process", attrRules);
        String checksumAfterAttr = DynamicInstrumentationConfig.getChecksum("com.example.Service", "process");

        DynamicInstrumentationConfig.registerReturn("com.example.Service", "process", returnRules);
        String checksumAfterReturn = DynamicInstrumentationConfig.getChecksum("com.example.Service", "process");

        // Checksum should be updated to include both
        assertNotNull(checksumAfterAttr);
        assertNotNull(checksumAfterReturn);
        assertTrue("Combined checksum should contain delimiter", checksumAfterReturn.contains(":"));
    }

    @Test
    public void testIntegrationScenario() {
        // Simulate initial registration
        List<DynamicInstrumentationConfig.AttributeRule> rules1 = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.id")
        );
        DynamicInstrumentationConfig.register("com.example.Service", "process", rules1);

        // Snapshot before "reload"
        Map<String, String> oldChecksums = DynamicInstrumentationConfig.getAllChecksums();

        // Simulate config change
        List<DynamicInstrumentationConfig.AttributeRule> rules2 = Arrays.asList(
                new DynamicInstrumentationConfig.AttributeRule(0, "getId", "app.new_id")  // changed attribute name
        );
        DynamicInstrumentationConfig.clear();
        DynamicInstrumentationConfig.register("com.example.Service", "process", rules2);

        // Snapshot after "reload"
        Map<String, String> newChecksums = DynamicInstrumentationConfig.getAllChecksums();

        // Compute diff
        InstrumentationDiff diff = InstrumentationDiff.compute(oldChecksums, newChecksums);

        assertTrue("Change should be detected", diff.hasChanges());
        assertEquals(1, diff.getAddedOrChanged().size());
    }
}
