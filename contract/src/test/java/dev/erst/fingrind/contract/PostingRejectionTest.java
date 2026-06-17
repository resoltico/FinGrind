package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.SourceDocumentType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostingRejection}. */
class PostingRejectionTest {
  @Test
  void wireCode_isStableForEverySubtype() {
    assertEquals(
        List.of(
            "posting-book-not-initialized",
            "entry-semantics-violations",
            "account-state-violations",
            "duplicate-idempotency-key",
            "book-functional-currency-mismatch",
            "closed-period-violation",
            "open-accounting-position-window-closed",
            "open-accounting-position-touches-nominal-account",
            "result-holding-account-reserved",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
            PostingRejection.wireCode(
                new PostingRejection.EntrySemanticsViolations(
                    List.of(
                        PostingRejection.accountTypeMismatch(
                            "CASH_REVENUE",
                            "cashAccountCode",
                            new AccountCode("1000"),
                            AccountType.ASSET,
                            AccountType.REVENUE)))),
            PostingRejection.wireCode(
                new PostingRejection.AccountStateViolations(
                    List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))))),
            PostingRejection.wireCode(new PostingRejection.DuplicateIdempotencyKey()),
            PostingRejection.wireCode(
                new PostingRejection.BookFunctionalCurrencyMismatch(
                    CurrencyUnit.of("EUR"), CurrencyUnit.of("USD"))),
            PostingRejection.wireCode(
                new PostingRejection.TransferredPeriodResultViolation(
                    java.time.LocalDate.parse("2026-04-30"),
                    java.time.LocalDate.parse("2026-05-01"))),
            PostingRejection.wireCode(
                new PostingRejection.OpenAccountingPositionWindowClosed(
                    PostingKind.STANDARD, java.time.LocalDate.parse("2026-05-02"))),
            PostingRejection.wireCode(
                new PostingRejection.OpenAccountingPositionTouchesNominalAccount(
                    new AccountCode("4000"), AccountType.REVENUE)),
            PostingRejection.wireCode(
                new PostingRejection.ResultHoldingAccountReserved(new AccountCode("3000"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalAlreadyExists(new PostingId("posting-2"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-3")))));
  }

  @Test
  void accountStateViolationWireCode_isStableForEverySubtype() {
    assertEquals(
        List.of("unknown-account", "inactive-account", "non-postable-account"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.UnknownAccount(new AccountCode("1000"))),
            PostingRejection.wireCode(
                new PostingRejection.InactiveAccount(new AccountCode("2000"))),
            PostingRejection.wireCode(
                new PostingRejection.NonPostableAccount(
                    new AccountCode("3000"), dev.erst.fingrind.core.AccountNodeKind.HEADER))));
  }

  @Test
  void descriptors_areStableAndComplete() {
    assertEquals(
        List.of(
            "posting-book-not-initialized",
            "entry-semantics-violations",
            "account-state-violations",
            "duplicate-idempotency-key",
            "book-functional-currency-mismatch",
            "closed-period-violation",
            "open-accounting-position-window-closed",
            "open-accounting-position-touches-nominal-account",
            "result-holding-account-reserved",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        PostingRejection.descriptors().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
  }

  @Test
  void bookNotInitializedCode_matchesTheCanonicalDescriptor() {
    assertEquals(
        PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
        PostingRejection.bookNotInitializedCode());
  }

  @Test
  void rejectionFactories_exposeStableEntrySemanticsDetails() {
    PostingRejection.EntrySemanticsViolation accountTypeViolation =
        PostingRejection.accountTypeMismatch(
            "CASH_REVENUE",
            "cashAccountCode",
            new AccountCode("1000"),
            AccountType.ASSET,
            AccountType.REVENUE);
    assertEquals("account-type-mismatch", accountTypeViolation.code());
    assertEquals("cashAccountCode", accountTypeViolation.field());

    PostingRejection.EntrySemanticsViolation classificationViolation =
        PostingRejection.financialPositionClassificationMismatch(
            "EQUITY_CONTRIBUTION",
            "equityAccountCode",
            new AccountCode("3000"),
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
            null);
    assertEquals("financial-position-classification-mismatch", classificationViolation.code());
    assertEquals("equityAccountCode", classificationViolation.field());
    assertTrue(classificationViolation.message().contains("<absent>"));

    PostingRejection.EntrySemanticsViolation classifiedMismatch =
        PostingRejection.financialPositionClassificationMismatch(
            "EQUITY_WITHDRAWAL",
            "equityAccountCode",
            new AccountCode("3100"),
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL,
            FinancialPositionLineClassification.RESULT_HOLDING);
    assertTrue(classifiedMismatch.message().contains("RESULT_HOLDING"));

    PostingRejection.EntrySemanticsViolation evidenceViolation =
        PostingRejection.sourceDocumentTypeNotAccepted(
            "CASH_EXPENSE",
            new SourceDocumentType("invoice"),
            List.of("expense-receipt", "cash-disbursement"));
    assertEquals("source-document-type-not-accepted", evidenceViolation.code());
    assertEquals("evidence.sourceDocuments[].sourceDocumentType", evidenceViolation.field());
    assertTrue(evidenceViolation.message().contains("expense-receipt, cash-disbursement"));

    PostingRejection.EntrySemanticsViolation distinctRoleAccountsViolation =
        PostingRejection.distinctRoleAccountsRequired(
            "CASH_REVENUE", "cashAccountCode", "revenueAccountCode", new AccountCode("1000"));
    assertEquals("distinct-role-accounts-required", distinctRoleAccountsViolation.code());
    assertEquals(null, distinctRoleAccountsViolation.field());
    assertTrue(distinctRoleAccountsViolation.message().contains("cashAccountCode"));
    assertTrue(distinctRoleAccountsViolation.message().contains("revenueAccountCode"));
    assertTrue(distinctRoleAccountsViolation.message().contains("1000"));

    assertIterableEquals(
        List.of(new AccountCode("1000"), new AccountCode("2000")),
        List.copyOf(
            PostingRejection.referencedAccountSet(
                new AccountCode("1000"), new AccountCode("1000"), new AccountCode("2000"))));
    assertThrows(
        NullPointerException.class,
        () -> PostingRejection.referencedAccountSet(new AccountCode("1000"), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> PostingRejection.referencedAccountSet(NullTestSupport.<AccountCode[]>nullOf()));
  }

  @Test
  void violationFamilies_copyInputsAndRejectEmptyCollections() {
    List<PostingRejection.AccountStateViolation> accountViolations = new ArrayList<>();
    accountViolations.add(
        new PostingRejection.NonPostableAccount(new AccountCode("3000"), AccountNodeKind.HEADER));
    PostingRejection.AccountStateViolations copiedAccountViolations =
        new PostingRejection.AccountStateViolations(accountViolations);
    accountViolations.clear();
    assertEquals(1, copiedAccountViolations.violations().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostingRejection.AccountStateViolations(List.of()));

    List<PostingRejection.EntrySemanticsViolation> entryViolations = new ArrayList<>();
    entryViolations.add(
        new PostingRejection.EntrySemanticsViolation(
            "entry-semantics-code", null, "entry semantics message"));
    PostingRejection.EntrySemanticsViolations copiedEntryViolations =
        new PostingRejection.EntrySemanticsViolations(entryViolations);
    entryViolations.clear();
    assertEquals(1, copiedEntryViolations.violations().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostingRejection.EntrySemanticsViolations(List.of()));
  }

  @Test
  void entrySemanticsViolation_rejectsBlankStructuredFields() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostingRejection.EntrySemanticsViolation("", null, "message"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostingRejection.EntrySemanticsViolation("code", " ", "message"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostingRejection.EntrySemanticsViolation("code", null, ""));
  }
}
