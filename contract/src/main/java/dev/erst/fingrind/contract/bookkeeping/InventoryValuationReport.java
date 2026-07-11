package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact inventory-pool report projected from the ordered inventory movement ledger. */
public record InventoryValuationReport(
    BookIdentity bookIdentity,
    Optional<LocalDate> effectiveDateAsOf,
    boolean includesMovements,
    List<InventoryValuationAccount> accounts) {
  /** Validates one inventory-valuation report. */
  public InventoryValuationReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    accounts = ContractDescriptorValidation.copyList(accounts, "accounts");
    if (!includesMovements
        && accounts.stream().anyMatch(account -> !account.movements().isEmpty())) {
      throw new IllegalArgumentException(
          "Accounts must not publish movements when includesMovements is false.");
    }
  }
}
