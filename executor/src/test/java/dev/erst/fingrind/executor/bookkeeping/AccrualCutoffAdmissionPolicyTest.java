package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedAccrualCutoffApplication;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Direct aggregate-admission coverage for the accrual cut-off lifecycle. */
class AccrualCutoffAdmissionPolicyTest {
  private static final AccrualCutoffAdmissionPolicy POLICY = new AccrualCutoffAdmissionPolicy();
  private static final AccountCode PREPAID_EXPENSE = new AccountCode("1410");
  private static final AccountCode EXPENSE = new AccountCode("5100");
  private static final AccountCode CASH = new AccountCode("1000");
  private static final AccountCode ACCRUED_EXPENSE = new AccountCode("2100");
  private static final AccountCode DEFERRED_REVENUE = new AccountCode("2200");
  private static final AccountCode REVENUE = new AccountCode("4000");

  @Test
  void resolve_recognitionCompletesTheAggregateOwnedJournal() {
    AccrualCutoffId cutoffId = new AccrualCutoffId("annual-insurance-2026");
    AccrualCutoffAdmissionPolicy.Resolution resolution =
        POLICY.resolve(
            recognition(cutoffId, "2026-04-15", "25.00"),
            new ValidationBook(Map.of(cutoffId, prepayment(cutoffId, "0.00", Optional.empty()))),
            "record-accrual-cutoff-recognition");

    assertTrue(resolution.rejection().isEmpty());
    AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition resolved =
        assertInstanceOf(
            AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition.class,
            resolution.entry());
    assertEquals(
        new ResolvedAccrualCutoffApplication(
            dev.erst.fingrind.core.AccrualCutoffKind.PREPAYMENT,
            dev.erst.fingrind.core.AccrualCutoffApplicationKind.RECOGNITION,
            EXPENSE,
            PREPAID_EXPENSE),
        resolved.resolvedApplication());
    assertEquals(
        new JournalEntry(
            LocalDate.parse("2026-04-15"),
            List.of(
                new JournalLine(EXPENSE, JournalLine.EntrySide.DEBIT, Money.parse("EUR", "25.00")),
                new JournalLine(
                    PREPAID_EXPENSE, JournalLine.EntrySide.CREDIT, Money.parse("EUR", "25.00")))),
        resolved.journalEntry());
  }

  @Test
  void resolve_rejectsMissingDuplicateAndInadmissibleLifecycleRequests() {
    AccrualCutoffId cutoffId = new AccrualCutoffId("annual-insurance-2026");
    ValidationBook emptyBook = new ValidationBook(Map.of());
    assertRejectionCode(
        POLICY.resolve(
            recognition(cutoffId, "2026-04-15", "25.00"),
            emptyBook,
            "record-accrual-cutoff-recognition"),
        "accrual-cutoff-not-found");

    assertRejectionCode(
        POLICY.resolve(
            new AccrualCutoffBookkeepingEntryVariants.Prepayment(
                LocalDate.parse("2026-04-07"),
                cutoffId,
                PREPAID_EXPENSE,
                EXPENSE,
                CASH,
                amount("100.00"),
                interval()),
            new ValidationBook(Map.of(cutoffId, prepayment(cutoffId, "0.00", Optional.empty()))),
            "record-prepayment"),
        "accrual-cutoff-id-already-exists");

    AccrualCutoffId accruedExpenseId = new AccrualCutoffId("contractor-fee-2026-04");
    assertRejectionCode(
        POLICY.resolve(
            recognition(accruedExpenseId, "2026-04-15", "25.00"),
            new ValidationBook(
                Map.of(
                    accruedExpenseId, accruedExpense(accruedExpenseId, "0.00", Optional.empty()))),
            "record-accrual-cutoff-recognition"),
        "accrual-cutoff-application-kind-not-admitted");
  }

  @Test
  void resolve_rejectsRecognitionOutsideIntervalBeforeHorizonAndOverApplication() {
    AccrualCutoffId cutoffId = new AccrualCutoffId("annual-insurance-2026");
    assertRejectionCode(
        POLICY.resolve(
            recognition(cutoffId, "2026-04-09", "25.00"),
            new ValidationBook(Map.of(cutoffId, prepayment(cutoffId, "0.00", Optional.empty()))),
            "record-accrual-cutoff-recognition"),
        "accrual-cutoff-application-outside-recognition-interval");
    assertRejectionCode(
        POLICY.resolve(
            recognition(cutoffId, "2026-04-14", "25.00"),
            new ValidationBook(
                Map.of(
                    cutoffId,
                    prepayment(cutoffId, "0.00", Optional.of(LocalDate.parse("2026-04-15"))))),
            "record-accrual-cutoff-recognition"),
        "accrual-cutoff-application-precedes-horizon");
    assertRejectionCode(
        POLICY.resolve(
            recognition(cutoffId, "2026-04-15", "100.01"),
            new ValidationBook(Map.of(cutoffId, prepayment(cutoffId, "0.00", Optional.empty()))),
            "record-accrual-cutoff-recognition"),
        "accrual-cutoff-application-exceeds-remaining-amount");
  }

