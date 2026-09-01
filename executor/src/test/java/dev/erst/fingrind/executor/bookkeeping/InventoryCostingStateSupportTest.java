package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** Direct coverage for executor-owned inventory state helpers and exact-state failures. */
class InventoryCostingStateSupportTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final AccountCode INVENTORY = new AccountCode("1400");

  @Test
  void inventoryMovementRecord_rejectsZeroDelta() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryMovementRecord(
                    INVENTORY, date("2026-04-07"), InventoryMovementKind.ACQUISITION, 0L, 0L));

    assertEquals(
        "Inventory movement records must change quantity, carrying cost, or both.",
        failure.getMessage());
  }

  @Test
  void inventoryValuationFacts_rejectInvalidReplayOrderAndProjectionShape() {
    IllegalArgumentException sequenceFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryValuationMovementRecord(
                    INVENTORY,
                    date("2026-04-07"),
                    0L,
                    InventoryMovementKind.ACQUISITION,
                    1L,
                    100L,
                    new dev.erst.fingrind.core.PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")));
    IllegalArgumentException noChangeFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryValuationMovementRecord(
                    INVENTORY,
                    date("2026-04-07"),
                    1L,
                    InventoryMovementKind.CAPITALIZATION,
                    0L,
                    0L,
                    new dev.erst.fingrind.core.PostingId("41a95cd2-4a5f-3ef3-8a33-c2771905f362")));
    IllegalArgumentException nonInventoryFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new InventoryValuationView(nonInventoryAccount(), pool(0L, 0L), null, List.of()));
    IllegalArgumentException zeroQuantityProjectionFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryValuationView(
                    inventoryAccount(),
                    pool(0L, 0L),
                    Money.ofMinorUnits(CurrencyUnit.of("EUR"), 1L),
                    List.of()));

    assertEquals("accountSequence must be positive.", sequenceFailure.getMessage());
    assertEquals(
        "Inventory valuation movements must change quantity or cost.",
        noChangeFailure.getMessage());
    assertEquals(
        "Inventory valuation requires one inventory account.", nonInventoryFailure.getMessage());
    assertEquals(
        "A rounded unit-cost projection is required exactly when quantity is positive.",
        zeroQuantityProjectionFailure.getMessage());
  }

  @Test
  void inventoryViolationRecords_rejectBlankFields() {
    IllegalArgumentException horizonFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryMovementPrecedesAccountHorizonViolation(
                    INVENTORY, nullOf(), date("2026-04-07"), date("2026-04-08")));
    IllegalArgumentException quantityFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryQuantityBelowZeroViolation(
                    INVENTORY,
                    " ",
                    date("2026-04-07"),
                    Quantity.ofScaledUnits(0, 1),
                    Quantity.ofScaledUnits(0, 2),
                    Quantity.ofScaledUnits(0, 1)));
    IllegalArgumentException carryingCostFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryWriteDownExceedsCarryingCostViolation(
                    INVENTORY,
                    " ",
                    date("2026-04-07"),
                    Money.parse("EUR", "10.00"),
                    Money.parse("EUR", "2.00"),
                    Money.parse("EUR", "1.00")));
    IllegalArgumentException carryingCostNullFieldFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryWriteDownExceedsCarryingCostViolation(
                    INVENTORY,
                    nullOf(),
                    date("2026-04-07"),
                    Money.parse("EUR", "10.00"),
                    Money.parse("EUR", "2.00"),
                    Money.parse("EUR", "1.00")));

    assertEquals("field must not be blank.", horizonFailure.getMessage());
    assertEquals("field must not be blank.", quantityFailure.getMessage());
    assertEquals("field must not be blank.", carryingCostFailure.getMessage());
    assertEquals("field must not be blank.", carryingCostNullFieldFailure.getMessage());
  }

  @Test
  void applyCompensatingMovement_rejectsQuantityShortfalls() {
    InventoryQuantityBelowZeroFailure failure =
        assertThrows(
            InventoryQuantityBelowZeroFailure.class,
            () ->
                InventoryCostingStateSupport.applyCompensatingMovement(
                    pool(1L, 1000L),
                    new InventoryMovementRecord(
                        INVENTORY, date("2026-04-07"), InventoryMovementKind.DISPOSAL, -2L, 0L),
                    "reversal.priorPostingId"));

    assertEquals(INVENTORY, failure.accountCode());
    assertEquals("reversal.priorPostingId", failure.field());
    assertEquals(date("2026-04-07"), failure.effectiveDate());
    assertEquals(Quantity.ofScaledUnits(0, 1), failure.quantityOnHand());
    assertEquals(Quantity.ofScaledUnits(0, 2), failure.requestedDecreaseQuantity());
    assertEquals(Quantity.ofScaledUnits(0, 1), failure.resultingShortfallQuantity());
  }

  @Test
  void applyCompensatingMovement_rejectsCarryingCostShortfalls() {
    InventoryWriteDownExceedsCarryingCostFailure failure =
        assertThrows(
            InventoryWriteDownExceedsCarryingCostFailure.class,
            () ->
                InventoryCostingStateSupport.applyCompensatingMovement(
                    pool(2L, 500L),
                    new InventoryMovementRecord(
                        INVENTORY, date("2026-04-07"), InventoryMovementKind.WRITE_DOWN, 0L, -900L),
                    "reversal.priorPostingId"));

    assertEquals(INVENTORY, failure.accountCode());
    assertEquals("reversal.priorPostingId", failure.field());
    assertEquals(date("2026-04-07"), failure.effectiveDate());
    assertEquals(Money.parse("EUR", "5.00"), failure.carryingCostOnHand());
    assertEquals(Money.parse("EUR", "9.00"), failure.requestedCostDecrease());
    assertEquals(Money.parse("EUR", "4.00"), failure.resultingCostShortfall());
  }

  @Test
  void applyCompensatingMovement_acceptsExactQuantityAndCostRelief() {
    WeightedAverageCostingMath.InventoryPool remainingPool =
        InventoryCostingStateSupport.applyCompensatingMovement(
            pool(1L, 100L),
            new InventoryMovementRecord(
                INVENTORY, date("2026-04-07"), InventoryMovementKind.DISPOSAL, -1L, -100L),
            "reversal.priorPostingId");

    assertEquals(Quantity.zero(0), remainingPool.quantityOnHand());
    assertEquals(Money.zero(CurrencyUnit.of("EUR")), remainingPool.costPool());
  }

  @Test
  void applyCompensatingMovement_wrapsExactPoolInvariantFailuresWithCause() {
    InventoryWriteDownExceedsCarryingCostFailure quantityExpansionFailure =
        assertThrows(
            InventoryWriteDownExceedsCarryingCostFailure.class,
            () ->
                InventoryCostingStateSupport.applyCompensatingMovement(
                    pool(1L, 1L),
                    new InventoryMovementRecord(
                        INVENTORY, date("2026-04-07"), InventoryMovementKind.ACQUISITION, 1L, 0L),
                    "inventory-adjustment"));
    InventoryWriteDownExceedsCarryingCostFailure zeroToZeroFailure =
        assertThrows(
            InventoryWriteDownExceedsCarryingCostFailure.class,
            () ->
                InventoryCostingStateSupport.applyCompensatingMovement(
                    pool(1L, 1L),
                    new InventoryMovementRecord(
                        INVENTORY, date("2026-04-07"), InventoryMovementKind.DISPOSAL, -1L, 0L),
                    "inventory-adjustment"));
    InventoryWriteDownExceedsCarryingCostFailure positiveQuantityZeroCostFailure =
        assertThrows(
            InventoryWriteDownExceedsCarryingCostFailure.class,
            () ->
                InventoryCostingStateSupport.applyCompensatingMovement(
                    pool(1L, 1L),
                    new InventoryMovementRecord(
                        INVENTORY, date("2026-04-07"), InventoryMovementKind.WRITE_DOWN, 0L, -1L),
                    "inventory-adjustment"));

    assertEquals(
        Money.zero(CurrencyUnit.of("EUR")), quantityExpansionFailure.requestedCostDecrease());
    assertEquals(Money.parse("EUR", "0.01"), quantityExpansionFailure.resultingCostShortfall());
    WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException quantityExpansionCause =
        assertInstanceOf(
            WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException.class,
            quantityExpansionFailure.getCause());
    assertEquals(Quantity.ofScaledUnits(0, 2L), quantityExpansionCause.quantityOnHand());
    assertEquals(Money.ofMinorUnits(CurrencyUnit.of("EUR"), 1L), quantityExpansionCause.costPool());
    assertEquals(Money.zero(CurrencyUnit.of("EUR")), zeroToZeroFailure.requestedCostDecrease());
    assertEquals(Money.parse("EUR", "0.01"), zeroToZeroFailure.resultingCostShortfall());
    WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException zeroToZeroCause =
        assertInstanceOf(
            WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException.class,
            zeroToZeroFailure.getCause());
    assertEquals(Quantity.zero(0), zeroToZeroCause.quantityOnHand());
    assertEquals(Money.ofMinorUnits(CurrencyUnit.of("EUR"), 1L), zeroToZeroCause.costPool());
    assertEquals(
        Money.ofMinorUnits(CurrencyUnit.of("EUR"), 1L),
        positiveQuantityZeroCostFailure.requestedCostDecrease());
    assertEquals(
        Money.ofMinorUnits(CurrencyUnit.of("EUR"), 1L),
        positiveQuantityZeroCostFailure.resultingCostShortfall());
    WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException positiveQuantityZeroCostCause =
        assertInstanceOf(
            WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException.class,
            positiveQuantityZeroCostFailure.getCause());
    assertEquals(Quantity.ofScaledUnits(0, 1L), positiveQuantityZeroCostCause.quantityOnHand());
    assertEquals(Money.zero(CurrencyUnit.of("EUR")), positiveQuantityZeroCostCause.costPool());
  }

  @Test
  void disposeInventory_wrapsOverdrawsAndRethrowsUnexpectedMathValidationErrors() {
    RegisteredAccount inventoryAccount = inventoryAccount();
    InventoryAccountContext context =
        new InventoryAccountContext(
            inventoryAccount,
            new InventoryAccountState(pool(1L, 1000L), Optional.of(date("2026-04-06"))),
            date("2026-04-07"));

    InventoryQuantityBelowZeroFailure overdrawFailure =
        assertThrows(
            InventoryQuantityBelowZeroFailure.class,
            () ->
                InventoryCostingStateSupport.disposeInventory(
                    context, Quantity.ofScaledUnits(0, 2), "inventoryRelief.quantity"));
    IllegalArgumentException unexpectedFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                InventoryCostingStateSupport.disposeInventory(
                    context, Quantity.zero(0), "inventoryRelief.quantity"));

    assertEquals(Quantity.ofScaledUnits(0, 1), overdrawFailure.quantityOnHand());
    assertEquals(Quantity.ofScaledUnits(0, 2), overdrawFailure.requestedDecreaseQuantity());
    assertEquals(Quantity.ofScaledUnits(0, 1), overdrawFailure.resultingShortfallQuantity());
    WeightedAverageCostingMath.DisposedQuantityExceedsOnHandException overdrawCause =
        assertInstanceOf(
            WeightedAverageCostingMath.DisposedQuantityExceedsOnHandException.class,
            overdrawFailure.getCause());
    assertEquals(Quantity.ofScaledUnits(0, 2), overdrawCause.disposedQuantity());
    assertEquals(Quantity.ofScaledUnits(0, 1), overdrawCause.quantityOnHand());
    assertEquals("disposedQuantity must be positive.", unexpectedFailure.getMessage());
  }

  @Test
  void disposeInventory_returnsExactCostOfSalesAndTheRemainingPool() {
    InventoryAccountContext context =
        new InventoryAccountContext(
            inventoryAccount(),
            new InventoryAccountState(pool(1L, 1000L), Optional.of(date("2026-04-06"))),
            date("2026-04-07"));

    WeightedAverageCostingMath.Disposal disposal =
        InventoryCostingStateSupport.disposeInventory(
            context, Quantity.ofScaledUnits(0, 1), "inventoryRelief.quantity");

    assertEquals(Quantity.zero(0), disposal.remainingPool().quantityOnHand());
    assertEquals(Money.zero(CurrencyUnit.of("EUR")), disposal.remainingPool().costPool());
    assertEquals(Money.parse("EUR", "10.00"), disposal.costOfSales());
  }

  @Test
  void inventoryContext_requiresDeclaredAccountUnitOfMeasureAndRespectsHorizon() {
    StateLookupStore emptyStore = new StateLookupStore();
    IllegalStateException missingAccountFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                InventoryCostingStateSupport.inventoryContext(
                    INVENTORY, date("2026-04-07"), "inventoryRelief.quantity", emptyStore));

    StateLookupStore nonInventoryStore = new StateLookupStore();
    nonInventoryStore.accounts.put(INVENTORY, nonInventoryAccount());
    IllegalStateException missingUnitOfMeasureFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                InventoryCostingStateSupport.inventoryContext(
                    INVENTORY, date("2026-04-07"), "inventoryRelief.quantity", nonInventoryStore));
    IllegalStateException directUnitOfMeasureFailure =
        assertThrows(
            IllegalStateException.class,
            () -> InventoryCostingStateSupport.requireUnitOfMeasure(nonInventoryAccount()));

    StateLookupStore horizonStore = new StateLookupStore();
    horizonStore.accounts.put(INVENTORY, inventoryAccount());
    horizonStore.states.put(
        INVENTORY, new InventoryAccountState(pool(1L, 1000L), Optional.of(date("2026-04-08"))));
    InventoryMovementPrecedesAccountHorizonFailure horizonFailure =
        assertThrows(
            InventoryMovementPrecedesAccountHorizonFailure.class,
            () ->
                InventoryCostingStateSupport.inventoryContext(
                    INVENTORY, date("2026-04-07"), "inventoryRelief.quantity", horizonStore));

    assertEquals(
        "Inventory resolution requires declared account 1400.", missingAccountFailure.getMessage());
    assertEquals(
        "Inventory resolution requires one inventory account with one unit of measure.",
        missingUnitOfMeasureFailure.getMessage());
    assertEquals(
        "Inventory resolution requires one inventory account with one unit of measure.",
        directUnitOfMeasureFailure.getMessage());
    assertEquals("inventoryRelief.quantity", horizonFailure.field());
    assertEquals(date("2026-04-07"), horizonFailure.attemptedEffectiveDate());
    assertEquals(date("2026-04-08"), horizonFailure.accountHorizonEffectiveDate());
  }

  @Test
  void inventoryContext_usesZeroStateAndExplicitOverride() {
    StateLookupStore store = new StateLookupStore();
    store.accounts.put(INVENTORY, inventoryAccount());

    InventoryAccountContext zeroStateContext =
        InventoryCostingStateSupport.inventoryContext(
            INVENTORY, date("2026-04-07"), "inventoryRelief.quantity", store);
    InventoryAccountState overridingState =
        new InventoryAccountState(pool(2L, 2000L), Optional.of(date("2026-04-05")));
    InventoryAccountContext overridingContext =
        InventoryCostingStateSupport.inventoryContext(
            INVENTORY, date("2026-04-05"), "inventoryRelief.quantity", store, overridingState);

    assertEquals(Quantity.zero(0), zeroStateContext.inventoryState().pool().quantityOnHand());
    assertEquals(
        Money.zero(CurrencyUnit.of("EUR")), zeroStateContext.inventoryState().pool().costPool());
    assertEquals(Optional.empty(), zeroStateContext.inventoryState().lastMovementDate());
    assertSame(overridingState, overridingContext.inventoryState());
  }

  private static WeightedAverageCostingMath.InventoryPool pool(
      long quantityUnits, long costMinorUnits) {
    return new WeightedAverageCostingMath.InventoryPool(
        Quantity.ofScaledUnits(0, quantityUnits),
        Money.ofMinorUnits(CurrencyUnit.of("EUR"), costMinorUnits));
  }

  private static RegisteredAccount inventoryAccount() {
    return new RegisteredAccount(
        INVENTORY,
        new AccountName("Inventory"),
        AccountType.ASSET,
        dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy(
            dev.erst.fingrind.core.FinancialPositionLineClassification.INVENTORY),
        new UnitOfMeasure("unit", 0),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount nonInventoryAccount() {
    return new RegisteredAccount(
        INVENTORY,
        new AccountName("Cash"),
        AccountType.ASSET,
        dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy(
            dev.erst.fingrind.core.FinancialPositionLineClassification.CURRENT_ASSET),
        true,
        DECLARED_AT);
  }

  private static LocalDate date(String value) {
    return LocalDate.parse(value);
  }

  /** Minimal validation-store double for exact inventory state helper coverage. */
  private static final class StateLookupStore implements PostingValidationStore {
    private final BookIdentity bookIdentity =
        dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity();
    private final Map<AccountCode, RegisteredAccount> accounts = new ConcurrentHashMap<>();
    private final Map<AccountCode, InventoryAccountState> states = new ConcurrentHashMap<>();

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(1001, 1, 1, DECLARED_AT, bookIdentity);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.ofNullable(accounts.get(accountCode));
    }

    @Override
    public Optional<InventoryAccountState> findInventoryAccountState(
        AccountCode inventoryAccountCode) {
      return Optional.ofNullable(states.get(inventoryAccountCode));
    }

    @Override
    public Optional<dev.erst.fingrind.executor.spi.StoredRequestPosting> findExistingPosting(
        IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(dev.erst.fingrind.core.PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(
        dev.erst.fingrind.core.PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public java.util.List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return java.util.List.of();
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
