package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountTaxonomyDoctrine;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CliFuzzSyntheticAccountFixturesTest {
  @Test
  void synthetic_posting_account_commands_useTaxonomyOwnedPolarity() {
    PostEntryCommand command =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));

    assertTrue(
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(command).stream()
            .allMatch(
                declareAccountCommand ->
                    AccountTaxonomyDoctrine.normalBalance(
                            declareAccountCommand.accountType(),
                            declareAccountCommand.accountTaxonomy())
                        != null));
  }

  @Test
  void typed_entry_account_declarations_follow_entry_semantics() {
    PostEntryCommand cashRevenueCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));
    PostEntryCommand cashExpenseCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            cashRevenueCommand,
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-09"),
                new AccountCode("6100"),
                new AccountCode("1100"),
                new MonetaryAmount("CHF", "42"),
                null,
                null,
                null));
    PostEntryCommand equityContributionCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            cashRevenueCommand,
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-10"),
                new AccountCode("1100"),
                new AccountCode("3100"),
                new MonetaryAmount("CAD", "750"),
                null));
    PostEntryCommand equityWithdrawalCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            cashRevenueCommand,
            new BookkeepingEntry.OwnerWithdrawal(
                LocalDate.parse("2026-04-11"),
                new AccountCode("3100"),
                new AccountCode("1100"),
                new MonetaryAmount("USD", "55"),
                null));

    assertEquals(
        List.of(
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "1000",
                AccountType.ASSET,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.CURRENT_ASSET)),
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "2000",
                AccountType.REVENUE,
                CliFuzzSyntheticAccountFixtureSupport.profitAndLossTaxonomy(
                    ProfitAndLossLineClassification.OPERATING_REVENUE))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(cashRevenueCommand));
    assertEquals(
        List.of(
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "6100",
                AccountType.EXPENSE,
                CliFuzzSyntheticAccountFixtureSupport.profitAndLossTaxonomy(
                    ProfitAndLossLineClassification.OPERATING_EXPENSE)),
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "1100",
                AccountType.ASSET,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.CURRENT_ASSET))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(cashExpenseCommand));
    assertEquals(
        List.of(
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "1100",
                AccountType.ASSET,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.CURRENT_ASSET)),
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "3100",
                AccountType.EQUITY,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.EQUITY_CONTRIBUTION))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(equityContributionCommand));
    assertEquals(
        List.of(
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "3100",
                AccountType.EQUITY,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.EQUITY_WITHDRAWAL)),
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "1100",
                AccountType.ASSET,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.CURRENT_ASSET))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(equityWithdrawalCommand));
  }

  @Test
  void typed_entry_account_declarations_collapseSameAccountDuplicates() {
    PostEntryCommand sameAccountCashRevenueCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzRequestSeedSupport.sameAccountCashRevenueRequestBytes());

    assertEquals(
        List.of(
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "1000",
                AccountType.ASSET,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.CURRENT_ASSET))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(
            sameAccountCashRevenueCommand));
  }

  @Test
  void reversal_entry_account_declarations_follow_resolved_reversal_journal() {
    PostEntryCommand reversalCommand =
        CliFuzzFixtureCommandSupport.reversalAdjustmentCommand("1000", "2000");

    assertEquals(
        List.of("1000", "2000"),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(reversalCommand)));
  }

  @Test
  void typed_entry_account_declarations_append_tax_accounts_when_applied_tax_carries_one() {
    PostEntryCommand templateCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));
    PostEntryCommand taxedSaleCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            templateCommand,
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-15"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                null,
                null,
                new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-sale")),
                appliedTax(
                    "vat-lv",
                    "vat-standard-sale",
                    TaxApplicationKind.OUTPUT_SALE,
                    TaxInclusionMode.EXCLUSIVE,
                    "2199")));
    PostEntryCommand taxedExpenseCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            templateCommand,
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-16"),
                new AccountCode("6100"),
                new AccountCode("1100"),
                new MonetaryAmount("EUR", "12100"),
                null,
                new TaxSelection(
                    new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-expense")),
                appliedTax(
                    "vat-lv",
                    "vat-standard-expense",
                    TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
                    TaxInclusionMode.INCLUSIVE,
                    "1307")));
    PostEntryCommand taxedSaleWithoutTaxAccountCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            templateCommand,
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-17"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                null,
                null,
                new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-sale")),
                appliedTax(
                    "vat-lv",
                    "vat-standard-sale",
                    TaxApplicationKind.OUTPUT_SALE,
                    TaxInclusionMode.EXCLUSIVE,
                    null)));

    assertEquals(
        List.of("1000", "4000", "2199"),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(taxedSaleCommand)));
    assertEquals(
        List.of("6100", "1100", "1307"),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(taxedExpenseCommand)));
    assertEquals(
        List.of("1000", "4000"),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(
                taxedSaleWithoutTaxAccountCommand)));
  }

  @Test
  void typed_entry_account_declarations_cover_credit_and_settlement_variants() {
    PostEntryCommand templateCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));
    PostEntryCommand creditSaleCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            templateCommand,
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-18"),
                new AccountCode("1100"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                null,
                null,
                new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-sale")),
                appliedTax(
                    "vat-lv",
                    "vat-standard-sale",
                    TaxApplicationKind.OUTPUT_SALE,
                    TaxInclusionMode.EXCLUSIVE,
                    "2199")));
    PostEntryCommand creditExpenseCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            templateCommand,
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-19"),
                new AccountCode("6100"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "12100"),
                null,
                new TaxSelection(
                    new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-expense")),
                appliedTax(
                    "vat-lv",
                    "vat-standard-expense",
                    TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
                    TaxInclusionMode.INCLUSIVE,
                    "1307")));
    PostEntryCommand receiptWithAdjunctCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            templateCommand,
            new BookkeepingEntry.Receipt(
                LocalDate.parse("2026-04-20"),
                new AccountCode("1000"),
                new AccountCode("1100"),
                new MonetaryAmount("EUR", "1250"),
                new dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct(
                    new AccountCode("6100"), new MonetaryAmount("EUR", "50"))));
    PostEntryCommand paymentWithoutAdjunctCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            templateCommand,
            new BookkeepingEntry.Payment(
                LocalDate.parse("2026-04-21"),
                new AccountCode("2100"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1250"),
                null));

    assertEquals(
        List.of("1100", "4000", "2199"),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(creditSaleCommand)));
    assertEquals(
        List.of("6100", "2100", "1307"),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(creditExpenseCommand)));
    assertEquals(
        List.of("1000", "1100", "6100"),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(
                receiptWithAdjunctCommand)));
    assertEquals(
        List.of("2100", "1000"),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(
                paymentWithoutAdjunctCommand)));
  }

  @Test
  void administrative_entry_account_declarations_preserve_distinct_line_order() {
    PostEntryCommand typedCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));
    PostEntryCommand structuredOpeningPositionCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            typedCommand,
            new BookkeepingEntry.OpeningPosition(
                LocalDate.parse("2026-04-12"),
                List.of(
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("1000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        new MonetaryAmount("SEK", "4200"),
                        null),
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("3000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        new MonetaryAmount("SEK", "4200"),
                        null))));
    PostEntryCommand openingPositionCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzHarnessTestSupport.openAccountingPositionRequestJson(
                    new CliFuzzHarnessTestSupport.OpenAccountingPositionRequestInput(
                        "2026-04-08",
                        """
                        [
                          {
                            "accountCode": "5000",
                            "side": "CREDIT",
                            "amount": {
                              "currencyCode": "GBP",
                              "minorUnits": "12345"
                            }
                          },
                          {
                            "accountCode": "5000",
                            "side": "DEBIT",
                            "amount": {
                              "currencyCode": "GBP",
                              "minorUnits": "2345"
                            }
                          },
                          {
                            "accountCode": "6000",
                            "side": "DEBIT",
                            "amount": {
                              "currencyCode": "GBP",
                              "minorUnits": "10000"
                            }
                          }
                        ]
                        """,
                        new CliFuzzHarnessTestSupport.RequestContext(
                            "document-idem-manual-2",
                            "opening-balance-sheet",
                            "2026-04-08",
                            "command-manual-2",
                            "idem-manual-2",
                            "cause-manual-2",
                            null)))
                .getBytes(UTF_8));
    PostEntryCommand reversalCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            typedCommand,
            new BookkeepingEntry.Reversal(
                LocalDate.parse("2026-04-13"),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new dev.erst.fingrind.core.ReversalReference(
                        new dev.erst.fingrind.core.PostingId(
                            "bdc03c47-a16c-3688-a18f-2445894bbc69")),
                    new dev.erst.fingrind.core.ReversalReason("operator reversal")),
                null,
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-13"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            dev.erst.fingrind.core.Money.parse("NOK", "12.50")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            dev.erst.fingrind.core.Money.parse("NOK", "12.50"))))));

    assertEquals(
        List.of("1000", "3000"),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(
                structuredOpeningPositionCommand)));
    assertEquals(
        List.of("5000", "6000"),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(openingPositionCommand)));
    assertEquals(
        List.of("1000", "2000"),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(reversalCommand)));
  }

  @Test
  void direct_journal_account_declarations_follow_synthetic_line_semantics() {
    PostEntryCommand templateCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));
    String hashedFallbackCode =
        CliFuzzSyntheticAccountFixtureSupport.hashedFallbackCodeForBucket(1);
    PostEntryCommand directJournalCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            templateCommand,
            new BookkeepingEntry.DirectJournal(
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-14"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            dev.erst.fingrind.core.Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode(hashedFallbackCode),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            dev.erst.fingrind.core.Money.parse("EUR", "10.00")))),
                null));

    Map<String, AccountType> directJournalAccountTypes =
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(directJournalCommand).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    declareAccountCommand -> declareAccountCommand.accountCode().value(),
                    DeclareAccountCommand::accountType));

    assertEquals(
        List.of("1000", hashedFallbackCode),
        CliFuzzSyntheticAccountFixtureSupport.accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(directJournalCommand)));
    assertEquals(AccountType.ASSET, directJournalAccountTypes.get("1000"));
    assertEquals(
        CliFuzzSyntheticAccountFixtureSupport.expectedSyntheticAccountType(hashedFallbackCode),
        directJournalAccountTypes.get(hashedFallbackCode));
  }

  @Test
  void administrative_entry_account_declarations_follow_entry_semantics() {
    String[] accountCodes = {
      "2000",
      "3000",
      "4000",
      "5000",
      CliFuzzSyntheticAccountFixtureSupport.zeroLeadingFallbackCode(),
      CliFuzzSyntheticAccountFixtureSupport.hashedFallbackCodeForBucket(0),
      CliFuzzSyntheticAccountFixtureSupport.hashedFallbackCodeForBucket(1),
      CliFuzzSyntheticAccountFixtureSupport.hashedFallbackCodeForBucket(2),
      CliFuzzSyntheticAccountFixtureSupport.hashedFallbackCodeForBucket(3),
      CliFuzzSyntheticAccountFixtureSupport.hashedFallbackCodeForBucket(4)
    };
    PostEntryCommand openingPositionCommand =
        CliFuzzFixtureCommandSupport.openAccountingPositionCommand(accountCodes);

    Map<String, AccountType> openingPositionAccountTypes =
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(openingPositionCommand)
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    declareAccountCommand -> declareAccountCommand.accountCode().value(),
                    DeclareAccountCommand::accountType));
    int splitIndex = accountCodes.length / 2;
    for (int index = 0; index < accountCodes.length; index++) {
      assertEquals(
          index < splitIndex ? AccountType.ASSET : AccountType.LIABILITY,
          openingPositionAccountTypes.get(accountCodes[index]));
    }

    Map<String, AccountType> reversalAccountTypes =
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(
                CliFuzzFixtureCommandSupport.reversalAdjustmentCommand(accountCodes))
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    declareAccountCommand -> declareAccountCommand.accountCode().value(),
                    DeclareAccountCommand::accountType));
    for (String accountCode : reversalAccountTypes.keySet()) {
      assertEquals(
          CliFuzzSyntheticAccountFixtureSupport.expectedSyntheticAccountType(accountCode),
          reversalAccountTypes.get(accountCode));
    }
  }

  private static AppliedTax appliedTax(
      String registrationId,
      String taxCode,
      TaxApplicationKind applicationKind,
      TaxInclusionMode inclusionMode,
      @org.jspecify.annotations.Nullable String taxAccountCode) {
    return new AppliedTax(
        new TaxRegistrationId(registrationId),
        new TaxCode(taxCode),
        new TaxCodeName("Synthetic " + taxCode),
        new TaxRate(210_000),
        inclusionMode,
        applicationKind,
        new MonetaryAmount("EUR", "10000"),
        new MonetaryAmount("EUR", "2100"),
        new MonetaryAmount("EUR", "12100"),
        taxAccountCode == null ? null : new AccountCode(taxAccountCode));
  }
}
