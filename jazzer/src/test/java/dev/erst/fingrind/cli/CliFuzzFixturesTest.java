package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
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
    var correctionCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzHarnessTestSupport.correctionAdjustmentRequestJson(
                    new CliFuzzHarnessTestSupport.CorrectionAdjustmentRequestInput(
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
                            "credit-note",
                            "2026-04-08",
                            "actor-manual-1",
                            "HUMAN",
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
    PostEntryCommand ownerContributionCommand =
        withEntry(
            typedCommand,
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-10"),
                new AccountCode("1100"),
                new AccountCode("3100"),
                new MonetaryAmount("CAD", "750")));
    PostEntryCommand ownerDrawCommand =
        withEntry(
            typedCommand,
            new BookkeepingEntry.OwnerDraw(
                LocalDate.parse("2026-04-11"),
                new AccountCode("3100"),
                new AccountCode("1100"),
                new MonetaryAmount("USD", "55")));
    PostEntryCommand openingBalanceCommand =
        withEntry(
            typedCommand,
            new BookkeepingEntry.OpeningBalanceAdjustment(
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-12"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            dev.erst.fingrind.core.Money.parse("SEK", "42.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("3000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            dev.erst.fingrind.core.Money.parse("SEK", "42.00"))))));
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
    assertEquals("GBP", CliFuzzFixtures.journalEntry(correctionCommand).currencyUnit().code());
    assertEquals(
        "CHF",
        CliFuzzFixtures.bookkeepingCommand(cashExpenseCommand)
            .journalEntry()
            .currencyUnit()
            .code());
    assertEquals(
        "CAD",
        CliFuzzFixtures.bookkeepingCommand(ownerContributionCommand)
            .journalEntry()
            .currencyUnit()
            .code());
    assertEquals(
        "USD",
        CliFuzzFixtures.bookkeepingCommand(ownerDrawCommand).journalEntry().currencyUnit().code());
    assertEquals(
        "SEK",
        CliFuzzFixtures.bookkeepingCommand(openingBalanceCommand)
            .journalEntry()
            .currencyUnit()
            .code());
    assertEquals(
        "NOK",
        CliFuzzFixtures.bookkeepingCommand(reversalCommand).journalEntry().currencyUnit().code());
    assertEquals(PostingKind.STANDARD, CliFuzzFixtures.postingKind(correctionCommand));
    assertEquals(PostingKind.OPENING_BALANCE, CliFuzzFixtures.postingKind(openingBalanceCommand));
  }

  @Test
  void lifecycle_helpers_manage_books_accounts_and_fail_fast_on_drift() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService administrationService =
          CliFuzzFixtures.administrationService(bookSession);
      var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));

      CliFuzzFixtures.openBook(administrationService);
      assertThrows(
          IllegalStateException.class, () -> CliFuzzFixtures.openBook(administrationService));

      java.util.List<DeclaredAccount> declaredAccounts =
          CliFuzzFixtures.declarePostingAccounts(administrationService, command);
      assertEquals(2, declaredAccounts.size());
      assertEquals(
          CliFuzzFixtures.firstAccountCode(command), declaredAccounts.getFirst().accountCode());
      assertEquals(2, CliFuzzFixtures.listAccounts(bookSession).size());

      DeclaredAccount firstAccount = declaredAccounts.getFirst();
      bookSession.deactivateAccount(firstAccount.accountCode());
      DeclaredAccount restoredAccount =
          CliFuzzFixtures.reactivateAccount(administrationService, firstAccount);
      assertTrue(restoredAccount.active());
      assertEquals(firstAccount.declaredAt(), restoredAccount.declaredAt());
    }
  }

  @Test
  void synthetic_posting_account_commands_never_require_removed_account_roles() {
    var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));

    assertTrue(
        CliFuzzFixtures.declarePostingAccountCommands(command).stream()
            .noneMatch(
                declareAccountCommand ->
                    declareAccountCommand.accountRole() != AccountRole.ORDINARY
                        && declareAccountCommand.accountRole() != AccountRole.CONTRA));
  }

  @Test
  void lifecycle_helpers_reject_uninitialized_service_states() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService administrationService =
          CliFuzzFixtures.administrationService(bookSession);
      var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));
      DeclaredAccount account =
          declaredAccount(new AccountCode("1000"), AccountType.ASSET, AccountRole.ORDINARY, false);

      assertThrows(
          IllegalStateException.class,
          () -> CliFuzzFixtures.declarePostingAccounts(administrationService, command));
      assertThrows(IllegalStateException.class, () -> CliFuzzFixtures.listAccounts(bookSession));
      assertThrows(
          IllegalStateException.class,
          () -> CliFuzzFixtures.reactivateAccount(administrationService, account));
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
              public BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity) {
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
                    CliFuzzFixtures.bookIdentity()),
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
                    CliFuzzFixtures.bookIdentity()),
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
        IllegalStateException.class, () -> CliFuzzFixtures.openBook(driftedOpenBookService));
    assertThrows(
        IllegalStateException.class,
        () -> CliFuzzFixtures.reactivateAccount(inactiveReactivationService, account));
    assertThrows(
        IllegalStateException.class,
        () -> CliFuzzFixtures.reactivateAccount(changedDeclaredAtService, account));
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
                7, 1, 1, Instant.EPOCH, CliFuzzFixtures.bookIdentity());
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

    assertEquals(List.of(firstAccount, secondAccount), CliFuzzFixtures.listAccounts(pagedStore));
    assertEquals(List.of(Optional.empty(), Optional.of(nextCursor)), cursors);
  }

  private abstract static class AbstractBookAdministrationStoreStub
      implements BookAdministrationStore, dev.erst.fingrind.executor.spi.AccountCatalogStore {
    @Override
    public BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity) {
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
          "businessActivityTags": ["translation-services"],
          "functionalCurrency": "%s",
          "fiscalYearStart": "01-01",
          "policyProfile": "INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1"
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
