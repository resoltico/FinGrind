package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.PublicationPathFailure;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicy;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicyMode;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import dev.erst.fingrind.core.WireValue;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Contract tests for maintenance result, rejection, and migration-policy types. */
class BookMaintenanceContractTypesTest extends ContractTestSupport {
  private static final Map<PublicationPathFailure, String>
      EXPECTED_MAINTENANCE_PATH_FAILURE_WIRE_VALUES =
          Map.ofEntries(
              Map.entry(
                  PublicationPathFailure.MISSING_PARENT_DIRECTORY, "missing-parent-directory"),
              Map.entry(PublicationPathFailure.PARENT_PATH_COLLISION, "parent-path-collision"),
              Map.entry(
                  PublicationPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
                  "parent-owner-access-required"),
              Map.entry(
                  PublicationPathFailure.PARENT_OWNER_ONLY_REQUIRED, "parent-owner-only-required"),
              Map.entry(
                  PublicationPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
                  "artifact-must-be-regular-non-symlink-file"),
              Map.entry(
                  PublicationPathFailure.TARGET_OWNER_ONLY_REQUIRED, "target-owner-only-required"),
              Map.entry(
                  PublicationPathFailure.TARGET_IDENTITY_UNESTABLISHED,
                  "target-identity-unestablished"),
              Map.entry(
                  PublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED,
                  "source-artifact-identity-duplicated"),
              Map.entry(
                  PublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_CHANGED,
                  "source-artifact-identity-changed"),
              Map.entry(
                  PublicationPathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
                  "unsupported-secure-filesystem"),
              Map.entry(
                  PublicationPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
                  "atomic-owner-only-protocol-file-creation-unsupported"),
              Map.entry(
                  PublicationPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
                  "atomic-secret-publication-unsupported"),
              Map.entry(
                  PublicationPathFailure.ATOMIC_ARTIFACT_PUBLICATION_UNSUPPORTED,
                  "atomic-artifact-publication-unsupported"),
              Map.entry(
                  PublicationPathFailure.ATOMIC_ARTIFACT_REPLACEMENT_UNSUPPORTED,
                  "atomic-artifact-replacement-unsupported"));

  @Test
  void maintenanceRejections_publishCanonicalDescriptorsAndWireCodes() {
    Path bookFile = Path.of("books/acme.sqlite");
    Path backupFile = Path.of("backup/acme.sqlite");
    Path backupKeyFile = Path.of("backup/acme.book-key");
    List<BookMaintenanceRejection> rejections =
        List.of(
            new BookMaintenanceRejection.BookHasBlockingArtifacts(
                hint(bookFile), List.of(hint(bookFile.resolveSibling("acme.sqlite-wal")))),
            new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                hint(backupFile), List.of(hint(backupFile.resolveSibling("acme.sqlite-wal")))),
            new BookMaintenanceRejection.BackupSourceMatchesLiveBook(
                hint(bookFile), hint(backupFile)),
            new BookMaintenanceRejection.PairTargetsConflict(hint(bookFile), hint(backupKeyFile)),
            new BookMaintenanceRejection.ArtifactPathInvalid(
                BookMaintenanceArtifactRole.BACKUP_TARGET,
                hint(backupFile),
                PublicationPathFailure.PARENT_PATH_COLLISION),
            new BookMaintenanceRejection.ArtifactBusy(
                BookMaintenanceArtifactRole.LIVE_BOOK, hint(bookFile)),
            new BookMaintenanceRejection.BackupAcknowledgementConflict(UUID.randomUUID()),
            new BookMaintenanceRejection.BackupDestinationAlreadyExists(hint(backupFile)),
            new BookMaintenanceRejection.SecretTargetOccupied(hint(backupKeyFile)),
            new BookMaintenanceRejection.BookDestinationOccupied(hint(bookFile)),
            new BookMaintenanceRejection.RecoveryPending(
                OperationId.BACKUP_BOOK, hint(bookFile), hint(backupKeyFile)),
            new BookMaintenanceRejection.ArtifactVerificationFailed(
                BookMaintenanceArtifactRole.BACKUP_SOURCE,
                hint(backupFile),
                BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED));

    Map<String, RejectionDescriptor> descriptorsByCode =
        BookMaintenanceRejection.descriptors().stream()
            .collect(
                Collectors.toUnmodifiableMap(RejectionDescriptor::code, descriptor -> descriptor));

