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
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CashFlowAssetClassification;
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
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared test-only helpers for expressing declared-account fixtures through taxonomy owners. */
public final class ExecutorAccountingTestSupport {
  private ExecutorAccountingTestSupport() {}

  /** Returns the default taxonomy owner used by legacy balance-driven test fixtures. */
  public static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty(),
              Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
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

  /**
   * Returns one taxonomy that owns the requested normal balance when the current doctrine defines
   * one directly.
   */
  public static AccountTaxonomy accountTaxonomy(
      AccountType accountType, NormalBalance normalBalance) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(normalBalance, "normalBalance");
    return switch (accountType) {
      case ASSET -> assetTaxonomy(normalBalance);
      case LIABILITY -> liabilityTaxonomy(normalBalance);
      case EQUITY -> equityTaxonomy(normalBalance);
      case REVENUE -> revenueTaxonomy(normalBalance);
      case EXPENSE -> expenseTaxonomy(normalBalance);
    };
  }

  private static AccountTaxonomy assetTaxonomy(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case DEBIT -> accountTaxonomy(AccountType.ASSET);
      case CREDIT ->
          throw new IllegalArgumentException(
              "ASSET tests must name an explicit contra-owning taxonomy, not credit-normal polarity.");
    };
  }

  private static AccountTaxonomy liabilityTaxonomy(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case CREDIT -> accountTaxonomy(AccountType.LIABILITY);
      case DEBIT ->
          throw new IllegalArgumentException(
              "LIABILITY tests must name an explicit contra-owning taxonomy, not debit-normal polarity.");
    };
  }

  private static AccountTaxonomy equityTaxonomy(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case CREDIT -> accountTaxonomy(AccountType.EQUITY);
      case DEBIT ->
          financialPositionTaxonomy(FinancialPositionLineClassification.EQUITY_WITHDRAWAL);
    };
  }

  private static AccountTaxonomy revenueTaxonomy(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case CREDIT -> accountTaxonomy(AccountType.REVENUE);
      case DEBIT ->
          throw new IllegalArgumentException(
              "REVENUE tests must name an explicit profit-and-loss taxonomy, not debit-normal polarity.");
    };
  }

  private static AccountTaxonomy expenseTaxonomy(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case DEBIT -> accountTaxonomy(AccountType.EXPENSE);
      case CREDIT ->
          throw new IllegalArgumentException(
              "EXPENSE tests must name an explicit profit-and-loss taxonomy, not credit-normal polarity.");
    };
  }

  /** Returns one explicit balance-sheet taxonomy for tests that need a specific statement line. */
  public static AccountTaxonomy financialPositionTaxonomy(
      FinancialPositionLineClassification lineClassification) {
    Objects.requireNonNull(lineClassification, "lineClassification");
    Optional<CashFlowAssetClassification> cashFlowAssetClassification =
        switch (lineClassification) {
          case CURRENT_ASSET, NONCURRENT_ASSET -> Optional.of(CashFlowAssetClassification.NON_CASH);
          case CURRENT_LIABILITY,
              NONCURRENT_LIABILITY,
              EQUITY_CONTRIBUTION,
              EQUITY_WITHDRAWAL,
              RESULT_HOLDING,
              RETAINED_ACCUMULATED,
              RESERVE,
              OTHER_EQUITY ->
              Optional.empty();
        };
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(lineClassification),
        Optional.empty(),
        cashFlowAssetClassification);
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
        accountTaxonomy(accountType, normalBalance),
        active,
        declaredAt);
  }

  /** Builds one published declared-account snapshot from an explicit taxonomy. */
  public static DeclaredAccount declaredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      boolean active,
      Instant declaredAt) {
    return new DeclaredAccount(
        accountCode, accountName, accountType, accountTaxonomy, active, declaredAt);
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
        accountTaxonomy(accountType, normalBalance),
        active,
        declaredAt);
  }

  /** Builds one local registered-account snapshot from an explicit taxonomy. */
  public static RegisteredAccount registeredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      boolean active,
      Instant declaredAt) {
    return new RegisteredAccount(
        accountCode, accountName, accountType, accountTaxonomy, active, declaredAt);
  }

  /** Returns one canonical test-only book identity for explicit open-book flows. */
  public static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
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
        readyCloseReadiness());
  }

  /** Returns one canonical close-readiness fixture for initialized inspection snapshots. */
  public static BookInspection.CloseReadiness readyCloseReadiness() {
    return new BookInspection.CloseReadiness(
        readyCloseTarget(
            FinancialPositionLineClassification.RESULT_HOLDING, new AccountCode("3200")),
        readyCloseTarget(
            FinancialPositionLineClassification.RETAINED_ACCUMULATED, new AccountCode("3300")));
  }

  /** Returns one canonical ready close-target fixture. */
  public static BookInspection.CloseTargetReadiness readyCloseTarget(
      FinancialPositionLineClassification classification, AccountCode accountCode) {
    return new BookInspection.CloseTargetReadiness(
        true, classification, accountCode, null, null, List.of());
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
        LocalDate.parse("2026-04-07"));
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
        bookIdentity(),
        accountCodeFilter,
        effectiveDateRange,
        postings,
        limit,
        nextCursor,
        java.util.Map.of());
  }

  /** Builds one published posting lookup success rooted in the canonical test book identity. */
  public static GetPostingResult.Found foundPosting(PostingFact postingFact) {
    return new GetPostingResult.Found(bookIdentity(), postingFact, Optional.empty());
  }
}
