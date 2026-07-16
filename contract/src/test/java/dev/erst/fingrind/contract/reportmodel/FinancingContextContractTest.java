package dev.erst.fingrind.contract.reportmodel;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFinancingApplication;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.PostingOriginKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Contract coverage for financing write facts, retained balances, and public reporting. */
class FinancingContextContractTest {
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-07-15");
  private static final AccountCode CASH = new AccountCode("1000");
  private static final AccountCode PRINCIPAL_LIABILITY = new AccountCode("2300");
  private static final AccountCode INTEREST_PAYABLE = new AccountCode("2310");
  private static final AccountCode INTEREST_EXPENSE = new AccountCode("6300");

  @Test
  void everyTypedFinancingEventDerivesOnlyItsAdmittedJournalAndOrigin() {
    ResolvedFinancingApplication resolution =
        new ResolvedFinancingApplication(PRINCIPAL_LIABILITY, INTEREST_PAYABLE);
    FinancingBookkeepingEntryVariants.Borrowing borrowing =
        new FinancingBookkeepingEntryVariants.Borrowing(
            EFFECTIVE_DATE,
            arrangementId(),
            CASH,
            PRINCIPAL_LIABILITY,
            INTEREST_PAYABLE,
            money("1000"));
    FinancingBookkeepingEntryVariants.PrincipalRepayment repayment =
        new FinancingBookkeepingEntryVariants.PrincipalRepayment(
            EFFECTIVE_DATE, arrangementId(), CASH, money("400"), resolution);
    FinancingBookkeepingEntryVariants.InterestAccrual accrual =
        new FinancingBookkeepingEntryVariants.InterestAccrual(
            EFFECTIVE_DATE, arrangementId(), INTEREST_EXPENSE, money("120"), resolution);
    FinancingBookkeepingEntryVariants.InterestPayment payment =
        new FinancingBookkeepingEntryVariants.InterestPayment(
            EFFECTIVE_DATE, arrangementId(), CASH, money("120"), resolution);

    assertEntry(
        borrowing, BookkeepingEntryKind.FINANCING_BORROWING, PostingOriginKind.FINANCING_BORROWING);
    assertEntry(
        repayment,
        BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT,
        PostingOriginKind.FINANCING_PRINCIPAL_REPAYMENT);
    assertEntry(
        accrual,
        BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL,
        PostingOriginKind.FINANCING_INTEREST_ACCRUAL);
    assertEntry(
        payment,
        BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT,
        PostingOriginKind.FINANCING_INTEREST_PAYMENT);
    assertEquals(PRINCIPAL_LIABILITY, repayment.journalEntry().lines().getFirst().accountCode());
    assertEquals(INTEREST_PAYABLE, accrual.journalEntry().lines().getLast().accountCode());
    assertEquals(INTEREST_PAYABLE, payment.journalEntry().lines().getFirst().accountCode());

    assertUnresolvedPrincipalRepayment();
    assertUnresolvedInterestAccrual();
    assertUnresolvedInterestPayment();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new FinancingBookkeepingEntryVariants.Borrowing(
                EFFECTIVE_DATE,
                arrangementId(),
                CASH,
                PRINCIPAL_LIABILITY,
                INTEREST_PAYABLE,
                money("0")));
  }

  @Test
  void financingIdentifiersAndRegisterRowsEnforceTheRetainedReconciliation() {
    FinancingRegisterRow row = registerRow();

    assertEquals("term-loan-2026-01", new FinancingArrangementId(" term-loan-2026-01 ").value());
    assertThrows(IllegalArgumentException.class, () -> new FinancingArrangementId("TERM-LOAN"));
    assertThrows(IllegalArgumentException.class, () -> new FinancingArrangementId(" "));
    assertThrows(IllegalArgumentException.class, () -> new FinancingArrangementId("a".repeat(121)));
    assertEquals(EFFECTIVE_DATE.plusMonths(2), row.lifecycleHorizon());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            registerRow(
                EFFECTIVE_DATE.minusDays(1),
                money("400"),
                money("600"),
                money("20"),
                money("100"),
                money("120")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            registerRow(
                EFFECTIVE_DATE.plusMonths(1),
                usd("400"),
                money("600"),
                money("20"),
                money("100"),
                money("120")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            registerRow(
                EFFECTIVE_DATE.plusMonths(1),
                money("399"),
                money("600"),
                money("20"),
                money("100"),
                money("120")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            registerRow(
                EFFECTIVE_DATE.plusMonths(1),
                money("400"),
                money("600"),
                money("20"),
                money("99"),
                money("120")));
    assertThrows(
        NullPointerException.class,
        () -> new ResolvedFinancingApplication(nullOf(), INTEREST_PAYABLE));
  }

  @Test
  void financingRegisterProjectsExactOutstandingAmountsAndClosedResults() {
    FinancingRegisterReport report =
        new FinancingRegisterReport(ReportModelTestSupport.bookIdentity(), List.of(registerRow()));
    ReportModel model = FinancingRegisterReportModelBuilder.INSTANCE.build(report);
    ReportCsvProjection csv = Objects.requireNonNull(model.tabularCsvProjection());
    FinancingRegisterResult.Reported reported = new FinancingRegisterResult.Reported(report);
    BookQueryRejection rejection = new BookQueryRejection.BookNotInitialized();
    FinancingRegisterResult.Rejected rejected = new FinancingRegisterResult.Rejected(rejection);

    assertEquals("financing-register", model.family());
    assertEquals(
        "term-loan-2026-01", model.sections().getFirst().rows().getFirst().cells().getFirst());
    assertEquals(
        "600", csv.rows().getFirst().get(csv.headers().indexOf("principalOutstandingMinorUnits")));
    assertSame(report, reported.reported());
    assertNull(reported.rejection());
    assertNull(rejected.reported());
    assertSame(rejection, rejected.rejection());
    assertEquals("reported", reported.fold(value -> "reported", value -> "rejected"));
    assertEquals("rejected", rejected.fold(value -> "reported", value -> "rejected"));
    assertEquals(new FinancingRegisterQuery(), new FinancingRegisterQuery());

    ReportModel empty =
        FinancingRegisterReportModelBuilder.buildModel(
            new FinancingRegisterReport(ReportModelTestSupport.bookIdentity(), List.of()));
    assertTrue(
        empty
            .sections()
            .getFirst()
            .verdicts()
            .getFirst()
            .value()
            .contains("No financing arrangements matched"));
  }

  private static void assertEntry(
      FinancingBookkeepingEntryVariants entry,
      BookkeepingEntryKind expectedEntryKind,
      PostingOriginKind expectedOriginKind) {
    assertEquals(expectedEntryKind, entry.entryKind());
    assertEquals(expectedOriginKind, entry.postingOriginKind());
    assertEquals(2, entry.journalEntry().lines().size());
  }

  private static void assertUnresolvedPrincipalRepayment() {
    FinancingBookkeepingEntryVariants.PrincipalRepayment repayment =
        new FinancingBookkeepingEntryVariants.PrincipalRepayment(
            EFFECTIVE_DATE, arrangementId(), CASH, money("400"), null);
    assertEquals(
        "principalRepayment requires executor-resolved financing facts.",
        assertThrows(IllegalStateException.class, repayment::journalEntry).getMessage());
  }

  private static void assertUnresolvedInterestAccrual() {
    FinancingBookkeepingEntryVariants.InterestAccrual accrual =
        new FinancingBookkeepingEntryVariants.InterestAccrual(
            EFFECTIVE_DATE, arrangementId(), INTEREST_EXPENSE, money("120"), null);
    assertEquals(
        "interestAccrual requires executor-resolved financing facts.",
        assertThrows(IllegalStateException.class, accrual::journalEntry).getMessage());
  }

  private static void assertUnresolvedInterestPayment() {
    FinancingBookkeepingEntryVariants.InterestPayment payment =
        new FinancingBookkeepingEntryVariants.InterestPayment(
            EFFECTIVE_DATE, arrangementId(), CASH, money("120"), null);
    assertEquals(
        "interestPayment requires executor-resolved financing facts.",
        assertThrows(IllegalStateException.class, payment::journalEntry).getMessage());
  }

  private static FinancingRegisterRow registerRow() {
    return registerRow(
        EFFECTIVE_DATE.plusMonths(2),
        money("400"),
        money("600"),
        money("20"),
        money("100"),
        money("120"));
  }

  private static FinancingRegisterRow registerRow(
      LocalDate horizon,
      MonetaryAmount principalRepaid,
      MonetaryAmount principalOutstanding,
      MonetaryAmount interestPaid,
      MonetaryAmount interestOutstanding,
      MonetaryAmount interestAccrued) {
    return new FinancingRegisterRow(
        arrangementId(),
        EFFECTIVE_DATE,
        horizon,
        PRINCIPAL_LIABILITY,
        INTEREST_PAYABLE,
        money("1000"),
        principalRepaid,
        principalOutstanding,
        interestAccrued,
        interestPaid,
        interestOutstanding);
  }

  private static FinancingArrangementId arrangementId() {
    return new FinancingArrangementId("term-loan-2026-01");
  }

  private static MonetaryAmount money(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }

  private static MonetaryAmount usd(String minorUnits) {
    return new MonetaryAmount("USD", minorUnits);
  }
}
