package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused behavioral coverage for small SQLite ownership and evidence boundaries. */
class SqliteSmallBoundaryCoverageTest extends SqliteNativeBridgeTestSupport {
  @Test
  void activityRegistrationWithoutAnExternalMarkerRemainsACloseOnceLocalFact() {
    Path bookPath = tempDirectory.resolve("book.sqlite");
    SqliteNativeActivityRegistration registration =
        new SqliteNativeActivityRegistration(bookPath, "object-identity", null);

    assertEquals(bookPath, registration.diagnosticBookPath());
    assertEquals("object-identity", registration.objectIdentity());
    assertNull(registration.activityRegistration());
    assertFalse(registration.publishesActivityMarker());
    assertTrue(registration.claimClose());
    assertFalse(registration.claimClose());
    registration.releaseActivityMarker();
  }

  @Test
  void generatedSecretStageIsOwnedUntilItIsExplicitlyReleased() {
    Path finalPath = tempDirectory.resolve("book.key");
    SqliteOwnedStagedArtifact stage =
        SqliteGeneratedSecretTarget.requireAbsent(finalPath).createStage("coverage", ".stage");

    assertTrue(Files.exists(stage.stagedPath()));
    stage.releaseRetained();
    assertThrows(
        IllegalStateException.class,
        () ->
            stage.forceForPairPublicationRecoveryBoundary(
                finalPath,
                (ignoredStep, ignoredParent) -> {
                  throw new AssertionError("a released stage cannot reach the durability boundary");
                }));
  }

  @Test
  void retainedStageMaterializationFailuresPreserveTheExactRecoveryPathAndOriginalCause() {
    Path retainedPath = tempDirectory.resolve("retained.key.stage");
    IOException cause = new IOException("stage write failed");
    SqliteBookKeyFileRetainedStageMaterializationFailure failure =
        new SqliteBookKeyFileRetainedStageMaterializationFailure(
            new ArtifactPublicationRetention(retainedPath), cause);

    assertEquals(retainedPath, failure.retention().retainedStagePath());
    assertSame(cause, failure.getCause());
  }

  @Test
  void ledgerTransactionStateExposesItsInitialAndResetState() {
    SqliteLedgerPlanTransactionStateHolder holder = new SqliteLedgerPlanTransactionStateHolder();

    assertInstanceOf(NoLedgerPlanTransaction.class, holder.current());
    assertFalse(holder.active());
    holder.reset();
    assertInstanceOf(NoLedgerPlanTransaction.class, holder.current());
  }

  @Test
  void evidenceKindsExposeExhaustiveDurabilityAndMandatoryRecoveryPolicies() {
    Map<
            SqliteProtectedBookPairPublicationEvidenceKind,
            SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep>
        expectedSteps =
            Map.of(
                SqliteProtectedBookPairPublicationEvidenceKind.CLAIM,
                SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                    .PAIR_STAGE_CLAIM,
                SqliteProtectedBookPairPublicationEvidenceKind.INTENT,
                SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.RECOVERY_INTENT,
                SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY,
                SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.RECOVERY_RECORD,
                SqliteProtectedBookPairPublicationEvidenceKind.RETAINED,
                SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                    .PREPUBLICATION_RETENTION,
                SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED,
                SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                    .RECOVERY_TERMINAL_RETENTION);

    for (SqliteProtectedBookPairPublicationEvidenceKind kind :
        SqliteProtectedBookPairPublicationEvidenceKind.values()) {
      assertEquals(
          expectedSteps.get(kind), SqlitePairPublicationEvidenceStatus.durabilityStep(kind));
      assertEquals(
          kind != SqliteProtectedBookPairPublicationEvidenceKind.RETAINED
              && kind != SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED,
          kind.isMandatoryRecoveryEvidence());
    }
  }

  @Test
  void evidenceKindsRejectUnknownWireValues() {
    for (SqliteProtectedBookPairPublicationEvidenceKind kind :
        SqliteProtectedBookPairPublicationEvidenceKind.values()) {
      assertEquals(
          kind, SqliteProtectedBookPairPublicationEvidenceKind.fromWireValue(kind.wireValue()));
    }

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteProtectedBookPairPublicationEvidenceKind.fromWireValue("unknown"));

    assertEquals("Unknown pair evidence kind.", exception.getMessage());

    String malformedClaimFile =
        SqliteProtectedBookPairPublicationEvidenceKind.CLAIM
            .recordFileName(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .replace("00000000-0000-0000-0000-000000000001", "not-a-uuid");

    assertTrue(
        SqliteProtectedBookPairPublicationEvidenceKind.fromCurrentFileName(malformedClaimFile)
            .isEmpty());
  }

  @Test
  void aclSupportRejectsAFileSystemWithoutAnAclView() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath path = fileSystem.path("\\artifact.sqlite");
      path.exists = true;
      path.regularFile = true;
      path.aclView = null;

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> SqliteBookAclSupport.aclView(path));

      assertTrue(
          java.util.Objects.requireNonNull(exception.getMessage(), "ACL failure message")
              .contains("Windows owner-only ACLs"));
    }
  }
}