    assertEquals(12, descriptorsByCode.size());
    assertEquals(rejections.size(), descriptorsByCode.size());
    for (BookMaintenanceRejection rejection : rejections) {
      String code = BookMaintenanceRejection.wireCode(rejection);
      RejectionDescriptor descriptor = descriptorsByCode.get(code);
      assertTrue(descriptor != null, () -> "Missing descriptor for code " + code);
      assertTrue(!descriptor.description().isBlank(), () -> "Blank description for " + code);
      assertEquals(BookMaintenanceRejection.exitCode(rejection), descriptor.exitCode());
    }
    assertFalse(
        descriptorsByCode.values().stream()
            .flatMap(descriptor -> descriptor.detailFields().stream())
            .anyMatch(field -> field.description().contains("Redacted public hint")));
    assertTrue(
        descriptorsByCode.values().stream()
            .flatMap(descriptor -> descriptor.detailFields().stream())
            .filter(
                field ->
                    List.of(
                            "bookFile",
                            "backupFile",
                            "artifactPath",
                            "secretTarget",
                            "blockingArtifacts")
                        .contains(field.name()))
            .allMatch(field -> field.description().startsWith("Canonical absolute")));
    RejectionDescriptor recoveryPending =
        Objects.requireNonNull(
            descriptorsByCode.get("maintenance-recovery-pending"),
            "maintenance recovery descriptor");
    String recoveryOperationDescription =
        recoveryPending.detailFields().stream()
            .filter(field -> "recoveryOperation".equals(field.name()))
            .findFirst()
            .orElseThrow()
            .description();
    assertEquals(
        "Canonical operation identifier that must resume the retained protected-book pair publication. Closed wire vocabulary: "
            + WireValue.wireValues(OperationId.class)
            + ".",
        recoveryOperationDescription);
    RejectionDescriptor pairTargetsConflict =
        Objects.requireNonNull(
            descriptorsByCode.get("pair-targets-conflict"), "pair-target conflict descriptor");
    assertEquals(
        List.of("bookTarget", "generatedSecretTarget"),
        pairTargetsConflict.detailFields().stream().map(field -> field.name()).toList());
    RejectionDescriptor artifactPathInvalid =
        Objects.requireNonNull(
            descriptorsByCode.get("artifact-path-invalid"), "artifact path invalid descriptor");
    assertEquals(6, artifactPathInvalid.exitCode());
    assertEquals(
        "Stable protected-book path-failure code naming the specific filesystem-contract violation. Closed wire vocabulary: "
            + PublicationPathFailure.wireValues()
            + ".",
        artifactPathInvalid.detailFields().stream()
            .filter(field -> "pathFailure".equals(field.name()))
            .findFirst()
            .orElseThrow()
            .description());
    RejectionDescriptor artifactVerificationFailed =
        Objects.requireNonNull(
            descriptorsByCode.get("artifact-verification-failed"),
            "artifact verification failed descriptor");
    assertEquals(
        "Stable public verification failure code for the rejected artifact. Closed wire vocabulary: "
            + BookMaintenanceVerificationFailure.wireValues()
            + ".",
        artifactVerificationFailed.detailFields().stream()
            .filter(field -> "verificationFailure".equals(field.name()))
            .findFirst()
            .orElseThrow()
            .description());
  }

  @Test
  void maintenanceRejections_validateConstructorState() {
    Path bookFile = Path.of("books/acme.sqlite");
    Path backupFile = Path.of("backup/acme.sqlite");

    assertThrows(
        IllegalArgumentException.class,
        () -> new BookMaintenanceRejection.BookHasBlockingArtifacts(hint(bookFile), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                hint(backupFile), List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactPathInvalid(
                nullOf(), hint(backupFile), PublicationPathFailure.PARENT_PATH_COLLISION));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactPathInvalid(
                BookMaintenanceArtifactRole.BACKUP_TARGET,
                nullOf(),
                PublicationPathFailure.PARENT_PATH_COLLISION));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactPathInvalid(
                BookMaintenanceArtifactRole.BACKUP_TARGET, hint(backupFile), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.ArtifactBusy(nullOf(), hint(bookFile)));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactBusy(
                BookMaintenanceArtifactRole.LIVE_BOOK, nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BackupSourceMatchesLiveBook(nullOf(), hint(backupFile)));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BackupSourceMatchesLiveBook(hint(bookFile), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.PairTargetsConflict(nullOf(), hint(backupFile)));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.PairTargetsConflict(hint(bookFile), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BackupDestinationAlreadyExists(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.SecretTargetOccupied(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BookDestinationOccupied(nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactVerificationFailed(
                nullOf(),
                hint(backupFile),
                BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactVerificationFailed(
                BookMaintenanceArtifactRole.BACKUP_SOURCE,
                nullOf(),
                BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.ArtifactVerificationFailed(
                BookMaintenanceArtifactRole.BACKUP_SOURCE, hint(backupFile), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMaintenanceRejection.BackupAcknowledgementConflict(nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.RecoveryPending(
                nullOf(), hint(bookFile), hint(backupFile)));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.RecoveryPending(
                OperationId.RESTORE_BOOK, nullOf(), hint(backupFile)));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookMaintenanceRejection.RecoveryPending(
                OperationId.RESTORE_BOOK, hint(bookFile), nullOf()));
  }

  @Test
  void maintenanceResults_andMigrationPolicy_exposeCanonicalState() {
    assertPublishedMaintenanceResultState();
    assertMaintenanceResultValidation();
  }

  private void assertPublishedMaintenanceResultState() {
    Path bookFile = Path.of("books/acme.sqlite");
    Path backupFile = Path.of("backup/acme.sqlite");
    Path backupKeyFile = Path.of("backup/acme.book-key");
    BookMaintenanceRejection rejection =
        new BookMaintenanceRejection.BackupDestinationAlreadyExists(hint(backupFile));

    UUID backupId = UUID.randomUUID();
    BackupBookResult.BackedUp backedUp =
        new BackupBookResult.BackedUp(
            hint(bookFile),
            hint(backupFile),
            hint(backupKeyFile),
            backupId,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            pairPublication(hint(backupFile), hint(backupKeyFile)),
            BackupAcknowledgementState.ACKNOWLEDGED,
            attestationCommit());
    BackupBookResult.BackedUp alreadyPresentAcknowledgement =
        new BackupBookResult.BackedUp(
            hint(bookFile),
            hint(backupFile),
            hint(backupKeyFile),
            backupId,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            pairPublication(hint(backupFile), hint(backupKeyFile)),
            BackupAcknowledgementState.ALREADY_PRESENT,
            null);
    BackupBookResult.BackedUp resumedAcknowledgement =
        new BackupBookResult.BackedUp(
            hint(bookFile),
            hint(backupFile),
            hint(backupKeyFile),
            backupId,
            ProtectedBookPairPublicationCompletion.RECOVERED,
            pairPublication(hint(backupFile), hint(backupKeyFile)),
            BackupAcknowledgementState.RESUMED,
            attestationCommit());
    BackupBookResult.BackedUp resumedWithoutNewCommit =
        new BackupBookResult.BackedUp(
            hint(bookFile),
            hint(backupFile),
            hint(backupKeyFile),
            backupId,
            ProtectedBookPairPublicationCompletion.RECOVERED,
            pairPublication(hint(backupFile), hint(backupKeyFile)),
            BackupAcknowledgementState.RESUMED,
            null);
    BackupBookResult.AcknowledgementPending acknowledgementPending =
        new BackupBookResult.AcknowledgementPending(
            hint(bookFile),
            hint(backupFile),
            hint(backupKeyFile),
            backupId,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            pairPublication(hint(backupFile), hint(backupKeyFile)));
    BackupBookResult.AcknowledgementAuthorizationRejected acknowledgementAuthorizationRejected =
        new BackupBookResult.AcknowledgementAuthorizationRejected(
            hint(bookFile),
            hint(backupFile),
            hint(backupKeyFile),
            backupId,
            ProtectedBookPairPublicationCompletion.RECOVERED,
            pairPublication(hint(backupFile), hint(backupKeyFile)),
            AttestationVerificationFailure.QUORUM_BELOW);
    BackupBookResult.Rejected backupRejected = new BackupBookResult.Rejected(rejection);
    RestoreBookResult.Restored restored =
        new RestoreBookResult.Restored(
            hint(bookFile),
            hint(bookFile.resolveSibling("acme-restored.book-key")),
            attestationCommit(),
            ProtectedBookPairPublicationCompletion.RECOVERED,
            pairPublication(
                hint(bookFile), hint(bookFile.resolveSibling("acme-restored.book-key"))));
    RestoreBookResult.Rejected restoreRejected = new RestoreBookResult.Rejected(rejection);
    BookMigrationPolicy migrationPolicy = BookMigrationPolicy.current(9);

    assertEquals(hint(bookFile), backedUp.bookFilePath());
    assertEquals(hint(backupFile), backedUp.backupFilePath());
    assertEquals(hint(backupKeyFile), backedUp.backupBookKeyFilePath());
    assertEquals(backupId, backedUp.backupId());
    assertEquals(
        ProtectedBookPairPublicationCompletion.PUBLISHED, backedUp.pairPublicationCompletion());
    assertEquals(BackupAcknowledgementState.ACKNOWLEDGED, backedUp.acknowledgementState());
    assertEquals(
        BackupAcknowledgementState.ALREADY_PRESENT,
        alreadyPresentAcknowledgement.acknowledgementState());
    assertEquals(null, alreadyPresentAcknowledgement.attestationCommit());
    assertEquals(BackupAcknowledgementState.RESUMED, resumedAcknowledgement.acknowledgementState());
    assertEquals(attestationCommit(), resumedAcknowledgement.attestationCommit());
    assertEquals(null, resumedWithoutNewCommit.attestationCommit());
    assertEquals(backupId, acknowledgementPending.backupId());
    assertEquals(
        ProtectedBookPairPublicationCompletion.PUBLISHED,
        acknowledgementPending.pairPublicationCompletion());
    assertEquals(backupId, acknowledgementAuthorizationRejected.backupId());
    assertEquals(
        ProtectedBookPairPublicationCompletion.RECOVERED,
        acknowledgementAuthorizationRejected.pairPublicationCompletion());
    assertEquals(
        AttestationVerificationFailure.QUORUM_BELOW,
        acknowledgementAuthorizationRejected.failure());
    assertEquals(rejection, backupRejected.rejection());
    assertEquals(hint(bookFile), restored.bookFilePath());
    assertEquals(
        hint(bookFile.resolveSibling("acme-restored.book-key")), restored.bookKeyFilePath());
    assertEquals(
        ProtectedBookPairPublicationCompletion.RECOVERED, restored.pairPublicationCompletion());
    assertEquals(rejection, restoreRejected.rejection());
    assertEquals(
        BookMigrationPolicyMode.HARD_BREAK_REJECT_NONCURRENT_FORMATS, migrationPolicy.mode());
    assertEquals(9, migrationPolicy.supportedBookFormatVersion());
    assertEquals(
        List.of("hard-break-reject-noncurrent-formats"), BookMigrationPolicyMode.wireValues());
    assertEquals(
        BookMigrationPolicyMode.HARD_BREAK_REJECT_NONCURRENT_FORMATS,
        BookMigrationPolicyMode.fromWireValue("hard-break-reject-noncurrent-formats"));
  }

  private void assertMaintenanceResultValidation() {
    Path bookFile = Path.of("books/acme.sqlite");
    Path backupFile = Path.of("backup/acme.sqlite");
    Path backupKeyFile = Path.of("backup/acme.book-key");
    UUID backupId = UUID.randomUUID();
    assertThrows(
        NullPointerException.class,
        () ->
            new BackupBookResult.BackedUp(
                nullOf(),
                hint(backupFile),
                hint(backupKeyFile),
                backupId,
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                pairPublication(hint(backupFile), hint(backupKeyFile)),
                BackupAcknowledgementState.ACKNOWLEDGED,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BackupBookResult.BackedUp(
                hint(bookFile),
                nullOf(),
                hint(backupKeyFile),
                backupId,
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                pairPublication(hint(backupFile), hint(backupKeyFile)),
                BackupAcknowledgementState.ACKNOWLEDGED,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BackupBookResult.BackedUp(
                hint(bookFile),
                hint(backupFile),
                nullOf(),
                backupId,
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                pairPublication(hint(backupFile), hint(backupKeyFile)),
                BackupAcknowledgementState.ACKNOWLEDGED,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BackupBookResult.BackedUp(
                hint(bookFile),
                hint(backupFile),
                hint(backupKeyFile),
                nullOf(),
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                pairPublication(hint(backupFile), hint(backupKeyFile)),
                BackupAcknowledgementState.ACKNOWLEDGED,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BackupBookResult.BackedUp(
                hint(bookFile),
                hint(backupFile),
                hint(backupKeyFile),
                backupId,
                nullOf(),
                pairPublication(hint(backupFile), hint(backupKeyFile)),
                BackupAcknowledgementState.ACKNOWLEDGED,
                null));
    IllegalArgumentException missingFreshAcknowledgementCommit =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BackupBookResult.BackedUp(
                    hint(bookFile),
                    hint(backupFile),
                    hint(backupKeyFile),
                    backupId,
                    ProtectedBookPairPublicationCompletion.PUBLISHED,
                    pairPublication(hint(backupFile), hint(backupKeyFile)),
                    BackupAcknowledgementState.ACKNOWLEDGED,
                    null));
    assertEquals(
        "A newly acknowledged backup must report its attestation operation.",
        missingFreshAcknowledgementCommit.getMessage());
    IllegalArgumentException alreadyPresentCommit =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BackupBookResult.BackedUp(
                    hint(bookFile),
                    hint(backupFile),
                    hint(backupKeyFile),
                    backupId,
                    ProtectedBookPairPublicationCompletion.PUBLISHED,
                    pairPublication(hint(backupFile), hint(backupKeyFile)),
                    BackupAcknowledgementState.ALREADY_PRESENT,
                    attestationCommit()));
    assertEquals(
        "An already-present backup acknowledgement must not report a newly appended operation.",
        alreadyPresentCommit.getMessage());
    assertThrows(NullPointerException.class, () -> new BackupBookResult.Rejected(nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            new BackupBookResult.AcknowledgementAuthorizationRejected(
                hint(bookFile),
                hint(backupFile),
                hint(backupKeyFile),
                backupId,
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                pairPublication(hint(backupFile), hint(backupKeyFile)),
                nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            new RestoreBookResult.Restored(
                nullOf(),
                hint(bookFile.resolveSibling("acme-restored.book-key")),
                attestationCommit(),
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                pairPublication(
                    hint(bookFile), hint(bookFile.resolveSibling("acme-restored.book-key")))));
    assertThrows(
        NullPointerException.class,
        () ->
            new RestoreBookResult.Restored(
                hint(bookFile),
                nullOf(),
                attestationCommit(),
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                pairPublication(
                    hint(bookFile), hint(bookFile.resolveSibling("acme-restored.book-key")))));
    assertThrows(
        NullPointerException.class,
        () ->
            new RestoreBookResult.Restored(
                hint(bookFile),
                hint(bookFile.resolveSibling("acme-restored.book-key")),
                attestationCommit(),
                nullOf(),
                pairPublication(
                    hint(bookFile), hint(bookFile.resolveSibling("acme-restored.book-key")))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RestoreBookResult.Restored(
                hint(bookFile),
                hint(bookFile.resolveSibling("acme-restored.book-key")),
                attestationCommit(),
                ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
                nullOf()));
    assertThrows(NullPointerException.class, () -> new RestoreBookResult.Rejected(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookMigrationPolicy(nullOf(), false, false, false, 8));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookMigrationPolicy(
                BookMigrationPolicyMode.HARD_BREAK_REJECT_NONCURRENT_FORMATS,
                false,
                false,
                false,
                0));
    assertThrows(
        IllegalArgumentException.class,
        () -> BookMigrationPolicyMode.fromWireValue("unsupported-mode"));
  }

  @Test
  void completedBackupResults_enforceTheClosedCompletionAndAcknowledgementMatrix() {
    Path bookFile = hint(Path.of("books/acme.sqlite"));
    Path backupFile = hint(Path.of("backup/acme.sqlite"));
    Path backupKeyFile = hint(Path.of("backup/acme.book-key"));
    UUID backupId = UUID.randomUUID();

    for (CompletionAndAcknowledgement valid :
        List.of(
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                BackupAcknowledgementState.ACKNOWLEDGED),
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                BackupAcknowledgementState.ALREADY_PRESENT),
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.RECOVERED,
                BackupAcknowledgementState.RESUMED),
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
                BackupAcknowledgementState.RESUMED))) {
      assertValidBackedUp(bookFile, backupFile, backupKeyFile, backupId, valid);
    }

    for (CompletionAndAcknowledgement invalid :
        List.of(
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                BackupAcknowledgementState.RESUMED),
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.RECOVERED,
                BackupAcknowledgementState.ACKNOWLEDGED),
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.RECOVERED,
                BackupAcknowledgementState.ALREADY_PRESENT),
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
                BackupAcknowledgementState.ACKNOWLEDGED),
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
                BackupAcknowledgementState.ALREADY_PRESENT))) {
      assertInvalidBackedUp(bookFile, backupFile, backupKeyFile, backupId, invalid);
    }

    for (ProtectedBookPairPublicationCompletion completion :
        ProtectedBookPairPublicationCompletion.values()) {
      assertAcknowledgementPending(bookFile, backupFile, backupKeyFile, backupId, completion);
      assertAcknowledgementAuthorizationRejected(
          bookFile, backupFile, backupKeyFile, backupId, completion);
    }
  }

  @Test
  void maintenanceEnums_andPublicPathHints_publishCanonicalWireVocabulary() {
    assertEquals("acknowledged", BackupAcknowledgementState.ACKNOWLEDGED.wireValue());
    assertEquals("resumed", BackupAcknowledgementState.RESUMED.wireValue());
    assertEquals("already-present", BackupAcknowledgementState.ALREADY_PRESENT.wireValue());
    assertIterableEquals(
        List.of("published", "recovered", "already-published"),
        ProtectedBookPairPublicationCompletion.wireValues());

    assertIterableEquals(
        List.of(
            "live-book",
            "live-book-key-source",
            "backup-source",
            "backup-key-source",
            "backup-target",
            "backup-key-target",
            "restored-target",
            "new-book-key-target"),
        BookMaintenanceArtifactRole.wireValues());
    assertEquals("live-book", BookMaintenanceArtifactRole.LIVE_BOOK.wireValue());
    assertEquals(
        "live-book-key-source", BookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE.wireValue());
    assertEquals("backup-source", BookMaintenanceArtifactRole.BACKUP_SOURCE.wireValue());
    assertEquals("backup-key-source", BookMaintenanceArtifactRole.BACKUP_KEY_SOURCE.wireValue());
    assertEquals("backup-target", BookMaintenanceArtifactRole.BACKUP_TARGET.wireValue());
    assertEquals("backup-key-target", BookMaintenanceArtifactRole.BACKUP_KEY_TARGET.wireValue());
    assertEquals("restored-target", BookMaintenanceArtifactRole.RESTORED_TARGET.wireValue());
    assertEquals(
        "new-book-key-target", BookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET.wireValue());

    List<String> expectedMaintenancePathFailureWires =
        java.util.Arrays.stream(PublicationPathFailure.values())
            .map(BookMaintenanceContractTypesTest::expectedMaintenancePathFailureWireValue)
            .toList();
    assertIterableEquals(expectedMaintenancePathFailureWires, PublicationPathFailure.wireValues());
    assertFalse(
        PublicationPathFailure.wireValues().contains("target-must-be-regular-non-symlink-file"));
    for (PublicationPathFailure pathFailure : PublicationPathFailure.values()) {
      assertEquals(expectedMaintenancePathFailureWireValue(pathFailure), pathFailure.wireValue());
    }

    assertIterableEquals(
        List.of(
            "missing",
            "blank-sqlite",
            "foreign-sqlite",
            "incomplete-fingrind",
            "protected-book-verification-failed"),
        BookMaintenanceVerificationFailure.wireValues());
    assertEquals("missing", BookMaintenanceVerificationFailure.MISSING.wireValue());
    assertEquals("blank-sqlite", BookMaintenanceVerificationFailure.BLANK_SQLITE.wireValue());
    assertEquals("foreign-sqlite", BookMaintenanceVerificationFailure.FOREIGN_SQLITE.wireValue());
    assertEquals(
        "incomplete-fingrind", BookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND.wireValue());
    assertEquals(
        "protected-book-verification-failed",
        BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED.wireValue());

    assertEquals(
        "<redacted>/books/acme.sqlite", new PublicPathHint("<redacted>/books/acme.sqlite").value());
    assertEquals("<redacted>", new PublicPathHint("<redacted>").value());
    assertEquals(
        "<redacted>/contract/books/acme.sqlite",
        PublicPathHint.fromPath(Path.of("books/acme.sqlite")).value());
    assertEquals("<redacted>", PublicPathHint.fromPath(Path.of("/")).value());
    assertIterableEquals(
        List.of(
            "<redacted>/work-volume/books/main.sqlite",
            "<redacted>/backup/books/main.sqlite",
            "<redacted>/backup/secrets/main.book-key"),
        PublicPathHint.disambiguate(
                List.of(
                    Path.of("/tmp/field-audit/work-volume/books/main.sqlite"),
                    Path.of("/tmp/field-audit/work-volume/backup/books/main.sqlite"),
                    Path.of("/tmp/field-audit/work-volume/backup/secrets/main.book-key")))
            .stream()
            .map(PublicPathHint::value)
            .toList());
    assertIterableEquals(List.of(), PublicPathHint.disambiguate(List.of()));
    assertIterableEquals(
        List.of(
            "<redacted>/field-audit/books/acme.sqlite", "<redacted>/field-audit/books/acme.sqlite"),
        PublicPathHint.disambiguate(
                List.of(
                    Path.of("/tmp/field-audit/books/acme.sqlite"),
                    Path.of("/tmp/field-audit/books/acme.sqlite")))
            .stream()
            .map(PublicPathHint::value)
            .toList());
    assertIterableEquals(
        List.of("<redacted>", "<redacted>/acme.sqlite"),
        PublicPathHint.disambiguate(
                List.of(Path.of("/"), Path.of("/tmp/field-audit/books/acme.sqlite")))
            .stream()
            .map(PublicPathHint::value)
            .toList());
    assertIterableEquals(
        List.of("<redacted>/field-audit/books/acme.sqlite", "<redacted>/books/acme.sqlite"),
        PublicPathHint.disambiguate(
                List.of(
                    Path.of("/tmp/field-audit/books/acme.sqlite"), Path.of("/books/acme.sqlite")))
            .stream()
            .map(PublicPathHint::value)
            .toList());

    assertThrows(NullPointerException.class, () -> PublicPathHint.disambiguate(nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            PublicPathHint.disambiguate(
                List.of(Path.of("/tmp/field-audit/books/acme.sqlite"), nullOf())));
    assertThrows(NullPointerException.class, () -> new PublicPathHint(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> new PublicPathHint("books/acme.sqlite"));
  }

  private static String expectedMaintenancePathFailureWireValue(
      PublicationPathFailure pathFailure) {
    return Objects.requireNonNull(
        EXPECTED_MAINTENANCE_PATH_FAILURE_WIRE_VALUES.get(pathFailure), "pathFailure wire value");
  }

  private void assertValidBackedUp(
      Path bookFile,
      Path backupFile,
      Path backupKeyFile,
      UUID backupId,
      CompletionAndAcknowledgement valid) {
    assertDoesNotThrow(
        () ->
            new BackupBookResult.BackedUp(
                bookFile,
                backupFile,
                backupKeyFile,
                backupId,
                valid.completion(),
                pairPublication(valid.completion(), backupFile, backupKeyFile),
                valid.acknowledgementState(),
                valid.acknowledgementState() == BackupAcknowledgementState.ACKNOWLEDGED
                    ? attestationCommit()
                    : null));
  }

  private void assertInvalidBackedUp(
      Path bookFile,
      Path backupFile,
      Path backupKeyFile,
      UUID backupId,
      CompletionAndAcknowledgement invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BackupBookResult.BackedUp(
                bookFile,
                backupFile,
                backupKeyFile,
                backupId,
                invalid.completion(),
                pairPublication(invalid.completion(), backupFile, backupKeyFile),
                invalid.acknowledgementState(),
                null));
  }

  private void assertAcknowledgementPending(
      Path bookFile,
      Path backupFile,
      Path backupKeyFile,
      UUID backupId,
      ProtectedBookPairPublicationCompletion completion) {
    assertDoesNotThrow(
        () ->
            new BackupBookResult.AcknowledgementPending(
                bookFile,
                backupFile,
                backupKeyFile,
                backupId,
                completion,
                pairPublication(completion, backupFile, backupKeyFile)));
  }

  private void assertAcknowledgementAuthorizationRejected(
      Path bookFile,
      Path backupFile,
      Path backupKeyFile,
      UUID backupId,
      ProtectedBookPairPublicationCompletion completion) {
    assertDoesNotThrow(
        () ->
            new BackupBookResult.AcknowledgementAuthorizationRejected(
                bookFile,
                backupFile,
                backupKeyFile,
                backupId,
                completion,
                pairPublication(completion, backupFile, backupKeyFile),
                AttestationVerificationFailure.QUORUM_BELOW));
  }

  private static Path hint(Path path) {
    return path.toAbsolutePath().normalize();
  }

  private record CompletionAndAcknowledgement(
      ProtectedBookPairPublicationCompletion completion,
      BackupAcknowledgementState acknowledgementState) {}
}
