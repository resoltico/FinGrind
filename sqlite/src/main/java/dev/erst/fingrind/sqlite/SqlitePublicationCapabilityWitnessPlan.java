package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable deterministic acquisition plan for publication-capability witnesses.
 *
 * <p>The plan collects all caller-admitted requirements before any witness is acquired. This keeps
 * duplicate collapse, same-parent admission, acquisition order, and later revalidation in one
 * explicit ordered model rather than in mutable maps with accidental concurrency implications.
 */
final class SqlitePublicationCapabilityWitnessPlan {
  private final List<Entry> entries;

  private SqlitePublicationCapabilityWitnessPlan(List<Entry> entries) {
    this.entries = List.copyOf(entries);
  }

  /** Builds one deterministic plan and attributes malformed requirements to their exact target. */
  static SqlitePublicationCapabilityWitnessPlan create(
      List<SqlitePublicationCapabilityWitness.Requirement> requirements)
      throws SqlitePublicationCapabilityWitness.AcquisitionFailure {
    List<SqlitePublicationCapabilityWitness.Requirement> checkedRequirements =
        List.copyOf(requirements);
    List<MutableEntry> entries = new ArrayList<>();
    for (SqlitePublicationCapabilityWitness.Requirement requirement : checkedRequirements) {
      SqlitePublicationCapabilityWitness.Requirement checkedRequirement =
          Objects.requireNonNull(requirement, "requirement");
      try {
        entryFor(
                entries,
                SqlitePublicationCapabilityWitnessKey.forTarget(
                    checkedRequirement.targetPath(), checkedRequirement.primitiveKind()))
            .requirements()
            .add(checkedRequirement);
      } catch (IOException | RuntimeException failure) {
        throw new SqlitePublicationCapabilityWitness.AcquisitionFailure(
            checkedRequirement, failure);
      }
    }
    entries.sort(Comparator.comparing(MutableEntry::key));
    return new SqlitePublicationCapabilityWitnessPlan(
        entries.stream().map(MutableEntry::freeze).toList());
  }

  /** Returns every unique witness key in deterministic acquisition order. */
  List<Entry> entries() {
    return entries;
  }

  /**
   * Returns every target that shares the selected witness's physical parent.
   *
   * <p>This scope exists only while acquiring the parent-directory lease. It is intentionally
   * broader than one primitive's final-publication authority: callers must retain the matching
   * entry's {@link Entry#exactTargetPaths()} for that later authorization check.
   */
  List<Path> sameParentTargetPaths(SqlitePublicationCapabilityWitnessKey selectedKey) {
    SqlitePublicationCapabilityWitnessKey checkedKey =
        Objects.requireNonNull(selectedKey, "selectedKey");
    List<Path> targets = new ArrayList<>();
    for (Entry entry : entries) {
      if (entry.key().parentFingerprint().equals(checkedKey.parentFingerprint())) {
        entry.requirements().stream()
            .map(SqlitePublicationCapabilityWitness.Requirement::targetPath)
            .forEach(targets::add);
      }
    }
    return List.copyOf(targets);
  }

  /** One immutable witness key and every requirement that shares that exact key. */
  record Entry(
      SqlitePublicationCapabilityWitnessKey key,
      List<SqlitePublicationCapabilityWitness.Requirement> requirements) {
    Entry {
      Objects.requireNonNull(key, "key");
      requirements = List.copyOf(requirements);
      if (requirements.isEmpty()) {
        throw new IllegalArgumentException("A publication-capability witness plan entry is empty.");
      }
    }

    /** Returns the exact targets authorized to use this entry's one primitive. */
    List<Path> exactTargetPaths() {
      return requirements.stream()
          .map(SqlitePublicationCapabilityWitness.Requirement::targetPath)
          .toList();
    }
  }

  /** Mutable collection state used only while one plan is being built. */
  private static final class MutableEntry {
    private final SqlitePublicationCapabilityWitnessKey key;
    private final List<SqlitePublicationCapabilityWitness.Requirement> requirements =
        new ArrayList<>();

    private MutableEntry(SqlitePublicationCapabilityWitnessKey key) {
      this.key = Objects.requireNonNull(key, "key");
    }

    private SqlitePublicationCapabilityWitnessKey key() {
      return key;
    }

    private List<SqlitePublicationCapabilityWitness.Requirement> requirements() {
      return requirements;
    }

    private Entry freeze() {
      return new Entry(key, requirements);
    }
  }

  private static MutableEntry entryFor(
      List<MutableEntry> entries, SqlitePublicationCapabilityWitnessKey key) {
    for (MutableEntry entry : entries) {
      if (entry.key().equals(key)) {
        return entry;
      }
    }
    MutableEntry entry = new MutableEntry(key);
    entries.add(entry);
    return entry;
  }
}
