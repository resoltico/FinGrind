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
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StorageLocator;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.time.LocalDate;
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
        : AccountRole.POLARITY_INVERTED;
  }

  /** Returns the default taxonomy owner used by legacy balance-driven test fixtures. */
  public static AccountTaxonomy accountTaxonomy(AccountType accountType) {
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

  /** Returns one explicit balance-sheet taxonomy for tests that need a specific statement line. */
  public static AccountTaxonomy financialPositionTaxonomy(
      FinancialPositionLineClassification lineClassification) {
    Objects.requireNonNull(lineClassification, "lineClassification");
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(lineClassification),
        Optional.empty());
  }

  /**
   * Returns one explicit profit-and-loss taxonomy for tests that need a specific statement line.
   */
  public static AccountTaxonomy profitAndLossTaxonomy(
      ProfitAndLossLineClassification lineClassification) {
    Objects.requireNonNull(lineClassification, "lineClassification");
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(lineClassification));
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
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_CASH_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"));
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
        bookIdentity(),
        resultTransferReadyInspection());
  }

  /**
   * Returns one canonical result-transfer-readiness fixture for initialized inspection snapshots.
   */
  public static BookInspection.ResultTransferReadiness resultTransferReadyInspection() {
    return new BookInspection.ResultTransferReadiness(
        true,
        FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
        new AccountCode("3200"),
        null,
        null,
        List.of());
  }

  /** Returns one canonical book-opened outcome fixture. */
  public static BookOpeningOutcome.Opened openedBook(Instant initializedAt) {
    return new BookOpeningOutcome.Opened(initializedAt, bookIdentity());
  }

  /** Returns the default report coverage used by fixtures that include generated postings. */
  public static PostingCoverage allPostingKinds() {
    return PostingCoverage.ALL_POSTING_KINDS;
  }

  /** Returns the report coverage used by fixtures that intentionally exclude transfer postings. */
  public static PostingCoverage standardOnly() {
    return PostingCoverage.NON_CLOSING_POSTINGS;
  }

  /** Returns one canonical evidence bundle for tests that create accepted accounting facts. */
  public static AccountingEvidence accountingEvidence(String token) {
    Objects.requireNonNull(token, "token");
    return new AccountingEvidence(
        List.of(sourceDocumentReference("document-" + token, "cash-receipt")), List.of());
  }

  /** Returns one canonical internal evidence bundle for system-generated accounting facts. */
  public static AccountingEvidence generatedEvidence(String token, String sourceDocumentType) {
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
    return new AccountingEvidence(
        List.of(sourceDocumentReference("generated-" + token, sourceDocumentType)), List.of());
  }

  private static SourceDocumentReference sourceDocumentReference(
      String sourceDocumentId, String sourceDocumentType) {
    return new SourceDocumentReference(
        new SourceDocumentId(sourceDocumentId),
        new SourceDocumentType(sourceDocumentType),
        LocalDate.parse("2026-04-07"),
        Instant.parse("2026-04-07T10:15:30Z"),
        new StorageLocator("vault://fixtures/" + sourceDocumentId),
        new ContentSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
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
