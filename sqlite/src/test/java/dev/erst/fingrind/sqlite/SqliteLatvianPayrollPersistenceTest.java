package dev.erst.fingrind.sqlite;

import static dev.erst.fingrind.sqlite.SqlitePostingFactFixtureSupport.accountTaxonomy;
import static dev.erst.fingrind.sqlite.SqlitePostingFactFixtureSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.sqlite.SqlitePostingFactFixtureSupport.generatedEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayroll2026;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AcceptedPosting;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.spi.LatvianPayrollLookupStore;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Durable lifecycle coverage for Latvian monthly-payroll facts in one protected SQLite book. */
class SqliteLatvianPayrollPersistenceTest extends SqlitePostingFactStoreTestSupport {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-15T00:00:00Z");
  private static final AccountCode CASH = new AccountCode("payroll-cash");
  private static final AccountCode WAGE_EXPENSE = new AccountCode("payroll-wage-expense");
  private static final AccountCode EMPLOYER_SOCIAL_EXPENSE =
      new AccountCode("payroll-employer-social-expense");
  private static final AccountCode NET_WAGES_PAYABLE = new AccountCode("payroll-net-wages-payable");
  private static final AccountCode EMPLOYEE_SOCIAL_PAYABLE =
      new AccountCode("payroll-employee-social-payable");
  private static final AccountCode EMPLOYER_SOCIAL_PAYABLE =
      new AccountCode("payroll-employer-social-payable");
  private static final AccountCode PERSONAL_INCOME_TAX_PAYABLE =
      new AccountCode("payroll-personal-income-tax-payable");
  private static final LatvianPayrollRunId RUN_ID =
      new LatvianPayrollRunId("payroll-run-2026-06-employee-001");
  private static final LatvianPayrollMonth PAYROLL_MONTH = LatvianPayrollMonth.parse("2026-06");

  @Test
  void persistence_keepsPayrollRunAndSettlementFactsInTheProtectedBook() {
    Path bookPath = tempDirectory.resolve("latvian-payroll-persistence.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      openBookWithNoDeclaredAccounts(store);
      declarePayrollAccounts(store);
      try (SqliteNativeDatabase database = requireStoreDatabase(store)) {
        SqliteClosePostingPersistence persistence =
            new SqliteClosePostingPersistence(
                store.storeContext(),
                SqliteCommitFaultHook.NONE,
                PostingAcceptancePolicy.currentKernel());
        LatvianMonthlyPayrollCalculation calculation =
            LatvianMonthlyPayroll2026.calculate(
                PAYROLL_MONTH,
                Money.parse("EUR", "2000.00"),
                dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile
                    .taxBookWithNoDependantsFor2026());
        LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthlyPayroll =
            new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
                LocalDate.parse("2026-06-30"),
                RUN_ID,
                new LatvianPayrollEmployeeReference("employee-001"),
                PAYROLL_MONTH,
                dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile
                    .taxBookWithNoDependantsFor2026(),
                WAGE_EXPENSE,
                EMPLOYER_SOCIAL_EXPENSE,
                NET_WAGES_PAYABLE,
                EMPLOYEE_SOCIAL_PAYABLE,
                EMPLOYER_SOCIAL_PAYABLE,
                PERSONAL_INCOME_TAX_PAYABLE,
                MonetaryAmount.of(calculation.grossWages()),
                calculation);
        CommittedPosting runPosting =
            persist(persistence, database, monthlyPayroll, "payroll-run-posting");
        ResolvedLatvianPayrollSettlement netWageSettlementFacts =
            resolvedSettlement(LatvianPayrollSettlementKind.NET_WAGES, calculation);
        LatvianPayrollBookkeepingEntryVariants.NetWageSettlement netWageSettlement =
            new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
                LocalDate.parse("2026-07-01"), RUN_ID, CASH, netWageSettlementFacts);
        ResolvedLatvianPayrollSettlement stateRemittanceFacts =
            resolvedSettlement(LatvianPayrollSettlementKind.STATE_REMITTANCE, calculation);
        LatvianPayrollBookkeepingEntryVariants.StateRemittance stateRemittance =
            new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
                LocalDate.parse("2026-07-02"), RUN_ID, CASH, stateRemittanceFacts);

