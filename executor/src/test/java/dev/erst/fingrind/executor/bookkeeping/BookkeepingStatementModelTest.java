package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.StatementLineKind;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Direct model-validation coverage for the accounting reporting and transfer-period-result surface.
 */
class BookkeepingStatementModelTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-05-12T13:45:00Z");

  @Test
  void registeredAccountDeclare_rejectsTypeConflictAndPreservesRoleBasedNormalBalance() {
    RegisteredAccount existing =
        new RegisteredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            accountTaxonomy(AccountType.ASSET),
            true,
            FIXED_INSTANT);
    AccountDeclaration conflictDeclaration =
        new AccountDeclaration(
            existing.accountCode(),
            new AccountName("Cash"),
            AccountType.LIABILITY,
            AccountRole.ORDINARY,
            accountTaxonomy(AccountType.LIABILITY));
    AccountDeclaration redeclaration =
        new AccountDeclaration(
            existing.accountCode(),
            new AccountName("Cash Reserve"),
            AccountType.ASSET,
            AccountRole.POLARITY_INVERTED,
            accountTaxonomy(AccountType.ASSET));
    AccountDeclaration taxonomyConflictDeclaration =
        new AccountDeclaration(
            existing.accountCode(),
            new AccountName("Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.of(new AccountCode("1099")),
                Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                Optional.empty()));
    AccountDeclaration firstDeclaration =
        new AccountDeclaration(
            new AccountCode("1200"),
            new AccountName("Receivable"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            accountTaxonomy(AccountType.ASSET));

    assertEquals(
        new AccountDeclarationOutcome.Rejected(
            new BookkeepingAdministrationRejection.AccountTypeConflict(
                existing.accountCode(), AccountType.ASSET, AccountType.LIABILITY)),
        RegisteredAccount.declare(existing, conflictDeclaration, FIXED_INSTANT));
    assertEquals(
        new AccountDeclarationOutcome.Rejected(
            new BookkeepingAdministrationRejection.AccountRoleConflict(
                existing.accountCode(), AccountRole.ORDINARY, AccountRole.POLARITY_INVERTED)),
        RegisteredAccount.declare(existing, redeclaration, FIXED_INSTANT));
    assertEquals(
        new AccountDeclarationOutcome.Rejected(
            new BookkeepingAdministrationRejection.AccountTaxonomyConflict(
                existing.accountCode(),
                existing.accountTaxonomy(),
                taxonomyConflictDeclaration.accountTaxonomy())),
        RegisteredAccount.declare(existing, taxonomyConflictDeclaration, FIXED_INSTANT));
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            new RegisteredAccount(
                new AccountCode("1200"),
                new AccountName("Receivable"),
                AccountType.ASSET,
                AccountRole.ORDINARY,
                accountTaxonomy(AccountType.ASSET),
                true,
                FIXED_INSTANT)),
        RegisteredAccount.declare(null, firstDeclaration, FIXED_INSTANT));
    assertEquals(NormalBalance.DEBIT, existing.normalBalance());
    assertEquals(
        NormalBalance.CREDIT,
        new AccountDeclaration(
                new AccountCode("1090"),
                new AccountName("Accumulated Depreciation"),
                AccountType.ASSET,
                AccountRole.POLARITY_INVERTED,
                accountTaxonomy(AccountType.ASSET))
            .normalBalance());
  }

  @Test
  void administrationRejections_requireTheirMandatoryFields() {
    assertEquals(
        FinancialPositionLineClassification.RESULT_HOLDING,
        new BookkeepingAdministrationRejection.ResultHoldingAccountCandidateMissing(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200")))
            .requiredFinancialPositionLineClassification());
    assertEquals(
        List.of(new AccountCode("3200")),
        new BookkeepingAdministrationRejection.ResultHoldingAccountCandidateMissing(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200")))
            .inactiveCandidateAccountCodes());
    assertEquals(
        LocalDate.parse("2026-05-13"),
        new BookkeepingAdministrationRejection.PeriodResultTransferFutureDate(
                LocalDate.parse("2026-05-13"))
            .attemptedEffectiveDateTo());
    assertEquals(
        "accountCode",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.AccountTypeConflict(
                        nullOf(AccountCode.class), AccountType.ASSET, AccountType.LIABILITY))
            .getMessage());
    assertEquals(
        "accountCode",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.AccountRoleConflict(
                        nullOf(AccountCode.class),
                        AccountRole.ORDINARY,
                        AccountRole.POLARITY_INVERTED))
            .getMessage());
    assertEquals(
        "requiredFinancialPositionLineClassification",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.ResultHoldingAccountCandidateMissing(
                        nullOf(FinancialPositionLineClassification.class), List.of()))
            .getMessage());
    assertEquals(
        "requiredEffectiveDateFrom",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.PeriodResultTransferMustStartAt(
                        nullOf(LocalDate.class)))
            .getMessage());
    assertEquals(
        "requiredFinancialPositionLineClassification",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.ResultHoldingAccountCandidateAmbiguous(
                        nullOf(FinancialPositionLineClassification.class),
                        List.of(new AccountCode("3200"))))
            .getMessage());
    assertEquals(
        "existingAccountTaxonomy",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.AccountTaxonomyConflict(
                        new AccountCode("3200"),
                        nullOf(dev.erst.fingrind.core.AccountTaxonomy.class),
                        accountTaxonomy(AccountType.EQUITY)))
            .getMessage());
    assertEquals(
        "requestedAccountTaxonomy",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.AccountTaxonomyConflict(
                        new AccountCode("3200"),
                        accountTaxonomy(AccountType.EQUITY),
                        nullOf(dev.erst.fingrind.core.AccountTaxonomy.class)))
            .getMessage());
    assertEquals(
        "attemptedEffectiveDateTo",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.PeriodResultTransferFutureDate(
                        nullOf(LocalDate.class)))
            .getMessage());
  }

  @Test
  void postingRejectionsAndAccountTotals_validateTheirMandatoryFieldsAndProjection() {
    RegisteredAccount assetAccount =
        new RegisteredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            accountTaxonomy(AccountType.ASSET),
            true,
            FIXED_INSTANT);
    AccountCurrencyTotals totals =
        new AccountCurrencyTotals(
            assetAccount, dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 10L, 4L);

    assertEquals(currencyBalance("0.10", "0.04", "0.06", BalanceSide.DEBIT), totals.balance());
    assertEquals(
        AccountType.REVENUE,
        new BookkeepingPostingRejection.OpenAccountingPositionTouchesNominalAccount(
                new AccountCode("4000"), AccountType.REVENUE)
            .accountType());
    assertEquals(
        dev.erst.fingrind.core.CurrencyUnit.of("EUR"),
        new BookkeepingPostingRejection.BookFunctionalCurrencyMismatch(
                dev.erst.fingrind.core.CurrencyUnit.of("USD"),
                dev.erst.fingrind.core.CurrencyUnit.of("EUR"))
            .attemptedCurrency());
    assertEquals(
        "currencyUnit",
        assertThrows(
                NullPointerException.class,
                () -> new AccountCurrencyTotals(assetAccount, nullOf(), 1L, 0L))
            .getMessage());
    assertEquals(
        "debitTotalMinor must be non-negative.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new AccountCurrencyTotals(
                        assetAccount, dev.erst.fingrind.core.CurrencyUnit.of("EUR"), -1L, 0L))
            .getMessage());
    assertEquals(
        "creditTotalMinor must be non-negative.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new AccountCurrencyTotals(
                        assetAccount, dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 0L, -1L))
            .getMessage());
    assertEquals(
        "accountType",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingPostingRejection.OpenAccountingPositionTouchesNominalAccount(
                        new AccountCode("4000"), nullOf(AccountType.class)))
            .getMessage());
    assertEquals(
        "attemptedCurrency",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingPostingRejection.BookFunctionalCurrencyMismatch(
                        dev.erst.fingrind.core.CurrencyUnit.of("USD"), nullOf()))
            .getMessage());
  }

  @Test
  void statementAndCloseModels_validateOrderingAndDefensivelyCopyCollections() {
    List<FinancialPositionSectionView> financialPositionSections =
        new ArrayList<>(
            List.of(
                new FinancialPositionSectionView(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRowView(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT)))));
    List<IncomeStatementSectionView> incomeSections =
        new ArrayList<>(
            List.of(
                new IncomeStatementSectionView(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRowView(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            Optional.of(AccountRole.ORDINARY),
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))));
    List<ChangesInEquityRowView> equityRows =
        new ArrayList<>(
            List.of(
                new ChangesInEquityRowView(
                    "current-period-result",
                    "Current Period Result",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))));
    List<PostingDraft> closingPostings = new ArrayList<>(List.of(postingDraft()));
    List<PostingId> transferPostingIds = new ArrayList<>(List.of(new PostingId("posting-1")));

    FinancialPositionView financialPositionView =
        new FinancialPositionView(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-05-12")),
            Optional.of(LocalDate.parse("2026-05-12")),
            EffectiveDateRange.of(null, LocalDate.parse("2025-05-12")),
            PostingCoverage.ALL_POSTING_KINDS,
            true,
            financialPositionSections,
            financialPositionSections);
    IncomeStatementView incomeStatementView =
        new IncomeStatementView(
            bookIdentity(),
            LocalDate.parse("2026-05-01"),
            LocalDate.parse("2026-05-12"),
            EffectiveDateRange.of(LocalDate.parse("2025-05-01"), LocalDate.parse("2025-05-12")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            incomeSections,
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            incomeSections,
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)));
    ChangesInEquityView changesInEquityView =
        new ChangesInEquityView(
            bookIdentity(),
            LocalDate.parse("2026-05-01"),
            LocalDate.parse("2026-05-12"),
            EffectiveDateRange.of(LocalDate.parse("2025-05-01"), LocalDate.parse("2025-05-12")),
            PostingCoverage.ALL_POSTING_KINDS,
            equityRows,
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            equityRows,
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)));
    PeriodResultTransferDraft periodResultTransferDraft =
        new PeriodResultTransferDraft(
            new ReportingPeriod(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-12")),
            new AccountCode("3200"),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            FIXED_INSTANT,
            closingPostings);
    TransferredPeriodResult transferredPeriodResult =
        new TransferredPeriodResult(
            1,
            new ReportingPeriod(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-12")),
            new AccountCode("3200"),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            FIXED_INSTANT,
            transferPostingIds);

    financialPositionSections.clear();
    incomeSections.clear();
    equityRows.clear();
    closingPostings.clear();
    transferPostingIds.clear();

    assertEquals(1, financialPositionView.sections().size());
    assertEquals(1, incomeStatementView.sections().size());
    assertEquals(1, changesInEquityView.rows().size());
    assertEquals(1, periodResultTransferDraft.closingPostings().size());
    assertEquals(1, transferredPeriodResult.transferPostingIds().size());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IncomeStatementCriteria(
                LocalDate.parse("2026-05-12"), LocalDate.parse("2026-05-01")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ChangesInEquityCriteria(
                LocalDate.parse("2026-05-12"), LocalDate.parse("2026-05-01")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IncomeStatementView(
                bookIdentity(),
                LocalDate.parse("2026-05-12"),
                LocalDate.parse("2026-05-01"),
                EffectiveDateRange.of(LocalDate.parse("2025-05-01"), LocalDate.parse("2025-05-12")),
                PostingCoverage.NON_CLOSING_POSTINGS,
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ChangesInEquityView(
                bookIdentity(),
                LocalDate.parse("2026-05-12"),
                LocalDate.parse("2026-05-01"),
                EffectiveDateRange.of(LocalDate.parse("2025-05-01"), LocalDate.parse("2025-05-12")),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TransferredPeriodResult(
                0,
                new ReportingPeriod(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-12")),
                new AccountCode("3200"),
                List.of(currencyBalance("0.00", "1.00", "1.00", BalanceSide.CREDIT)),
                FIXED_INSTANT,
                List.of()));
  }

  private static PostingDraft postingDraft() {
    return new PostingDraft(
        new JournalEntry(
            LocalDate.parse("2026-05-12"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "1.00")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "1.00")))),
        PostingLineageModel.direct(),
        PostingKind.PERIOD_RESULT_TRANSFER,
        dev.erst.fingrind.core.PostingOriginKind.PERIOD_RESULT_TRANSFER,
        generatedEvidence("period-result-transfer-eur", "period-result-transfer-plan"),
        new CommittedProvenance(
            new RequestProvenance(
                new dev.erst.fingrind.core.ActorId("actor-1"),
                dev.erst.fingrind.core.ActorType.SYSTEM,
                new dev.erst.fingrind.core.CommandId("command-1"),
                new dev.erst.fingrind.core.IdempotencyKey("idem-1"),
                new dev.erst.fingrind.core.CausationId("cause-1"),
                Optional.empty()),
            FIXED_INSTANT,
            SourceChannel.SYSTEM));
  }

  private static CurrencyBalance currencyBalance(
      String debitAmount, String creditAmount, String netAmount, BalanceSide balanceSide) {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(Money.parse("EUR", debitAmount), Money.parse("EUR", creditAmount));
    assertEquals(Money.parse("EUR", netAmount), balance.netAmount());
    assertEquals(balanceSide, balance.balanceSide());
    return balance;
  }
}
