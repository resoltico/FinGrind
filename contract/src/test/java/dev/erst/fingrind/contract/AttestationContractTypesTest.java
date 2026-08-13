package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.bookkeeping.AttestationRegistryMutationResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.AdmissionContext;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import dev.erst.fingrind.core.attestation.AttestationReviewFinding;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract tests for the attested-book creation and immutable-chain inspection types. */
class AttestationContractTypesTest extends ContractTestSupport {
  private static final UUID BOOK_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final String OPERATION_HEAD =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String PREVIOUS_HEAD =
      "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
  private static final String GENESIS_PREVIOUS_HEAD = "0".repeat(64);
  private static final List<AttestationAuthorizationFailure> BOOK_CHAIN_AUTHORIZATION_FAILURES =
      List.of(
          AttestationAuthorizationFailure.UNSUPPORTED_VERSION,
          AttestationAuthorizationFailure.PREIMAGE_INVALID,
          AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID,
          AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
          AttestationAuthorizationFailure.UNKNOWN_OPERATION_KIND,
          AttestationAuthorizationFailure.ENVELOPE_ORDER_INVALID,
          AttestationAuthorizationFailure.QUORUM_BELOW,
          AttestationAuthorizationFailure.QUORUM_EXCESS,
          AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL,
          AttestationAuthorizationFailure.DUPLICATE_KEY,
          AttestationAuthorizationFailure.KEY_NOT_ENROLLED,
          AttestationAuthorizationFailure.KEY_REVOKED,
          AttestationAuthorizationFailure.KEY_SUPERSEDED,
          AttestationAuthorizationFailure.KEY_PRINCIPAL_MISMATCH,
          AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID,
          AttestationAuthorizationFailure.SIGNATURE_INVALID,
          AttestationAuthorizationFailure.CAPABILITY_INVALID,
          AttestationAuthorizationFailure.POLICY_CAPACITY_INVALID,
          AttestationAuthorizationFailure.CREDENTIAL_PURPOSE_INVALID,
          AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
          AttestationAuthorizationFailure.GENESIS_INVALID);