        CommittedPosting netWagePosting =
            persist(persistence, database, netWageSettlement, "payroll-net-wage-posting");
        CommittedPosting stateRemittancePosting =
            persist(persistence, database, stateRemittance, "payroll-state-remittance-posting");

        assertEquals(new PostingId("46292ace-d9a6-38c2-8d72-a1b0e45a0e0d"), runPosting.postingId());
        assertEquals(new PostingId("99e31f28-d419-38dc-a82a-793a21ea95cd"), netWagePosting.postingId());
        assertEquals(
            new PostingId("d9306dfe-fcc0-36ef-ade7-29280d38c67f"), stateRemittancePosting.postingId());
        assertEquals(1, queryInt(database, "select count(*) from latvian_payroll_run"));
        assertEquals(2, queryInt(database, "select count(*) from latvian_payroll_settlement"));
        assertEquals(
            "payroll-run-2026-06-employee-001:200000:21000:47180:55000:31620:147380",
            queryText(
                database,
                """
              select payroll_run_id || ':' || cast(gross_wages_minor as text) || ':'
                  || cast(employee_social_contribution_minor as text) || ':'
                  || cast(employer_social_contribution_minor as text) || ':'
                  || cast(non_taxable_minimum_minor as text) || ':'
                  || cast(personal_income_tax_minor as text) || ':'
                  || cast(net_wages_minor as text)
              from latvian_payroll_run
              """));
        assertEquals(
            "NET_WAGES:payroll-net-wage-posting;STATE_REMITTANCE:payroll-state-remittance-posting",
            queryText(
                database,
                """
              select group_concat(entry, ';')
              from (
                  select settlement_kind || ':' || origin_posting_id as entry
                  from latvian_payroll_settlement
                  order by settlement_kind
              )
              """));

        SqliteTransactionValidationBook validationBook =
            new SqliteTransactionValidationBook(database, store.postingReader());
        assertPayrollLookupSurface(
            validationBook, runPosting, netWagePosting, stateRemittancePosting);
        assertPayrollLookupSurface(
            readPayrollCapability(store), runPosting, netWagePosting, stateRemittancePosting);
        assertRehydratedPayrollEntries(store, runPosting, netWagePosting, stateRemittancePosting);

        CommittedPosting netWageReversal =
            persistReversal(
                persistence,
                database,
                netWagePosting,
                "payroll-net-wage-reversal",
                LocalDate.parse("2026-07-03"));
        CommittedPosting stateRemittanceReversal =
            persistReversal(
                persistence,
                database,
                stateRemittancePosting,
                "payroll-state-remittance-reversal",
                LocalDate.parse("2026-07-04"));
        CommittedPosting runReversal =
            persistReversal(
                persistence,
                database,
                runPosting,
                "payroll-run-reversal",
                LocalDate.parse("2026-07-05"));

