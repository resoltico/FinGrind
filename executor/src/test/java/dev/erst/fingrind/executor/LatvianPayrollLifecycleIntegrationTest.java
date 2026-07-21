package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.TEST_AUTHORIZER;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Exercises the complete Latvian payroll write, settlement, reversal, register, and readback flow.
 */
class LatvianPayrollLifecycleIntegrationTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate PAYROLL_DATE = LocalDate.parse("2026-07-31");
  private static final LatvianPayrollRunId RUN_ID =
      new LatvianPayrollRunId("lv-payroll-2026-07-employee-001");
  private static final LatvianPayrollRunId SECOND_RUN_ID =
      new LatvianPayrollRunId("lv-payroll-2026-07-employee-002");
  private static final LatvianPayrollEmployeeReference EMPLOYEE =
      new LatvianPayrollEmployeeReference("employee-001");
  private static final LatvianPayrollEmployeeReference SECOND_EMPLOYEE =
      new LatvianPayrollEmployeeReference("employee-002");
  private static final LatvianPayrollMonth MONTH = LatvianPayrollMonth.parse("2026-07");
  private static final AccountCode CASH = new AccountCode("cash");
  private static final AccountCode WAGE_EXPENSE = new AccountCode("wage-expense");
  private static final AccountCode EMPLOYER_SOCIAL_EXPENSE =
      new AccountCode("employer-social-expense");
  private static final AccountCode NET_WAGES_PAYABLE = new AccountCode("net-wages-payable");
  private static final AccountCode EMPLOYEE_SOCIAL_PAYABLE =
      new AccountCode("employee-social-payable");
  private static final AccountCode EMPLOYER_SOCIAL_PAYABLE =
      new AccountCode("employer-social-payable");
  private static final AccountCode PERSONAL_INCOME_TAX_PAYABLE =
      new AccountCode("personal-income-tax-payable");

  @Test
  void payrollLifecycle_resolvesPostsReadsAndReversesEveryRetainedObligation() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declarePayrollAccounts(bookSession);
      PostingApplicationService service = postingService(bookSession);

      PostEntryResult.Committed run = commit(service, monthlyPayroll(), "payroll-run");
      assertEquals("posting-1", run.postingId().value());
      assertEquals(
          List.of(
              "wage-expense:DEBIT:2000.00",
              "employer-social-expense:DEBIT:471.80",
              "net-wages-payable:CREDIT:1473.80",
              "employee-social-payable:CREDIT:210.00",
              "employer-social-payable:CREDIT:471.80",
              "personal-income-tax-payable:CREDIT:316.20"),
          journalLines(run.resolvedJournal().expandedLines().lines()));
      assertEquals(6, run.resolvedJournal().expandedLines().lines().size());

      PostEntryResult.Committed netWages =
          commit(service, netWageSettlement(PAYROLL_DATE), "payroll-net-wages");
      PostEntryResult.Committed stateRemittance =
          commit(
              service, stateRemittance(LocalDate.parse("2026-08-05")), "payroll-state-remittance");
      PostEntryResult.Committed secondRun =
          commit(service, monthlyPayroll(SECOND_RUN_ID, SECOND_EMPLOYEE), "payroll-second-run");
      assertEquals("posting-2", netWages.postingId().value());
      assertEquals("posting-3", stateRemittance.postingId().value());
      assertEquals("posting-4", secondRun.postingId().value());

      PostEntryResult.CommitRejected duplicateSettlement =
          assertInstanceOf(
              PostEntryResult.CommitRejected.class,
              service.commit(
                  command(netWageSettlement(PAYROLL_DATE), "payroll-net-wages-duplicate"),
                  TEST_AUTHORIZER));
      assertEntrySemanticsCode(
          duplicateSettlement.rejection(), "latvian-payroll-settlement-already-exists");

      PostEntryResult.CommitRejected runReversalWithActiveSettlements =
          assertInstanceOf(
              PostEntryResult.CommitRejected.class,
              service.commit(
                  reversal(run.postingId(), LocalDate.parse("2026-08-06"), "reverse-run"),
                  TEST_AUTHORIZER));
      assertEntrySemanticsCode(
          runReversalWithActiveSettlements.rejection(),
          "latvian-payroll-run-reversal-requires-settlements-reversed");

      PostEntryResult.CommitRejected prematureSettlementReversal =
          assertInstanceOf(
              PostEntryResult.CommitRejected.class,
              service.commit(
                  reversal(
                      netWages.postingId(), LocalDate.parse("2026-07-30"), "reverse-net-before"),
                  TEST_AUTHORIZER));
      assertEntrySemanticsCode(
          prematureSettlementReversal.rejection(),
          "latvian-payroll-settlement-reversal-precedes-settlement");

      PostEntryResult.Committed netWagesReversal =
          commitReversal(
              service, netWages.postingId(), LocalDate.parse("2026-08-06"), "reverse-net");
      PostEntryResult.Committed stateRemittanceReversal =
          commitReversal(
              service, stateRemittance.postingId(), LocalDate.parse("2026-08-06"), "reverse-state");
      PostEntryResult.Committed runReversal =
          commitReversal(service, run.postingId(), LocalDate.parse("2026-08-06"), "reverse-run");

      LatvianPayrollRegisterResult.Reported register =
          assertInstanceOf(
              LatvianPayrollRegisterResult.Reported.class,
              new BookReadService(bookSession)
                  .latvianPayrollRegister(new LatvianPayrollRegisterQuery()));
      assertEquals(2, register.report().rows().size());
      var row = register.report().rows().getFirst();
      assertEquals(RUN_ID, row.payrollRunId());
      assertEquals(Money.parse("EUR", "2000.00"), row.grossWages().toMoney());
      assertEquals(Money.parse("EUR", "1473.80"), row.netWages().toMoney());
      assertEquals(Money.parse("EUR", "998.00"), row.stateRemittance().toMoney());
      assertEquals(Optional.of(runReversal.postingId()), row.reversalPostingId());
      assertEquals(2, row.settlements().size());
      assertEquals(
          LatvianPayrollSettlementKind.NET_WAGES, row.settlements().get(0).settlementKind());
      assertEquals(
          Optional.of(netWagesReversal.postingId()), row.settlements().get(0).reversalPostingId());
      assertEquals(
          LatvianPayrollSettlementKind.STATE_REMITTANCE, row.settlements().get(1).settlementKind());
      assertEquals(
          Optional.of(stateRemittanceReversal.postingId()),
          row.settlements().get(1).reversalPostingId());
      assertEquals(SECOND_RUN_ID, register.report().rows().get(1).payrollRunId());

      GetPostingResult.Found foundRun =
          assertInstanceOf(
              GetPostingResult.Found.class,
              new BookReadService(bookSession).getPosting(run.postingId()));
      LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll resolvedRun =
          assertInstanceOf(
              LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll.class,
              foundRun.postingFact().callerAuthoredEntry().orElseThrow());
      assertEquals(
          Money.parse("EUR", "1473.80"),
          Objects.requireNonNull(resolvedRun.resolvedCalculation(), "resolvedCalculation")
              .netWages());

      GetPostingResult.Found foundStateRemittance =
          assertInstanceOf(
              GetPostingResult.Found.class,
              new BookReadService(bookSession).getPosting(stateRemittance.postingId()));
      LatvianPayrollBookkeepingEntryVariants.StateRemittance resolvedStateRemittance =
          assertInstanceOf(
              LatvianPayrollBookkeepingEntryVariants.StateRemittance.class,
              foundStateRemittance.postingFact().callerAuthoredEntry().orElseThrow());
      assertEquals(
          Money.parse("EUR", "998.00"),
          Objects.requireNonNull(resolvedStateRemittance.resolvedSettlement(), "resolvedSettlement")
              .stateRemittance());
      assertTrue(foundStateRemittance.reversedByPostingId().isPresent());
    }
  }

  private static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(CLOCK.instant(), ExecutorAccountingTestSupport.bookIdentity(), List.of());
    return bookSession;
  }

  private static void declarePayrollAccounts(InMemoryBookSession bookSession) {
    declare(
        bookSession,
        CASH,
        "Cash",
        AccountType.ASSET,
        accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT));
    declare(
        bookSession,
        WAGE_EXPENSE,
        "Wage expense",
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE, NormalBalance.DEBIT));
    declare(
        bookSession,
        EMPLOYER_SOCIAL_EXPENSE,
        "Employer social contribution expense",
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE, NormalBalance.DEBIT));
    declareCurrentLiability(bookSession, NET_WAGES_PAYABLE, "Net wages payable");
    declareCurrentLiability(
        bookSession, EMPLOYEE_SOCIAL_PAYABLE, "Employee social contribution payable");
    declareCurrentLiability(
        bookSession, EMPLOYER_SOCIAL_PAYABLE, "Employer social contribution payable");
    declareCurrentLiability(
        bookSession, PERSONAL_INCOME_TAX_PAYABLE, "Personal income tax payable");
  }

  private static void declareCurrentLiability(
      InMemoryBookSession bookSession, AccountCode accountCode, String accountName) {
    declare(
        bookSession,
        accountCode,
        accountName,
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY));
  }

  private static void declare(
      InMemoryBookSession bookSession,
      AccountCode accountCode,
      String accountName,
      AccountType accountType,
      dev.erst.fingrind.core.AccountTaxonomy accountTaxonomy) {
    bookSession.declareAccount(
        accountCode, new AccountName(accountName), accountType, accountTaxonomy, CLOCK.instant());
  }

  private static PostingApplicationService postingService(InMemoryBookSession bookSession) {
    AtomicInteger sequence = new AtomicInteger();
    return new PostingApplicationService(
        bookSession,
        bookSession,
        () -> new PostingId("posting-" + sequence.incrementAndGet()),
        CLOCK);
  }

  private static LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthlyPayroll() {
    return monthlyPayroll(RUN_ID, EMPLOYEE);
  }

  private static LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthlyPayroll(
      LatvianPayrollRunId payrollRunId, LatvianPayrollEmployeeReference employeeReference) {
    return new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
        PAYROLL_DATE,
        payrollRunId,
        employeeReference,
        MONTH,
        dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile
            .taxBookWithNoDependantsFor2026(),
        WAGE_EXPENSE,
        EMPLOYER_SOCIAL_EXPENSE,
        NET_WAGES_PAYABLE,
        EMPLOYEE_SOCIAL_PAYABLE,
        EMPLOYER_SOCIAL_PAYABLE,
        PERSONAL_INCOME_TAX_PAYABLE,
        MonetaryAmount.of(Money.parse("EUR", "2000.00")),
        null);
  }

  private static LatvianPayrollBookkeepingEntryVariants.NetWageSettlement netWageSettlement(
      LocalDate effectiveDate) {
    return new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
        effectiveDate, RUN_ID, CASH, null);
  }

  private static LatvianPayrollBookkeepingEntryVariants.StateRemittance stateRemittance(
      LocalDate effectiveDate) {
    return new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
        effectiveDate, RUN_ID, CASH, null);
  }

  private static PostEntryResult.Committed commit(
      PostingApplicationService service, BookkeepingEntry entry, String token) {
    return assertInstanceOf(
        PostEntryResult.Committed.class, service.commit(command(entry, token), TEST_AUTHORIZER));
  }

  private static PostEntryResult.Committed commitReversal(
      PostingApplicationService service,
      PostingId priorPostingId,
      LocalDate effectiveDate,
      String token) {
    return assertInstanceOf(
        PostEntryResult.Committed.class,
        service.commit(reversal(priorPostingId, effectiveDate, token), TEST_AUTHORIZER));
  }

  private static PostEntryCommand reversal(
      PostingId priorPostingId, LocalDate effectiveDate, String token) {
    return command(
        new BookkeepingEntry.Reversal(
            effectiveDate,
            new PostingLineage.Reversal(
                new ReversalReference(priorPostingId), new ReversalReason("Payroll correction")),
            null,
            null),
        token);
  }

  private static PostEntryCommand command(BookkeepingEntry entry, String token) {
    return new PostEntryCommand(
        entry,
        evidence(token, sourceDocumentType(entry)),
        new RequestProvenance(
            new CommandId("payroll-command-" + token),
            new IdempotencyKey("payroll-idempotency-" + token),
            new CausationId("payroll-cause-" + token),
            Optional.empty()),
        SourceChannel.CLI);
  }

  private static AccountingEvidence evidence(String token, String sourceDocumentType) {
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId("payroll-evidence-" + token),
                new SourceDocumentType(sourceDocumentType),
                PAYROLL_DATE)),
        List.of());
  }

  private static String sourceDocumentType(BookkeepingEntry entry) {
    return switch (entry) {
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll _ -> "payroll-register";
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement _ -> "bank-payment-order";
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance _ -> "social-insurance-report";
      case BookkeepingEntry.Reversal _ -> "adjustment-support";
      default -> throw new IllegalArgumentException("Payroll lifecycle test entry required.");
    };
  }

  private static void assertEntrySemanticsCode(PostingRejection rejection, String expectedCode) {
    PostingRejection.EntrySemanticsViolations violations =
        assertInstanceOf(PostingRejection.EntrySemanticsViolations.class, rejection);
    assertEquals(expectedCode, violations.violations().getFirst().code());
  }

  private static List<String> journalLines(List<JournalLine> lines) {
    return lines.stream()
        .map(
            line ->
                line.accountCode().value()
                    + ":"
                    + line.side()
                    + ":"
                    + line.amount().money().canonicalDecimal())
        .toList();
  }
}
