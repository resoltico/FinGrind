package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayroll2026;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Verifies the admitted Latvian payroll profile and its named entry-semantics refusals. */
class LatvianPayrollAdmissionPolicyTest {
  private static final LatvianPayrollMonth PAYROLL_MONTH =
      new LatvianPayrollMonth(YearMonth.of(2026, 7));
  private static final LocalDate EFFECTIVE_DATE = PAYROLL_MONTH.value().atEndOfMonth();
  private static final LatvianPayrollRunId PAYROLL_RUN_ID =
      new LatvianPayrollRunId("payroll-2026-07-employee-1");
  private static final LatvianPayrollEmployeeReference EMPLOYEE =
      new LatvianPayrollEmployeeReference("employee-1");

  private final LatvianPayrollAdmissionPolicy policy = new LatvianPayrollAdmissionPolicy();

  @Test
  void resolve_leavesNonPayrollEntriesUntouchedAndCalculatesTheAdmittedEurProfile() {
    BookkeepingEntry.ExpenseSettled ordinaryEntry =
        new BookkeepingEntry.ExpenseSettled(
            EFFECTIVE_DATE,
            new AccountCode("5000"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null);
    LatvianPayrollAdmissionPolicy.Resolution ordinaryResolution =
        policy.resolve(
            ordinaryEntry,
            new PayrollBook(bookIdentity(), Optional.empty(), Optional.empty()),
            "entry");

    assertSame(ordinaryEntry, ordinaryResolution.entry());
    LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll resolvedPayroll =
        assertInstanceOf(
            LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll.class,
            policy
                .resolve(
                    monthly(PAYROLL_MONTH, new MonetaryAmount("EUR", "200000")),
                    new PayrollBook(bookIdentity(), Optional.empty(), Optional.empty()),
                    "entry")
                .entry());
    assertEquals(
        Money.parse("EUR", "1473.80"),
        Objects.requireNonNull(resolvedPayroll.resolvedCalculation(), "resolvedCalculation")
            .netWages());
  }

  @Test
  void resolve_refusesWrongBookIdentityAndDuplicateRunOwnership() {
    BookIdentity eurBook = bookIdentity();
    BookIdentity usdBook =
        new BookIdentity(
            eurBook.entityProfile(),
            eurBook.bookDoctrine(),
            CurrencyUnit.of("USD"),
            eurBook.fiscalYearStart());
    LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll =
        monthly(PAYROLL_MONTH, new MonetaryAmount("EUR", "200000"));

    assertRejection(
        policy.resolve(
            payroll, new PayrollBook(usdBook, Optional.empty(), Optional.empty()), "entry"),
        "latvian-payroll-requires-eur-book");
    assertRejection(
        policy.resolve(
            payroll, new PayrollBook(eurBook, Optional.of(run()), Optional.empty()), "entry"),
        "latvian-payroll-run-id-already-exists");
    assertRejection(
        policy.resolve(
            payroll, new PayrollBook(eurBook, Optional.empty(), Optional.of(run())), "entry"),
        "latvian-payroll-employee-month-already-exists");
  }

  @Test
  void resolve_namesTheSpecificProfileFieldThatIsOutsideTheAdmittedModel() {
    assertRejection(
        policy.resolve(
            monthly(PAYROLL_MONTH, new MonetaryAmount("EUR", "877501")),
            new PayrollBook(bookIdentity(), Optional.empty(), Optional.empty()),
            "entry"),
        "latvian-payroll-profile-not-admitted",
        "grossWages");
    assertRejection(
        policy.resolve(
            monthly(PAYROLL_MONTH, new MonetaryAmount("USD", "200000")),
            new PayrollBook(bookIdentity(), Optional.empty(), Optional.empty()),
            "entry"),
        "latvian-payroll-profile-not-admitted",
        "grossWages.currencyCode");
    assertRejection(
        policy.resolve(
            monthly(
                new LatvianPayrollMonth(YearMonth.of(2027, 1)),
                new MonetaryAmount("EUR", "200000")),
            new PayrollBook(bookIdentity(), Optional.empty(), Optional.empty()),
            "entry"),
        "latvian-payroll-profile-not-admitted",
        "payrollMonth");
  }

  @Test
  void resolution_requiresAnEntryWheneverThereIsNoRejection() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new LatvianPayrollAdmissionPolicy.Resolution(null, Optional.empty()));
  }

  private static void assertRejection(
      LatvianPayrollAdmissionPolicy.Resolution resolution, String expectedCode) {
    assertRejection(resolution, expectedCode, null);
  }

  private static void assertRejection(
      LatvianPayrollAdmissionPolicy.Resolution resolution,
      String expectedCode,
      @Nullable String expectedField) {
    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            resolution.rejection().orElseThrow());
    BookkeepingPostingRejection.EntrySemanticsViolation violation =
        rejection.violations().getFirst();
    assertEquals(expectedCode, violation.code());
    if (expectedField != null) {
      assertEquals(expectedField, violation.field());
    }
  }

  private static LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthly(
      LatvianPayrollMonth payrollMonth, MonetaryAmount grossWages) {
    return new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
        payrollMonth.value().atEndOfMonth(),
        PAYROLL_RUN_ID,
        EMPLOYEE,
        payrollMonth,
        new AccountCode("5000"),
        new AccountCode("5010"),
        new AccountCode("2200"),
        new AccountCode("2210"),
        new AccountCode("2220"),
        new AccountCode("2230"),
        grossWages,
        null);
  }

  private static LatvianPayrollRunRecord run() {
    LatvianMonthlyPayrollCalculation calculation =
        LatvianMonthlyPayroll2026.calculate(PAYROLL_MONTH, Money.parse("EUR", "2000.00"));
    return new LatvianPayrollRunRecord(
        PAYROLL_RUN_ID,
        EMPLOYEE,
        PAYROLL_MONTH,
        EFFECTIVE_DATE,
        new AccountCode("5000"),
        new AccountCode("5010"),
        new AccountCode("2200"),
        new AccountCode("2210"),
        new AccountCode("2220"),
        new AccountCode("2230"),
        calculation,
        new PostingId("payroll-run"),
        Optional.empty());
  }

  /** Supplies the payroll run state consulted by the payroll admission policy. */
  private static final class PayrollBook extends EmptyValidationStore {
    private final BookIdentity bookIdentity;
    private final Optional<LatvianPayrollRunRecord> matchingRun;
    private final Optional<LatvianPayrollRunRecord> matchingActiveEmployeeMonthRun;

    private PayrollBook(
        BookIdentity bookIdentity,
        Optional<LatvianPayrollRunRecord> matchingRun,
        Optional<LatvianPayrollRunRecord> matchingActiveEmployeeMonthRun) {
      this.bookIdentity = bookIdentity;
      this.matchingRun = matchingRun;
      this.matchingActiveEmployeeMonthRun = matchingActiveEmployeeMonthRun;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(
          1001, 1, 1, Instant.parse("2026-08-01T00:00:00Z"), bookIdentity);
    }

    @Override
    public Optional<LatvianPayrollRunRecord> findLatvianPayrollRun(
        LatvianPayrollRunId payrollRunId) {
      return matchingRun;
    }

    @Override
    public Optional<LatvianPayrollRunRecord> findActiveLatvianPayrollRun(
        LatvianPayrollEmployeeReference employeeReference, LatvianPayrollMonth payrollMonth) {
      return matchingActiveEmployeeMonthRun;
    }
  }
}
