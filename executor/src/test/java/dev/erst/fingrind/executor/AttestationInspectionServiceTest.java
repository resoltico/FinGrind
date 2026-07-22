package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationCapability;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.VerificationFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers read-only attestation review and independently retained receipt lifecycle semantics. */
class AttestationInspectionServiceTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);

  @TempDir Path temporaryDirectory;

  @Test
  void verifiesReviewsExportsAndVerifiesAnIndependentlyRetainedReceipt() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    AttestationInspectionService service = service(bookPath, List.of(genesis(credential)));
    BookAccess access = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);
    Path retainedDirectory = Files.createDirectories(temporaryDirectory.resolve("retained"));
    Path receiptPath = retainedDirectory.resolve("book.fgar");

    VerifyBookAttestationResult.Valid verified =
        assertInstanceOf(
            VerifyBookAttestationResult.Valid.class,
            service.verifyBook(access, List.of()).requireAccepted());
    assertEquals(0, verified.headOrder().intValueExact());
    assertEquals(verified.bookId(), service.review(access, List.of()).requireAccepted().bookId());

    ExportAttestationReceiptResult.Exported exported =
        assertInstanceOf(
            ExportAttestationReceiptResult.Exported.class,
            service.exportReceipt(access, receiptPath).requireAccepted());
    assertEquals(List.of(), exported.warnings());
    assertTrue(Files.isRegularFile(receiptPath));
    assertInstanceOf(
        VerifyAttestationReceiptResult.Valid.class,
        service.verifyReceipt(access, receiptPath).requireAccepted());
    assertInstanceOf(
        VerifyAttestationReceiptResult.Invalid.class,
        service.verifyReceipt(access, retainedDirectory.resolve("missing.fgar")).requireAccepted());
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        service.exportReceipt(access, receiptPath));
    Files.writeString(retainedDirectory.resolve("malformed.fgar"), "not an attestation receipt");
    VerifyAttestationReceiptResult.Invalid malformed =
        assertInstanceOf(
            VerifyAttestationReceiptResult.Invalid.class,
            service
                .verifyReceipt(access, retainedDirectory.resolve("malformed.fgar"))
                .requireAccepted());
    assertEquals(
        AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID.wireCode(),
        malformed.failureCode());
    Path alteredReceiptPath = retainedDirectory.resolve("altered.fgar");
    byte[] alteredReceipt = Files.readAllBytes(receiptPath);
    alteredReceipt[alteredReceipt.length - 1] ^= 1;
    Files.write(alteredReceiptPath, alteredReceipt);
    VerifyAttestationReceiptResult.Invalid altered =
        assertInstanceOf(
            VerifyAttestationReceiptResult.Invalid.class,
            service.verifyReceipt(access, alteredReceiptPath).requireAccepted());
    assertEquals(
        AttestationVerificationFailure.SIGNATURE_INVALID.wireCode(), altered.failureCode());
    Path unreadableReceiptPath = retainedDirectory.resolve("unreadable.fgar");
    Files.writeString(unreadableReceiptPath, "unreadable receipt");
    Files.setPosixFilePermissions(unreadableReceiptPath, Set.of());
    try {
      assertInstanceOf(
          dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
          service.verifyReceipt(access, unreadableReceiptPath));
    } finally {
      Files.setPosixFilePermissions(
          unreadableReceiptPath,
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        service.exportReceipt(access, Path.of("/dev/fingrind-receipt-output.fgar")));
  }

  @Test
  void marksReceiptsStoredBesideTheBookAsNonIndependent() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookDirectory = Files.createDirectories(temporaryDirectory.resolve("book"));
    Path bookPath = bookDirectory.resolve("live.sqlite");
    AttestationInspectionService service = service(bookPath, List.of(genesis(credential)));

    ExportAttestationReceiptResult.Exported exported =
        assertInstanceOf(
            ExportAttestationReceiptResult.Exported.class,
            service
                .exportReceipt(
                    AttestationMaintenanceTestSupport.bookAccess(bookPath, credential),
                    bookDirectory.resolve("receipt.fgar"))
                .requireAccepted());

    assertEquals(List.of("receipt-not-independent"), exported.warnings());
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
    assertEquals(
        verified.reviewFindings(),
        service.review(access, List.of(review)).requireAccepted().findings());
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
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        structurallyInvalid.review(access, List.of()));

    AttestationInspectionService valid = service(bookPath, List.of(genesis(credential)));
    BookAccess credentialFreeAccess =
        new BookAccess(
            bookPath,
            new BookAccess.PassphraseSource.KeyFile(bookPath.resolveSibling("book.key")),
            List.of());
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        valid.exportReceipt(credentialFreeAccess, temporaryDirectory.resolve("receipt.fgar")));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        structurallyInvalid.exportReceipt(access, temporaryDirectory.resolve("receipt.fgar")));

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

    Path retainedDirectory = Files.createDirectories(temporaryDirectory.resolve("retained"));
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
                null)));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        new AttestationInspectionService(CLOCK, storageFailure).verifyBook(access, List.of()));
  }

  private AttestationInspectionService service(Path bookPath, List<AttestationEvidence> evidence) {
    return new AttestationInspectionService(
        CLOCK, new AttestationMaintenanceTestSupport.Store(bookPath, evidence));
  }

  private AttestationMaintenanceTestSupport.CredentialFixture credential() throws IOException {
    return AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
  }

  private static AttestationEvidence genesis(
      AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT);
  }

  @Test
  void retainsReceiptCleanupWarningsAndLeavesPrimaryFailureCleanupBestEffort() throws IOException {
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    Path receiptPath = temporaryDirectory.resolve("book/receipt.fgar");
    Path stagedDirectory = Files.createDirectory(temporaryDirectory.resolve("staged"));
    Files.writeString(stagedDirectory.resolve("retained-after-failure"), "fixture");

    assertEquals(
        List.of("receipt-not-independent", "receipt-staging-cleanup-required:" + stagedDirectory),
        AttestationReceiptOperations.publicationWarnings(
            bookPath,
            receiptPath,
            AttestationReceiptOperations.deleteStagedReceipt(stagedDirectory)));

    AttestationReceiptOperations.deleteStagedQuietly(stagedDirectory);
    assertTrue(Files.isDirectory(stagedDirectory));
    Files.delete(stagedDirectory.resolve("retained-after-failure"));
    Files.delete(stagedDirectory);
  }
}
