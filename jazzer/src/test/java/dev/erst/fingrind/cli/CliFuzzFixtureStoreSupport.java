package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.executor.bookkeeping.AccountAmendmentOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.AccountRetirementOutcome;
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
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

final class CliFuzzFixtureStoreSupport {
  private CliFuzzFixtureStoreSupport() {}

  static RegisteredAccount toRegisteredAccount(DeclaredAccount account) {
    return new RegisteredAccount(
        account.accountCode(),
        account.accountName(),
        account.accountType(),
        account.accountTaxonomy(),
        account.active(),
        account.declaredAt());
  }

  static DeclaredAccount declaredAccount(
      AccountCode accountCode, AccountType accountType, boolean active) {
    return new DeclaredAccount(
        accountCode,
        new AccountName(accountType == AccountType.REVENUE ? "Revenue" : "Cash"),
        accountType,
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
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty(),
              Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
      case LIABILITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty(),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty(),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
              Optional.empty());
      case EXPENSE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE),
              Optional.empty());
    };
  }

  abstract static class AbstractBookAdministrationStoreStub
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
        AccountDeclaration declaration, Instant declaredAt) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public AccountAmendmentOutcome amendAccount(AccountDeclaration amendment, Instant amendedAt) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public AccountRetirementOutcome retireAccount(AccountCode accountCode, Instant retiredAt) {
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

  abstract static class AbstractBookkeepingReadStoreStub implements BookkeepingReadStore {
    @Override
    public BookLifecycleInspection inspectBook() {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(
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
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public List<dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord>
        inventoryValuationMovements(Optional<java.time.LocalDate> effectiveDateAsOf) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<java.time.LocalDate> latestPostingEffectiveDate() {
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
}
