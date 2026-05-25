package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.command;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Direct coverage for typed-entry semantics validation at the application boundary. */
class PostEntrySemanticsPolicyTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");

  @Test
  void rejectionFor_acceptsAllSupportedTypedKernelEntriesWhenAccountsAndEvidenceMatch() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble book =
        new PostingValidationStoreDouble(
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE),
                new AccountCode("3000"),
                account("3000", AccountType.EXPENSE),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
                new AccountCode("3210"),
                equityAccount("3210", FinancialPositionLineClassification.EQUITY_WITHDRAWAL)));

    assertTrue(policy.rejectionFor(cashRevenue("cash-ok", "cash-receipt"), book).isEmpty());
    assertTrue(policy.rejectionFor(cashExpense("expense-ok", "expense-receipt"), book).isEmpty());
    assertTrue(
        policy
            .rejectionFor(equityContribution("equity-contribution-ok", "equity-contribution"), book)
            .isEmpty());
    assertTrue(
        policy
            .rejectionFor(equityWithdrawal("equity-withdrawal-ok", "equity-withdrawal"), book)
            .isEmpty());
  }

  @Test
  void rejectionFor_reportsTypedEntryDoctrineViolationsAcrossAllSupportedTypedEntries() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble book =
        new PostingValidationStoreDouble(
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.LIABILITY),
                new AccountCode("2000"),
                account("2000", AccountType.ASSET),
                new AccountCode("3000"),
                account("3000", AccountType.REVENUE),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.OTHER_EQUITY),
                new AccountCode("3210"),
                equityAccount("3210", FinancialPositionLineClassification.RESULT_HOLDING)));

    assertViolationCodes(
        policy.rejectionFor(cashRevenue("cash-bad", "invoice"), book),
        "account-type-mismatch",
        "account-type-mismatch",
        "source-document-type-not-accepted");
    assertViolationCodes(
        policy.rejectionFor(cashExpense("expense-bad", "invoice"), book),
        "account-type-mismatch",
        "account-type-mismatch",
        "source-document-type-not-accepted");
    assertViolationCodes(
        policy.rejectionFor(equityContribution("equity-contribution-bad", "invoice"), book),
        "account-type-mismatch",
        "financial-position-classification-mismatch",
        "source-document-type-not-accepted");
    assertViolationCodes(
        policy.rejectionFor(equityWithdrawal("equity-withdrawal-bad", "invoice"), book),
        "financial-position-classification-mismatch",
        "account-type-mismatch",
        "source-document-type-not-accepted");
  }

  @Test
  void rejectionFor_skipsSemanticsChecksForAdministrativeAdjustments() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());

    assertTrue(
        policy
            .rejectionFor(
                new PostEntryCommand(
                    new BookkeepingEntry.OpeningBalanceAdjustment(
                        PostingApplicationServiceTestSupport.journalEntry()),
                    generatedEvidence("opening-balance", "opening-balance"),
                    requestProvenance("opening-balance"),
                    SourceChannel.CLI),
                emptyBook)
            .isEmpty());
    assertTrue(
        policy
            .rejectionFor(
                new PostEntryCommand(
                    new BookkeepingEntry.CorrectionAdjustment(
                        PostingApplicationServiceTestSupport.journalEntry()),
                    generatedEvidence("correction-adjustment", "correction-adjustment"),
                    requestProvenance("correction-adjustment"),
                    SourceChannel.CLI),
                emptyBook)
            .isEmpty());
    assertTrue(
        policy
            .rejectionFor(
                command(
                    "reversal-adjustment",
                    PostingApplicationServiceTestSupport.reversalReference("posting-1"),
                    Optional.of(new dev.erst.fingrind.core.ReversalReason("full reversal")),
                    PostingApplicationServiceTestSupport.reversalJournalEntry()),
                emptyBook)
            .isEmpty());
  }

  @Test
  void rejectionFor_leavesUndeclaredAccountsToAccountStateValidation() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();

    assertTrue(
        policy
            .rejectionFor(
                equityContribution("undeclared-accounts", "equity-contribution"),
                new PostingValidationStoreDouble(Map.of()))
            .isEmpty());
  }

  private static void assertViolationCodes(
      Optional<BookkeepingPostingRejection> rejection, String... expectedCodes) {
    BookkeepingPostingRejection.EntrySemanticsViolations violations =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class, rejection.orElseThrow());
    assertEquals(expectedCodes.length, violations.violations().size());
    for (int index = 0; index < expectedCodes.length; index++) {
      assertEquals(expectedCodes[index], violations.violations().get(index).code());
    }
  }

  private static PostEntryCommand cashRevenue(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.CashRevenue(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00"))),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand cashExpense(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.CashExpense(
            LocalDate.parse("2026-04-07"),
            new AccountCode("3000"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00"))),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand equityContribution(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.EquityContribution(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("3200"),
            MonetaryAmount.of(Money.parse("EUR", "10.00"))),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand equityWithdrawal(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.EquityWithdrawal(
            LocalDate.parse("2026-04-07"),
            new AccountCode("3210"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00"))),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static RegisteredAccount account(String code, AccountType accountType) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        accountType,
        accountType == AccountType.REVENUE ? NormalBalance.CREDIT : NormalBalance.DEBIT,
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount equityAccount(
      String code, FinancialPositionLineClassification lineClassification) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.EQUITY,
        ExecutorAccountingTestSupport.accountRole(AccountType.EQUITY, NormalBalance.CREDIT),
        financialPositionTaxonomy(lineClassification),
        true,
        DECLARED_AT);
  }

  /** Minimal validation-store double for account-role and evidence semantics tests. */
  private static final class PostingValidationStoreDouble implements PostingValidationStore {
    private final Map<AccountCode, RegisteredAccount> accounts;

    private PostingValidationStoreDouble(Map<AccountCode, RegisteredAccount> accounts) {
      this.accounts = accounts;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return java.util.Optional.ofNullable(accounts.get(accountCode));
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
    public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
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
