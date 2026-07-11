package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Inventory-specific caller-authored bookkeeping-entry variants. */
public sealed interface InventoryBookkeepingEntryVariants extends TypedBookkeepingEntry
    permits InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled,
        InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit,
        InventoryBookkeepingEntryVariants.InventoryWriteDown,
        InventoryBookkeepingEntryVariants.InventoryShrinkage,
        InventoryBookkeepingEntryVariants.InventoryCountIncrease {
  record InventoryCapitalizationSettled(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements InventoryBookkeepingEntryVariants {
    public InventoryCapitalizationSettled {
      var state =
          InventoryEntryConstructionSupport.inventoryCapitalizationSettled(
              effectiveDate,
              inventoryAccountCode,
              cashAccountCode,
              amount,
              foreignExchangeDetails,
              taxSelection,
              appliedTax);
      effectiveDate = state.effectiveDate();
      inventoryAccountCode = state.inventoryAccountCode();
      cashAccountCode = state.cashAccountCode();
      amount = state.amount();
    }
  }

  record InventoryCapitalizationOnCredit(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode payableAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements InventoryBookkeepingEntryVariants {
    public InventoryCapitalizationOnCredit {
      var state =
          InventoryEntryConstructionSupport.inventoryCapitalizationOnCredit(
              effectiveDate,
              inventoryAccountCode,
              payableAccountCode,
              amount,
              foreignExchangeDetails,
              taxSelection,
              appliedTax);
      effectiveDate = state.effectiveDate();
      inventoryAccountCode = state.inventoryAccountCode();
      payableAccountCode = state.payableAccountCode();
      amount = state.amount();
      foreignExchangeDetails = state.foreignExchangeDetails();
    }
  }

  record InventoryWriteDown(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode writeDownLossAccountCode,
      MonetaryAmount amount)
      implements InventoryBookkeepingEntryVariants {
    public InventoryWriteDown {
      var state =
          InventoryEntryConstructionSupport.inventoryWriteDown(
              effectiveDate, inventoryAccountCode, writeDownLossAccountCode, amount);
      effectiveDate = state.effectiveDate();
      inventoryAccountCode = state.inventoryAccountCode();
      writeDownLossAccountCode = state.writeDownLossAccountCode();
      amount = state.amount();
    }
  }

  record InventoryShrinkage(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode shrinkageLossAccountCode,
      QuantityText quantity,
      @Nullable ResolvedInventoryDisposal resolvedInventoryDisposal)
      implements InventoryBookkeepingEntryVariants {
    public InventoryShrinkage {
      var state =
          InventoryEntryConstructionSupport.inventoryShrinkage(
              effectiveDate,
              inventoryAccountCode,
              shrinkageLossAccountCode,
              quantity,
              resolvedInventoryDisposal);
      effectiveDate = state.effectiveDate();
      inventoryAccountCode = state.inventoryAccountCode();
      shrinkageLossAccountCode = state.shrinkageLossAccountCode();
      quantity = state.quantity();
      resolvedInventoryDisposal = state.resolvedInventoryDisposal();
    }
  }

  record InventoryCountIncrease(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode countGainAccountCode,
      QuantityText quantity,
      MonetaryAmount unitCost,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition)
      implements InventoryBookkeepingEntryVariants {
    public InventoryCountIncrease {
      var state =
          InventoryEntryConstructionSupport.inventoryCountIncrease(
              effectiveDate,
              inventoryAccountCode,
              countGainAccountCode,
              quantity,
              unitCost,
              resolvedInventoryAcquisition);
      effectiveDate = state.effectiveDate();
      inventoryAccountCode = state.inventoryAccountCode();
      countGainAccountCode = state.countGainAccountCode();
      quantity = state.quantity();
      unitCost = state.unitCost();
      resolvedInventoryAcquisition = state.resolvedInventoryAcquisition();
    }
  }
}
