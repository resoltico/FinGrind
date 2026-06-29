package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
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
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
            .rejectionFor(equityContribution("equity-contribution-ok", "owner-contribution"), book)
            .isEmpty());
    assertTrue(
        policy
            .rejectionFor(equityWithdrawal("equity-withdrawal-ok", "owner-withdrawal"), book)
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
        "cash-flow-asset-classification-mismatch",
        "account-type-mismatch",
        "source-document-type-not-accepted");
    assertViolationCodes(
        policy.rejectionFor(cashExpense("expense-bad", "invoice"), book),
        "account-type-mismatch",
        "account-type-mismatch",
        "cash-flow-asset-classification-mismatch",
        "source-document-type-not-accepted");
    assertViolationCodes(
        policy.rejectionFor(equityContribution("equity-contribution-bad", "invoice"), book),
        "account-type-mismatch",
        "cash-flow-asset-classification-mismatch",
        "financial-position-classification-mismatch",
        "source-document-type-not-accepted");
    assertViolationCodes(
        policy.rejectionFor(equityWithdrawal("equity-withdrawal-bad", "invoice"), book),
        "financial-position-classification-mismatch",
        "account-type-mismatch",
        "cash-flow-asset-classification-mismatch",
        "source-document-type-not-accepted");
  }

  @Test
  void rejectionFor_skipsSemanticsChecksForAdministrativeEntriesOutsideTypedKernelEvents() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());

    assertTrue(
        policy
            .rejectionFor(
                new PostEntryCommand(
                    new BookkeepingEntry.OpeningPosition(
                        LocalDate.parse("2026-04-07"),
                        List.of(
                            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                                new AccountCode("1000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                MonetaryAmount.of(Money.parse("EUR", "10.00"))),
                            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                                new AccountCode("2000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                MonetaryAmount.of(Money.parse("EUR", "10.00"))))),
                    generatedEvidence("opening-balance", "opening-balance"),
                    requestProvenance("opening-balance"),
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
                equityContribution("undeclared-accounts", "owner-contribution"),
                new PostingValidationStoreDouble(Map.of()))
            .isEmpty());
  }

  @Test
  void rejectionFor_acceptsCallerAuthoredSourceDocumentTypesForPatternOnlyEntries() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());

    assertTrue(
        policy
            .rejectionFor(
                new PostEntryCommand(
                    new BookkeepingEntry.OpeningPosition(
                        LocalDate.parse("2026-04-07"),
                        List.of(
                            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                                new AccountCode("1000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                MonetaryAmount.of(Money.parse("EUR", "10.00"))),
                            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                                new AccountCode("2000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                MonetaryAmount.of(Money.parse("EUR", "10.00"))))),
                    generatedEvidence("open-pattern", "field-note"),
                    requestProvenance("open-pattern"),
                    SourceChannel.CLI),
                emptyBook)
            .isEmpty());
    assertTrue(
        policy
            .rejectionFor(
                new PostEntryCommand(
                    new BookkeepingEntry.Reversal(
                        PostingApplicationServiceTestSupport.reversalJournalEntry(),
                        new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                            PostingApplicationServiceTestSupport.reversalReference("posting-1")
                                .orElseThrow(),
                            new dev.erst.fingrind.core.ReversalReason("full reversal")),
                        null),
                    generatedEvidence("reversal-pattern", "operator-annotation"),
                    requestProvenance("reversal-pattern"),
                    SourceChannel.CLI),
                emptyBook)
            .isEmpty());
  }

  @Test
  void roleSemanticsValidate_treatsOpeningAndReversalEntriesAsNoOps() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();

    PostEntryRoleAccountSemantics.validate(
        violations,
        Map.of(),
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-07"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00"))),
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("2000"),
                    dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00"))))),
        "entryKind",
        "OPENING_POSITION");
    PostEntryRoleAccountSemantics.validate(
        violations,
        Map.of(),
        new BookkeepingEntry.Reversal(
            PostingApplicationServiceTestSupport.reversalJournalEntry(),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                PostingApplicationServiceTestSupport.reversalReference("posting-1").orElseThrow(),
                new dev.erst.fingrind.core.ReversalReason("full reversal")),
            null),
        "entryKind",
        "REVERSAL");

    assertTrue(violations.isEmpty());
  }

  @Test
  void rejectionFor_rejectsTypedEntriesThatReuseOneAccountAcrossBothBusinessRoles() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());

    assertSingleViolation(
        policy.rejectionFor(duplicateCashRevenue("duplicate-cash-revenue"), emptyBook),
        "distinct-role-accounts-required");
    assertSingleViolation(
        policy.rejectionFor(duplicateCashExpense("duplicate-cash-expense"), emptyBook),
        "distinct-role-accounts-required");
    assertSingleViolation(
        policy.rejectionFor(
            duplicateEquityContribution("duplicate-equity-contribution"), emptyBook),
        "distinct-role-accounts-required");
    assertSingleViolation(
        policy.rejectionFor(duplicateEquityWithdrawal("duplicate-equity-withdrawal"), emptyBook),
        "distinct-role-accounts-required");
  }

  @Test
  void rejectionFor_acceptsDirectJournalsWithCallerAuthoredEvidenceTypesWhenTheyMoveBalances() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());

    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.DirectJournal(
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2100"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "4.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2200"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "6.00")))),
                null),
            generatedEvidence("direct-journal", "operator-note"),
            requestProvenance("direct-journal"),
            SourceChannel.CLI);

    assertTrue(policy.rejectionFor(command, emptyBook).isEmpty());
  }

  @Test
  void rejectionFor_rejectsEconomicallyNullDirectJournalsAfterPerAccountNetting() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());

    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.DirectJournal(
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "10.00")))),
                null),
            generatedEvidence("economic-null-journal", "operator-note"),
            requestProvenance("economic-null-journal"),
            SourceChannel.CLI);

    assertSingleViolation(policy.rejectionFor(command, emptyBook), "economic-null-journal");
  }

  @Test
  void rejectionFor_rejectsDirectJournalsThatNeverTouchDeclaredCashAccounts() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble book =
        new PostingValidationStoreDouble(
            Map.of(
                new AccountCode("3000"),
                account("3000", AccountType.EXPENSE),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.OTHER_EQUITY)));

    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.DirectJournal(
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("3000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("3200"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "10.00")))),
                null),
            generatedEvidence("non-cash-direct-journal", "operator-note"),
            requestProvenance("non-cash-direct-journal"),
            SourceChannel.CLI);

    assertSingleViolation(policy.rejectionFor(command, book), "cash-basis-account-required");
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

  private static void assertSingleViolation(
      Optional<BookkeepingPostingRejection> rejection, String expectedCode) {
    BookkeepingPostingRejection.EntrySemanticsViolations violations =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class, rejection.orElseThrow());
    assertEquals(1, violations.violations().size());
    assertEquals(expectedCode, violations.violations().getFirst().code());
  }

  private static PostEntryCommand cashRevenue(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.Sale(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand cashExpense(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.Expense(
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

  private static PostEntryCommand equityContribution(String token, String sourceDocumentType) {
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

  private static PostEntryCommand equityWithdrawal(String token, String sourceDocumentType) {
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

  private static PostEntryCommand duplicateCashRevenue(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.Sale(
            LocalDate.parse("2026-04-07"),
            new AccountCode("9999"),
            new AccountCode("9999"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null),
        generatedEvidence(token, "cash-receipt"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand duplicateCashExpense(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.Expense(
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

  private static PostEntryCommand duplicateEquityContribution(String token) {
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

  private static PostEntryCommand duplicateEquityWithdrawal(String token) {
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

  private static RegisteredAccount account(String code, AccountType accountType) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        accountType,
        accountTaxonomy(accountType),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount equityAccount(
      String code, FinancialPositionLineClassification lineClassification) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.EQUITY,
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
