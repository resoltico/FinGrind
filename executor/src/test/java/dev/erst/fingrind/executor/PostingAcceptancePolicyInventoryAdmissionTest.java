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
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.InventoryAccountState;
import dev.erst.fingrind.executor.bookkeeping.InventoryQuantityBelowZeroViolation;
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

/** Focused integration coverage for inventory admission inside posting acceptance. */
class PostingAcceptancePolicyInventoryAdmissionTest {
  private static final PostingAcceptancePolicy POSTING_ACCEPTANCE_POLICY =
      PostingAcceptancePolicy.currentKernel();

  @Test
  void rejectionFor_rejectsOpeningPositionsThatTouchNominalAccountsAlongsideInventory() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    book.accounts.put(
        new AccountCode("4000"),
        registeredAccount(
            new AccountCode("4000"),
            new AccountName("Revenue"),
            AccountType.REVENUE,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z")));
    book.accounts.put(
        new AccountCode("1400"),
        new RegisteredAccount(
            new AccountCode("1400"),
            new AccountName("Inventory"),
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
            new UnitOfMeasure("unit", 0),
            true,
            Instant.parse("2026-04-07T10:15:30Z")));

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            openingBalanceCommand(
                "idem-opening-inventory-backstop",
                List.of(
                    line("4000", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("1400", JournalLine.EntrySide.CREDIT, "10.00"))),
            book);

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.OpeningPositionTouchesNominalAccount(
                new AccountCode("4000"), AccountType.REVENUE)),
        rejection);
  }

  @Test
  void rejectionFor_allowsTheFinalPostingLayerToReceiveMultipleInventoryOpeningLines() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    book.accounts.put(
        new AccountCode("1400"),
        new RegisteredAccount(
            new AccountCode("1400"),
            new AccountName("Inventory Materials"),
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
            new UnitOfMeasure("unit", 0),
            true,
            Instant.parse("2026-04-07T10:15:30Z")));
    book.accounts.put(
        new AccountCode("1410"),
        new RegisteredAccount(
            new AccountCode("1410"),
            new AccountName("Inventory Finished Goods"),
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
            new UnitOfMeasure("unit", 0),
            true,
            Instant.parse("2026-04-07T10:15:30Z")));
    book.accounts.put(
        new AccountCode("3000"),
        registeredAccount(
            new AccountCode("3000"),
            new AccountName("Owner Capital"),
            AccountType.EQUITY,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z")));

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            openingBalanceCommand(
                "idem-opening-multi-inventory-backstop",
                List.of(
                    line("1400", JournalLine.EntrySide.DEBIT, "5.00"),
                    line("1410", JournalLine.EntrySide.DEBIT, "5.00"),
                    line("3000", JournalLine.EntrySide.CREDIT, "10.00"))),
            book);

    assertEquals(Optional.empty(), rejection);
  }

  @Test
  void rejectionFor_rejectsTypedSaleThatWouldOverRelieveInventoryQuantity() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    book.accounts.put(
        new AccountCode("1000"),
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z")));
    book.accounts.put(
        new AccountCode("1400"),
        new RegisteredAccount(
            new AccountCode("1400"),
            new AccountName("Inventory"),
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
            new UnitOfMeasure("unit", 0),
            true,
            Instant.parse("2026-04-07T10:15:30Z")));
    book.accounts.put(
        new AccountCode("4000"),
        registeredAccount(
            new AccountCode("4000"),
            new AccountName("Sales Revenue"),
            AccountType.REVENUE,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z")));
    book.accounts.put(
        new AccountCode("5000"),
        registeredAccount(
            new AccountCode("5000"),
            new AccountName("Cost of Sales"),
            AccountType.EXPENSE,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z")));
    book.inventoryStates.put(
        new AccountCode("1400"),
        new InventoryAccountState(
            new WeightedAverageCostingMath.InventoryPool(
                Quantity.ofScaledUnits(0, 1), dev.erst.fingrind.core.Money.parse("EUR", "10.00")),
            Optional.of(LocalDate.parse("2026-04-06"))));

    PostingCommand sale =
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.SALE_SETTLED,
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "70.00"),
                    line("4000", JournalLine.EntrySide.CREDIT, "70.00"),
                    line("5000", JournalLine.EntrySide.DEBIT, "20.00"),
                    line("1400", JournalLine.EntrySide.CREDIT, "20.00"))),
            PostingLineageModel.direct(),
            accountingEvidence("idem-sale-over-relief"),
            new RequestProvenance(
                new CommandId("c8312911-6f81-3981-8d79-aa32649462db"),
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
                    new dev.erst.fingrind.contract.bookkeeping.QuantityText("2")),
                null,
                null,
                null,
                null),
            null);

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(sale, book);

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.AccountStateViolations(
                List.of(
                    new InventoryQuantityBelowZeroViolation(
                        new AccountCode("1400"),
                        "inventoryRelief.quantity",
                        LocalDate.parse("2026-04-07"),
                        Quantity.ofScaledUnits(0, 1),
                        Quantity.ofScaledUnits(0, 2),
                        Quantity.ofScaledUnits(0, 1))))),
        rejection);
    assertEquals(0, book.findAccountsCalls);
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode), side, dev.erst.fingrind.core.Money.parse("EUR", amount));
  }

  private static PostingCommand openingBalanceCommand(
      String idempotencyKey, List<JournalLine> lines) {
    return new PostingCommand(
        PostingKind.OPENING_BALANCE,
        dev.erst.fingrind.core.PostingOriginKind.OPENING_POSITION,
        new JournalEntry(LocalDate.parse("2026-04-07"), lines),
        PostingLineageModel.direct(),
        accountingEvidence(idempotencyKey),
        new RequestProvenance(
            dev.erst.fingrind.executor.TestCommandIds.fromLabel("command-" + idempotencyKey),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-" + idempotencyKey),
            Optional.of(new CorrelationId("corr-" + idempotencyKey))),
        SourceChannel.CLI,
        null,
        null);
  }

  /** Validation-book double that exposes both batch account lookup and inventory-state lookup. */
  private static final class RecordingValidationBook implements PostingValidationStore {
    private final Map<AccountCode, RegisteredAccount> accounts = new ConcurrentHashMap<>();
    private final Map<AccountCode, InventoryAccountState> inventoryStates =
        new ConcurrentHashMap<>();
    private boolean initialized;
    private int findAccountsCalls;

    @Override
    public BookLifecycleInspection inspectBook() {
      return initialized
          ? initializedLifecycleInspection(1001, 1, 1, Instant.parse("2026-04-07T10:15:30Z"))
          : new BookLifecycleInspection.Missing(1);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.ofNullable(accounts.get(accountCode));
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
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Optional<InventoryAccountState> findInventoryAccountState(
        AccountCode inventoryAccountCode) {
      return Optional.ofNullable(inventoryStates.get(inventoryAccountCode));
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
    public List<CommittedPosting> postings(
        dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
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
