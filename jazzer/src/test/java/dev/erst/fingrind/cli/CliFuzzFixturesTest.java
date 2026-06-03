package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.InMemoryBookFixtureMutations;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Covers deterministic helper behavior shared by Jazzer CLI-backed harnesses. */
class CliFuzzFixturesTest {
  @Test
  void parsing_and_posting_id_helpers_are_deterministic() {
    byte[] requestBytes = basicValidRequest().getBytes(UTF_8);

    assertEquals(
        "2026-04-07",
        CliFuzzFixtures.journalEntry(CliFuzzFixtures.readPostEntryCommand(requestBytes))
            .effectiveDate()
            .toString());
    assertEquals(
        "plan-1",
        CliFuzzFixtures.readLedgerPlan(basicValidLedgerPlan().getBytes(UTF_8)).planId().value());
    assertEquals(
        CliFuzzFixtures.postingIdGenerator(requestBytes).nextPostingId().value(),
        CliFuzzFixtures.postingIdGenerator(requestBytes).nextPostingId().value());
    assertNotEquals(
        CliFuzzFixtures.postingIdGenerator(requestBytes).nextPostingId().value(),
        CliFuzzFixtures.postingIdGenerator("other".getBytes(UTF_8)).nextPostingId().value());
    assertEquals(Instant.parse("2026-04-07T12:00:00Z"), CliFuzzFixtures.fixedClock().instant());
    assertThrows(
        NullPointerException.class, () -> CliFuzzFixtures.readPostEntryCommand(nullValue()));
    assertThrows(NullPointerException.class, () -> CliFuzzFixtures.readLedgerPlan(nullValue()));
    assertThrows(NullPointerException.class, () -> CliFuzzFixtures.postingIdGenerator(nullValue()));
  }