  @Test
  void openBookCommand_requiresAUniqueOneThroughFiveFounderSet() {
    AttestationFounderInput first = founder("first", "first-key");
    AttestationFounderInput second = founder("second", "second-key");
    OpenBookCommand command = new OpenBookCommand(bookIdentity(), List.of(first, second));

    assertEquals(List.of(first, second), command.attestationFounders());
    assertEquals(
        Path.of("keys", "first-key.fgatk").toAbsolutePath().normalize(),
        first.encryptedKeyFilePath());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationFounderInput(
                dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
                BOOK_ID,
                Path.of("same"),
                Path.of("same")));
    assertThrows(
        IllegalArgumentException.class, () -> new OpenBookCommand(bookIdentity(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OpenBookCommand(
                bookIdentity(),
                List.of(
                    founder("one", "one-key"),
                    founder("two", "two-key"),
                    founder("three", "three-key"),
                    founder("four", "four-key"),
                    founder("five", "five-key"),
                    founder("six", "six-key"))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OpenBookCommand(bookIdentity(), List.of(first, founder("first", "third-key"))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OpenBookCommand(bookIdentity(), List.of(first, founder("third", "first-key"))));
  }

  @Test
  void attestationResultTypes_validateUnsignedOrders_and_preserve_review_state() {
    List<String> warnings = new ArrayList<>(List.of("review-key-rotation"));
    AttestationReviewFinding finding =
        new AttestationReviewFinding(
            new AttestationCompromiseReview("a".repeat(64), BigInteger.ZERO, null), BigInteger.ONE);
    List<AttestationReviewFinding> reviewFindings = new ArrayList<>(List.of(finding));
    AttestationReviewResult.Valid review =
        new AttestationReviewResult.Valid(BOOK_ID, BigInteger.ONE, OPERATION_HEAD, reviewFindings);
    ExportAttestationReceiptResult.Exported exported =
        new ExportAttestationReceiptResult.Exported(
            ContractPublicationTransactionFixtures.completedArtifact(
                Path.of("receipts", "book.fgatt")),
            BOOK_ID,
            BigInteger.ONE,
            OPERATION_HEAD,
            warnings);
    ExportAttestationReceiptResult.AuthorizationRejected authorizationRejected =
        new ExportAttestationReceiptResult.AuthorizationRejected(
            AttestationVerificationFailure.QUORUM_BELOW);
    ExportAttestationReceiptResult.VerificationRejected verificationRejected =
        new ExportAttestationReceiptResult.VerificationRejected(
            AttestationVerificationFailure.PREIMAGE_INVALID);
    VerifyAttestationReceiptResult.Valid receipt =
        new VerifyAttestationReceiptResult.Valid(
            Path.of("receipts", "book.fgatt"), BOOK_ID, BigInteger.TWO, OPERATION_HEAD, warnings);
    VerifyBookAttestationResult.Valid reviewedBook =
        new VerifyBookAttestationResult.Valid(
            BOOK_ID,
            BigInteger.TEN,
            OPERATION_HEAD,
            PREVIOUS_HEAD,
            reviewFindings,
            registry(BOOK_ID, BigInteger.TEN));
    VerifyBookAttestationResult.Valid cleanBook =
        new VerifyBookAttestationResult.Valid(
            BOOK_ID,
            BigInteger.ZERO,
            OPERATION_HEAD,
            GENESIS_PREVIOUS_HEAD,
            List.of(),
            registry(BOOK_ID, BigInteger.ZERO));

    warnings.clear();
    reviewFindings.clear();

    assertEquals(List.of(finding), review.findings());
    assertEquals(OPERATION_HEAD, review.operationHeadHex());
    assertEquals(List.of("review-key-rotation"), exported.warnings());
    assertEquals(AttestationVerificationFailure.QUORUM_BELOW, authorizationRejected.failure());
    assertEquals(AttestationVerificationFailure.PREIMAGE_INVALID, verificationRejected.failure());
    assertEquals(List.of("review-key-rotation"), receipt.findings());
    assertEquals(OPERATION_HEAD, receipt.operationHeadHex());
    assertEquals(
        Path.of("receipts", "book.fgatt").toAbsolutePath().normalize(), receipt.receiptFilePath());
    assertEquals(List.of(finding), reviewedBook.reviewFindings());
    assertEquals(
        Path.of("receipts", "book.fgatt").toAbsolutePath().normalize(), exported.receiptFilePath());
    assertTrue(reviewedBook.reviewRequired());
    assertFalse(cleanBook.reviewRequired());
    assertEquals(
        AttestationVerificationFailure.RECEIPT_INVALID.wireCode(),
        new VerifyAttestationReceiptResult.Invalid(
                AttestationVerificationFailure.RECEIPT_INVALID.wireCode())
            .failureCode());
    assertEquals(
        AttestationVerificationFailure.PREIMAGE_INVALID.wireCode(),
        new VerifyBookAttestationResult.Invalid(
                AttestationVerificationFailure.PREIMAGE_INVALID.wireCode())
            .failureCode());
    assertEquals(
        AttestationVerificationFailure.PREVIOUS_HEAD_INVALID.wireCode(),
        new AttestationReviewResult.Invalid(
                AttestationVerificationFailure.PREVIOUS_HEAD_INVALID.wireCode())
            .failureCode());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationReviewResult.Valid(
                BOOK_ID, BigInteger.ONE.negate(), OPERATION_HEAD, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationReviewResult.Valid(
                BOOK_ID, oversizedUnsignedOrder(), OPERATION_HEAD, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationReviewResult.Valid(
                BOOK_ID, BigInteger.ONE, OPERATION_HEAD.toUpperCase(Locale.ROOT), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExportAttestationReceiptResult.Exported(
                ContractPublicationTransactionFixtures.completedArtifact(Path.of("receipt")),
                BOOK_ID,
                BigInteger.ONE.negate(),
                OPERATION_HEAD,
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExportAttestationReceiptResult.Exported(
                ContractPublicationTransactionFixtures.completedArtifact(Path.of("receipt")),
                BOOK_ID,
                oversizedUnsignedOrder(),
                OPERATION_HEAD,
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExportAttestationReceiptResult.Exported(
                ContractPublicationTransactionFixtures.completedArtifact(Path.of("receipt")),
                BOOK_ID,
                BigInteger.ZERO,
                OPERATION_HEAD.toUpperCase(Locale.ROOT),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyAttestationReceiptResult.Valid(
                Path.of("receipt"), BOOK_ID, BigInteger.ONE.negate(), OPERATION_HEAD, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyAttestationReceiptResult.Valid(
                Path.of("receipt"), BOOK_ID, oversizedUnsignedOrder(), OPERATION_HEAD, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyAttestationReceiptResult.Valid(
                Path.of("receipt"),
                BOOK_ID,
                BigInteger.ZERO,
                OPERATION_HEAD.toUpperCase(Locale.ROOT),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new VerifyAttestationReceiptResult.Invalid("attestation-unpublished"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                BigInteger.ONE.negate(),
                OPERATION_HEAD,
                PREVIOUS_HEAD,
                List.of(),
                registry(BOOK_ID, BigInteger.ZERO)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                oversizedUnsignedOrder(),
                OPERATION_HEAD,
                PREVIOUS_HEAD,
                List.of(),
                registry(BOOK_ID, BigInteger.ZERO)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                BigInteger.ZERO,
                OPERATION_HEAD.toUpperCase(Locale.ROOT),
                GENESIS_PREVIOUS_HEAD,
                List.of(),
                registry(BOOK_ID, BigInteger.ZERO)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                BigInteger.ONE,
                OPERATION_HEAD,
                PREVIOUS_HEAD.toUpperCase(Locale.ROOT),
                List.of(),
                registry(BOOK_ID, BigInteger.ONE)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                BigInteger.ZERO,
                OPERATION_HEAD,
                PREVIOUS_HEAD,
                List.of(),
                registry(BOOK_ID, BigInteger.ZERO)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                UUID.fromString("30213243-5465-7687-98a9-babcbddceeff"),
                BigInteger.ZERO,
                OPERATION_HEAD,
                GENESIS_PREVIOUS_HEAD,
                List.of(),
                registry(BOOK_ID, BigInteger.ZERO)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                BigInteger.ONE,
                OPERATION_HEAD,
                PREVIOUS_HEAD,
                List.of(),
                registry(BOOK_ID, BigInteger.ZERO)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                BigInteger.ONE,
                OPERATION_HEAD,
                PREVIOUS_HEAD,
                List.of(),
                registry(BOOK_ID, BigInteger.ONE, "f".repeat(64))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new VerifyBookAttestationResult.Invalid("attestation-unpublished"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationReviewResult.Invalid("attestation-unpublished"));
  }

  @Test
  void successfulAttestationResults_rejectFindingsOutsideTheVerifiedReviewScope() {
    AttestationCompromiseReview unboundedReview =
        new AttestationCompromiseReview("b".repeat(64), BigInteger.ZERO, null);
    assertSuccessfulResultsRejectInvalidFindings(
        BigInteger.valueOf(4),
        List.of(new AttestationReviewFinding(unboundedReview, BigInteger.valueOf(5))));

    AttestationCompromiseReview boundedReview =
        new AttestationCompromiseReview(
            "c".repeat(64), BigInteger.valueOf(3), BigInteger.valueOf(4));
    assertSuccessfulResultsRejectInvalidFindings(
        BigInteger.valueOf(4),
        List.of(new AttestationReviewFinding(boundedReview, BigInteger.TWO)));
    assertSuccessfulResultsRejectInvalidFindings(
        BigInteger.valueOf(5),
        List.of(new AttestationReviewFinding(boundedReview, BigInteger.valueOf(5))));

    AttestationReviewFinding first =
        new AttestationReviewFinding(boundedReview, BigInteger.valueOf(3));
    AttestationReviewFinding duplicate =
        new AttestationReviewFinding(
            new AttestationCompromiseReview(
                "c".repeat(64), BigInteger.valueOf(3), BigInteger.valueOf(4)),
            BigInteger.valueOf(3));
    assertSuccessfulResultsRejectInvalidFindings(BigInteger.valueOf(4), List.of(first, duplicate));
  }

  @Test
  void attestationResultVariants_rejectFailuresOutsideTheirPublishedResponseSurface() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Invalid(
                AttestationVerificationFailure.MANIFEST_INVALID.wireCode()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationReviewResult.Invalid(
                AttestationVerificationFailure.RECEIPT_INVALID.wireCode()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExportAttestationReceiptResult.VerificationRejected(
                AttestationVerificationFailure.RECEIPT_INVALID));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyAttestationReceiptResult.Invalid(
                AttestationVerificationFailure.MANIFEST_INVALID.wireCode()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExportAttestationReceiptResult.AuthorizationRejected(
                AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryMutationResult.AuthorizationRejected(
                AttestationVerificationFailure.MANIFEST_INVALID));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BackupBookResult.AcknowledgementAuthorizationRejected(
                Path.of("book"),
                Path.of("backup"),
                Path.of("backup-key"),
                BOOK_ID,
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                pairPublication(Path.of("backup"), Path.of("backup-key")),
                AttestationVerificationFailure.RECEIPT_INVALID));
  }

  @Test
  void attestationVerificationFailures_areAnExactPublishedVocabulary() {
    List<String> authorizationCodes =
        Arrays.stream(AttestationAuthorizationFailure.values())
            .map(AttestationAuthorizationFailure::code)
            .toList();

    assertEquals(24, AttestationVerificationFailure.values().length);
    assertEquals(
        AttestationVerificationFailure.values().length,
        Arrays.stream(AttestationVerificationFailure.values())
            .map(AttestationVerificationFailure::description)
            .distinct()
            .count());
    assertTrue(
        Arrays.stream(AttestationVerificationFailure.values())
            .map(AttestationVerificationFailure::admissionRemediation)
            .noneMatch(String::isBlank));
    assertTrue(
        Arrays.stream(AttestationVerificationFailure.values())
            .map(AttestationVerificationFailure::verificationRemediation)
            .noneMatch(String::isBlank));
    assertEquals(
        List.of(
            AdmissionContext.ORDINARY_LIVE_ADMISSION,
            AdmissionContext.REGISTRY_MUTATION,
            AdmissionContext.BACKUP_ACKNOWLEDGEMENT),
        AttestationVerificationFailure.admissionDiagnosticContexts().stream()
            .map(context -> context.context())
            .toList());
    List<String> operationAdmissionCodes = bookChainAuthorizationCodes();
    for (var context : AttestationVerificationFailure.admissionDiagnosticContexts()) {
      List<String> expectedCodes =
          context.context() == AdmissionContext.ORDINARY_LIVE_ADMISSION
              ? authorizationCodes
              : operationAdmissionCodes;
      assertEquals(
          expectedCodes,
          context.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      for (var diagnostic : context.diagnostics()) {
        assertEquals(
            diagnostic,
            AttestationVerificationFailure.fromWireCode(diagnostic.code())
                .admissionDiagnostic(context.context()));
      }
    }
    assertEquals(
        List.of(AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID),
        Arrays.stream(AttestationVerificationFailure.values())
            .filter(failure -> !authorizationCodes.contains(failure.wireCode()))
            .toList());
    assertEquals(
        AttestationVerificationFailure.SIGNATURE_INVALID,
        AttestationVerificationFailure.fromWireCode("attestation-signature-invalid"));
    assertEquals(
        AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID,
        AttestationVerificationFailure.fromWireCode("receipt-artifact-invalid"));
    assertEquals(
        "The selected receipt artifact cannot be verified.",
        AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID.description());
    assertEquals(
        "The attestation envelope provides fewer signatures than the required attestation quorum.",
        AttestationVerificationFailure.QUORUM_BELOW.description());
    assertEquals(
        "The attestation envelope provides more signatures than the required attestation quorum.",
        AttestationVerificationFailure.QUORUM_EXCESS.description());
    assertEquals(
        "The attestation registry at the resolving position does not authorize the required capability for this action.",
        AttestationVerificationFailure.CAPABILITY_INVALID.description());
    assertEquals(
        "A selected credential does not belong to its asserted principal at the resolving attestation position.",
        AttestationVerificationFailure.KEY_PRINCIPAL_MISMATCH.description());
    assertEquals(
        "The selected signing credentials provide fewer signatures than the required attestation quorum.",
        AttestationVerificationFailure.QUORUM_BELOW.admissionDescription());
    assertEquals(
        "The selected signing credentials provide more signatures than the required attestation quorum.",
        AttestationVerificationFailure.QUORUM_EXCESS.admissionDescription());
    assertEquals(
        "The live attestation registry does not authorize the required capability for this action.",
        AttestationVerificationFailure.CAPABILITY_INVALID.admissionDescription());
    assertEquals(
        "A selected credential does not belong to its asserted principal at the live book head.",
        AttestationVerificationFailure.KEY_PRINCIPAL_MISMATCH.admissionDescription());
    assertEquals(
        "Confirm that the required capability has an active policy and enough active principals with grants at the live book head, then select exactly its quorum.",
        AttestationVerificationFailure.CAPABILITY_INVALID.admissionRemediation());
    assertEquals(
        AttestationVerificationFailure.KEY_SUPERSEDED,
        AttestationVerificationFailure.fromWireCode("attestation-key-superseded"));
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationVerificationFailure.fromWireCode(" attestation-signature-invalid "));
  }

  @Test
  void attestationVerificationDiagnosticCatalogs_publishOnlyReachableFailureCodes() {
    List<String> bookChainCodes = bookChainAuthorizationCodes();

    assertEquals(
        List.of(
            OperationId.VERIFY_BOOK,
            OperationId.ATTESTATION_REVIEW,
            OperationId.EXPORT_ATTESTATION_RECEIPT,
            OperationId.VERIFY_RECEIPT),
        AttestationVerificationFailure.verificationDiagnosticSurfaces().stream()
            .map(surface -> surface.surface())
            .toList());

    for (var surface : AttestationVerificationFailure.verificationDiagnosticSurfaces()) {
      List<String> expectedCodes =
          surface.surface() == OperationId.VERIFY_RECEIPT
              ? java.util.stream.Stream.concat(
                      bookChainCodes.stream(),
                      java.util.stream.Stream.of(
                          AttestationVerificationFailure.RECEIPT_INVALID.wireCode(),
                          AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID.wireCode()))
                  .toList()
              : bookChainCodes;
      assertEquals(
          expectedCodes,
          surface.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      for (var diagnostic : surface.diagnostics()) {
        assertEquals(
            diagnostic,
            AttestationVerificationFailure.fromWireCode(diagnostic.code())
                .verificationDiagnostic(surface.surface()));
      }
    }
  }

  @Test
  void
      attestationContracts_rejectNullRequiredValues_and_standardSteps_emitStandardJournalEntries() {
    assertThrows(
        NullPointerException.class,
        () ->
            new AttestationFounderInput(
                dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
                nullOf(),
                Path.of("key"),
                Path.of("passphrase")));
    assertThrows(
        NullPointerException.class,
        () -> new OpenBookCommand(nullOf(), List.of(founder("first", "first-key"))));
    assertThrows(
        NullPointerException.class,
        () ->
            new AttestationReviewResult.Valid(
                nullOf(), BigInteger.ZERO, OPERATION_HEAD, List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new AttestationReviewResult.Valid(BOOK_ID, BigInteger.ZERO, nullOf(), List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new VerifyAttestationReceiptResult.Valid(
                Path.of("receipt"), nullOf(), BigInteger.ZERO, OPERATION_HEAD, List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new VerifyAttestationReceiptResult.Valid(
                nullOf(), BOOK_ID, BigInteger.ZERO, OPERATION_HEAD, List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                nullOf(),
                BigInteger.ZERO,
                OPERATION_HEAD,
                PREVIOUS_HEAD,
                List.of(),
                registry(BOOK_ID, BigInteger.ZERO)));

    LedgerStep step = new LedgerStep.InspectBook(new LedgerStepId("inspect-book"));

    assertEquals(LedgerStepKind.INSPECT_BOOK, step.journalStep().kind());
    assertNull(step.detailKind());
  }

  private static AttestationFounderInput founder(String principalName, String keyName) {
    return new AttestationFounderInput(
        dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
        UUID.nameUUIDFromBytes(principalName.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        Path.of("keys", keyName + ".fgatk"),
        Path.of("keys", keyName + ".passphrase"));
  }

  private static List<String> bookChainAuthorizationCodes() {
    return BOOK_CHAIN_AUTHORIZATION_FAILURES.stream()
        .map(AttestationAuthorizationFailure::code)
        .toList();
  }

  private static void assertSuccessfulResultsRejectInvalidFindings(
      BigInteger headOrder, List<AttestationReviewFinding> findings) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationReviewResult.Valid(BOOK_ID, headOrder, OPERATION_HEAD, findings));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                headOrder,
                OPERATION_HEAD,
                previousHeadFor(headOrder),
                findings,
                registry(BOOK_ID, headOrder)));
  }

  private static BigInteger oversizedUnsignedOrder() {
    return BigInteger.ONE.shiftLeft(Long.SIZE);
  }

  private static AttestationRegistryInspection registry(UUID bookId, BigInteger headOrder) {
    return registry(bookId, headOrder, OPERATION_HEAD);
  }

  private static AttestationRegistryInspection registry(
      UUID bookId, BigInteger headOrder, String operationHeadHex) {
    return new AttestationRegistryInspection(
        bookId, headOrder, operationHeadHex, List.of(), List.of(), List.of(), List.of());
  }

  private static String previousHeadFor(BigInteger headOrder) {
    return headOrder.signum() == 0 ? GENESIS_PREVIOUS_HEAD : PREVIOUS_HEAD;
  }
}
