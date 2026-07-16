package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.profitAndLossTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFinancingApplication;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDepreciation;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDisposal;
import dev.erst.fingrind.contract.bookkeeping.ResolvedRealizedForeignExchangeSettlement;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Direct coverage for purchase and no-op account-semantics branches. */
class PostEntryRoleAccountSemanticsCoverageTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-05-13T11:00:00Z");

  @Test
  void validate_coversDirectJournalPurchaseAndNoopVariants() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();

    PostEntryRoleAccountSemantics.validate(
        violations,
        Map.of(),
        new BookkeepingEntry.DirectJournal(
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        dev.erst.fingrind.core.Money.ofMinorUnits(
                            dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1000)),
                    new JournalLine(
                        new AccountCode("4000"),
                        JournalLine.EntrySide.CREDIT,
                        dev.erst.fingrind.core.Money.ofMinorUnits(
                            dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1000)))),
            null),
        "entryKind",
        "DIRECT_JOURNAL");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts(),
        new BookkeepingEntry.PurchaseSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1400"),
            new AccountCode("1000"),
            new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null),
        "entryKind",
        "PURCHASE_SETTLED");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts(),
        new BookkeepingEntry.PurchaseOnCredit(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1400"),
            new AccountCode("2100"),
            new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null),
        "entryKind",
        "PURCHASE_ON_CREDIT");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts(),
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-07"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new MonetaryAmount("EUR", "1000"),
                    null))),
        "entryKind",
        "OPENING_POSITION");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts(),
        new BookkeepingEntry.Reversal(
            LocalDate.parse("2026-04-07"),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new ReversalReference(new PostingId("posting-1")),
                new ReversalReason("operator correction")),
            null,
            null),
        "entryKind",
        "REVERSAL");

    assertEquals(List.of(), violations);
  }

  @Test
  void validate_coversEveryFixedAssetVariantBeforeAndAfterResolution() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    Map<AccountCode, RegisteredAccount> accounts = lifecycleAccounts();
    FixedAssetId assetId = new FixedAssetId("office-desk");

    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FixedAssetBookkeepingEntryVariants.Capitalization(
            LocalDate.parse("2026-04-07"),
            assetId,
            code("1600"),
            code("1601"),
            code("5000"),
            code("4100"),
            code("5001"),
            code("1000"),
            amount(12_000),
            new FixedAssetDepreciationSchedule(LocalDate.parse("2026-04-07"), 60, amount(0))),
        "entryKind",
        "FIXED_ASSET_CAPITALIZATION");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FixedAssetBookkeepingEntryVariants.Depreciation(
            LocalDate.parse("2026-05-01"), assetId, null),
        "entryKind",
        "FIXED_ASSET_DEPRECIATION");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FixedAssetBookkeepingEntryVariants.Depreciation(
            LocalDate.parse("2026-05-01"),
            assetId,
            new ResolvedFixedAssetDepreciation(code("5000"), code("1601"), amount(200))),
        "entryKind",
        "FIXED_ASSET_DEPRECIATION");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FixedAssetBookkeepingEntryVariants.Disposal(
            LocalDate.parse("2026-06-01"), assetId, code("1000"), amount(10_500), null),
        "entryKind",
        "FIXED_ASSET_DISPOSAL");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FixedAssetBookkeepingEntryVariants.Disposal(
            LocalDate.parse("2026-06-01"),
            assetId,
            code("1000"),
            amount(10_500),
            new ResolvedFixedAssetDisposal(
                code("1600"),
                code("1601"),
                code("4100"),
                amount(12_000),
                amount(400),
                amount(11_600),
                amount(1_100),
                true)),
        "entryKind",
        "FIXED_ASSET_DISPOSAL");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FixedAssetBookkeepingEntryVariants.Disposal(
            LocalDate.parse("2026-06-01"),
            assetId,
            code("1000"),
            amount(10_000),
            new ResolvedFixedAssetDisposal(
                code("1600"),
                code("1601"),
                code("5001"),
                amount(12_000),
                amount(400),
                amount(11_600),
                amount(1_600),
                false)),
        "entryKind",
        "FIXED_ASSET_DISPOSAL");

    assertEquals(List.of(), violations);
  }

  @Test
  void validate_coversEveryFinancingVariantBeforeAndAfterResolution() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    Map<AccountCode, RegisteredAccount> accounts = lifecycleAccounts();
    FinancingArrangementId arrangementId = new FinancingArrangementId("working-capital-loan");
    ResolvedFinancingApplication resolved =
        new ResolvedFinancingApplication(code("2000"), code("2001"));

    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FinancingBookkeepingEntryVariants.Borrowing(
            LocalDate.parse("2026-04-07"),
            arrangementId,
            code("1000"),
            code("2000"),
            code("2001"),
            amount(10_000)),
        "entryKind",
        "FINANCING_BORROWING");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FinancingBookkeepingEntryVariants.PrincipalRepayment(
            LocalDate.parse("2026-05-01"), arrangementId, code("1000"), amount(1_000), null),
        "entryKind",
        "FINANCING_PRINCIPAL_REPAYMENT");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FinancingBookkeepingEntryVariants.PrincipalRepayment(
            LocalDate.parse("2026-05-01"), arrangementId, code("1000"), amount(1_000), resolved),
        "entryKind",
        "FINANCING_PRINCIPAL_REPAYMENT");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FinancingBookkeepingEntryVariants.InterestAccrual(
            LocalDate.parse("2026-05-31"), arrangementId, code("5002"), amount(200), null),
        "entryKind",
        "FINANCING_INTEREST_ACCRUAL");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FinancingBookkeepingEntryVariants.InterestAccrual(
            LocalDate.parse("2026-05-31"), arrangementId, code("5002"), amount(200), resolved),
        "entryKind",
        "FINANCING_INTEREST_ACCRUAL");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FinancingBookkeepingEntryVariants.InterestPayment(
            LocalDate.parse("2026-06-01"), arrangementId, code("1000"), amount(200), null),
        "entryKind",
        "FINANCING_INTEREST_PAYMENT");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new FinancingBookkeepingEntryVariants.InterestPayment(
            LocalDate.parse("2026-06-01"), arrangementId, code("1000"), amount(200), resolved),
        "entryKind",
        "FINANCING_INTEREST_PAYMENT");

    assertEquals(List.of(), violations);
  }

  @Test
  void validate_coversEveryRealizedForeignExchangeVariantAndBothSettlementOutcomes() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    Map<AccountCode, RegisteredAccount> accounts = lifecycleAccounts();
    ForeignCurrencyObligationId obligationId =
        new ForeignCurrencyObligationId("usd-client-invoice");

    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable(
            LocalDate.parse("2026-04-07"),
            obligationId,
            code("1100"),
            code("4000"),
            code("4100"),
            code("5001"),
            usdAtEuro(10_000, 9_200)),
        "entryKind",
        "FOREIGN_CURRENCY_RECEIVABLE");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
            LocalDate.parse("2026-05-01"),
            obligationId,
            code("1000"),
            usdAtEuro(10_000, 9_500),
            null),
        "entryKind",
        "FOREIGN_CURRENCY_SETTLEMENT");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
            LocalDate.parse("2026-05-01"),
            obligationId,
            code("1000"),
            usdAtEuro(10_000, 9_500),
            new ResolvedRealizedForeignExchangeSettlement(
                code("1100"), code("4100"), amount(9_200), amount(300), true)),
        "entryKind",
        "FOREIGN_CURRENCY_SETTLEMENT");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
            LocalDate.parse("2026-05-01"),
            obligationId,
            code("1000"),
            usdAtEuro(10_000, 9_000),
            new ResolvedRealizedForeignExchangeSettlement(
                code("1100"), code("5001"), amount(9_200), amount(200), false)),
        "entryKind",
        "FOREIGN_CURRENCY_SETTLEMENT");

    assertEquals(List.of(), violations);
  }

  @Test
  void validate_rejectsLifecycleAccountRoleMismatch() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();

    PostEntryRoleAccountSemantics.validate(
        violations,
        Map.of(code("1000"), lifecycleAccounts().get(code("1000"))),
        new FinancingBookkeepingEntryVariants.Borrowing(
            LocalDate.parse("2026-04-07"),
            new FinancingArrangementId("working-capital-loan"),
            code("1000"),
            code("1000"),
            code("2001"),
            amount(10_000)),
        "entryKind",
        "FINANCING_BORROWING");

    assertFalse(violations.isEmpty());
  }

  private static Map<AccountCode, RegisteredAccount> accounts() {
    RegisteredAccount cash =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            accountTaxonomy(AccountType.ASSET),
            true,
            DECLARED_AT);
    RegisteredAccount inventory =
        registeredAccount(
            new AccountCode("1400"),
            new AccountName("Inventory"),
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
            true,
            DECLARED_AT);
    RegisteredAccount payable =
        registeredAccount(
            new AccountCode("2100"),
            new AccountName("Accounts Payable"),
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_PAYABLE),
            true,
            DECLARED_AT);
    RegisteredAccount revenue =
        registeredAccount(
            new AccountCode("4000"),
            new AccountName("Sales Revenue"),
            AccountType.REVENUE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE),
            true,
            DECLARED_AT);
    return Map.of(
        cash.accountCode(), cash,
        inventory.accountCode(), inventory,
        payable.accountCode(), payable,
        revenue.accountCode(), revenue);
  }

  private static Map<AccountCode, RegisteredAccount> lifecycleAccounts() {
    RegisteredAccount cash =
        registeredAccount(
            code("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            accountTaxonomy(AccountType.ASSET),
            true,
            DECLARED_AT);
    RegisteredAccount receivable =
        registeredAccount(
            code("1100"),
            new AccountName("Trade Receivable"),
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_RECEIVABLE),
            true,
            DECLARED_AT);
    RegisteredAccount asset =
        registeredAccount(
            code("1600"),
            new AccountName("Equipment"),
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET),
            true,
            DECLARED_AT);
    RegisteredAccount accumulatedDepreciation =
        registeredAccount(
            code("1601"),
            new AccountName("Accumulated Depreciation"),
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET),
            true,
            DECLARED_AT);
    RegisteredAccount principalLiability =
        registeredAccount(
            code("2000"),
            new AccountName("Loan Principal"),
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY),
            true,
            DECLARED_AT);
    RegisteredAccount interestPayable =
        registeredAccount(
            code("2001"),
            new AccountName("Interest Payable"),
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY),
            true,
            DECLARED_AT);
    RegisteredAccount revenue =
        registeredAccount(
            code("4000"),
            new AccountName("Revenue"),
            AccountType.REVENUE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE),
            true,
            DECLARED_AT);
    RegisteredAccount gain =
        registeredAccount(
            code("4100"),
            new AccountName("Gain"),
            AccountType.REVENUE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE),
            true,
            DECLARED_AT);
    RegisteredAccount depreciationExpense =
        registeredAccount(
            code("5000"),
            new AccountName("Depreciation Expense"),
            AccountType.EXPENSE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE),
            true,
            DECLARED_AT);
    RegisteredAccount loss =
        registeredAccount(
            code("5001"),
            new AccountName("Loss"),
            AccountType.EXPENSE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE),
            true,
            DECLARED_AT);
    RegisteredAccount interestExpense =
        registeredAccount(
            code("5002"),
            new AccountName("Interest Expense"),
            AccountType.EXPENSE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE),
            true,
            DECLARED_AT);
    return Map.ofEntries(
        Map.entry(cash.accountCode(), cash),
        Map.entry(receivable.accountCode(), receivable),
        Map.entry(asset.accountCode(), asset),
        Map.entry(accumulatedDepreciation.accountCode(), accumulatedDepreciation),
        Map.entry(principalLiability.accountCode(), principalLiability),
        Map.entry(interestPayable.accountCode(), interestPayable),
        Map.entry(revenue.accountCode(), revenue),
        Map.entry(gain.accountCode(), gain),
        Map.entry(depreciationExpense.accountCode(), depreciationExpense),
        Map.entry(loss.accountCode(), loss),
        Map.entry(interestExpense.accountCode(), interestExpense));
  }

  private static AccountCode code(String value) {
    return new AccountCode(value);
  }

  private static MonetaryAmount amount(long minorUnits) {
    return new MonetaryAmount("EUR", Long.toString(minorUnits));
  }

  private static ForeignExchangeDetails usdAtEuro(long usdMinorUnits, long euroMinorUnits) {
    MonetaryAmount transactionAmount = new MonetaryAmount("USD", Long.toString(usdMinorUnits));
    MonetaryAmount functionalAmount = new MonetaryAmount("EUR", Long.toString(euroMinorUnits));
    return new ForeignExchangeDetails(
        transactionAmount,
        functionalAmount,
        new QuotedExchangeRate(
            transactionAmount, functionalAmount, LocalDate.parse("2026-04-07"), "test quote"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }
}
