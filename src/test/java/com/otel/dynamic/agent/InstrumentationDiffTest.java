package com.otel.dynamic.agent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for InstrumentationDiff.
 */
public class InstrumentationDiffTest {

    @Before
    @After
    public void clearSystemProperties() {
        // Clean up any system properties from tests
        DynamicInstrumentationConfig.clear();
    }

    @Test
    public void testEmptyDiffs() {
        Map<String, String> oldChecksums = new HashMap<>();
        Map<String, String> newChecksums = new HashMap<>();

        InstrumentationDiff diff = InstrumentationDiff.compute(oldChecksums, newChecksums);

        assertFalse(diff.hasChanges());
        assertTrue(diff.getAddedOrChanged().isEmpty());
        assertTrue(diff.getRemoved().isEmpty());
        assertTrue(diff.getUnchanged().isEmpty());
        assertEquals(0, diff.getTotalCount());
    }

    @Test
    public void testAddedEntry() {
        Map<String, String> oldChecksums = new HashMap<>();
        Map<String, String> newChecksums = new HashMap<>();
        newChecksums.put("com.example.Service#process", "abc123");

        InstrumentationDiff diff = InstrumentationDiff.compute(oldChecksums, newChecksums);

        assertTrue(diff.hasChanges());
        assertEquals(1, diff.getAddedOrChanged().size());
        assertTrue(diff.getAddedOrChanged().contains("com.example.Service#process"));
        assertTrue(diff.getRemoved().isEmpty());
        assertTrue(diff.getUnchanged().isEmpty());
    }

    @Test
    public void testRemovedEntry() {
        Map<String, String> oldChecksums = new HashMap<>();
        oldChecksums.put("com.example.Service#process", "abc123");
        Map<String, String> newChecksums = new HashMap<>();

        InstrumentationDiff diff = InstrumentationDiff.compute(oldChecksums, newChecksums);

        assertTrue(diff.hasChanges());
        assertTrue(diff.getAddedOrChanged().isEmpty());
        assertEquals(1, diff.getRemoved().size());
        assertTrue(diff.getRemoved().contains("com.example.Service#process"));
        assertTrue(diff.getUnchanged().isEmpty());
    }

    @Test
    public void testChangedEntry() {
        Map<String, String> oldChecksums = new HashMap<>();
        oldChecksums.put("com.example.Service#process", "abc123");
        Map<String, String> newChecksums = new HashMap<>();
        newChecksums.put("com.example.Service#process", "xyz789");

        InstrumentationDiff diff = InstrumentationDiff.compute(oldChecksums, newChecksums);

        assertTrue(diff.hasChanges());
        assertEquals(1, diff.getAddedOrChanged().size());
        assertTrue(diff.getAddedOrChanged().contains("com.example.Service#process"));
        assertTrue(diff.getRemoved().isEmpty());
        assertTrue(diff.getUnchanged().isEmpty());
    }

    @Test
    public void testUnchangedEntry() {
        Map<String, String> oldChecksums = new HashMap<>();
        oldChecksums.put("com.example.Service#process", "abc123");
        Map<String, String> newChecksums = new HashMap<>();
        newChecksums.put("com.example.Service#process", "abc123");

        InstrumentationDiff diff = InstrumentationDiff.compute(oldChecksums, newChecksums);

        assertFalse(diff.hasChanges());
        assertTrue(diff.getAddedOrChanged().isEmpty());
        assertTrue(diff.getRemoved().isEmpty());
        assertEquals(1, diff.getUnchanged().size());
        assertTrue(diff.getUnchanged().contains("com.example.Service#process"));
    }

    @Test
    public void testMixedScenario() {
        Map<String, String> oldChecksums = new HashMap<>();
        oldChecksums.put("com.example.Service#process", "abc123");      // will be unchanged
        oldChecksums.put("com.example.Service#oldMethod", "def456");    // will be removed
        oldChecksums.put("com.example.Handler#handle", "oldhash");      // will be changed

        Map<String, String> newChecksums = new HashMap<>();
        newChecksums.put("com.example.Service#process", "abc123");      // unchanged
        newChecksums.put("com.example.Handler#handle", "newhash");      // changed
        newChecksums.put("com.example.NewService#run", "ghi789");       // added

        InstrumentationDiff diff = InstrumentationDiff.compute(oldChecksums, newChecksums);

        assertTrue(diff.hasChanges());
        // Total: 2 (added/changed) + 1 (removed) + 1 (unchanged) = 4
        assertEquals(4, diff.getTotalCount());

        // Added + Changed = 2 (NewService#run added, Handler#handle changed)
        assertEquals(2, diff.getAddedOrChanged().size());
        assertTrue(diff.getAddedOrChanged().contains("com.example.NewService#run"));
        assertTrue(diff.getAddedOrChanged().contains("com.example.Handler#handle"));

        // Removed = 1
        assertEquals(1, diff.getRemoved().size());
        assertTrue(diff.getRemoved().contains("com.example.Service#oldMethod"));

        // Unchanged = 1
        assertEquals(1, diff.getUnchanged().size());
        assertTrue(diff.getUnchanged().contains("com.example.Service#process"));
    }

    @Test
    public void testGetAffected() {
        Map<String, String> oldChecksums = new HashMap<>();
        oldChecksums.put("com.example.Service#process", "abc123");
        oldChecksums.put("com.example.Service#removed", "xyz");

        Map<String, String> newChecksums = new HashMap<>();
        newChecksums.put("com.example.Service#process", "abc123");      // unchanged
        newChecksums.put("com.example.NewService#run", "new");          // added

        InstrumentationDiff diff = InstrumentationDiff.compute(oldChecksums, newChecksums);

        Set<String> affected = diff.getAffected();
        assertEquals(2, affected.size());
        assertTrue(affected.contains("com.example.NewService#run"));     // added
        assertTrue(affected.contains("com.example.Service#removed"));   // removed
        assertFalse(affected.contains("com.example.Service#process"));  // unchanged
    }

    @Test
    public void testSameContentNoChanges() {
        Map<String, String> oldChecksums = new HashMap<>();
        oldChecksums.put("com.example.Service#process", "abc123");
        oldChecksums.put("com.example.Handler#handle", "def456");

        Map<String, String> newChecksums = new HashMap<>();
        newChecksums.put("com.example.Service#process", "abc123");
        newChecksums.put("com.example.Handler#handle", "def456");

        InstrumentationDiff diff = InstrumentationDiff.compute(oldChecksums, newChecksums);

        assertFalse("No changes should be detected", diff.hasChanges());
        assertEquals(2, diff.getUnchanged().size());
        assertTrue(diff.getAffected().isEmpty());
    }
}
