package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.profitAndLossTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared fixtures and validation-store doubles for PostEntrySemanticsPolicy tests. */
final class PostEntrySemanticsPolicyTestSupport {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");

  private PostEntrySemanticsPolicyTestSupport() {}

  static PostEntryCommand cashRevenue(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null,
            null,
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand cashExpense(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.ExpenseSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("3000"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand equityContribution(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.OwnerContribution(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("3200"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand equityWithdrawal(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.OwnerWithdrawal(
            LocalDate.parse("2026-04-07"),
            new AccountCode("3210"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand creditSale(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.SaleOnCredit(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1100"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null,
            null,
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand creditExpense(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.ExpenseOnCredit(
            LocalDate.parse("2026-04-07"),
            new AccountCode("3000"),
            new AccountCode("2100"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand receipt(
      String token, String sourceDocumentType, AccountCode settlementAdjunctAccountCode) {
    return new PostEntryCommand(
        new BookkeepingEntry.Receipt(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("1100"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            settlementAdjunctAccountCode == null
                ? null
                : new SettlementAdjunct(
                    settlementAdjunctAccountCode, MonetaryAmount.of(Money.parse("EUR", "1.00")))),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand receiptWithoutAdjunct(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.Receipt(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("1100"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand payment(
      String token, String sourceDocumentType, AccountCode settlementAdjunctAccountCode) {
    return new PostEntryCommand(
        new BookkeepingEntry.Payment(
            LocalDate.parse("2026-04-07"),
            new AccountCode("2100"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            settlementAdjunctAccountCode == null
                ? null
                : new SettlementAdjunct(
                    settlementAdjunctAccountCode, MonetaryAmount.of(Money.parse("EUR", "1.00")))),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand paymentWithoutAdjunct(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.Payment(
            LocalDate.parse("2026-04-07"),
            new AccountCode("2100"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand duplicateCashRevenue(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("9999"),
            new AccountCode("9999"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null,
            null,
            null),
        generatedEvidence(token, "cash-receipt"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand duplicateCashExpense(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.ExpenseSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("9999"),
            new AccountCode("9999"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null),
        generatedEvidence(token, "expense-receipt"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand duplicateEquityContribution(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.OwnerContribution(
            LocalDate.parse("2026-04-07"),
            new AccountCode("9999"),
            new AccountCode("9999"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        generatedEvidence(token, "owner-contribution"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static PostEntryCommand duplicateEquityWithdrawal(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.OwnerWithdrawal(
            LocalDate.parse("2026-04-07"),
            new AccountCode("9999"),
            new AccountCode("9999"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        generatedEvidence(token, "owner-withdrawal"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  static RegisteredAccount account(String code, AccountType accountType) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        accountType,
        accountTaxonomy(accountType),
        true,
        DECLARED_AT);
  }

  static RegisteredAccount equityAccount(
      String code, FinancialPositionLineClassification lineClassification) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.EQUITY,
        financialPositionTaxonomy(lineClassification),
        true,
        DECLARED_AT);
  }

  static RegisteredAccount receivableAccount(String code) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_RECEIVABLE),
        true,
        DECLARED_AT);
  }

  static RegisteredAccount payableAccount(String code) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_PAYABLE),
        true,
        DECLARED_AT);
  }

  static RegisteredAccount settlementAdjunctAccount(String code) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.EXPENSE,
        profitAndLossTaxonomy(ProfitAndLossLineClassification.SETTLEMENT_FEE),
        true,
        DECLARED_AT);
  }

  static RegisteredAccount inventoryAssetAccount(String code) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
        true,
        DECLARED_AT);
  }

  static RegisteredAccount operatingExpenseAccount(String code) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.EXPENSE,
        profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE),
        true,
        DECLARED_AT);
  }

  static RegisteredAccount costOfSalesAccount(String code) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.EXPENSE,
        profitAndLossTaxonomy(ProfitAndLossLineClassification.COST_OF_SALES),
        true,
        DECLARED_AT);
  }

  static BookIdentity accrualBookIdentity() {
    BookIdentity baseline = ExecutorAccountingTestSupport.bookIdentity();
    return new BookIdentity(
        baseline.entityProfile(),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL,
        baseline.functionalCurrency(),
        baseline.fiscalYearStart());
  }

  static BookIdentity tradingCashBookIdentity() {
    BookIdentity baseline = ExecutorAccountingTestSupport.bookIdentity();
    return new BookIdentity(
        baseline.entityProfile(),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING,
        baseline.functionalCurrency(),
        baseline.fiscalYearStart());
  }

  static BookIdentity tradingAccrualBookIdentity() {
    BookIdentity baseline = ExecutorAccountingTestSupport.bookIdentity();
    return new BookIdentity(
        baseline.entityProfile(),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL,
        baseline.functionalCurrency(),
        baseline.fiscalYearStart());
  }

  /** Minimal validation-store double for account-role and evidence semantics tests. */
  static final class PostingValidationStoreDouble implements PostingValidationStore {
    private final BookIdentity bookIdentity;
    private final Map<AccountCode, RegisteredAccount> accounts;
    private final Map<PostingId, CommittedPosting> postingsById;
    private final Map<AccrualCutoffId, AccrualCutoffRecord> cutoffsById;

    PostingValidationStoreDouble(Map<AccountCode, RegisteredAccount> accounts) {
      this(ExecutorAccountingTestSupport.bookIdentity(), accounts, Map.of(), Map.of());
    }

    PostingValidationStoreDouble(
        BookIdentity bookIdentity, Map<AccountCode, RegisteredAccount> accounts) {
      this(bookIdentity, accounts, Map.of(), Map.of());
    }

    PostingValidationStoreDouble(
        BookIdentity bookIdentity,
        Map<AccountCode, RegisteredAccount> accounts,
        Map<PostingId, CommittedPosting> postingsById) {
      this(bookIdentity, accounts, postingsById, Map.of());
    }

    PostingValidationStoreDouble(
        BookIdentity bookIdentity,
        Map<AccountCode, RegisteredAccount> accounts,
        Map<PostingId, CommittedPosting> postingsById,
        Map<AccrualCutoffId, AccrualCutoffRecord> cutoffsById) {
      this.bookIdentity = bookIdentity;
      this.accounts = accounts;
      this.postingsById = postingsById;
      this.cutoffsById = cutoffsById;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(1001, 1, 1, DECLARED_AT, bookIdentity);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.ofNullable(accounts.get(accountCode));
    }

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      return accounts.entrySet().stream()
          .filter(entry -> accountCodes.contains(entry.getKey()))
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.ofNullable(postingsById.get(postingId));
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return postingsById.values().stream()
          .filter(
              posting ->
                  posting.reversalReference().stream()
                      .anyMatch(reference -> reference.priorPostingId().equals(priorPostingId)))
          .findFirst();
    }

    @Override
    public Optional<AccrualCutoffRecord> findAccrualCutoff(AccrualCutoffId accrualCutoffId) {
      return Optional.ofNullable(cutoffsById.get(accrualCutoffId));
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return List.of();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }
  }
}
