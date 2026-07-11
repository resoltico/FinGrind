package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Shared fixture support for inventory-admission executor tests. */
class InventoryAdmissionPolicyTestSupport {
  protected static final InventoryAdmissionPolicy POLICY = new InventoryAdmissionPolicy();
  protected static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");
  protected static final AccountCode INVENTORY = new AccountCode("1400");
  protected static final AccountCode INVENTORY_FRACTIONAL = new AccountCode("1410");

  protected final BookkeepingEntry.SaleSettled saleEntry(LocalDate effectiveDate, String quantity) {
    return new BookkeepingEntry.SaleSettled(
        effectiveDate,
        new AccountCode("1000"),
        new AccountCode("4000"),
        new MonetaryAmount("EUR", "7000"),
        new InventoryRelief(
            INVENTORY,
            new AccountCode("5000"),
            new dev.erst.fingrind.contract.bookkeeping.QuantityText(quantity)),
        null,
        null,
        null,
        null);
  }

  protected final BookkeepingEntry.SaleOnCredit saleOnCreditEntry(
      LocalDate effectiveDate, String quantity) {
    return new BookkeepingEntry.SaleOnCredit(
        effectiveDate,
        new AccountCode("1200"),
        new AccountCode("4000"),
        new MonetaryAmount("EUR", "7000"),
        new InventoryRelief(
            INVENTORY,
            new AccountCode("5000"),
            new dev.erst.fingrind.contract.bookkeeping.QuantityText(quantity)),
        null,
        null,
        null,
        null);
  }

  protected final BookkeepingEntry.Reversal reversalEntry(
      LocalDate effectiveDate,
      PostingId priorPostingId,
      List<dev.erst.fingrind.core.JournalLine> lines) {
    return new BookkeepingEntry.Reversal(
        effectiveDate,
        new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
            new ReversalReference(priorPostingId), new ReversalReason("operator correction")),
        null,
        new dev.erst.fingrind.core.JournalEntry(effectiveDate, lines));
  }

  protected final RegisteredAccount inventoryAccount() {
    return inventoryAccount(INVENTORY, new UnitOfMeasure("unit", 0));
  }

  protected final RegisteredAccount inventoryAccount(
      AccountCode accountCode, UnitOfMeasure unitOfMeasure) {
    return new RegisteredAccount(
        accountCode,
        new AccountName("Inventory"),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
        unitOfMeasure,
        true,
        DECLARED_AT);
  }

  protected final InventoryAccountState inventoryState(
      Quantity quantity, Money costPool, LocalDate lastMovementDate) {
    return new InventoryAccountState(
        new WeightedAverageCostingMath.InventoryPool(quantity, costPool),
        Optional.of(lastMovementDate));
  }

  /**
   * Validation-book double that exposes declared accounts, inventory state, and prior movements.
   */
  protected static final class RecordingValidationBook implements PostingValidationStore {
    final Map<AccountCode, RegisteredAccount> accounts = new ConcurrentHashMap<>();
    final Map<AccountCode, InventoryAccountState> states = new ConcurrentHashMap<>();
    final Map<PostingId, List<InventoryMovementRecord>> movementsByPostingId =
        new ConcurrentHashMap<>();

    @Override
    public BookLifecycleInspection inspectBook() {
      return initializedLifecycleInspection(1001, 1, 1, DECLARED_AT);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.ofNullable(accounts.get(accountCode));
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
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
    public Optional<InventoryAccountState> findInventoryAccountState(
        AccountCode inventoryAccountCode) {
      return Optional.ofNullable(states.get(inventoryAccountCode));
    }

    @Override
    public List<InventoryMovementRecord> inventoryMovements(PostingId postingId) {
      return movementsByPostingId.getOrDefault(postingId, List.of());
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(
        dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
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

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }
  }
}
