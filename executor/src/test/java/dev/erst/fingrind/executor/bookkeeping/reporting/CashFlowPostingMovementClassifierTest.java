package dev.erst.fingrind.executor.bookkeeping.reporting;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.profitAndLossTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Direct coverage for cash-flow movement classification and allocation. */
class CashFlowPostingMovementClassifierTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-05-13T11:00:00Z");
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");

  @Test
  void postingMovements_ignoreInternalCashTransfers() {
    RegisteredAccount cash = cashAccount("1000", "Cash");
    RegisteredAccount pettyCash = cashAccount("1010", "Petty Cash");

    assertEquals(
        List.of(),
        CashFlowPostingMovementClassifier.postingMovements(
            Map.of(cash.accountCode(), cash, pettyCash.accountCode(), pettyCash),
            posting(
                "posting-internal-transfer",
                line("1000", JournalLine.EntrySide.DEBIT, "4.00"),
                line("1010", JournalLine.EntrySide.CREDIT, "4.00"))));
  }

  @Test
  void postingMovements_classifyCanonicalCounterpartFamiliesBySection() {
    RegisteredAccount cash = cashAccount("1000", "Cash");
    RegisteredAccount revenue =
        account(
            "4000",
            "Sales",
            AccountType.REVENUE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE));
    RegisteredAccount receivable =
        account(
            "1200",
            "Accounts Receivable",
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_RECEIVABLE));
    RegisteredAccount currentAsset =
        account(
            "1300",
            "Prepaid Expense",
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET));
    RegisteredAccount inventory =
        account(
            "1400",
            "Inventory",
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY));
    RegisteredAccount equipment =
        account(
            "1500",
            "Equipment",
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET));
    RegisteredAccount payable =
        account(
            "2000",
            "Accounts Payable",
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_PAYABLE));
    RegisteredAccount currentLiability =
        account(
            "2050",
            "Accrued Expense",
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY));
    RegisteredAccount loan =
        account(
            "2100",
            "Term Loan",
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_LIABILITY));
    RegisteredAccount capital =
        account(
            "3000",
            "Owner Capital",
            AccountType.EQUITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.OTHER_EQUITY));
    RegisteredAccount expense =
        account(
            "5000",
            "Supplies",
            AccountType.EXPENSE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    Map<AccountCode, RegisteredAccount> accountsByCode =
        Map.ofEntries(
            Map.entry(cash.accountCode(), cash),
            Map.entry(revenue.accountCode(), revenue),
            Map.entry(receivable.accountCode(), receivable),
            Map.entry(currentAsset.accountCode(), currentAsset),
            Map.entry(inventory.accountCode(), inventory),
            Map.entry(equipment.accountCode(), equipment),
            Map.entry(payable.accountCode(), payable),
            Map.entry(currentLiability.accountCode(), currentLiability),
            Map.entry(loan.accountCode(), loan),
            Map.entry(capital.accountCode(), capital),
            Map.entry(expense.accountCode(), expense));

    assertMovement(
        singleMovement(
            accountsByCode,
            posting(
                "posting-sale",
                PostingOriginKind.SALE_SETTLED,
                line("1000", JournalLine.EntrySide.DEBIT, "7.00"),
                line("4000", JournalLine.EntrySide.CREDIT, "7.00"))),
        CashFlowSectionKind.OPERATING,
        "4000",
        "7.00",
        "0.00");
    assertMovement(
        singleMovement(
            accountsByCode,
            posting(
                "posting-receivable-collection",
                PostingOriginKind.RECEIPT,
                line("1000", JournalLine.EntrySide.DEBIT, "4.00"),
                line("1200", JournalLine.EntrySide.CREDIT, "4.00"))),
        CashFlowSectionKind.OPERATING,
        "1200",
        "4.00",
        "0.00");
    assertMovement(
        singleMovement(
            accountsByCode,
            posting(
                "posting-current-asset",
                line("1300", JournalLine.EntrySide.DEBIT, "9.00"),
                line("1000", JournalLine.EntrySide.CREDIT, "9.00"))),
        CashFlowSectionKind.OPERATING,
        "1300",
        "0.00",
        "9.00");
    assertMovement(
        singleMovement(
            accountsByCode,
            posting(
                "posting-inventory",
                line("1400", JournalLine.EntrySide.DEBIT, "4.50"),
                line("1000", JournalLine.EntrySide.CREDIT, "4.50"))),
        CashFlowSectionKind.OPERATING,
        "1400",
        "0.00",
        "4.50");
    assertMovement(
        singleMovement(
            accountsByCode,
            posting(
                "posting-equipment",
                line("1500", JournalLine.EntrySide.DEBIT, "3.00"),
                line("1000", JournalLine.EntrySide.CREDIT, "3.00"))),
        CashFlowSectionKind.INVESTING,
        "1500",
        "0.00",
        "3.00");
    assertMovement(
        singleMovement(
            accountsByCode,
            posting(
                "posting-payable",
                PostingOriginKind.PAYMENT,
                line("2000", JournalLine.EntrySide.DEBIT, "2.00"),
                line("1000", JournalLine.EntrySide.CREDIT, "2.00"))),
        CashFlowSectionKind.OPERATING,
        "2000",
        "0.00",
        "2.00");
    assertMovement(
        singleMovement(
            accountsByCode,
            posting(
                "posting-current-liability",
                line("2050", JournalLine.EntrySide.DEBIT, "1.50"),
                line("1000", JournalLine.EntrySide.CREDIT, "1.50"))),
        CashFlowSectionKind.OPERATING,
        "2050",
        "0.00",
        "1.50");
    assertMovement(
        singleMovement(
            accountsByCode,
            posting(
                "posting-loan",
                line("1000", JournalLine.EntrySide.DEBIT, "8.00"),
                line("2100", JournalLine.EntrySide.CREDIT, "8.00"))),
        CashFlowSectionKind.FINANCING,
        "2100",
        "8.00",
        "0.00");
    assertMovement(
        singleMovement(
            accountsByCode,
            posting(
                "posting-capital",
                line("1000", JournalLine.EntrySide.DEBIT, "6.50"),
                line("3000", JournalLine.EntrySide.CREDIT, "6.50"))),
        CashFlowSectionKind.FINANCING,
        "3000",
        "6.50",
        "0.00");
    assertMovement(
        singleMovement(
            accountsByCode,
            posting(
                "posting-owner-contribution",
                PostingOriginKind.OWNER_CONTRIBUTION,
                line("1000", JournalLine.EntrySide.DEBIT, "6.00"),
                line("3000", JournalLine.EntrySide.CREDIT, "6.00"))),
        CashFlowSectionKind.FINANCING,
        "3000",
        "6.00",
        "0.00");
    assertMovement(
        singleMovement(
            accountsByCode,
            posting(
                "posting-expense",
                line("5000", JournalLine.EntrySide.DEBIT, "5.00"),
                line("1000", JournalLine.EntrySide.CREDIT, "5.00"))),
        CashFlowSectionKind.OPERATING,
        "5000",
        "0.00",
        "5.00");
  }

  @Test
  void postingMovements_allocateResidualCashProportionallyAndDropZeroShares() {
    RegisteredAccount cash = cashAccount("1000", "Cash");
    RegisteredAccount pettyCash = cashAccount("1010", "Petty Cash");
    RegisteredAccount revenue =
        account(
            "4000",
            "Sales",
            AccountType.REVENUE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE));
    RegisteredAccount loan =
        account(
            "2100",
            "Term Loan",
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_LIABILITY));
    RegisteredAccount expense =
        account(
            "5000",
            "Supplies",
            AccountType.EXPENSE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    Map<AccountCode, RegisteredAccount> accountsByCode =
        Map.of(
            cash.accountCode(), cash,
            pettyCash.accountCode(), pettyCash,
            revenue.accountCode(), revenue,
            loan.accountCode(), loan,
            expense.accountCode(), expense);

    List<CashFlowPostingMovementClassifier.CashFlowRowMovement> allocatedReceiptMovements =
        CashFlowPostingMovementClassifier.postingMovements(
            accountsByCode,
            posting(
                "posting-proportional-receipt",
                line("1000", JournalLine.EntrySide.DEBIT, "5.00"),
                line("5000", JournalLine.EntrySide.DEBIT, "2.00"),
                line("1010", JournalLine.EntrySide.CREDIT, "2.00"),
                line("4000", JournalLine.EntrySide.CREDIT, "2.00"),
                line("2100", JournalLine.EntrySide.CREDIT, "3.00")));

    assertEquals(2, allocatedReceiptMovements.size());
    Map<String, CashFlowPostingMovementClassifier.CashFlowRowMovement> movementsByAccountCode =
        movementByAccountCode(allocatedReceiptMovements);
    assertMovement(
        movementForAccount(movementsByAccountCode, "4000"),
        CashFlowSectionKind.OPERATING,
        "4000",
        "1.20",
        "0.00");
    assertMovement(
        movementForAccount(movementsByAccountCode, "2100"),
        CashFlowSectionKind.FINANCING,
        "2100",
        "1.80",
        "0.00");

    List<CashFlowPostingMovementClassifier.CashFlowRowMovement> filteredReceiptMovements =
        CashFlowPostingMovementClassifier.postingMovements(
            accountsByCode,
            posting(
                "posting-filtered-receipt",
                line("1000", JournalLine.EntrySide.DEBIT, "2.01"),
                line("5000", JournalLine.EntrySide.DEBIT, "4.99"),
                line("1010", JournalLine.EntrySide.CREDIT, "2.00"),
                line("4000", JournalLine.EntrySide.CREDIT, "2.00"),
                line("2100", JournalLine.EntrySide.CREDIT, "3.00")));

    assertEquals(1, filteredReceiptMovements.size());
    assertMovement(
        filteredReceiptMovements.getFirst(), CashFlowSectionKind.FINANCING, "2100", "0.01", "0.00");
  }

  @Test
  void postingMovements_rejectUndeclaredAccounts() {
    RegisteredAccount cash = cashAccount("1000", "Cash");

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                CashFlowPostingMovementClassifier.postingMovements(
                    Map.of(cash.accountCode(), cash),
                    posting(
                        "posting-undeclared",
                        line("1000", JournalLine.EntrySide.DEBIT, "4.00"),
                        line("4000", JournalLine.EntrySide.CREDIT, "4.00"))));

    assertEquals(
        "Posting posting-undeclared references undeclared account 4000 during cash-flow classification.",
        failure.getMessage());
  }

  private static CashFlowPostingMovementClassifier.CashFlowRowMovement singleMovement(
      Map<AccountCode, RegisteredAccount> accountsByCode, CommittedPosting posting) {
    List<CashFlowPostingMovementClassifier.CashFlowRowMovement> movements =
        CashFlowPostingMovementClassifier.postingMovements(accountsByCode, posting);
    assertEquals(1, movements.size());
    return movements.getFirst();
  }

  private static Map<String, CashFlowPostingMovementClassifier.CashFlowRowMovement>
      movementByAccountCode(List<CashFlowPostingMovementClassifier.CashFlowRowMovement> movements) {
    return movements.stream()
        .collect(
            Collectors.toUnmodifiableMap(
                movement -> movement.account().accountCode().value(), Function.identity()));
  }

  private static CashFlowPostingMovementClassifier.CashFlowRowMovement movementForAccount(
      Map<String, CashFlowPostingMovementClassifier.CashFlowRowMovement> movementsByAccountCode,
      String accountCode) {
    CashFlowPostingMovementClassifier.CashFlowRowMovement movement =
        movementsByAccountCode.get(accountCode);
    if (movement == null) {
      throw new AssertionError("Missing movement for account " + accountCode + ".");
    }
    return movement;
  }

  private static void assertMovement(
      CashFlowPostingMovementClassifier.CashFlowRowMovement movement,
      CashFlowSectionKind expectedSectionKind,
      String expectedAccountCode,
      String expectedDebit,
      String expectedCredit) {
    assertEquals(expectedSectionKind, movement.sectionKind());
    assertEquals(expectedAccountCode, movement.account().accountCode().value());
    assertEquals(balance(expectedDebit, expectedCredit), movement.movement());
  }

  private static RegisteredAccount cashAccount(String accountCode, String accountName) {
    return account(accountCode, accountName, AccountType.ASSET, accountTaxonomy(AccountType.ASSET));
  }

  private static RegisteredAccount account(
      String accountCode,
      String accountName,
      AccountType accountType,
      dev.erst.fingrind.core.AccountTaxonomy accountTaxonomy) {
    return registeredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountTaxonomy,
        true,
        DECLARED_AT);
  }

  private static CommittedPosting posting(String postingId, JournalLine... lines) {
    return posting(postingId, PostingOriginKind.DIRECT_JOURNAL, lines);
  }

  private static CommittedPosting posting(
      String postingId, PostingOriginKind postingOriginKind, JournalLine... lines) {
    return new CommittedPosting(
        new PostingId(postingId),
        new JournalEntry(EFFECTIVE_DATE, List.of(lines)),
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        postingOriginKind,
        accountingEvidence(postingId),
        provenance(postingId));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  private static CurrencyBalance balance(String debit, String credit) {
    return BalanceMath.currencyBalance(
        EUR, Money.parse("EUR", debit).minorUnits(), Money.parse("EUR", credit).minorUnits());
  }

  private static CommittedProvenance provenance(String token) {
    return new CommittedProvenance(
        new RequestProvenance(
            new CommandId("command-" + token),
            new IdempotencyKey("idem-" + token),
            new CausationId("cause-" + token),
            Optional.of(new CorrelationId("corr-" + token))),
        DECLARED_AT,
        SourceChannel.CLI);
  }
}
