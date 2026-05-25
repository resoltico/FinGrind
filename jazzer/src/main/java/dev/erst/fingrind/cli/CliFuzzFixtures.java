package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.BookReadService;
import dev.erst.fingrind.executor.LedgerPlanService;
import dev.erst.fingrind.executor.PostEntryCommandTranslator;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.PostingLookupStore;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shared helpers for FinGrind Jazzer harnesses that start from CLI request JSON. */
public final class CliFuzzFixtures {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);

  private CliFuzzFixtures() {}

  /** Parses one CLI request payload from bytes using the same reader used by the production CLI. */
  public static PostEntryCommand readPostEntryCommand(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    return new CliRequestReader(new ByteArrayInputStream(input)).readPostEntryCommand(Path.of("-"));
  }

  /** Derives the local bookkeeping posting command for one published post-entry command. */
  public static dev.erst.fingrind.executor.bookkeeping.PostingCommand bookkeepingCommand(
      PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return PostEntryCommandTranslator.toPostingCommand(command);
  }

  /** Returns the derived journal entry carried by one published command. */
  public static dev.erst.fingrind.core.JournalEntry journalEntry(PostEntryCommand command) {
    return bookkeepingCommand(command).journalEntry();
  }

  /** Returns the derived posting kind carried by one published command. */
  public static PostingKind postingKind(PostEntryCommand command) {
    return bookkeepingCommand(command).postingKind();
  }

  /** Returns the derived reversal target carried by one published command. */
  public static Optional<ReversalReference> reversalReference(PostEntryCommand command) {
    return bookkeepingCommand(command).postingLineage().reversalReference();
  }

  /** Returns the derived posting lineage carried by one published command. */
  public static dev.erst.fingrind.contract.bookkeeping.PostingLineage postingLineage(
      PostEntryCommand command) {
    return BookkeepingPublishedLanguageTranslator.toPublished(
        bookkeepingCommand(command).postingLineage());
  }

  /** Parses one ledger-plan payload from bytes using the production CLI request reader. */
  public static LedgerPlan readLedgerPlan(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    return new CliRequestReader(new ByteArrayInputStream(input)).readLedgerPlan(Path.of("-"));
  }

  /** Returns a deterministic posting-id generator for one fuzz iteration. */
  public static PostingIdGenerator postingIdGenerator(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    String postingId = UUID.nameUUIDFromBytes(input).toString();
    return () -> new PostingId(postingId);
  }

  /** Returns the deterministic clock shared by Jazzer harnesses and regression replay. */
  public static Clock fixedClock() {
    return FIXED_CLOCK;
  }

  /** Returns the canonical book identity used by Jazzer lifecycle setup. */
  public static BookIdentity bookIdentity() {
    return bookIdentity(CurrencyUnit.of("EUR"));
  }

  /** Returns the canonical book identity used by Jazzer lifecycle setup for one currency. */
  public static BookIdentity bookIdentity(CurrencyUnit functionalCurrency) {
    return new BookIdentity(
        new EntityProfile(
            new BookEntityName("Acme Studio"),
            List.of(new BusinessActivityTag("translation-services"))),
        Objects.requireNonNull(functionalCurrency, "functionalCurrency"),
        FiscalYearStart.parse("01-01"));
  }

  /** Returns the canonical open-book command used by workflow and replay setup. */
  public static OpenBookCommand openBookCommand() {
    return openBookCommand(CurrencyUnit.of("EUR"));
  }

  /** Returns the canonical open-book command used by workflow and replay setup for one currency. */
  public static OpenBookCommand openBookCommand(CurrencyUnit functionalCurrency) {
    return new OpenBookCommand(bookIdentity(functionalCurrency));
  }

  /** Creates the fixed-clock administration service used by lifecycle-aware harnesses. */
  public static <T extends BookLifecycleReader & BookAdministrationStore & AccountCatalogStore>
      BookAdministrationService administrationService(T bookStore) {
    Objects.requireNonNull(bookStore, "bookStore must not be null");
    return new BookAdministrationService(bookStore, bookStore, bookStore, fixedClock());
  }

  /** Creates the fixed-clock posting service used by workflow fuzz harnesses and replay. */
  public static PostingApplicationService postingApplicationService(
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator) {
    Objects.requireNonNull(validationStore, "validationStore must not be null");
    Objects.requireNonNull(commitStore, "commitStore must not be null");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator must not be null");
    return new PostingApplicationService(
        validationStore, commitStore, postingIdGenerator, fixedClock());
  }

  /** Creates the fixed-clock ledger-plan service used by plan fuzz harnesses and replay. */
  public static <T extends BookAdministrationStore & AccountCatalogStore>
      LedgerPlanService ledgerPlanService(
          LedgerPlanTransaction transactionStore,
          T administrationStore,
          BookkeepingReadStore readStore,
          PostingValidationStore validationStore,
          PostingCommitStore commitStore,
          PostingIdGenerator postingIdGenerator) {
    Objects.requireNonNull(transactionStore, "transactionStore must not be null");
    Objects.requireNonNull(administrationStore, "administrationStore must not be null");
    Objects.requireNonNull(readStore, "readStore must not be null");
    Objects.requireNonNull(validationStore, "validationStore must not be null");
    Objects.requireNonNull(commitStore, "commitStore must not be null");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator must not be null");
    return new LedgerPlanService(
        transactionStore,
        administrationStore,
        administrationStore,
        readStore,
        validationStore,
        commitStore,
        postingIdGenerator,
        fixedClock());
  }

  /** Opens one book and fails fast if lifecycle setup drifts unexpectedly. */
  public static void openBook(BookAdministrationService administrationService) {
    openBook(administrationService, CurrencyUnit.of("EUR"));
  }

  /** Opens one book in the supplied functional currency and fails fast on lifecycle drift. */
  public static void openBook(
      BookAdministrationService administrationService, CurrencyUnit functionalCurrency) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    OpenBookResult result =
        BookkeepingPublishedLanguageTranslator.toPublished(
            administrationService.openBook(bookIdentity(functionalCurrency)));
    OpenBookResult.Opened opened =
        switch (result) {
          case OpenBookResult.Opened accepted -> accepted;
          case OpenBookResult.Rejected _ ->
              throw new IllegalStateException("Lifecycle setup failed to initialize the book.");
        };
    if (!opened.initializedAt().equals(fixedClock().instant())) {
      throw new IllegalStateException("Lifecycle setup used an unexpected initialized-at instant.");
    }
  }

  /**
   * Declares every distinct posting account so the final write path can exercise business rules.
   */
  public static List<DeclaredAccount> declarePostingAccounts(
      BookAdministrationService administrationService, PostEntryCommand command) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    return declarePostingAccountCommands(command).stream()
        .map(declareAccountCommand -> declareAccount(administrationService, declareAccountCommand))
        .map(CliFuzzFixtures::requireDeclaredAccount)
        .toList();
  }

  /** Returns deterministic declare-account commands for every distinct posting account. */
  public static List<DeclareAccountCommand> declarePostingAccountCommands(
      PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return switch (command.entry()) {
      case BookkeepingEntry.CashRevenue event ->
          List.of(
              syntheticDeclareAccountCommand(
                  event.cashAccountCode(),
                  AccountType.ASSET,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET)),
              syntheticDeclareAccountCommand(
                  event.revenueAccountCode(),
                  AccountType.REVENUE,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE)));
      case BookkeepingEntry.CashExpense event ->
          List.of(
              syntheticDeclareAccountCommand(
                  event.expenseAccountCode(),
                  AccountType.EXPENSE,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE)),
              syntheticDeclareAccountCommand(
                  event.cashAccountCode(),
                  AccountType.ASSET,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET)));
      case BookkeepingEntry.EquityContribution event ->
          List.of(
              syntheticDeclareAccountCommand(
                  event.cashAccountCode(),
                  AccountType.ASSET,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET)),
              syntheticDeclareAccountCommand(
                  event.equityAccountCode(),
                  AccountType.EQUITY,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(
                      FinancialPositionLineClassification.EQUITY_CONTRIBUTION)));
      case BookkeepingEntry.EquityWithdrawal event ->
          List.of(
              syntheticDeclareAccountCommand(
                  event.equityAccountCode(),
                  AccountType.EQUITY,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(FinancialPositionLineClassification.EQUITY_WITHDRAWAL)),
              syntheticDeclareAccountCommand(
                  event.cashAccountCode(),
                  AccountType.ASSET,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET)));
      case BookkeepingEntry.OpeningBalanceAdjustment _ ->
          distinctJournalLineAccountDeclarations(command);
      case BookkeepingEntry.CorrectionAdjustment _ ->
          distinctJournalLineAccountDeclarations(command);
      case BookkeepingEntry.ReversalAdjustment _ -> distinctJournalLineAccountDeclarations(command);
    };
  }

  /** Returns the first journal-line account code for lifecycle assertions. */
  public static AccountCode firstAccountCode(PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return journalEntry(command).lines().getFirst().accountCode();
  }

  /** Reactivates one account with an updated display name and asserts the durable shape. */
  public static DeclaredAccount reactivateAccount(
      BookAdministrationService administrationService, DeclaredAccount account) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    Objects.requireNonNull(account, "account must not be null");
    DeclareAccountResult result =
        declareAccount(
            administrationService,
            new DeclareAccountCommand(
                account.accountCode(),
                new AccountName(account.accountName().value() + " restored"),
                account.accountType(),
                account.accountRole(),
                account.accountTaxonomy()));
    DeclaredAccount restoredAccount = requireDeclaredAccount(result);
    if (!restoredAccount.active()) {
      throw new IllegalStateException("Account reactivation did not restore the active flag.");
    }
    if (!restoredAccount.declaredAt().equals(account.declaredAt())) {
      throw new IllegalStateException(
          "Account reactivation changed the original declared-at timestamp.");
    }
    return restoredAccount;
  }

  /** Runs one posting preflight through the internal bookkeeping translation boundary. */
  public static PreflightEntryResult preflight(
      PostingApplicationService applicationService, PostEntryCommand command) {
    Objects.requireNonNull(applicationService, "applicationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    return applicationService.preflight(command);
  }

  /** Runs one posting commit through the internal bookkeeping translation boundary. */
  public static CommitEntryResult commit(
      PostingApplicationService applicationService, PostEntryCommand command) {
    Objects.requireNonNull(applicationService, "applicationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    return applicationService.commit(command);
  }

  /**
   * Translates an optional stored posting from the bookkeeping model into the public fact shape.
   */
  public static Optional<PostingFact> publishedStoredPosting(
      Optional<CommittedPosting> storedPosting) {
    Objects.requireNonNull(storedPosting, "storedPosting must not be null");
    return storedPosting.map(BookkeepingPublishedLanguageTranslator::toPublished);
  }

  /**
   * Loads one stored posting from a posting session and translates it into the public fact shape.
   */
  public static Optional<PostingFact> publishedStoredPosting(
      PostingLookupStore bookStore, dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
    Objects.requireNonNull(bookStore, "bookStore must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    return publishedStoredPosting(bookStore.findExistingPosting(idempotencyKey));
  }

  /** Lists accounts and fails fast if the registry surface is not in the expected state. */
  public static List<DeclaredAccount> listAccounts(BookkeepingReadStore bookStore) {
    Objects.requireNonNull(bookStore, "bookStore must not be null");
    List<DeclaredAccount> accounts = new java.util.ArrayList<>();
    BookReadService readService = new BookReadService(bookStore);
    Optional<AccountPageCursor> cursor = Optional.empty();
    while (true) {
      ListAccountsResult result = listAccountsPage(readService, cursor);
      ListAccountsResult.Listed listed =
          switch (result) {
            case ListAccountsResult.Listed accepted -> accepted;
            case ListAccountsResult.Rejected _ ->
                throw new IllegalStateException(
                    "Lifecycle setup failed to list declared accounts.");
          };
      accounts.addAll(listed.page().accounts());
      if (!listed.page().hasMore()) {
        return List.copyOf(accounts);
      }
      cursor = listed.page().nextCursor();
    }
  }

  private static ListAccountsResult listAccountsPage(
      BookReadService readService, Optional<AccountPageCursor> cursor) {
    return readService.listAccounts(
        new ListAccountsQuery(InteractionLimits.PAGE_LIMIT_MAX, cursor));
  }

  private static DeclaredAccount requireDeclaredAccount(DeclareAccountResult result) {
    return switch (result) {
      case DeclareAccountResult.Declared declared -> declared.account();
      case DeclareAccountResult.Rejected _ ->
          throw new IllegalStateException("Lifecycle setup failed to declare an account.");
    };
  }

  private static DeclareAccountResult declareAccount(
      BookAdministrationService administrationService, DeclareAccountCommand command) {
    return BookkeepingPublishedLanguageTranslator.toPublished(
        administrationService.declareAccount(
            BookkeepingPublishedLanguageTranslator.fromPublished(command)));
  }

  private static List<DeclareAccountCommand> distinctJournalLineAccountDeclarations(
      PostEntryCommand command) {
    return journalEntry(command).lines().stream()
        .map(line -> line.accountCode())
        .distinct()
        .map(CliFuzzFixtures::syntheticDeclareAccountCommand)
        .toList();
  }

  private static AccountName syntheticAccountName(AccountCode accountCode) {
    return new AccountName("Synthetic " + accountCode.value());
  }

  private static DeclareAccountCommand syntheticDeclareAccountCommand(AccountCode accountCode) {
    AccountType accountType = syntheticAccountType(accountCode);
    AccountRole accountRole = syntheticAccountRole(accountCode);
    return syntheticDeclareAccountCommand(
        accountCode, accountType, accountRole, syntheticAccountTaxonomy(accountType));
  }

  private static DeclareAccountCommand syntheticDeclareAccountCommand(
      AccountCode accountCode,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy) {
    return new DeclareAccountCommand(
        accountCode, syntheticAccountName(accountCode), accountType, accountRole, accountTaxonomy);
  }

  private static AccountRole syntheticAccountRole(AccountCode accountCode) {
    int bucket = Math.floorMod(accountCode.value().hashCode(), 4);
    if (bucket == 0) {
      return AccountRole.CONTRA;
    }
    return AccountRole.ORDINARY;
  }

  private static AccountType syntheticAccountType(AccountCode accountCode) {
    String normalized = Objects.requireNonNull(accountCode, "accountCode").value().strip();
    if (Character.isDigit(normalized.charAt(0))) {
      return switch (normalized.charAt(0)) {
        case '1' -> AccountType.ASSET;
        case '2' -> AccountType.LIABILITY;
        case '3' -> AccountType.EQUITY;
        case '4' -> AccountType.REVENUE;
        case '5', '6', '7', '8', '9' -> AccountType.EXPENSE;
        default -> hashedAccountType(normalized);
      };
    }
    return hashedAccountType(normalized);
  }

  private static AccountType hashedAccountType(String normalizedAccountCode) {
    return switch (Math.floorMod(normalizedAccountCode.hashCode(), 5)) {
      case 0 -> AccountType.ASSET;
      case 1 -> AccountType.LIABILITY;
      case 2 -> AccountType.EQUITY;
      case 3 -> AccountType.REVENUE;
      default -> AccountType.EXPENSE;
    };
  }

  private static AccountTaxonomy syntheticAccountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET);
      case LIABILITY ->
          syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY);
      case EQUITY -> syntheticAccountTaxonomy(FinancialPositionLineClassification.OTHER_EQUITY);
      case REVENUE -> syntheticAccountTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE);
      case EXPENSE -> syntheticAccountTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE);
    };
  }

  private static AccountTaxonomy syntheticAccountTaxonomy(
      FinancialPositionLineClassification classification) {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(Objects.requireNonNull(classification, "classification")),
        Optional.empty());
  }

  private static AccountTaxonomy syntheticAccountTaxonomy(
      ProfitAndLossLineClassification classification) {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(Objects.requireNonNull(classification, "classification")));
  }
}
