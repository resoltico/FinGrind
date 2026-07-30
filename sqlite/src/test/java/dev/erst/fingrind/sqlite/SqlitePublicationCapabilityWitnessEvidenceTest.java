package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Tests that retained publication evidence stays exact, complete, and identity-bound. */
class SqlitePublicationCapabilityWitnessEvidenceTest
    extends SqlitePublicationCapabilityWitnessTestFixture {
  @Test
  void retainedWitnessRejectsNoReplaceEvidenceWhoseCompletionWasReplaced() throws Exception {
    Path targetPath = tempDirectory.resolve("replaced-completion.key");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      Path completion = publicationCapabilityState(".complete");
      byte[] exactRecord = Files.readAllBytes(completion);
      Files.delete(completion);
      SqliteCoordinationControlFiles.createAtomicallySecureRecord(completion, exactRecord);

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      targetPath,
                      SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "witness failure message")
              .contains("no longer share one identity"));
    }
  }

  @Test
  void retainedWitnessRejectsAtomicReplacementEvidenceThatRetainsItsSource() throws Exception {
    Path targetPath = tempDirectory.resolve("replacement-residue.sqlite");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.atomicReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      Path completion = publicationCapabilityState(".complete");
      Path replacement =
          completion.resolveSibling(
              completion.getFileName().toString().replaceFirst("\\.complete$", ".replacement"));
      Files.writeString(replacement, "unexpected atomic replacement residue");

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "witness failure message")
              .contains("no longer complete"));
    }
  }

  @Test
  void retainedWitnessRejectsAnAtomicReplacementWhoseCompletionDisappearsBeforeConfirmation()
      throws Exception {
    Path targetPath = tempDirectory.resolve("missing-atomic-completion.sqlite");

    SqlitePublicationCapabilityWitness.AcquisitionFailure failure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(targetPath)),
                    Files::createLink,
                    (replacement, completion) -> {
                      SqliteProtectedBookPublicationSupport.moveReplacing(replacement, completion);
                      Files.delete(completion);
                    }));

    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(failure.getCause(), "atomic completion failure cause")
                    .getMessage(),
                "atomic completion failure message")
            .contains("did not retain the replacement state"));
  }

  @Test
  void retainedWitnessRejectsAnAtomicCompletionThatDisappearsAfterAcquisition() throws Exception {
    Path targetPath = tempDirectory.resolve("deleted-atomic-completion.sqlite");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.atomicReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      Files.delete(publicationCapabilityState(".complete"));

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE));

      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "witness failure message")
              .contains("no longer complete"));
    }
  }

  @Test
  void retainedWitnessRefusesPrimitivesWhoseProbeDoesNotEstablishTheRequiredIdentity()
      throws Exception {
    Path noReplaceTarget = tempDirectory.resolve("non-atomic-no-replace.key");
    SqlitePublicationCapabilityWitness.AcquisitionFailure noReplaceFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(noReplaceTarget)),
                    (completion, source) -> Files.copy(source, completion),
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(noReplaceFailure.getCause(), "no-replace failure cause")
                    .getMessage(),
                "no-replace failure message")
            .contains("does not retain the source file identity"));

    Path atomicTarget = tempDirectory.resolve("non-atomic-replacement.sqlite");
    SqlitePublicationCapabilityWitness.AcquisitionFailure atomicFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(atomicTarget)),
                    Files::createLink,
                    (source, target) ->
                        Files.copy(
                            source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(atomicFailure.getCause(), "atomic failure cause")
                    .getMessage(),
                "atomic failure message")
            .contains("did not retain the replacement state"));

    Path incompleteAtomicTarget = tempDirectory.resolve("incomplete-atomic-replacement.sqlite");
    SqlitePublicationCapabilityWitness.AcquisitionFailure incompleteAtomicFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(
                            incompleteAtomicTarget)),
                    Files::createLink,
                    (source, target) -> Files.delete(source)));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(
                        incompleteAtomicFailure.getCause(), "incomplete atomic failure cause")
                    .getMessage(),
                "incomplete atomic failure message")
            .contains("witness"));
  }

  @Test
  void retainedWitnessRejectsAtomicCompletionThatRevertedToItsPriorState() throws Exception {
    Path targetPath = tempDirectory.resolve("reverted-atomic-completion.sqlite");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.atomicReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      Path completion = publicationCapabilityState(".complete");
      Path prior = publicationCapabilityState(".prior");
      byte[] priorRecord = Files.readAllBytes(prior);
      Files.delete(completion);
      SqliteCoordinationControlFiles.createAtomicallySecureRecord(completion, priorRecord);

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "witness failure message")
              .contains("no longer complete"));
    }
  }

  @Test
  void retainedWitnessAcceptsAnExactRecordCreatedByARacingProcess() throws Exception {
    Path targetPath = tempDirectory.resolve("record-creation-race.key");
    java.util.concurrent.atomic.AtomicInteger recordCreationAttempts =
        new java.util.concurrent.atomic.AtomicInteger();

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing,
            (recordPath, magic) -> {
              SqliteCoordinationControlFiles.createAtomicallySecureRecord(recordPath, magic);
              if (recordCreationAttempts.getAndIncrement() == 0) {
                throw new FileAlreadyExistsException(recordPath.toString());
              }
            },
            ignoredParent -> {})) {
      witnesses.requireCurrent(
          targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
    }

    assertEquals(1, recordCreationAttempts.get());
  }

  @Test
  void retainedWitnessRejectsAnAtomicCompletionRemovedAfterItsDurabilityBoundary()
      throws Exception {
    Path targetPath = tempDirectory.resolve("partial-atomic-witness.sqlite");
    java.util.concurrent.atomic.AtomicInteger parentForceCount =
        new java.util.concurrent.atomic.AtomicInteger();

    SqlitePublicationCapabilityWitness.AcquisitionFailure failure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(targetPath)),
                    Files::createLink,
                    SqliteProtectedBookPublicationSupport::moveReplacing,
                    SqliteCoordinationControlFiles::createAtomicallySecureRecord,
                    ignoredParent -> {
                      if (parentForceCount.getAndIncrement() == 1) {
                        Files.delete(publicationCapabilityState(".complete"));
                      }
                    }));

    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(failure.getCause(), "partial-state failure cause")
                    .getMessage(),
                "partial-state failure message")
            .contains("impossible partial state"));
  }

  @Test
  void retainedWitnessRejectsTamperedDurableEvidenceWhenItIsReacquired() throws Exception {
    Path noReplaceParent = Files.createDirectory(tempDirectory.resolve("reacquire-no-replace"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(noReplaceParent);
    Path noReplaceTarget = noReplaceParent.resolve("book.key");
    establishWitness(
        noReplaceTarget, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
    Path source = publicationCapabilityState(noReplaceParent, ".source");
    Path completion = publicationCapabilityState(noReplaceParent, ".complete");
    byte[] sourceRecord = Files.readAllBytes(source);
    Files.delete(completion);
    SqliteCoordinationControlFiles.createAtomicallySecureRecord(completion, sourceRecord);

    SqlitePublicationCapabilityWitness.AcquisitionFailure noReplaceFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(noReplaceTarget)),
                    Files::createLink,
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(noReplaceFailure.getCause(), "no-replace failure cause")
                    .getMessage(),
                "no-replace failure message")
            .contains("does not retain the source file identity"));

    Path atomicResidueParent = Files.createDirectory(tempDirectory.resolve("reacquire-atomic"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(atomicResidueParent);
    Path atomicTarget = atomicResidueParent.resolve("book.sqlite");
    establishWitness(atomicTarget, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE);
    Path atomicCompletion = publicationCapabilityState(atomicResidueParent, ".complete");
    Path replacement =
        atomicCompletion.resolveSibling(
            atomicCompletion.getFileName().toString().replaceFirst("\\.complete$", ".replacement"));
    Files.writeString(replacement, "unexpected replacement source");

    SqlitePublicationCapabilityWitness.AcquisitionFailure atomicResidueFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(atomicTarget)),
                    Files::createLink,
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(atomicResidueFailure.getCause(), "atomic residue cause")
                    .getMessage(),
                "atomic residue message")
            .contains("impossible replacement source"));

    Path malformedParent = Files.createDirectory(tempDirectory.resolve("reacquire-malformed"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(malformedParent);
    Path malformedTarget = malformedParent.resolve("book.sqlite");
    establishWitness(
        malformedTarget, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE);
    Path malformedCompletion = publicationCapabilityState(malformedParent, ".complete");
    Files.delete(malformedCompletion);
    SqliteCoordinationControlFiles.createAtomicallySecureRecord(
        malformedCompletion,
        new byte[Files.readAllBytes(publicationCapabilityState(malformedParent, ".prior")).length]);

    SqlitePublicationCapabilityWitness.AcquisitionFailure malformedFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(
                            malformedTarget)),
                    Files::createLink,
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(malformedFailure.getCause(), "malformed failure cause")
                    .getMessage(),
                "malformed failure message")
            .contains("unexpected immutable state"));
  }

  @Test
  void retainedWitnessRejectsAParentReplacementThatLosesItsImmutableEvidence() throws Exception {
    Path originalParent = Files.createDirectory(tempDirectory.resolve("identity-parent"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(originalParent);
    Path targetPath = originalParent.resolve("book.key");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      Files.move(originalParent, tempDirectory.resolve("identity-parent-retained"));
      Files.createDirectory(originalParent);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(originalParent);

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      targetPath,
                      SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "identity failure message")
              .contains("not valid"));
    }
  }
}
