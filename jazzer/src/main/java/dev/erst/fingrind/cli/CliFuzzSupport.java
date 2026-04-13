package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.BookAdministrationService;
import dev.erst.fingrind.application.BookSession;
import dev.erst.fingrind.application.DeclareAccountCommand;
import dev.erst.fingrind.application.DeclareAccountResult;
import dev.erst.fingrind.application.DeclaredAccount;
import dev.erst.fingrind.application.ListAccountsResult;
import dev.erst.fingrind.application.OpenBookResult;
import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostingIdGenerator;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Shared helpers for FinGrind Jazzer harnesses that start from CLI request JSON. */
public final class CliFuzzSupport {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);

  private CliFuzzSupport() {}

  /** Parses one CLI request payload from bytes using the same reader used by the production CLI. */
  public static PostEntryCommand readPostEntryCommand(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    return new CliRequestReader(new ByteArrayInputStream(input)).readPostEntryCommand(Path.of("-"));
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

  /** Creates the fixed-clock administration service used by lifecycle-aware harnesses. */
  public static BookAdministrationService administrationService(BookSession bookSession) {
    Objects.requireNonNull(bookSession, "bookSession must not be null");
    return new BookAdministrationService(bookSession, fixedClock());
  }

  /** Opens one book and fails fast if lifecycle setup drifts unexpectedly. */
  public static void openBook(BookAdministrationService administrationService) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    OpenBookResult result = administrationService.openBook();
    if (!(result instanceof OpenBookResult.Opened opened)) {
      throw new IllegalStateException("Lifecycle setup failed to initialize the book.");
    }
    if (!opened.initializedAt().equals(fixedClock().instant())) {
      throw new IllegalStateException("Lifecycle setup used an unexpected initialized-at instant.");
    }
  }

  /** Declares every distinct posting account so the final write path can exercise business rules. */
  public static List<DeclaredAccount> declarePostingAccounts(
      BookAdministrationService administrationService, PostEntryCommand command) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    LinkedHashMap<AccountCode, DeclareAccountCommand> commandsByAccount = new LinkedHashMap<>();
    for (var line : command.journalEntry().lines()) {
      commandsByAccount.computeIfAbsent(
          line.accountCode(),
          accountCode ->
              new DeclareAccountCommand(
                  accountCode, syntheticAccountName(accountCode), syntheticNormalBalance(accountCode)));
    }
    return commandsByAccount.values().stream()
        .map(administrationService::declareAccount)
        .map(CliFuzzSupport::requireDeclaredAccount)
        .toList();
  }

  /** Returns the first journal-line account code for lifecycle assertions. */
  public static AccountCode firstAccountCode(PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return command.journalEntry().lines().getFirst().accountCode();
  }

  /** Reactivates one account with an updated display name and asserts the durable shape. */
  public static DeclaredAccount reactivateAccount(
      BookAdministrationService administrationService, DeclaredAccount account) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    Objects.requireNonNull(account, "account must not be null");
    DeclareAccountResult result =
        administrationService.declareAccount(
            new DeclareAccountCommand(
                account.accountCode(),
                new AccountName(account.accountName().value() + " restored"),
                account.normalBalance()));
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

  /** Lists accounts and fails fast if the registry surface is not in the expected state. */
  public static List<DeclaredAccount> listAccounts(BookAdministrationService administrationService) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    ListAccountsResult result = administrationService.listAccounts();
    if (!(result instanceof ListAccountsResult.Listed listed)) {
      throw new IllegalStateException("Lifecycle setup failed to list declared accounts.");
    }
    return listed.accounts();
  }

  private static DeclaredAccount requireDeclaredAccount(DeclareAccountResult result) {
    if (!(result instanceof DeclareAccountResult.Declared declared)) {
      throw new IllegalStateException("Lifecycle setup failed to declare an account.");
    }
    return declared.account();
  }

  private static AccountName syntheticAccountName(AccountCode accountCode) {
    return new AccountName("Synthetic " + accountCode.value());
  }

  private static NormalBalance syntheticNormalBalance(AccountCode accountCode) {
    return Math.floorMod(accountCode.value().hashCode(), 2) == 0
        ? NormalBalance.DEBIT
        : NormalBalance.CREDIT;
  }
}
