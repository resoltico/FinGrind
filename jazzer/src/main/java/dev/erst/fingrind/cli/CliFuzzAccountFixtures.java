package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.BookReadService;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingRequestPublishedLanguageTranslator;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Account-registry fixtures shared by Jazzer harnesses. */
public final class CliFuzzAccountFixtures {
  private CliFuzzAccountFixtures() {}

  /**
   * Declares every distinct posting account so the final write path can exercise business rules.
   */
  public static List<DeclaredAccount> declarePostingAccounts(
      BookAdministrationService administrationService, PostEntryCommand command) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    return CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(command).stream()
        .map(declareAccountCommand -> declareAccount(administrationService, declareAccountCommand))
        .map(CliFuzzAccountFixtures::requireSuccessfulAccountState)
        .toList();
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
                new dev.erst.fingrind.core.AccountName(account.accountName().value() + " restored"),
                account.accountType(),
                account.accountTaxonomy()));
    DeclaredAccount restoredAccount = requireSuccessfulAccountState(result);
    if (!restoredAccount.active()) {
      throw new IllegalStateException("Account reactivation did not restore the active flag.");
    }
    if (!restoredAccount.declaredAt().equals(account.declaredAt())) {
      throw new IllegalStateException(
          "Account reactivation changed the original declared-at timestamp.");
    }
    return restoredAccount;
  }

  /** Lists accounts and fails fast if the registry surface is not in the expected state. */
  public static List<DeclaredAccount> listAccounts(BookkeepingReadStore bookStore) {
    Objects.requireNonNull(bookStore, "bookStore must not be null");
    List<DeclaredAccount> accounts = new java.util.ArrayList<>();
    BookReadService readService = new BookReadService(bookStore, postingIds -> java.util.Map.of());
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
        new ListAccountsQuery(
            dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits.PAGE_LIMIT_MAX, cursor));
  }

  private static DeclaredAccount requireSuccessfulAccountState(DeclareAccountResult result) {
    return switch (result) {
      case DeclareAccountResult.Declared declared -> declared.account();
      case DeclareAccountResult.Reactivated reactivated -> reactivated.account();
      case DeclareAccountResult.Renamed renamed -> renamed.account();
      case DeclareAccountResult.Unchanged unchanged -> unchanged.account();
      case DeclareAccountResult.Rejected _ ->
          throw new IllegalStateException("Lifecycle setup failed to declare an account.");
    };
  }

  private static DeclareAccountResult declareAccount(
      BookAdministrationService administrationService, DeclareAccountCommand command) {
    var outcome =
        CliFuzzWorkflowFixtures.withAttestationAuthorization(
            attestationAuthorizer ->
                administrationService.declareAccount(
                    BookkeepingRequestPublishedLanguageTranslator.fromPublished(command),
                    attestationAuthorizer));
    return BookkeepingPublishedLanguageTranslator.toPublished(outcome);
  }
}
