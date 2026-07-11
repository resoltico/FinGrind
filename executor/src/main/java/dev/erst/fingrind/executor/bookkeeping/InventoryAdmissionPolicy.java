package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;

/** First-defense inventory admission owner before SQLite trigger backstops run. */
public final class InventoryAdmissionPolicy {
  private final InventoryCostingEngine costingEngine = new InventoryCostingEngine();

  /**
   * Resolves inventory-owned consequences or raises the first deterministic inventory rejection.
   */
  public InventoryPostingResolution resolve(BookkeepingEntry entry, PostingValidationStore book) {
    try {
      return costingEngine.resolve(entry, book);
    } catch (InventoryEntrySemanticsFailure failure) {
      throw new InventoryAdmissionFailure(
          new BookkeepingPostingRejection.EntrySemanticsViolations(
              java.util.List.of(failure.violation())),
          failure);
    } catch (InventoryMovementPrecedesAccountHorizonFailure failure) {
      throw new InventoryAdmissionFailure(
          new BookkeepingPostingRejection.AccountStateViolations(
              java.util.List.of(
                  new InventoryMovementPrecedesAccountHorizonViolation(
                      failure.accountCode(),
                      failure.field(),
                      failure.attemptedEffectiveDate(),
                      failure.accountHorizonEffectiveDate()))),
          failure);
    } catch (InventoryQuantityBelowZeroFailure failure) {
      throw new InventoryAdmissionFailure(
          new BookkeepingPostingRejection.AccountStateViolations(
              java.util.List.of(
                  new InventoryQuantityBelowZeroViolation(
                      failure.accountCode(),
                      failure.field(),
                      failure.effectiveDate(),
                      failure.quantityOnHand(),
                      failure.requestedDecreaseQuantity(),
                      failure.resultingShortfallQuantity()))),
          failure);
    } catch (InventoryWriteDownExceedsCarryingCostFailure failure) {
      throw new InventoryAdmissionFailure(
          new BookkeepingPostingRejection.AccountStateViolations(
              java.util.List.of(
                  new InventoryWriteDownExceedsCarryingCostViolation(
                      failure.accountCode(),
                      failure.field(),
                      failure.effectiveDate(),
                      failure.carryingCostOnHand(),
                      failure.requestedCostDecrease(),
                      failure.resultingCostShortfall()))),
          failure);
    }
  }

  /** Signals that inventory admission resolved into one published posting rejection. */
  public static final class InventoryAdmissionFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient BookkeepingPostingRejection rejection;

    InventoryAdmissionFailure(BookkeepingPostingRejection rejection, Throwable cause) {
      super("Inventory admission failed.", cause);
      this.rejection = java.util.Objects.requireNonNull(rejection, "rejection");
    }

    /** Returns the deterministic published rejection produced by inventory admission. */
    public BookkeepingPostingRejection rejection() {
      return rejection;
    }
  }
}
