package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReportingObligationStatus;
import dev.erst.fingrind.core.TaxRegistrationStatus;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared test-only helpers for expressing legacy normal-balance fixtures in account-role terms. */
public final class ExecutorAccountingTestSupport {
  private ExecutorAccountingTestSupport() {}

  /**
   * Derives the doctrinal role implied by one legacy fixture balance.
   *
   * <p>Tests that need specialized equity taxonomy must request it explicitly rather than relying
   * on this ordinary/contra projection.
   */
  public static AccountRole accountRole(AccountType accountType, NormalBalance normalBalance) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(normalBalance, "normalBalance");
    return AccountSemantics.normalBalance(accountType, AccountRole.ORDINARY) == normalBalance
        ? AccountRole.ORDINARY
        : AccountRole.CONTRA;
  }

  /** Returns the default taxonomy owner used by legacy balance-driven test fixtures. */
  public static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty());
      case LIABILITY ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
      case EXPENSE ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    };
  }

  /** Returns one explicit balance-sheet taxonomy for tests that need a specific statement line. */
  public static AccountTaxonomy financialPositionTaxonomy(
      FinancialPositionLineClassification lineClassification) {
    Objects.requireNonNull(lineClassification, "lineClassification");
    return new AccountTaxonomy(Optional.empty(), Optional.of(lineClassification), Optional.empty());
  }

  /**
   * Returns one explicit profit-and-loss taxonomy for tests that need a specific statement line.
   */
  public static AccountTaxonomy profitAndLossTaxonomy(
      ProfitAndLossLineClassification lineClassification) {
    Objects.requireNonNull(lineClassification, "lineClassification");
    return new AccountTaxonomy(Optional.empty(), Optional.empty(), Optional.of(lineClassification));
  }

  /** Builds one published declared-account snapshot from a legacy normal-balance fixture. */
  public static DeclaredAccount declaredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return declaredAccount(
        accountCode,
        accountName,
        accountType,
        accountRole(accountType, normalBalance),
        accountTaxonomy(accountType),
        active,
        declaredAt);
  }

  /** Builds one published declared-account snapshot from an explicit role and taxonomy. */
  public static DeclaredAccount declaredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy,
      boolean active,
      Instant declaredAt) {
    return new DeclaredAccount(
        accountCode, accountName, accountType, accountRole, accountTaxonomy, active, declaredAt);
  }

  /** Builds one local registered-account snapshot from a legacy normal-balance fixture. */
  public static RegisteredAccount registeredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return registeredAccount(
        accountCode,
        accountName,
        accountType,
        accountRole(accountType, normalBalance),
        accountTaxonomy(accountType),
        active,
        declaredAt);
  }

  /** Builds one local registered-account snapshot from an explicit role and taxonomy. */
  public static RegisteredAccount registeredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy,
      boolean active,
      Instant declaredAt) {
    return new RegisteredAccount(
        accountCode, accountName, accountType, accountRole, accountTaxonomy, active, declaredAt);
  }

  /** Returns one canonical test-only book identity for explicit open-book flows. */
  public static BookIdentity bookIdentity() {
    return bookIdentity(EntityForm.COMPANY);
  }

  /** Returns one canonical test-only book identity for the supplied entity form. */
  public static BookIdentity bookIdentity(EntityForm entityForm) {
    Objects.requireNonNull(entityForm, "entityForm");
    return new BookIdentity(
        new EntityProfile(
            new BookEntityName("Acme Studio"),
            entityForm,
            OwnerModel.MULTI_OWNER,
            ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
            TaxRegistrationStatus.UNSPECIFIED,
            List.of()),
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        AccountingBasis.ACCRUAL);
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

  /** Builds one published account page rooted in the canonical test book identity. */
  public static AccountPage accountPage(
      List<DeclaredAccount> accounts, int limit, Optional<AccountPageCursor> nextCursor) {
    return new AccountPage(bookIdentity(), accounts, limit, nextCursor);
  }

  /** Builds one published posting page rooted in the canonical test book identity. */
  public static PostingPage postingPage(
      Optional<AccountCode> accountCodeFilter,
      EffectiveDateRange effectiveDateRange,
      List<PostingFact> postings,
      int limit,
      Optional<PostingPageCursor> nextCursor) {
    return new PostingPage(
        bookIdentity(), accountCodeFilter, effectiveDateRange, postings, limit, nextCursor);
  }

  /** Builds one published posting lookup success rooted in the canonical test book identity. */
  public static GetPostingResult.Found foundPosting(PostingFact postingFact) {
    return new GetPostingResult.Found(bookIdentity(), postingFact);
  }
}
