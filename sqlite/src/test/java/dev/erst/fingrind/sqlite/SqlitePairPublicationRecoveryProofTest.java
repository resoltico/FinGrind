package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Behavioral coverage for record-bound recovery proof path selection. */
class SqlitePairPublicationRecoveryProofTest extends SqliteArtifactPublicationTestSupport {
  @Test
  void proofUsesStagedMembersWhenFinalMembersAreAbsent() throws Exception {
    Fixture fixture = fixture("staged-members");
    AtomicBoolean verified = new AtomicBoolean();
    SqlitePairPublicationRecoveryProof proof =
        new SqlitePairPublicationRecoveryProof(
            (bookPath, secretPath, binding) -> {
              assertEquals(fixture.record().bookStagePath, bookPath);
              assertEquals(fixture.record().secretStagePath, secretPath);
              verified.set(true);
              return true;
            },
            (ignoredStep, ignoredParent) -> {});

    assertTrue(proof.verifiesRecordBoundPair(fixture.record()));
    assertTrue(verified.get());
  }

  @Test
  void proofRefusesChangedStagedMembersBeforeInvokingTheVerifier() throws Exception {
    Fixture changedBook = fixture("changed-book-stage");
    Files.writeString(changedBook.record().bookStagePath, "changed staged book");
    SqlitePairPublicationRecoveryProof proof =
        new SqlitePairPublicationRecoveryProof(
            (bookPath, secretPath, binding) -> {
              throw new AssertionError("A changed staged book must not reach verification.");
            },
            (ignoredStep, ignoredParent) -> {});

    assertFalse(proof.verifiesRecordBoundPair(changedBook.record()));

    Fixture changedSecret = fixture("changed-secret-stage");
    Files.writeString(changedSecret.record().secretStagePath, "changed staged secret");

    assertFalse(proof.verifiesRecordBoundPair(changedSecret.record()));
  }

  @Test
  void proofConvertsVerifierRuntimeFailureIntoAnUnverifiedResult() throws Exception {
    Fixture fixture = fixture("verifier-runtime-failure");
    IllegalStateException verifierFailure = new IllegalStateException("injected verifier failure");
    SqlitePairPublicationRecoveryProof proof =
        new SqlitePairPublicationRecoveryProof(
            (bookPath, secretPath, binding) -> {
              throw verifierFailure;
            },
            (ignoredStep, ignoredParent) -> {});

    assertFalse(proof.verifiesRecordBoundPair(fixture.record()));
  }

  private Fixture fixture(String directoryName) throws IOException {
    Path bookTarget = tempDirectory.resolve(directoryName).resolve("book.sqlite");
    Path secretTarget = tempDirectory.resolve(directoryName).resolve("book.key");
    Path bookStage = writeArtifact(directoryName + "/.book.stage", "staged protected book");
    Path secretStage = writeArtifact(directoryName + "/.secret.stage", "staged book key");
    return new Fixture(
        SqliteProtectedBookPairPublicationRecord.create(
            bookTarget,
            secretTarget,
            bookStage,
            secretStage,
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            backupBinding(bookTarget.resolveSibling("source.sqlite")),
            (ignoredStep, ignoredParent) -> {}));
  }

  private record Fixture(SqliteProtectedBookPairPublicationRecord record) {}
}