        assertEquals(new PostingId("efaca3b3-1da8-31bc-be11-763ede578863"), netWageReversal.postingId());
        assertEquals(
            new PostingId("ca8d102b-6ba4-389f-aaf5-5fb7f49dcf3e"),
            stateRemittanceReversal.postingId());
        assertEquals(new PostingId("7ade4ccf-dbf0-3b8b-b7cf-227ac041613d"), runReversal.postingId());
        assertEquals(
            2, queryInt(database, "select count(*) from latvian_payroll_settlement_reversal"));
        assertEquals(1, queryInt(database, "select count(*) from latvian_payroll_run_reversal"));
        assertEquals(
            Optional.empty(),
            validationBook.findActiveLatvianPayrollRun(
                new LatvianPayrollEmployeeReference("employee-001"), PAYROLL_MONTH));
        assertEquals(
            Optional.empty(),
            validationBook.findActiveLatvianPayrollSettlement(
                RUN_ID, LatvianPayrollSettlementKind.NET_WAGES));
      }
    }
  }

  private static SqliteReadLatvianPayrollCapabilityView readPayrollCapability(
      SqlitePostingFactStore store) {
    return new SqliteReadLatvianPayrollCapabilityView() {
      @Override
      public SqliteThreadOwner storeThreadOwner() {
        return store.storeThreadOwner();
      }

      @Override
      public SqliteStoreReadOperations storeReadOperations() {
        return store.storeReadOperations();
      }
    };
  }

  private static void assertPayrollLookupSurface(
      LatvianPayrollLookupStore payrollLookup,
      CommittedPosting runPosting,
      CommittedPosting netWagePosting,
      CommittedPosting stateRemittancePosting) {
    assertEquals(RUN_ID, payrollLookup.findLatvianPayrollRun(RUN_ID).orElseThrow().payrollRunId());
    assertEquals(
        runPosting.postingId(),
        payrollLookup
            .findLatvianPayrollRunByOriginPosting(runPosting.postingId())
            .orElseThrow()
            .originPostingId());
    assertEquals(
        RUN_ID,
        payrollLookup
            .findActiveLatvianPayrollRun(
                new LatvianPayrollEmployeeReference("employee-001"), PAYROLL_MONTH)
            .orElseThrow()
            .payrollRunId());
    assertEquals(
        List.of(RUN_ID),
        payrollLookup.latvianPayrollRuns().stream().map(run -> run.payrollRunId()).toList());
    assertEquals(
        netWagePosting.postingId(),
        payrollLookup
            .findActiveLatvianPayrollSettlement(RUN_ID, LatvianPayrollSettlementKind.NET_WAGES)
            .orElseThrow()
            .originPostingId());
    assertEquals(
        stateRemittancePosting.postingId(),
        payrollLookup
            .findLatvianPayrollSettlementByPosting(stateRemittancePosting.postingId())
            .orElseThrow()
            .originPostingId());
    assertEquals(
        List.of(
            LatvianPayrollSettlementKind.NET_WAGES, LatvianPayrollSettlementKind.STATE_REMITTANCE),
        payrollLookup.latvianPayrollSettlements().stream()
            .map(settlement -> settlement.settlementKind())
            .toList());
    assertEquals(
        Optional.empty(),
        payrollLookup.findLatvianPayrollRun(new LatvianPayrollRunId("missing-payroll-run")));
    assertEquals(
        Optional.empty(),
        payrollLookup.findLatvianPayrollSettlementByPosting(
            new PostingId("03e7e664-ac40-34eb-b5a1-98a7c656cbb1")));
  }

  private static void assertRehydratedPayrollEntries(
      SqlitePostingFactStore store,
      CommittedPosting runPosting,
      CommittedPosting netWagePosting,
      CommittedPosting stateRemittancePosting) {
    assertEquals(
        RUN_ID,
        assertInstanceOf(
                LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll.class,
                store
                    .findPosting(runPosting.postingId())
                    .orElseThrow()
                    .resolvedOriginatingEntry()
                    .orElseThrow())
            .payrollRunId());
    assertEquals(
        RUN_ID,
        assertInstanceOf(
                LatvianPayrollBookkeepingEntryVariants.NetWageSettlement.class,
                store
                    .findPosting(netWagePosting.postingId())
                    .orElseThrow()
                    .resolvedOriginatingEntry()
                    .orElseThrow())
            .payrollRunId());
    assertEquals(
        RUN_ID,
        assertInstanceOf(
                LatvianPayrollBookkeepingEntryVariants.StateRemittance.class,
                store
                    .findPosting(stateRemittancePosting.postingId())
                    .orElseThrow()
                    .resolvedOriginatingEntry()
                    .orElseThrow())
            .payrollRunId());
  }

  private static void declarePayrollAccounts(SqlitePostingFactStore store) {
    declareAccount(
        store,
        CASH,
        new AccountName("Payroll Cash"),
        AccountType.ASSET,
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
        RECORDED_AT);
    declareAccount(
        store,
        WAGE_EXPENSE,
        new AccountName("Payroll Wage Expense"),
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE),
        RECORDED_AT);
    declareAccount(
        store,
        EMPLOYER_SOCIAL_EXPENSE,
        new AccountName("Payroll Employer Social Contribution Expense"),
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE),
        RECORDED_AT);
    declareCurrentLiability(store, NET_WAGES_PAYABLE, "Payroll Net Wages Payable");
    declareCurrentLiability(
        store, EMPLOYEE_SOCIAL_PAYABLE, "Payroll Employee Social Contribution Payable");
    declareCurrentLiability(
        store, EMPLOYER_SOCIAL_PAYABLE, "Payroll Employer Social Contribution Payable");
    declareCurrentLiability(
        store, PERSONAL_INCOME_TAX_PAYABLE, "Payroll Personal Income Tax Payable");
  }

  private static void declareCurrentLiability(
      SqlitePostingFactStore store, AccountCode accountCode, String accountName) {
    declareAccount(
        store,
        accountCode,
        new AccountName(accountName),
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY),
        RECORDED_AT);
  }

  private static ResolvedLatvianPayrollSettlement resolvedSettlement(
      LatvianPayrollSettlementKind kind, LatvianMonthlyPayrollCalculation calculation) {
    return new ResolvedLatvianPayrollSettlement(
        kind,
        RUN_ID,
        CASH,
        NET_WAGES_PAYABLE,
        EMPLOYEE_SOCIAL_PAYABLE,
        EMPLOYER_SOCIAL_PAYABLE,
        PERSONAL_INCOME_TAX_PAYABLE,
        calculation.netWages(),
        calculation.employeeSocialContribution(),
        calculation.employerSocialContribution(),
        calculation.personalIncomeTax());
  }

  private static CommittedPosting persist(
      SqliteClosePostingPersistence persistence,
      SqliteNativeDatabase database,
      LatvianPayrollBookkeepingEntryVariants entry,
      String postingId) {
    return persist(
        persistence,
        database,
        entry,
        entry,
        entry.journalEntry(),
        PostingLineageModel.direct(),
        entry.postingOriginKind(),
        postingId);
  }

  private static CommittedPosting persistReversal(
      SqliteClosePostingPersistence persistence,
      SqliteNativeDatabase database,
      CommittedPosting priorPosting,
      String postingId,
      LocalDate effectiveDate) {
    JournalEntry reversalJournal = negate(priorPosting.journalEntry(), effectiveDate);
    ReversalReference reference = new ReversalReference(priorPosting.postingId());
    ReversalReason reason = new ReversalReason("payroll correction");
    BookkeepingEntry.Reversal reversal =
        new BookkeepingEntry.Reversal(
            effectiveDate, new PostingLineage.Reversal(reference, reason), null, reversalJournal);
    return persist(
        persistence,
        database,
        reversal,
        reversal,
        reversalJournal,
        PostingLineageModel.reversal(reference, reason),
        PostingOriginKind.REVERSAL,
        postingId);
  }

  private static CommittedPosting persist(
      SqliteClosePostingPersistence persistence,
      SqliteNativeDatabase database,
      BookkeepingEntry callerAuthoredEntry,
      BookkeepingEntry resolvedOriginatingEntry,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      PostingOriginKind postingOriginKind,
      String postingId) {
    return persistence.persistAcceptedPosting(
        database,
        new AcceptedPosting(
            journalEntry,
            postingLineage,
            PostingKind.STANDARD,
            postingOriginKind,
            generatedEvidence(postingId, "payroll-register"),
            requestProvenance(postingId),
            SourceChannel.CLI,
            callerAuthoredEntry,
            resolvedOriginatingEntry,
            List.of(),
            Map.of()),
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
        new CommittedProvenance(requestProvenance(postingId), RECORDED_AT, SourceChannel.CLI),
        () -> new PostingId(java.util.UUID.nameUUIDFromBytes(("fingrind-test-postingid:" + postingId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()));
  }

  private static JournalEntry negate(JournalEntry original, LocalDate effectiveDate) {
    return new JournalEntry(
        effectiveDate,
        original.lines().stream()
            .map(
                line ->
                    new JournalLine(
                        line.accountCode(),
                        line.side() == JournalLine.EntrySide.DEBIT
                            ? JournalLine.EntrySide.CREDIT
                            : JournalLine.EntrySide.DEBIT,
                        line.amount()))
            .toList());
  }

  private static RequestProvenance requestProvenance(String token) {
    return new RequestProvenance(
        new CommandId("command-" + token),
        new IdempotencyKey("idempotency-" + token),
        new CausationId("cause-" + token),
        Optional.of(new CorrelationId("correlation-" + token)));
  }
}
