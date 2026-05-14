package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.util.Objects;

/** Shared test-only helpers for expressing legacy normal-balance fixtures in account-role terms. */
public final class ExecutorAccountingTestSupport {
  private ExecutorAccountingTestSupport() {}

  /**
   * Derives the doctrinal role implied by one legacy fixture balance.
   *
   * <p>Tests that need retained earnings must request {@link AccountRole#RETAINED_EARNINGS}
   * explicitly rather than relying on this ordinary/contra projection.
   */
  public static AccountRole accountRole(AccountType accountType, NormalBalance normalBalance) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(normalBalance, "normalBalance");
    return AccountSemantics.normalBalance(accountType, AccountRole.ORDINARY) == normalBalance
        ? AccountRole.ORDINARY
        : AccountRole.CONTRA;
  }

  /** Builds one published declared-account snapshot from a legacy normal-balance fixture. */
  public static DeclaredAccount declaredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return new DeclaredAccount(
        accountCode,
        accountName,
        accountType,
        accountRole(accountType, normalBalance),
        active,
        declaredAt);
  }

  /** Builds one local registered-account snapshot from a legacy normal-balance fixture. */
  public static RegisteredAccount registeredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return new RegisteredAccount(
        accountCode,
        accountName,
        accountType,
        accountRole(accountType, normalBalance),
        active,
        declaredAt);
  }

  /** Returns one canonical test-only book identity for explicit open-book flows. */
  public static BookIdentity bookIdentity() {
    return new BookIdentity(
        new BookEntityName("Acme Studio"), CurrencyUnit.of("EUR"), FiscalYearStart.parse("01-01"));
  }

  /**
   * Returns one canonical open-book command for tests that need the public initialization shape.
   */
  public static OpenBookCommand openBookCommand() {
    return new OpenBookCommand(bookIdentity());
  }

  /** Returns one canonical local initialized-book inspection fixture. */
  public static BookLifecycleInspection.Initialized initializedLifecycleInspection(
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      Instant initializedAt) {
    return new BookLifecycleInspection.Initialized(
        applicationId,
        detectedBookFormatVersion,
        supportedBookFormatVersion,
        initializedAt,
        bookIdentity());
  }

  /** Returns one canonical published initialized-book inspection fixture. */
  public static BookInspection.Initialized initializedBookInspection(
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      Instant initializedAt) {
    return new BookInspection.Initialized(
        applicationId,
        detectedBookFormatVersion,
        supportedBookFormatVersion,
        initializedAt,
        bookIdentity());
  }

  /** Returns one canonical book-opened outcome fixture. */
  public static BookOpeningOutcome.Opened openedBook(Instant initializedAt) {
    return new BookOpeningOutcome.Opened(initializedAt, bookIdentity());
  }

  /** Returns the default report coverage used by fixtures that include generated postings. */
  public static PostingCoverage allPostingKinds() {
    return PostingCoverage.ALL_POSTING_KINDS;
  }

  /** Returns the report coverage used by fixtures that intentionally exclude closing postings. */
  public static PostingCoverage standardOnly() {
    return PostingCoverage.NON_CLOSING_POSTINGS;
  }
}