  @Test
  void resolve_settlementCompletesAccruedExpenseLiabilityAndCashJournal() {
    AccrualCutoffId cutoffId = new AccrualCutoffId("contractor-fee-2026-04");
    AccrualCutoffAdmissionPolicy.Resolution resolution =
        POLICY.resolve(
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                LocalDate.parse("2026-04-15"), cutoffId, CASH, amount("40.00"), null),
            new ValidationBook(
                Map.of(cutoffId, accruedExpense(cutoffId, "0.00", Optional.empty()))),
            "record-accrued-expense-settlement");

    assertTrue(resolution.rejection().isEmpty());
    AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement resolved =
        assertInstanceOf(
            AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement.class,
            resolution.entry());
    assertEquals(
        new ResolvedAccrualCutoffApplication(
            dev.erst.fingrind.core.AccrualCutoffKind.ACCRUED_EXPENSE,
            dev.erst.fingrind.core.AccrualCutoffApplicationKind.SETTLEMENT,
            ACCRUED_EXPENSE,
            CASH),
        resolved.resolvedApplication());
    assertEquals(
        new JournalEntry(
            LocalDate.parse("2026-04-15"),
            List.of(
                new JournalLine(
                    ACCRUED_EXPENSE, JournalLine.EntrySide.DEBIT, Money.parse("EUR", "40.00")),
                new JournalLine(CASH, JournalLine.EntrySide.CREDIT, Money.parse("EUR", "40.00")))),
        resolved.journalEntry());
  }

  @Test
  void resolve_recognitionCompletesDeferredRevenueAndRejectsInvalidSettlementFacts() {
    AccrualCutoffId deferredRevenueId = new AccrualCutoffId("annual-support-2026");
    AccrualCutoffAdmissionPolicy.Resolution deferredRevenueResolution =
        POLICY.resolve(
            recognition(deferredRevenueId, "2026-04-15", "25.00"),
            new ValidationBook(
                Map.of(
                    deferredRevenueId,
                    deferredRevenue(deferredRevenueId, "0.00", Optional.empty()))),
            "record-accrual-cutoff-recognition");

    assertTrue(deferredRevenueResolution.rejection().isEmpty());
    AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition resolvedRecognition =
        assertInstanceOf(
            AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition.class,
            deferredRevenueResolution.entry());
    assertEquals(
        new ResolvedAccrualCutoffApplication(
            dev.erst.fingrind.core.AccrualCutoffKind.DEFERRED_REVENUE,
            dev.erst.fingrind.core.AccrualCutoffApplicationKind.RECOGNITION,
            DEFERRED_REVENUE,
            REVENUE),
        resolvedRecognition.resolvedApplication());

    AccrualCutoffId accruedExpenseId = new AccrualCutoffId("contractor-fee-2026-04");
    assertRejectionCode(
        POLICY.resolve(
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                LocalDate.parse("2026-04-15"), accruedExpenseId, CASH, amount("25.00"), null),
            new ValidationBook(Map.of()),
            "record-accrued-expense-settlement"),
        "accrual-cutoff-not-found");
    assertRejectionCode(
        POLICY.resolve(
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                LocalDate.parse("2026-04-15"), deferredRevenueId, CASH, amount("25.00"), null),
            new ValidationBook(
                Map.of(
                    deferredRevenueId,
                    deferredRevenue(deferredRevenueId, "0.00", Optional.empty()))),
            "record-accrued-expense-settlement"),
        "accrual-cutoff-application-kind-not-admitted");
    assertRejectionCode(
        POLICY.resolve(
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                LocalDate.parse("2026-04-14"), accruedExpenseId, CASH, amount("25.00"), null),
            new ValidationBook(
                Map.of(
                    accruedExpenseId,
                    accruedExpense(
                        accruedExpenseId, "0.00", Optional.of(LocalDate.parse("2026-04-15"))))),
            "record-accrued-expense-settlement"),
        "accrual-cutoff-application-precedes-horizon");
    assertRejectionCode(
        POLICY.resolve(
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                LocalDate.parse("2026-04-15"), accruedExpenseId, CASH, amount("100.01"), null),
            new ValidationBook(
                Map.of(
                    accruedExpenseId, accruedExpense(accruedExpenseId, "0.00", Optional.empty()))),
            "record-accrued-expense-settlement"),
        "accrual-cutoff-application-exceeds-remaining-amount");
  }

  @Test
  void resolve_rejectsLifecycleApplicationsInTheWrongCurrencyAndPassesThroughOtherEntries() {
    AccrualCutoffId cutoffId = new AccrualCutoffId("annual-insurance-2026");
    AccrualCutoffAdmissionPolicy.Resolution wrongCurrencyResolution =
        POLICY.resolve(
            new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
                LocalDate.parse("2026-04-15"),
                cutoffId,
                MonetaryAmount.of(Money.parse("USD", "25.00")),
                null),
            new ValidationBook(Map.of(cutoffId, prepayment(cutoffId, "0.00", Optional.empty()))),
            "record-accrual-cutoff-recognition");
    assertInstanceOf(
        BookkeepingPostingRejection.BookFunctionalCurrencyMismatch.class,
        wrongCurrencyResolution.rejection().orElseThrow());

    AccrualCutoffAdmissionPolicy.Resolution nonLifecycleResolution =
        POLICY.resolve(
            new dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-15"),
                CASH,
                new AccountCode("3000"),
                amount("25.00"),
                null),
            new ValidationBook(Map.of()),
            "record-owner-contribution");
    assertTrue(nonLifecycleResolution.rejection().isEmpty());
    assertInstanceOf(
        dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry.OwnerContribution.class,
        nonLifecycleResolution.entry());
  }

  @Test
  void resolutionRejectsAnAcceptedStateWithoutAnEntry() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AccrualCutoffAdmissionPolicy.Resolution(null, Optional.empty()));
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition(
      AccrualCutoffId cutoffId, String effectiveDate, String amount) {
    return new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
        LocalDate.parse(effectiveDate), cutoffId, amount(amount), null);
  }

  private static AccrualCutoffRecord.Prepayment prepayment(
      AccrualCutoffId cutoffId, String appliedAmount, Optional<LocalDate> latestApplicationDate) {
    return new AccrualCutoffRecord.Prepayment(
        cutoffId,
        LocalDate.parse("2026-04-07"),
        PREPAID_EXPENSE,
        EXPENSE,
        Money.parse("EUR", "100.00"),
        interval(),
        Money.parse("EUR", appliedAmount),
        latestApplicationDate);
  }

  private static AccrualCutoffRecord.AccruedExpense accruedExpense(
      AccrualCutoffId cutoffId, String appliedAmount, Optional<LocalDate> latestApplicationDate) {
    return new AccrualCutoffRecord.AccruedExpense(
        cutoffId,
        LocalDate.parse("2026-04-07"),
        ACCRUED_EXPENSE,
        EXPENSE,
        Money.parse("EUR", "100.00"),
        Money.parse("EUR", appliedAmount),
        latestApplicationDate);
  }

  private static AccrualCutoffRecord.DeferredRevenue deferredRevenue(
      AccrualCutoffId cutoffId, String appliedAmount, Optional<LocalDate> latestApplicationDate) {
    return new AccrualCutoffRecord.DeferredRevenue(
        cutoffId,
        LocalDate.parse("2026-04-07"),
        DEFERRED_REVENUE,
        REVENUE,
        Money.parse("EUR", "100.00"),
        interval(),
        Money.parse("EUR", appliedAmount),
        latestApplicationDate);
  }

  private static AccrualCutoffRecognitionInterval interval() {
    return new AccrualCutoffRecognitionInterval(
        LocalDate.parse("2026-04-10"), LocalDate.parse("2026-05-31"));
  }

  private static MonetaryAmount amount(String decimalAmount) {
    return MonetaryAmount.of(Money.parse("EUR", decimalAmount));
  }

  private static void assertRejectionCode(
      AccrualCutoffAdmissionPolicy.Resolution resolution, String expectedCode) {
    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            resolution.rejection().orElseThrow());
    assertEquals(expectedCode, rejection.violations().getFirst().code());
  }

  /** Minimal validation store for aggregate-admission tests that do not read ledger state. */
  private static final class ValidationBook implements PostingValidationStore {
    private final Map<AccrualCutoffId, AccrualCutoffRecord> cutoffsById;

    private ValidationBook(Map<AccrualCutoffId, AccrualCutoffRecord> cutoffsById) {
      this.cutoffsById = cutoffsById;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(
          1001, 1, 1, Instant.parse("2026-04-07T10:15:30Z"), bookIdentity());
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.empty();
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      return Map.of();
    }

    @Override
    public Optional<DeclaredTaxRegistration> findTaxRegistration(
        TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public Optional<AccrualCutoffRecord> findAccrualCutoff(AccrualCutoffId accrualCutoffId) {
      return Optional.ofNullable(cutoffsById.get(accrualCutoffId));
    }

    @Override
    public List<CommittedPosting> postings(
        dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
      return List.of();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }
  }
}
