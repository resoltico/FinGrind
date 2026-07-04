package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.InventoryBalanceBelowZeroViolation;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
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
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** Focused unit tests for non-negative inventory admission rules. */
class PostingAcceptancePolicyInventoryBalanceTest {
  private static final PostingAcceptancePolicy POSTING_ACCEPTANCE_POLICY =
      PostingAcceptancePolicy.currentKernel();

  @Test
  void rejectionFor_rejectsTypedSaleThatWouldOverRelieveInventory() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    RegisteredAccount cash =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount inventory =
        new RegisteredAccount(
            new AccountCode("1400"),
            new AccountName("Inventory"),
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount revenue =
        registeredAccount(
            new AccountCode("4000"),
            new AccountName("Sales Revenue"),
            AccountType.REVENUE,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount costOfSales =
        registeredAccount(
            new AccountCode("5000"),
            new AccountName("Cost of Sales"),
            AccountType.EXPENSE,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(cash.accountCode(), cash);
    book.accounts.put(inventory.accountCode(), inventory);
    book.accounts.put(revenue.accountCode(), revenue);
    book.accounts.put(costOfSales.accountCode(), costOfSales);
    book.postings =
        List.of(
            existingPosting(
                "posting-inventory-1",
                "idem-inventory-1",
                new JournalEntry(
                    LocalDate.parse("2026-04-06"),
                    List.of(
                        line("1400", JournalLine.EntrySide.DEBIT, "10.00"),
                        line("1000", JournalLine.EntrySide.CREDIT, "10.00")))));

    PostingCommand sale =
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.SALE_SETTLED,
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "70.00"),
                    line("4000", JournalLine.EntrySide.CREDIT, "70.00"),
                    line("5000", JournalLine.EntrySide.DEBIT, "50.00"),
                    line("1400", JournalLine.EntrySide.CREDIT, "50.00"))),
            PostingLineageModel.direct(),
            accountingEvidence("idem-sale-over-relief"),
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-sale-over-relief"),
                new IdempotencyKey("idem-sale-over-relief"),
                new CausationId("cause-sale-over-relief"),
                Optional.of(new CorrelationId("corr-sale-over-relief"))),
            SourceChannel.CLI,
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "7000"),
                new InventoryRelief(
                    new AccountCode("1400"),
                    new AccountCode("5000"),
                    new MonetaryAmount("EUR", "5000")),
                null,
                null,
                null));

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(sale, book);

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.AccountStateViolations(
                List.of(
                    new InventoryBalanceBelowZeroViolation(
                        new AccountCode("1400"),
                        "inventoryRelief.amount",
                        LocalDate.parse("2026-04-07"),
                        dev.erst.fingrind.core.BalanceSide.DEBIT,
                        Money.parse("EUR", "10.00"),
                        Money.parse("EUR", "50.00"),
                        Money.parse("EUR", "40.00"))))),
        rejection);
    assertEquals(1, book.findAccountsCalls);
  }

  @Test
  void rejectionFor_allowsInventoryIncreaseThatRepairsOneLegacyNegativeInventoryBalance() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    RegisteredAccount cash =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount inventory =
        new RegisteredAccount(
            new AccountCode("1400"),
            new AccountName("Inventory"),
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount expense =
        registeredAccount(
            new AccountCode("5000"),
            new AccountName("Expense"),
            AccountType.EXPENSE,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(cash.accountCode(), cash);
    book.accounts.put(inventory.accountCode(), inventory);
    book.accounts.put(expense.accountCode(), expense);
    book.postings =
        List.of(
            existingPosting(
                "posting-negative-inventory-1",
                "idem-negative-inventory-1",
                new JournalEntry(
                    LocalDate.parse("2026-04-06"),
                    List.of(
                        line("5000", JournalLine.EntrySide.DEBIT, "40.00"),
                        line("1400", JournalLine.EntrySide.CREDIT, "40.00")))));

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            command(
                "idem-repair-negative-inventory",
                List.of(
                    line("1400", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("1000", JournalLine.EntrySide.CREDIT, "10.00"))),
            book);

    assertEquals(Optional.empty(), rejection);
  }

  private static PostingCommand command(String idempotencyKey, List<JournalLine> lines) {
    return new PostingCommand(
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        new JournalEntry(LocalDate.parse("2026-04-07"), lines),
        PostingLineageModel.direct(),
        accountingEvidence(idempotencyKey),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        SourceChannel.CLI);
  }

  private static CommittedPosting existingPosting(
      String postingId, String idempotencyKey, JournalEntry journalEntry) {
    return new CommittedPosting(
        new PostingId(postingId),
        journalEntry,
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        accountingEvidence(idempotencyKey),
        new dev.erst.fingrind.core.CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  /** Validation-book double that exposes the batch account lookup path explicitly. */
  private static final class RecordingValidationBook implements PostingValidationStore {
    private final Map<AccountCode, RegisteredAccount> accounts = new ConcurrentHashMap<>();
    private boolean initialized;
    private final Optional<StoredRequestPosting> existingPosting = Optional.empty();
    private final Optional<LocalDate> closedThrough = Optional.empty();
    private List<CommittedPosting> postings = List.of();
    private int findAccountsCalls;

    @Override
    public BookLifecycleInspection inspectBook() {
      return initialized
          ? initializedLifecycleInspection(1001, 1, 1, Instant.parse("2026-04-07T10:15:30Z"))
          : new BookLifecycleInspection.Missing(1);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      throw new AssertionError("findAccount should not be used when batch lookup is available");
    }

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      findAccountsCalls++;
      Map<AccountCode, RegisteredAccount> matchedAccounts = new ConcurrentHashMap<>();
      for (AccountCode accountCode : accountCodes) {
        RegisteredAccount account = accounts.get(accountCode);
        if (account != null) {
          matchedAccounts.put(accountCode, account);
        }
      }
      return Map.copyOf(matchedAccounts);
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return existingPosting;
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
    public List<CommittedPosting> postings(
        dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
      return postings;
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return closedThrough;
    }
  }
}
