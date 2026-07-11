package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import java.time.LocalDate;
import java.util.Objects;

/** Inventory-costing admission failures raised before durable SQLite backstops run. */
sealed class InventoryCostingResolutionFailure extends RuntimeException
    permits InventoryMovementPrecedesAccountHorizonFailure,
        InventoryQuantityBelowZeroFailure,
        InventoryWriteDownExceedsCarryingCostFailure {
  private static final long serialVersionUID = 1L;

  InventoryCostingResolutionFailure(String message) {
    super(message);
  }

  InventoryCostingResolutionFailure(String message, Throwable cause) {
    super(message, cause);
  }
}

/** Signals that one inventory movement attempts to backdate before the account horizon. */
final class InventoryMovementPrecedesAccountHorizonFailure
    extends InventoryCostingResolutionFailure {
  private static final long serialVersionUID = 1L;

  private final transient AccountCode accountCode;
  private final String field;
  private final LocalDate attemptedEffectiveDate;
  private final LocalDate accountHorizonEffectiveDate;

  InventoryMovementPrecedesAccountHorizonFailure(
      AccountCode accountCode,
      String field,
      LocalDate attemptedEffectiveDate,
      LocalDate accountHorizonEffectiveDate) {
    super("Inventory movement precedes the durable account horizon.");
    this.accountCode = Objects.requireNonNull(accountCode, "accountCode");
    this.field = Objects.requireNonNull(field, "field");
    this.attemptedEffectiveDate =
        Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
    this.accountHorizonEffectiveDate =
        Objects.requireNonNull(accountHorizonEffectiveDate, "accountHorizonEffectiveDate");
  }

  AccountCode accountCode() {
    return accountCode;
  }

  String field() {
    return field;
  }

  LocalDate attemptedEffectiveDate() {
    return attemptedEffectiveDate;
  }

  LocalDate accountHorizonEffectiveDate() {
    return accountHorizonEffectiveDate;
  }
}

/** Signals that one inventory movement would push on-hand quantity below zero. */
final class InventoryQuantityBelowZeroFailure extends InventoryCostingResolutionFailure {
  private static final long serialVersionUID = 1L;

  private final transient AccountCode accountCode;
  private final String field;
  private final LocalDate effectiveDate;
  private final transient Quantity quantityOnHand;
  private final transient Quantity requestedDecreaseQuantity;
  private final transient Quantity resultingShortfallQuantity;

  InventoryQuantityBelowZeroFailure(
      AccountCode accountCode,
      String field,
      LocalDate effectiveDate,
      Quantity quantityOnHand,
      Quantity requestedDecreaseQuantity,
      Quantity resultingShortfallQuantity) {
    super("Inventory quantity would fall below zero.");
    this.accountCode = Objects.requireNonNull(accountCode, "accountCode");
    this.field = Objects.requireNonNull(field, "field");
    this.effectiveDate = Objects.requireNonNull(effectiveDate, "effectiveDate");
    this.quantityOnHand = Objects.requireNonNull(quantityOnHand, "quantityOnHand");
    this.requestedDecreaseQuantity =
        Objects.requireNonNull(requestedDecreaseQuantity, "requestedDecreaseQuantity");
    this.resultingShortfallQuantity =
        Objects.requireNonNull(resultingShortfallQuantity, "resultingShortfallQuantity");
  }

  InventoryQuantityBelowZeroFailure(
      AccountCode accountCode,
      String field,
      LocalDate effectiveDate,
      Quantity quantityOnHand,
      Quantity requestedDecreaseQuantity,
      Quantity resultingShortfallQuantity,
      Throwable cause) {
    super("Inventory quantity would fall below zero.", cause);
    this.accountCode = Objects.requireNonNull(accountCode, "accountCode");
    this.field = Objects.requireNonNull(field, "field");
    this.effectiveDate = Objects.requireNonNull(effectiveDate, "effectiveDate");
    this.quantityOnHand = Objects.requireNonNull(quantityOnHand, "quantityOnHand");
    this.requestedDecreaseQuantity =
        Objects.requireNonNull(requestedDecreaseQuantity, "requestedDecreaseQuantity");
    this.resultingShortfallQuantity =
        Objects.requireNonNull(resultingShortfallQuantity, "resultingShortfallQuantity");
  }

  AccountCode accountCode() {
    return accountCode;
  }

  String field() {
    return field;
  }

  LocalDate effectiveDate() {
    return effectiveDate;
  }

  Quantity quantityOnHand() {
    return quantityOnHand;
  }

  Quantity requestedDecreaseQuantity() {
    return requestedDecreaseQuantity;
  }

  Quantity resultingShortfallQuantity() {
    return resultingShortfallQuantity;
  }
}

/** Signals that one inventory write-down would reduce carrying cost below zero. */
final class InventoryWriteDownExceedsCarryingCostFailure extends InventoryCostingResolutionFailure {
  private static final long serialVersionUID = 1L;

  private final transient AccountCode accountCode;
  private final String field;
  private final LocalDate effectiveDate;
  private final transient Money carryingCostOnHand;
  private final transient Money requestedCostDecrease;
  private final transient Money resultingCostShortfall;

  InventoryWriteDownExceedsCarryingCostFailure(
      AccountCode accountCode,
      String field,
      LocalDate effectiveDate,
      Money carryingCostOnHand,
      Money requestedCostDecrease,
      Money resultingCostShortfall) {
    super("Inventory carrying cost would fall below zero.");
    this.accountCode = Objects.requireNonNull(accountCode, "accountCode");
    this.field = Objects.requireNonNull(field, "field");
    this.effectiveDate = Objects.requireNonNull(effectiveDate, "effectiveDate");
    this.carryingCostOnHand = Objects.requireNonNull(carryingCostOnHand, "carryingCostOnHand");
    this.requestedCostDecrease =
        Objects.requireNonNull(requestedCostDecrease, "requestedCostDecrease");
    this.resultingCostShortfall =
        Objects.requireNonNull(resultingCostShortfall, "resultingCostShortfall");
  }

  InventoryWriteDownExceedsCarryingCostFailure(
      AccountCode accountCode,
      String field,
      LocalDate effectiveDate,
      Money carryingCostOnHand,
      Money requestedCostDecrease,
      Money resultingCostShortfall,
      Throwable cause) {
    super("Inventory carrying cost would fall below zero.", cause);
    this.accountCode = Objects.requireNonNull(accountCode, "accountCode");
    this.field = Objects.requireNonNull(field, "field");
    this.effectiveDate = Objects.requireNonNull(effectiveDate, "effectiveDate");
    this.carryingCostOnHand = Objects.requireNonNull(carryingCostOnHand, "carryingCostOnHand");
    this.requestedCostDecrease =
        Objects.requireNonNull(requestedCostDecrease, "requestedCostDecrease");
    this.resultingCostShortfall =
        Objects.requireNonNull(resultingCostShortfall, "resultingCostShortfall");
  }

  AccountCode accountCode() {
    return accountCode;
  }

  String field() {
    return field;
  }

  LocalDate effectiveDate() {
    return effectiveDate;
  }

  Money carryingCostOnHand() {
    return carryingCostOnHand;
  }

  Money requestedCostDecrease() {
    return requestedCostDecrease;
  }

  Money resultingCostShortfall() {
    return resultingCostShortfall;
  }
}
