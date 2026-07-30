package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Owns a partially acquired witness set until every record transfers to its final set owner. */
final class SqlitePublicationCapabilityWitnessAcquisitionResources implements AutoCloseable {
  private final List<SqlitePublicationCapabilityWitness.Set.AdmittedWitness> witnesses =
      new ArrayList<>();
  private boolean transferredOrClosed;

  void acquire(
      SqlitePublicationCapabilityWitnessPlan.Entry entry,
      List<Path> sameParentTargetPaths,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover mover,
      SqlitePublicationCapabilityWitness.SecureRecordCreator recordCreator,
      SqlitePublicationCapabilityWitness.ParentDirectoryForcer parentDirectoryForcer)
      throws IOException {
    if (transferredOrClosed) {
      throw new IllegalStateException("Publication-capability witness acquisition has completed.");
    }
    SqlitePublicationCapabilityWitness.Requirement representative = entry.requirements().getFirst();
    SqliteOwnedResourceSlot<SqlitePublicationCapabilityWitnessRecord> pendingWitness =
        SqliteOwnedResourceSlot.create(
            "publicationCapabilityWitness", SqlitePublicationCapabilityWitnessRecord::close);
    try {
      pendingWitness.hold(
          SqlitePublicationCapabilityWitnessRecord.acquire(
              entry.key(),
              representative,
              sameParentTargetPaths,
              linkCreator,
              mover,
              recordCreator,
              parentDirectoryForcer));
      SqlitePublicationCapabilityWitness.Set.AdmittedWitness admittedWitness =
          new SqlitePublicationCapabilityWitness.Set.AdmittedWitness(
              entry.key(), pendingWitness.peekRequired(), entry.exactTargetPaths());
      witnesses.add(admittedWitness);
      pendingWitness.transferToSuccessor();
    } catch (IOException | RuntimeException failure) {
      SqliteRuntimeCloseSequence.closeAllPreservingFailure(
          List.of(pendingWitness::releaseIfHeld), failure);
      throw failure;
    }
  }

  SqlitePublicationCapabilityWitness.Set transferToWitnessSet() {
    if (transferredOrClosed) {
      throw new IllegalStateException("Publication-capability witness acquisition has completed.");
    }
    SqlitePublicationCapabilityWitness.Set witnessSet =
        new SqlitePublicationCapabilityWitness.Set(witnesses);
    witnesses.clear();
    transferredOrClosed = true;
    return witnessSet;
  }

  @Override
  public void close() {
    if (transferredOrClosed) {
      return;
    }
    transferredOrClosed = true;
    try {
      SqliteRuntimeCloseSequence.closeAllReverse(
          witnesses.stream()
              .map(witness -> (SqliteRuntimeCloseSequence.CloseAction) witness.record()::close)
              .toList());
    } finally {
      witnesses.clear();
    }
  }
}
