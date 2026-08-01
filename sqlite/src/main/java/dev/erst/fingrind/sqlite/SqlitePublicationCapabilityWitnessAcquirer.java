package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Acquires deterministic, de-duplicated retained publication-capability witness records. */
final class SqlitePublicationCapabilityWitnessAcquirer {
  private SqlitePublicationCapabilityWitnessAcquirer() {}

  static SqlitePublicationCapabilityWitness.Set acquire(
      List<SqlitePublicationCapabilityWitness.Requirement> requirements,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover mover,
      SqlitePublicationCapabilityWitness.SecureRecordCreator recordCreator,
      SqlitePublicationCapabilityWitness.ParentDirectoryForcer parentDirectoryForcer)
      throws SqlitePublicationCapabilityWitness.AcquisitionFailure {
    Objects.requireNonNull(linkCreator, "linkCreator");
    Objects.requireNonNull(mover, "mover");
    Objects.requireNonNull(recordCreator, "recordCreator");
    Objects.requireNonNull(parentDirectoryForcer, "parentDirectoryForcer");
    SqlitePublicationCapabilityWitnessPlan plan =
        SqlitePublicationCapabilityWitnessPlan.create(requirements);
    try (SqlitePublicationCapabilityWitnessAcquisitionResources acquired =
        new SqlitePublicationCapabilityWitnessAcquisitionResources()) {
      for (SqlitePublicationCapabilityWitnessPlan.Entry entry : plan.entries()) {
        SqlitePublicationCapabilityWitness.Requirement representative =
            entry.requirements().getFirst();
        try {
          acquired.acquire(
              entry,
              plan.sameParentTargetPaths(entry.key()),
              linkCreator,
              mover,
              recordCreator,
              parentDirectoryForcer);
        } catch (IOException | RuntimeException failure) {
          throw acquisitionFailure(representative, failure);
        }
      }
      return acquired.transferToWitnessSet();
    }
  }

  private static SqlitePublicationCapabilityWitness.AcquisitionFailure acquisitionFailure(
      SqlitePublicationCapabilityWitness.Requirement representative, Throwable failure) {
    return new SqlitePublicationCapabilityWitness.AcquisitionFailure(representative, failure);
  }
}
