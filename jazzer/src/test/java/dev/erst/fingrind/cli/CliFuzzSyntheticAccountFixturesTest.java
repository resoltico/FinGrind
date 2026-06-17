package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CliFuzzSyntheticAccountFixturesTest {
  @Test
  void synthetic_posting_account_commands_never_require_removed_account_roles() {
    PostEntryCommand command =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));

    assertTrue(
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(command).stream()
            .noneMatch(
                declareAccountCommand ->
                    declareAccountCommand.accountRole() != AccountRole.ORDINARY
                        && declareAccountCommand.accountRole() != AccountRole.POLARITY_INVERTED));
  }

  @Test
  void typed_entry_account_declarations_follow_entry_semantics() {
    PostEntryCommand cashRevenueCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));
    PostEntryCommand cashExpenseCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            cashRevenueCommand,
            BookkeepingEntry.cashExpense(
                LocalDate.parse("2026-04-09"),
                new AccountCode("6100"),
                new AccountCode("1100"),
                new MonetaryAmount("CHF", "42")));
    PostEntryCommand equityContributionCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            cashRevenueCommand,
            BookkeepingEntry.equityContribution(
                LocalDate.parse("2026-04-10"),
                new AccountCode("1100"),
                new AccountCode("3100"),
                new MonetaryAmount("CAD", "750")));
    PostEntryCommand equityWithdrawalCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            cashRevenueCommand,
            BookkeepingEntry.equityWithdrawal(
                LocalDate.parse("2026-04-11"),
                new AccountCode("3100"),
                new AccountCode("1100"),
                new MonetaryAmount("USD", "55")));

    assertEquals(
        List.of(
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "1000",
                AccountType.ASSET,
                AccountRole.ORDINARY,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.CURRENT_ASSET)),
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "2000",
                AccountType.REVENUE,
                AccountRole.ORDINARY,
                CliFuzzSyntheticAccountFixtureSupport.profitAndLossTaxonomy(
                    ProfitAndLossLineClassification.OPERATING_REVENUE))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(cashRevenueCommand));
    assertEquals(
        List.of(
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "6100",
                AccountType.EXPENSE,
                AccountRole.ORDINARY,
                CliFuzzSyntheticAccountFixtureSupport.profitAndLossTaxonomy(
                    ProfitAndLossLineClassification.OPERATING_EXPENSE)),
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "1100",
                AccountType.ASSET,
                AccountRole.ORDINARY,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.CURRENT_ASSET))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(cashExpenseCommand));
    assertEquals(
        List.of(
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "1100",
                AccountType.ASSET,
                AccountRole.ORDINARY,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.CURRENT_ASSET)),
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "3100",
                AccountType.EQUITY,
                AccountRole.ORDINARY,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.EQUITY_CONTRIBUTION))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(equityContributionCommand));
    assertEquals(
        List.of(
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "3100",
                AccountType.EQUITY,
                AccountRole.ORDINARY,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.EQUITY_WITHDRAWAL)),
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "1100",
                AccountType.ASSET,
                AccountRole.ORDINARY,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.CURRENT_ASSET))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(equityWithdrawalCommand));
  }

  @Test
  void typed_entry_account_declarations_collapse_sameAccount_role_collisions() {
    PostEntryCommand sameAccountCashRevenueCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzRequestSeedSupport.sameAccountCashRevenueRequestBytes());

    assertEquals(
        List.of(
            CliFuzzSyntheticAccountFixtureSupport.declaredAccountCommand(
                "1000",
                AccountType.ASSET,
                AccountRole.ORDINARY,
                CliFuzzSyntheticAccountFixtureSupport.financialPositionTaxonomy(
                    FinancialPositionLineClassification.CURRENT_ASSET))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(
            sameAccountCashRevenueCommand));
  }

  @Test
  void administrative_entry_account_declarations_preserve_distinct_line_order() {
    PostEntryCommand typedCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));
    PostEntryCommand structuredOpeningPositionCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            typedCommand,
            new BookkeepingEntry.OpenAccountingPosition(
                LocalDate.parse("2026-04-12"),
                List.of(
                    new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
                        new AccountCode("1000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        new MonetaryAmount("SEK", "4200")),
                    new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
                        new AccountCode("3000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        new MonetaryAmount("SEK", "4200")))));
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
                            "actor-manual-2",
                            "PERSON",
                            "command-manual-2",
                            "idem-manual-2",
                            "cause-manual-2",
                            null)))
                .getBytes(UTF_8));
    PostEntryCommand reversalCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            typedCommand,
            new BookkeepingEntry.ReversalAdjustment(
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
                            dev.erst.fingrind.core.Money.parse("NOK", "12.50")))),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new dev.erst.fingrind.core.ReversalReference(
                        new dev.erst.fingrind.core.PostingId("posting-1")),
                    new dev.erst.fingrind.core.ReversalReason("operator reversal"))));

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
            new BookkeepingEntry.Journal(
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
}