  @Test
  void bookkeeping_helpers_follow_typed_and_administrative_entry_currency_shapes() {
    var typedCommand =
        CliFuzzFixtures.readPostEntryCommand(CliFuzzHarnessTestSupport.validJpyRequestBytes());
    var openingPositionCommand =
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
                            "accountCode": "6000",
                            "side": "DEBIT",
                            "amount": {
                              "currencyCode": "GBP",
                              "minorUnits": "12345"
                            }
                          }
                        ]
                        """,
                        new CliFuzzHarnessTestSupport.RequestContext(
                            "document-idem-manual-1",
                            "opening-balance-sheet",
                            "2026-04-08",
                            "actor-manual-1",
                            "PERSON",
                            "command-manual-1",
                            "idem-manual-1",
                            "cause-manual-1",
                            null)))
                .getBytes(UTF_8));
    PostEntryCommand cashExpenseCommand =
        withEntry(
            typedCommand,
            new BookkeepingEntry.CashExpense(
                LocalDate.parse("2026-04-09"),
                new AccountCode("6100"),
                new AccountCode("1100"),
                new MonetaryAmount("CHF", "42")));
    PostEntryCommand equityContributionCommand =
        withEntry(
            typedCommand,
            new BookkeepingEntry.EquityContribution(
                LocalDate.parse("2026-04-10"),
                new AccountCode("1100"),
                new AccountCode("3100"),
                new MonetaryAmount("CAD", "750")));
    PostEntryCommand equityWithdrawalCommand =
        withEntry(
            typedCommand,
            new BookkeepingEntry.EquityWithdrawal(
                LocalDate.parse("2026-04-11"),
                new AccountCode("3100"),
                new AccountCode("1100"),
                new MonetaryAmount("USD", "55")));
    PostEntryCommand structuredOpeningPositionCommand =
        withEntry(
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
    PostEntryCommand reversalCommand =
        withEntry(
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

    assertEquals("JPY", CliFuzzFixtures.journalEntry(typedCommand).currencyUnit().code());
    assertEquals(
        "JPY",
        CliFuzzFixtures.bookkeepingCommand(typedCommand).journalEntry().currencyUnit().code());
    assertEquals("GBP", CliFuzzFixtures.journalEntry(openingPositionCommand).currencyUnit().code());
    assertEquals(
        "CHF",
        CliFuzzFixtures.bookkeepingCommand(cashExpenseCommand)
            .journalEntry()
            .currencyUnit()
            .code());
    assertEquals(
        "CAD",
        CliFuzzFixtures.bookkeepingCommand(equityContributionCommand)
            .journalEntry()
            .currencyUnit()
            .code());
    assertEquals(
        "USD",
        CliFuzzFixtures.bookkeepingCommand(equityWithdrawalCommand)
            .journalEntry()
            .currencyUnit()
            .code());
    assertEquals(
        "SEK",
        CliFuzzFixtures.bookkeepingCommand(structuredOpeningPositionCommand)
            .journalEntry()
            .currencyUnit()
            .code());
    assertEquals(
        "NOK",
        CliFuzzFixtures.bookkeepingCommand(reversalCommand).journalEntry().currencyUnit().code());
    assertEquals(PostingKind.OPENING_BALANCE, CliFuzzFixtures.postingKind(openingPositionCommand));
    assertEquals(
        PostingKind.OPENING_BALANCE, CliFuzzFixtures.postingKind(structuredOpeningPositionCommand));
    assertEquals(PostingKind.STANDARD, CliFuzzFixtures.postingKind(reversalCommand));
  }

  @Test
  void lifecycle_helpers_manage_books_accounts_and_fail_fast_on_drift() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService administrationService =
          CliFuzzWorkflowFixtures.administrationService(bookSession);
      var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));

      CliFuzzWorkflowFixtures.openBook(administrationService);
      assertThrows(
          IllegalStateException.class,
          () -> CliFuzzWorkflowFixtures.openBook(administrationService));

      java.util.List<DeclaredAccount> declaredAccounts =
          CliFuzzAccountFixtures.declarePostingAccounts(administrationService, command);
      assertEquals(2, declaredAccounts.size());
      assertEquals(
          CliFuzzSyntheticAccountFixtures.firstAccountCode(command),
          declaredAccounts.getFirst().accountCode());
      assertTrue(CliFuzzAccountFixtures.listAccounts(bookSession).containsAll(declaredAccounts));

      DeclaredAccount firstAccount = declaredAccounts.getFirst();
      InMemoryBookFixtureMutations.deactivateAccount(bookSession, firstAccount.accountCode());
      DeclaredAccount restoredAccount =
          CliFuzzAccountFixtures.reactivateAccount(administrationService, firstAccount);
      assertTrue(restoredAccount.active());
      assertEquals(firstAccount.declaredAt(), restoredAccount.declaredAt());
    }
  }

  @Test
  void synthetic_posting_account_commands_never_require_removed_account_roles() {
    var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));

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
        CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));
    PostEntryCommand cashExpenseCommand =
        withEntry(
            cashRevenueCommand,
            new BookkeepingEntry.CashExpense(
                LocalDate.parse("2026-04-09"),
                new AccountCode("6100"),
                new AccountCode("1100"),
                new MonetaryAmount("CHF", "42")));
    PostEntryCommand equityContributionCommand =
        withEntry(
            cashRevenueCommand,
            new BookkeepingEntry.EquityContribution(
                LocalDate.parse("2026-04-10"),
                new AccountCode("1100"),
                new AccountCode("3100"),
                new MonetaryAmount("CAD", "750")));
    PostEntryCommand equityWithdrawalCommand =
        withEntry(
            cashRevenueCommand,
            new BookkeepingEntry.EquityWithdrawal(
                LocalDate.parse("2026-04-11"),
                new AccountCode("3100"),
                new AccountCode("1100"),
                new MonetaryAmount("USD", "55")));

    assertEquals(
        List.of(
            declaredAccountCommand(
                "1000",
                AccountType.ASSET,
                AccountRole.ORDINARY,
                financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET)),
            declaredAccountCommand(
                "2000",
                AccountType.REVENUE,
                AccountRole.ORDINARY,
                profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(cashRevenueCommand));
    assertEquals(
        List.of(
            declaredAccountCommand(
                "6100",
                AccountType.EXPENSE,
                AccountRole.ORDINARY,
                profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE)),
            declaredAccountCommand(
                "1100",
                AccountType.ASSET,
                AccountRole.ORDINARY,
                financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(cashExpenseCommand));
    assertEquals(
        List.of(
            declaredAccountCommand(
                "1100",
                AccountType.ASSET,
                AccountRole.ORDINARY,
                financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET)),
            declaredAccountCommand(
                "3100",
                AccountType.EQUITY,
                AccountRole.ORDINARY,
                financialPositionTaxonomy(
                    FinancialPositionLineClassification.EQUITY_CONTRIBUTION))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(equityContributionCommand));
    assertEquals(
        List.of(
            declaredAccountCommand(
                "3100",
                AccountType.EQUITY,
                AccountRole.ORDINARY,
                financialPositionTaxonomy(FinancialPositionLineClassification.EQUITY_WITHDRAWAL)),
            declaredAccountCommand(
                "1100",
                AccountType.ASSET,
                AccountRole.ORDINARY,
                financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET))),
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(equityWithdrawalCommand));
  }

  @Test
  void administrative_entry_account_declarations_preserve_distinct_line_order() {
    PostEntryCommand typedCommand =
        CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));
    PostEntryCommand structuredOpeningPositionCommand =
        withEntry(
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
        withEntry(
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
        accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(
                structuredOpeningPositionCommand)));
    assertEquals(
        List.of("5000", "6000"),
        accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(openingPositionCommand)));
    assertEquals(
        List.of("1000", "2000"),
        accountCodes(
            CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(reversalCommand)));
  }

  @Test
  void administrative_entry_account_declarations_follow_entry_semantics() {
    String[] accountCodes = {
      "2000",
      "3000",
      "4000",
      "5000",
      zeroLeadingFallbackCode(),
      hashedFallbackCodeForBucket(0),
      hashedFallbackCodeForBucket(1),
      hashedFallbackCodeForBucket(2),
      hashedFallbackCodeForBucket(3),
      hashedFallbackCodeForBucket(4)
    };
    PostEntryCommand openingPositionCommand =
        openAccountingPositionCommand(
            accountCodes[0],
            accountCodes[1],
            accountCodes[2],
            accountCodes[3],
            accountCodes[4],
            accountCodes[5],
            accountCodes[6],
            accountCodes[7],
            accountCodes[8],
            accountCodes[9]);

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
                reversalAdjustmentCommand(accountCodes))
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    declareAccountCommand -> declareAccountCommand.accountCode().value(),
                    DeclareAccountCommand::accountType));
    for (String accountCode : reversalAccountTypes.keySet()) {
      assertEquals(
          expectedSyntheticAccountType(accountCode), reversalAccountTypes.get(accountCode));
    }
  }

  @Test
  void lifecycle_helpers_reject_uninitialized_service_states() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService administrationService =
          CliFuzzWorkflowFixtures.administrationService(bookSession);
      var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));
      DeclaredAccount account =
          declaredAccount(new AccountCode("1000"), AccountType.ASSET, AccountRole.ORDINARY, false);

      assertThrows(
          IllegalStateException.class,
          () -> CliFuzzAccountFixtures.declarePostingAccounts(administrationService, command));
      assertThrows(
          IllegalStateException.class, () -> CliFuzzAccountFixtures.listAccounts(bookSession));
      assertThrows(
          IllegalStateException.class,
          () -> CliFuzzAccountFixtures.reactivateAccount(administrationService, account));
    }
  }

  @Test
  void lifecycle_helpers_reject_drifted_openBook_and_reactivateAccount_shapes() {
    DeclaredAccount account =
        declaredAccount(new AccountCode("1000"), AccountType.ASSET, AccountRole.ORDINARY, false);

    BookAdministrationService driftedOpenBookService =
        new BookAdministrationService(
            () -> new dev.erst.fingrind.executor.spi.BookLifecycleInspection.Missing(7),
            new AbstractBookAdministrationStoreStub() {
              @Override
              public BookOpeningOutcome openBook(
                  Instant initializedAt,
                  BookIdentity bookIdentity,
                  List<dev.erst.fingrind.executor.bookkeeping.AccountDeclaration> seededAccounts) {
                return new BookOpeningOutcome.Opened(initializedAt.plusSeconds(1), bookIdentity);
              }
            },
            new AbstractBookAdministrationStoreStub() {},
            CliFuzzFixtures.fixedClock());
    BookAdministrationService inactiveReactivationService =
        new BookAdministrationService(
            () ->
                new dev.erst.fingrind.executor.spi.BookLifecycleInspection.Initialized(
                    dev.erst.fingrind.contract.runtime.BookFormatContract.APPLICATION_ID,
                    dev.erst.fingrind.contract.runtime.BookFormatContract.FORMAT_VERSION,
                    dev.erst.fingrind.contract.runtime.BookFormatContract.FORMAT_VERSION,
                    CliFuzzFixtures.fixedClock().instant(),
                    CliFuzzWorkflowFixtures.bookIdentity()),
            new AbstractBookAdministrationStoreStub() {
              @Override
              public AccountDeclarationOutcome declareAccount(
                  AccountCode accountCode,
                  AccountName accountName,
                  AccountType accountType,
                  AccountRole accountRole,
                  AccountTaxonomy accountTaxonomy,
                  Instant declaredAt) {
                return new AccountDeclarationOutcome.Declared(
                    new RegisteredAccount(
                        accountCode,
                        accountName,
                        accountType,
                        accountRole,
                        accountTaxonomy,
                        false,
                        declaredAt));
              }
            },
            new AbstractBookAdministrationStoreStub() {},
            CliFuzzFixtures.fixedClock());
    BookAdministrationService changedDeclaredAtService =
        new BookAdministrationService(
            () ->
                new dev.erst.fingrind.executor.spi.BookLifecycleInspection.Initialized(
                    dev.erst.fingrind.contract.runtime.BookFormatContract.APPLICATION_ID,
                    dev.erst.fingrind.contract.runtime.BookFormatContract.FORMAT_VERSION,
                    dev.erst.fingrind.contract.runtime.BookFormatContract.FORMAT_VERSION,
                    CliFuzzFixtures.fixedClock().instant(),
                    CliFuzzWorkflowFixtures.bookIdentity()),
            new AbstractBookAdministrationStoreStub() {
              @Override
              public AccountDeclarationOutcome declareAccount(
                  AccountCode accountCode,
                  AccountName accountName,
                  AccountType accountType,
                  AccountRole accountRole,
                  AccountTaxonomy accountTaxonomy,
                  Instant declaredAt) {
                return new AccountDeclarationOutcome.Declared(
                    new RegisteredAccount(
                        accountCode,
                        accountName,
                        accountType,
                        accountRole,
                        accountTaxonomy,
                        true,
                        declaredAt.plusSeconds(1)));
              }
            },
            new AbstractBookAdministrationStoreStub() {},
            CliFuzzFixtures.fixedClock());

    assertThrows(
        IllegalStateException.class,
        () -> CliFuzzWorkflowFixtures.openBook(driftedOpenBookService));
    assertThrows(
        IllegalStateException.class,
        () -> CliFuzzAccountFixtures.reactivateAccount(inactiveReactivationService, account));
    assertThrows(
        IllegalStateException.class,
        () -> CliFuzzAccountFixtures.reactivateAccount(changedDeclaredAtService, account));
  }

  @Test
  void listAccounts_follows_pagination_until_cursor_is_exhausted() {
    DeclaredAccount firstAccount =
        declaredAccount(new AccountCode("1000"), AccountType.ASSET, AccountRole.ORDINARY, true);
    DeclaredAccount secondAccount =
        declaredAccount(new AccountCode("2000"), AccountType.REVENUE, AccountRole.ORDINARY, true);
    AccountRegistryCursor nextCursor = new AccountRegistryCursor(firstAccount.accountCode());
    AtomicInteger pageCalls = new AtomicInteger();
    List<Optional<AccountRegistryCursor>> cursors = new ArrayList<>();
    BookkeepingReadStore pagedStore =
        new AbstractBookkeepingReadStoreStub() {
          @Override
          public BookLifecycleInspection inspectBook() {
            return new BookLifecycleInspection.Initialized(
                7, 1, 1, Instant.EPOCH, CliFuzzWorkflowFixtures.bookIdentity());
          }

          @Override
          public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
            cursors.add(query.cursor());
            if (pageCalls.getAndIncrement() == 0) {
              return new AccountRegistryPage(
                  List.of(toRegisteredAccount(firstAccount)),
                  query.limit(),
                  Optional.of(nextCursor));
            }
            return new AccountRegistryPage(
                List.of(toRegisteredAccount(secondAccount)), query.limit(), Optional.empty());
          }
        };

    assertEquals(
        List.of(firstAccount, secondAccount), CliFuzzAccountFixtures.listAccounts(pagedStore));
    assertEquals(List.of(Optional.empty(), Optional.of(nextCursor)), cursors);
  }

  private abstract static class AbstractBookAdministrationStoreStub
      implements BookAdministrationStore, dev.erst.fingrind.executor.spi.AccountCatalogStore {
    @Override
    public BookOpeningOutcome openBook(
        Instant initializedAt,
        BookIdentity bookIdentity,
        List<dev.erst.fingrind.executor.bookkeeping.AccountDeclaration> seededAccounts) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        AccountType accountType,
        AccountRole accountRole,
        AccountTaxonomy accountTaxonomy,
        Instant declaredAt) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      return List.of();
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      throw new UnsupportedOperationException("not used");
    }
  }

  private abstract static class AbstractBookkeepingReadStoreStub implements BookkeepingReadStore {
    @Override
    public BookLifecycleInspection inspectBook() {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(
        dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<CommittedPosting> findPosting(dev.erst.fingrind.core.PostingId postingId) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(
        dev.erst.fingrind.core.PostingId priorPostingId) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public PostingHistoryPage listPostings(PostingHistoryQuery query) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public List<AccountCurrencyTotals> accountTotals(
        EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
      throw new UnsupportedOperationException("not used");
    }
  }

  private static RegisteredAccount toRegisteredAccount(DeclaredAccount account) {
    return new RegisteredAccount(
        account.accountCode(),
        account.accountName(),
        account.accountType(),
        account.accountRole(),
        account.accountTaxonomy(),
        account.active(),
        account.declaredAt());
  }

  private static DeclaredAccount declaredAccount(
      AccountCode accountCode, AccountType accountType, AccountRole accountRole, boolean active) {
    return new DeclaredAccount(
        accountCode,
        new AccountName(accountType == AccountType.REVENUE ? "Revenue" : "Cash"),
        accountType,
        accountRole,
        accountTaxonomy(accountType),
        active,
        CliFuzzFixtures.fixedClock().instant());
  }

  private static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty());
      case LIABILITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
      case EXPENSE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    };
  }

  private static String basicValidRequest() {
    return SqliteRoundTripWorkflowTestSupport.basicValidRequest();
  }

  private static List<String> accountCodes(List<DeclareAccountCommand> commands) {
    return commands.stream().map(command -> command.accountCode().value()).toList();
  }

  private static PostEntryCommand openAccountingPositionCommand(String... accountCodes) {
    if (accountCodes.length == 0 || accountCodes.length % 2 != 0) {
      throw new IllegalArgumentException(
          "Open-accounting-position fixture requires an even positive number of account codes.");
    }
    List<BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance> balances =
        new ArrayList<>();
    int splitIndex = accountCodes.length / 2;
    for (int index = 0; index < accountCodes.length; index++) {
      balances.add(
          new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
              new AccountCode(accountCodes[index]),
              index < splitIndex
                  ? dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT
                  : dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
              new MonetaryAmount("EUR", "100")));
    }
    return withEntry(
        CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8)),
        new BookkeepingEntry.OpenAccountingPosition(LocalDate.parse("2026-04-14"), balances));
  }

  private static PostEntryCommand reversalAdjustmentCommand(String... accountCodes) {
    if (accountCodes.length == 0 || accountCodes.length % 2 != 0) {
      throw new IllegalArgumentException(
          "Reversal-adjustment fixture requires an even positive number of account codes.");
    }
    List<dev.erst.fingrind.core.JournalLine> lines = new ArrayList<>();
    int splitIndex = accountCodes.length / 2;
    for (int index = 0; index < accountCodes.length; index++) {
      lines.add(
          new dev.erst.fingrind.core.JournalLine(
              new AccountCode(accountCodes[index]),
              index < splitIndex
                  ? dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT
                  : dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
              dev.erst.fingrind.core.Money.parse("EUR", "1.00")));
    }
    return withEntry(
        CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8)),
        new BookkeepingEntry.ReversalAdjustment(
            new dev.erst.fingrind.core.JournalEntry(LocalDate.parse("2026-04-14"), lines),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new dev.erst.fingrind.core.ReversalReference(
                    new dev.erst.fingrind.core.PostingId("posting-admin-test")),
                new dev.erst.fingrind.core.ReversalReason("administrative fixture"))));
  }

  private static String hashedFallbackCodeForBucket(int bucket) {
    for (int candidate = 0; candidate < 10_000; candidate++) {
      String accountCode = "A" + candidate;
      if (Math.floorMod(accountCode.hashCode(), 5) == bucket) {
        return accountCode;
      }
    }
    throw new IllegalStateException(
        "No synthetic fallback account code found for bucket " + bucket);
  }

  private static String zeroLeadingFallbackCode() {
    return "0fallback";
  }

  private static AccountType expectedSyntheticAccountType(String accountCode) {
    char first = accountCode.charAt(0);
    if (Character.isDigit(first)) {
      return switch (first) {
        case '1' -> AccountType.ASSET;
        case '2' -> AccountType.LIABILITY;
        case '3' -> AccountType.EQUITY;
        case '4' -> AccountType.REVENUE;
        case '5', '6', '7', '8', '9' -> AccountType.EXPENSE;
        default -> hashedAccountType(accountCode);
      };
    }
    return hashedAccountType(accountCode);
  }

  private static AccountType hashedAccountType(String accountCode) {
    return switch (Math.floorMod(accountCode.hashCode(), 5)) {
      case 0 -> AccountType.ASSET;
      case 1 -> AccountType.LIABILITY;
      case 2 -> AccountType.EQUITY;
      case 3 -> AccountType.REVENUE;
      default -> AccountType.EXPENSE;
    };
  }

  private static DeclareAccountCommand declaredAccountCommand(
      String accountCode,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy) {
    return new DeclareAccountCommand(
        new AccountCode(accountCode),
        new AccountName("Synthetic " + accountCode),
        accountType,
        accountRole,
        accountTaxonomy);
  }

  private static AccountTaxonomy financialPositionTaxonomy(
      FinancialPositionLineClassification classification) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(classification),
        Optional.empty());
  }

  private static AccountTaxonomy profitAndLossTaxonomy(
      ProfitAndLossLineClassification classification) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(classification));
  }

  private static String basicValidLedgerPlan() {
    return """
        {
          "planId": "plan-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book",
              "openBook": %s
            }
          ]
        }
        """
        .formatted(canonicalOpenBookJson("EUR"));
  }

  private static String canonicalOpenBookJson(String functionalCurrency) {
    return """
        {
          "entityName": "Acme Studio",
          "functionalCurrency": "%s",
          "fiscalYearStart": "01-01"
        }
        """
        .formatted(functionalCurrency)
        .indent(14)
        .stripLeading();
  }

  @SuppressWarnings({"NullAway", "TypeParameterUnusedInFormals"})
  private static <T> T nullValue() {
    return null;
  }

  private static PostEntryCommand withEntry(PostEntryCommand template, BookkeepingEntry entry) {
    return new PostEntryCommand(
        entry, template.evidence(), template.requestProvenance(), template.sourceChannel());
  }
}
