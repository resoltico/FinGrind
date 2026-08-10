package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationCapability;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationReviewWindowException;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedPublicationPathFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.VerificationFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers verified-book review, authorization, and protected-book admission boundaries. */
class AttestationInspectionServiceVerificationTest extends AttestationInspectionServiceTestSupport {
  @Test
  void mapsKeyFileAdmissionFailuresAtEveryPublicInspectionOperation() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookDirectory = privateOutputDirectory("book");
    Path bookPath = bookDirectory.resolve("live.sqlite");
    Path keyPath = bookDirectory.resolve("live.key");
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(bookPath, List.of(genesis(credential)));
    store.rejectNormalization(
        keyPath,
        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE,
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE,
            keyPath.toAbsolutePath().normalize(),
            ProtectedPublicationPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE));
    AttestationInspectionService service = new AttestationInspectionService(CLOCK, store);
    BookAccess bookAccess =
        new BookAccess(
            bookPath,
            new BookAccess.PassphraseSource.KeyFile(keyPath),
            List.of(credential.source()));

    assertLiveKeyPathFailure(service.verifyBook(bookAccess, List.of()), keyPath);
    assertLiveKeyPathFailure(service.review(bookAccess, List.of()), keyPath);
    assertLiveKeyPathFailure(
        service.exportReceipt(bookAccess, bookDirectory.resolve("attestation.fgar")), keyPath);
    assertLiveKeyPathFailure(
        service.verifyReceipt(bookAccess, bookDirectory.resolve("attestation.fgar")), keyPath);
  }

  @Test
  void mapsLiveBookPathAdmissionFailuresIncludingVerificationTimeRechecks() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookDirectory = privateOutputDirectory("book");
    Path bookPath = bookDirectory.resolve("live.sqlite");
    BookAccess bookAccess = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);
    AttestationMaintenanceTestSupport.Store canonicalizationFailure =
        new AttestationMaintenanceTestSupport.Store(bookPath, List.of(genesis(credential)));
    canonicalizationFailure.rejectNormalization(
        bookPath,
        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            bookPath.toAbsolutePath().normalize(),
            ProtectedPublicationPathFailure.PARENT_OWNER_ONLY_REQUIRED));

    assertLiveBookPathFailure(
        new AttestationInspectionService(CLOCK, canonicalizationFailure)
            .verifyBook(bookAccess, List.of()),
        bookPath);

    AttestationMaintenanceTestSupport.Store verificationTimeFailure =
        new AttestationMaintenanceTestSupport.Store(bookPath, List.of(genesis(credential)));
    verificationTimeFailure.rejectLiveVerification(
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            bookPath.toAbsolutePath().normalize(),
            ProtectedPublicationPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE));

    assertLiveBookPathFailure(
        new AttestationInspectionService(CLOCK, verificationTimeFailure)
            .verifyBook(bookAccess, List.of()),
        bookPath);
  }

  @Test
  void treatsAnyNonInspectionMaintenanceRejectionAsAConstructionDefect() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookDirectory = privateOutputDirectory("book");
    Path bookPath = bookDirectory.resolve("live.sqlite");
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(bookPath, List.of(genesis(credential)));
    store.rejectNormalization(
        bookPath,
        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
            bookDirectory.resolve("backup.sqlite"),
            ProtectedPublicationPathFailure.PARENT_PATH_COLLISION));
    AttestationInspectionService service = new AttestationInspectionService(CLOCK, store);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                service.verifyBook(
                    AttestationMaintenanceTestSupport.bookAccess(bookPath, credential), List.of()));
    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("outside its admitted live-book access boundary"));
  }

  @Test
  void canonicalizesBookAndKeyFileAtEveryPublicInspectionBoundaryWithoutDroppingReceiptCredentials()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path requestedDirectory = privateOutputDirectory("requested-book");
    Path canonicalDirectory = privateOutputDirectory("canonical-book");
    Path requestedBookPath = requestedDirectory.resolve("live.sqlite");
    Path canonicalBookPath = canonicalDirectory.resolve("live.sqlite");
    Path requestedKeyPath = requestedDirectory.resolve("live.key");
    Path canonicalKeyPath = canonicalDirectory.resolve("live.key");
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(
            canonicalBookPath, List.of(genesis(credential)));
    store.canonicalize(requestedBookPath, canonicalBookPath);
    store.canonicalize(requestedKeyPath, canonicalKeyPath);
    BookAccess requestedAccess =
        new BookAccess(
            requestedBookPath,
            new BookAccess.PassphraseSource.KeyFile(requestedKeyPath),
            List.of(credential.source()));
    AttestationInspectionService service = new AttestationInspectionService(CLOCK, store);

    assertInstanceOf(
        VerifyBookAttestationResult.Valid.class,
        service.verifyBook(requestedAccess, List.of()).requireAccepted());
    assertInstanceOf(
        AttestationReviewResult.Valid.class,
        service.review(requestedAccess, List.of()).requireAccepted());
    Path receiptPath = canonicalDirectory.resolve("attestation.fgar");
    ExportAttestationReceiptResult.Exported exported =
        assertInstanceOf(
            ExportAttestationReceiptResult.Exported.class,
            service.exportReceipt(requestedAccess, receiptPath).requireAccepted());
    assertEquals(List.of("receipt-not-independent"), exported.warnings());
    VerifyAttestationReceiptResult.Valid verifiedReceipt =
        assertInstanceOf(
            VerifyAttestationReceiptResult.Valid.class,
            service.verifyReceipt(requestedAccess, receiptPath).requireAccepted());
    assertEquals(List.of("receipt-not-independent"), verifiedReceipt.findings());

    Path normalizedBookPath = canonicalBookPath.toAbsolutePath().normalize();
    Path normalizedKeyPath = canonicalKeyPath.toAbsolutePath().normalize();
    assertEquals(normalizedBookPath, store.verifiedBookPath());
    BookAccess.PassphraseSource.KeyFile verifiedKeySource =
        assertInstanceOf(
            BookAccess.PassphraseSource.KeyFile.class,
            store.verifiedBookAccess().passphraseSource().toPublished());
    assertEquals(normalizedKeyPath, verifiedKeySource.bookKeyFilePath());
    assertEquals(
        List.of(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE),
        store.normalizationRequests().stream()
            .map(
                AttestationMaintenanceTestSupport.MaintenanceStore.NormalizationRequest
                    ::artifactRole)
            .toList());
    assertEquals(
        List.of(
            "bookFilePath",
            "bookKeyFilePath",
            "bookFilePath",
            "bookKeyFilePath",
            "bookFilePath",
            "bookKeyFilePath",
            "bookFilePath",
            "bookKeyFilePath"),
        store.normalizationRequests().stream()
            .map(
                AttestationMaintenanceTestSupport.MaintenanceStore.NormalizationRequest
                    ::argumentName)
            .toList());
    assertTrue(
        store.normalizationRequests().stream()
            .allMatch(
                request ->
                    request.normalizationBoundary()
                        == AttestationMaintenanceTestSupport.MaintenanceStore.NormalizationBoundary
                            .OPTIONAL_ARTIFACT));
  }

  @Test
  void appliesExternalCompromiseReviewDeclarationsWithoutChangingVerifiedEvidence()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    BookAccess access = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);
    AttestationInspectionService service = service(bookPath, List.of(genesis(credential)));
    AttestationCompromiseReview review =
        new AttestationCompromiseReview(
            HexFormat.of()
                .formatHex(
                    AttestationKeyFiles.loadPublicCredential(
                            credential.source().encryptedKeyFilePath())
                        .keyId()),
            java.math.BigInteger.ZERO,
            null);

    VerifyBookAttestationResult.Valid verified =
        assertInstanceOf(
            VerifyBookAttestationResult.Valid.class,
            service.verifyBook(access, List.of(review)).requireAccepted());

    assertTrue(verified.reviewRequired());
    assertEquals(
        List.of(
            new dev.erst.fingrind.core.attestation.AttestationReviewFinding(
                review, java.math.BigInteger.ZERO)),
        verified.reviewFindings());
    AttestationReviewResult.Valid reviewed =
        assertInstanceOf(
            AttestationReviewResult.Valid.class,
            service.review(access, List.of(review)).requireAccepted());
    assertEquals(verified.reviewFindings(), reviewed.findings());
    assertEquals(verified.headOrder(), reviewed.headOrder());
    assertEquals(verified.operationHeadHex(), reviewed.operationHeadHex());
  }

  @Test
  void refusesReviewDeclarationsOutsideTheAuthenticatedHeadBeforeProjectingResults()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    BookAccess access = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);
    AttestationInspectionService service = service(bookPath, List.of(genesis(credential)));
    String credentialKeyId =
        HexFormat.of()
            .formatHex(
                AttestationKeyFiles.loadPublicCredential(credential.source().encryptedKeyFilePath())
                    .keyId());
    AttestationCompromiseReview boundedPastHead =
        new AttestationCompromiseReview(
            credentialKeyId, java.math.BigInteger.ZERO, java.math.BigInteger.ONE);

    AttestationReviewWindowException boundedFailure =
        assertThrows(
            AttestationReviewWindowException.class,
            () -> service.verifyBook(access, List.of(boundedPastHead)));
    assertEquals(boundedPastHead, boundedFailure.review());
    assertEquals(java.math.BigInteger.ZERO, boundedFailure.verifiedHeadOrder());

    AttestationCompromiseReview openPastHead =
        new AttestationCompromiseReview(credentialKeyId, java.math.BigInteger.ONE, null);
    AttestationReviewWindowException openFailure =
        assertThrows(
            AttestationReviewWindowException.class,
            () -> service.review(access, List.of(openPastHead)));
    assertEquals(openPastHead, openFailure.review());
    assertEquals(java.math.BigInteger.ZERO, openFailure.verifiedHeadOrder());
  }

  @Test
  void returnsAuthorizationRejectionAndPublishesNoReceiptWhenTheSigningQuorumIsIncomplete()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture first = credential();
    AttestationMaintenanceTestSupport.CredentialFixture second =
        AttestationMaintenanceTestSupport.createCredential(
            temporaryDirectory,
            UUID.fromString("01234567-89ab-4cde-8fab-0123456789ab"),
            "cofounder");
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    Path receiptPath = temporaryDirectory.resolve("retained/incomplete.fgar");
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(
            bookPath,
            List.of(
                AttestationMaintenanceTestSupport.genesis(List.of(first, second), RECORDED_AT)));
    BookAccess bothFounders =
        new BookAccess(
            bookPath,
            new BookAccess.PassphraseSource.KeyFile(bookPath.resolveSibling("book.key")),
            List.of(first.source(), second.source()));
    new ProtectedBookMaintenanceService(CLOCK, store)
        .mutateRegistry(
            bothFounders,
            new AttestationRegistryMutation.AlterPolicy(
                List.of(
                    new AttestationRegistryMutation.PolicyRule(AttestationCapability.ANCHOR, 2)),
                List.of(),
                List.of()))
        .requireAccepted();
    AttestationInspectionService service = new AttestationInspectionService(CLOCK, store);

    ExportAttestationReceiptResult.AuthorizationRejected rejected =
        assertInstanceOf(
            ExportAttestationReceiptResult.AuthorizationRejected.class,
            service
                .exportReceipt(
                    AttestationMaintenanceTestSupport.bookAccess(bookPath, first), receiptPath)
                .requireAccepted());

    assertEquals(AttestationVerificationFailure.QUORUM_BELOW, rejected.failure());
    assertFalse(Files.exists(receiptPath));
  }

  @Test
  void separatesStructuralInvalidityAndCredentialAdmissionFromReadableBookVerification()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    BookAccess access = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);
    AttestationInspectionService structurallyInvalid = service(bookPath, List.of());

    assertInstanceOf(
        VerifyBookAttestationResult.Invalid.class,
        structurallyInvalid.verifyBook(access, List.of()).requireAccepted());
    AttestationReviewResult.Invalid invalidReview =
        assertInstanceOf(
            AttestationReviewResult.Invalid.class,
            structurallyInvalid.review(access, List.of()).requireAccepted());
    assertEquals(
        AttestationVerificationFailure.PREIMAGE_INVALID.wireCode(), invalidReview.failureCode());

    AttestationInspectionService valid = service(bookPath, List.of(genesis(credential)));
    BookAccess credentialFreeAccess =
        new BookAccess(
            bookPath,
            new BookAccess.PassphraseSource.KeyFile(bookPath.resolveSibling("book.key")),
            List.of());
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        valid.exportReceipt(credentialFreeAccess, temporaryDirectory.resolve("receipt.fgar")));
    ExportAttestationReceiptResult.VerificationRejected invalidReceiptExport =
        assertInstanceOf(
            ExportAttestationReceiptResult.VerificationRejected.class,
            structurallyInvalid
                .exportReceipt(access, temporaryDirectory.resolve("receipt.fgar"))
                .requireAccepted());
    assertEquals(AttestationVerificationFailure.PREIMAGE_INVALID, invalidReceiptExport.failure());

    BookAccess unreadableCredentialAccess =
        new BookAccess(
            bookPath,
            new BookAccess.PassphraseSource.KeyFile(bookPath.resolveSibling("book.key")),
            List.of(
                new AttestationCredentialSource(
                    dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
                    credential.source().principalId(),
                    temporaryDirectory.resolve("missing.fgatk"),
                    temporaryDirectory.resolve("missing.passphrase"))));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        valid.exportReceipt(
            unreadableCredentialAccess, temporaryDirectory.resolve("receipt.fgar")));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        valid.exportReceipt(access, temporaryDirectory.resolve("missing-parent/receipt.fgar")));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        valid.exportReceipt(access, Path.of("/")));

    Path retainedDirectory = privateOutputDirectory("retained");
    Path receiptPath = retainedDirectory.resolve("book.fgar");
    valid.exportReceipt(access, receiptPath).requireAccepted();
    AttestationInspectionService changedBook =
        service(
            bookPath,
            List.of(
                AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT.plusSeconds(1))));
    assertInstanceOf(
        VerifyAttestationReceiptResult.Invalid.class,
        changedBook.verifyReceipt(access, receiptPath).requireAccepted());
    BookAccess rootPathAccess =
        new BookAccess(
            Path.of("/"),
            new BookAccess.PassphraseSource.KeyFile(bookPath.resolveSibling("book.key")),
            List.of());
    assertInstanceOf(
        VerifyAttestationReceiptResult.Valid.class,
        valid.verifyReceipt(rootPathAccess, receiptPath).requireAccepted());
    AttestationInspectionService malformedEvidence =
        service(bookPath, List.of(new AttestationEvidence(new byte[0], new byte[0], new byte[0])));
    assertInstanceOf(
        VerifyAttestationReceiptResult.Invalid.class,
        malformedEvidence.verifyReceipt(access, receiptPath).requireAccepted());
  }

  @Test
  void rejectsUnreadableProtectedBookStatesBeforeReadingAttestationEvidence() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    BookAccess access = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);
    AttestationMaintenanceTestSupport.Store verificationFailure =
        new AttestationMaintenanceTestSupport.Store(bookPath, List.of(genesis(credential)));
    verificationFailure.setLiveVerification(
        MaintenanceDecision.accepted(
            new VerificationFailure(bookPath, ProtectedBookVerificationFailure.MISSING)));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        new AttestationInspectionService(CLOCK, verificationFailure).verifyBook(access, List.of()));

    AttestationMaintenanceTestSupport.Store storageFailure =
        new AttestationMaintenanceTestSupport.Store(bookPath, List.of(genesis(credential)));
    storageFailure.setLiveVerification(
        MaintenanceDecision.failed(
            new MaintenanceFailure(
                ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
                "simulated protected-book storage failure",
                null,
                null,
                null,
                null)));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        new AttestationInspectionService(CLOCK, storageFailure).verifyBook(access, List.of()));
  }

  private static void assertLiveKeyPathFailure(ContractDecision<?> decision, Path expectedPath) {
    var failure = decision.requireRejected();
    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, failure.descriptor());
    assertEquals(ProtocolBookAccessOptions.BOOK_KEY_FILE, failure.argument());
    assertEquals(
        expectedPath.toAbsolutePath().normalize(),
        java.util.Objects.requireNonNull(failure.paths()).path());
  }

  private static void assertLiveBookPathFailure(ContractDecision<?> decision, Path expectedPath) {
    var failure = decision.requireRejected();
    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH, failure.descriptor());
    assertEquals(ProtocolBookAccessOptions.BOOK_FILE, failure.argument());
    assertEquals(
        expectedPath.toAbsolutePath().normalize(),
        java.util.Objects.requireNonNull(failure.paths()).path());
  }
}
