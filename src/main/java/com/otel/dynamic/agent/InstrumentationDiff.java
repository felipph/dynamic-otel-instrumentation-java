package com.otel.dynamic.agent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents the difference between two instrumentation configurations.
 * Used to determine which classes need retransformation during hot-reload.
 *
 * By comparing checksums of old and new rules, we can avoid retransforming
 * classes that haven't changed, significantly improving performance for
 * large applications with thousands of classes.
 */
public class InstrumentationDiff {

    private final Set<String> addedOrChanged;
    private final Set<String> removed;
    private final Set<String> unchanged;

    private InstrumentationDiff(Set<String> addedOrChanged, Set<String> removed, Set<String> unchanged) {
        this.addedOrChanged = Collections.unmodifiableSet(addedOrChanged);
        this.removed = Collections.unmodifiableSet(removed);
        this.unchanged = Collections.unmodifiableSet(unchanged);
    }

    /**
     * Compute the diff between old and new checksum snapshots.
     *
     * @param oldChecksums checksums before reload (className#methodName -> checksum)
     * @param newChecksums checksums after reload (className#methodName -> checksum)
     * @return diff indicating added, changed, removed, and unchanged entries
     */
    public static InstrumentationDiff compute(Map<String, String> oldChecksums, Map<String, String> newChecksums) {
        Set<String> addedOrChanged = new HashSet<>();
        Set<String> removed = new HashSet<>();
        Set<String> unchanged = new HashSet<>();

        // Find removed entries (in old but not in new)
        for (String key : oldChecksums.keySet()) {
            if (!newChecksums.containsKey(key)) {
                removed.add(key);
            }
        }

        // Find added or changed entries
        for (Map.Entry<String, String> entry : newChecksums.entrySet()) {
            String key = entry.getKey();
            String newChecksum = entry.getValue();
            String oldChecksum = oldChecksums.get(key);

            if (oldChecksum == null) {
                // New entry
                addedOrChanged.add(key);
            } else if (!oldChecksum.equals(newChecksum)) {
                // Changed entry
                addedOrChanged.add(key);
            } else {
                // Unchanged
                unchanged.add(key);
            }
        }

        return new InstrumentationDiff(addedOrChanged, removed, unchanged);
    }

    /**
     * @return class#method entries that are new or have changed checksums
     */
    public Set<String> getAddedOrChanged() {
        return addedOrChanged;
    }

    /**
     * @return class#method entries that were removed in the new config
     */
    public Set<String> getRemoved() {
        return removed;
    }

    /**
     * @return class#method entries that have not changed
     */
    public Set<String> getUnchanged() {
        return unchanged;
    }

    /**
     * @return all entries that need retransformation (added, changed, or removed)
     */
    public Set<String> getAffected() {
        Set<String> affected = new HashSet<>(addedOrChanged);
        affected.addAll(removed);
        return affected;
    }

    /**
     * @return true if there are any changes that require retransformation
     */
    public boolean hasChanges() {
        return !addedOrChanged.isEmpty() || !removed.isEmpty();
    }

    /**
     * @return total number of entries across all categories
     */
    public int getTotalCount() {
        return addedOrChanged.size() + removed.size() + unchanged.size();
    }

    @Override
    public String toString() {
        return String.format("InstrumentationDiff[added=%d, changed=%d (in addedOrChanged), removed=%d, unchanged=%d]",
                addedOrChanged.size(), removed.size(), unchanged.size());
    }
}
