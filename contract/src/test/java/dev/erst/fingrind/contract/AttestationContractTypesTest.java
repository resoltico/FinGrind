package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract tests for the attested-book creation and immutable-chain inspection types. */
class AttestationContractTypesTest extends ContractTestSupport {
  private static final UUID BOOK_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final String OPERATION_HEAD =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

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
        () -> new AttestationFounderInput(BOOK_ID, Path.of("same"), Path.of("same")));
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
    AttestationReviewResult review =
        new AttestationReviewResult(BOOK_ID, BigInteger.ZERO, warnings);
    ExportAttestationReceiptResult exported =
        new ExportAttestationReceiptResult(
            Path.of("receipts", "book.fgatt"), BOOK_ID, BigInteger.ONE, OPERATION_HEAD, warnings);
    VerifyAttestationReceiptResult.Valid receipt =
        new VerifyAttestationReceiptResult.Valid(BOOK_ID, BigInteger.TWO, warnings);
    VerifyBookAttestationResult.Valid reviewedBook =
        new VerifyBookAttestationResult.Valid(BOOK_ID, BigInteger.TEN, OPERATION_HEAD, warnings);
    VerifyBookAttestationResult.Valid cleanBook =
        new VerifyBookAttestationResult.Valid(BOOK_ID, BigInteger.ZERO, OPERATION_HEAD, List.of());

    warnings.clear();

    assertEquals(List.of("review-key-rotation"), review.findings());
    assertEquals(List.of("review-key-rotation"), exported.warnings());
    assertEquals(List.of("review-key-rotation"), receipt.findings());
    assertEquals(List.of("review-key-rotation"), reviewedBook.reviewFindings());
    assertEquals(
        Path.of("receipts", "book.fgatt").toAbsolutePath().normalize(), exported.receiptFilePath());
    assertTrue(reviewedBook.reviewRequired());
    assertFalse(cleanBook.reviewRequired());
    assertEquals(
        "receipt-chain-invalid",
        new VerifyAttestationReceiptResult.Invalid(" receipt-chain-invalid ").failureCode());
    assertEquals(
        "book-chain-invalid",
        new VerifyBookAttestationResult.Invalid(" book-chain-invalid ").failureCode());

    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationReviewResult(BOOK_ID, BigInteger.ONE.negate(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationReviewResult(BOOK_ID, oversizedUnsignedOrder(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExportAttestationReceiptResult(
                Path.of("receipt"), BOOK_ID, BigInteger.ONE.negate(), OPERATION_HEAD, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExportAttestationReceiptResult(
                Path.of("receipt"), BOOK_ID, oversizedUnsignedOrder(), OPERATION_HEAD, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExportAttestationReceiptResult(
                Path.of("receipt"),
                BOOK_ID,
                BigInteger.ZERO,
                OPERATION_HEAD.toUpperCase(Locale.ROOT),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyAttestationReceiptResult.Valid(BOOK_ID, BigInteger.ONE.negate(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyAttestationReceiptResult.Valid(BOOK_ID, oversizedUnsignedOrder(), List.of()));
    assertThrows(
        IllegalArgumentException.class, () -> new VerifyAttestationReceiptResult.Invalid(" "));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                BOOK_ID, BigInteger.ONE.negate(), OPERATION_HEAD, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                BOOK_ID, oversizedUnsignedOrder(), OPERATION_HEAD, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                BOOK_ID, BigInteger.ZERO, OPERATION_HEAD.toUpperCase(Locale.ROOT), List.of()));
    assertThrows(
        IllegalArgumentException.class, () -> new VerifyBookAttestationResult.Invalid(" "));
  }

  @Test
  void
      attestationContracts_rejectNullRequiredValues_and_standardSteps_emitStandardJournalEntries() {
    assertThrows(
        NullPointerException.class,
        () -> new AttestationFounderInput(nullOf(), Path.of("key"), Path.of("passphrase")));
    assertThrows(
        NullPointerException.class,
        () -> new OpenBookCommand(nullOf(), List.of(founder("first", "first-key"))));
    assertThrows(
        NullPointerException.class,
        () -> new AttestationReviewResult(nullOf(), BigInteger.ZERO, List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new VerifyAttestationReceiptResult.Valid(nullOf(), BigInteger.ZERO, List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new VerifyBookAttestationResult.Valid(
                nullOf(), BigInteger.ZERO, OPERATION_HEAD, List.of()));

    LedgerStep step = new LedgerStep.InspectBook(new LedgerStepId("inspect-book"));

    assertEquals(LedgerStepKind.INSPECT_BOOK, step.journalStep().kind());
    assertNull(step.detailKind());
  }

  private static AttestationFounderInput founder(String principalName, String keyName) {
    return new AttestationFounderInput(
        UUID.nameUUIDFromBytes(principalName.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        Path.of("keys", keyName + ".fgatk"),
        Path.of("keys", keyName + ".passphrase"));
  }

  private static BigInteger oversizedUnsignedOrder() {
    return BigInteger.ONE.shiftLeft(Long.SIZE);
  }
}
