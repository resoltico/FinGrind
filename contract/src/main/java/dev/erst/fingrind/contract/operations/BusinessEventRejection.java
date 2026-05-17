package dev.erst.fingrind.contract.operations;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BusinessEventId;
import dev.erst.fingrind.core.BusinessEventKind;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.InventoryItemId;
import dev.erst.fingrind.core.TaxCode;
import java.util.Objects;

/** Deterministic rejection family for typed business-event commands. */
public sealed interface BusinessEventRejection
    permits BusinessEventRejection.BookNotInitialized,
        BusinessEventRejection.AccountingBasisUnsupported,
        BusinessEventRejection.FirstClassEvidenceRequired,
        BusinessEventRejection.TaxProfileRequired,
        BusinessEventRejection.UnknownTaxCode,
        BusinessEventRejection.ForeignExchangeUnsupported,
        BusinessEventRejection.UnknownAccount,
        BusinessEventRejection.InactiveAccount,
        BusinessEventRejection.InventoryItemMissing,
        BusinessEventRejection.BusinessEventNotFound,
        BusinessEventRejection.SettlementAmountExceedsOpenAmount {
  /** Book must be initialized before business-event commands may run. */
  record BookNotInitialized() implements BusinessEventRejection {}

  /** Selected accounting basis does not support the requested business-event recipe. */
  record AccountingBasisUnsupported(
      AccountingBasis accountingBasis, BusinessEventKind businessEventKind)
      implements BusinessEventRejection {
    public AccountingBasisUnsupported {
      Objects.requireNonNull(accountingBasis, "accountingBasis");
      Objects.requireNonNull(businessEventKind, "businessEventKind");
    }
  }

  /** Active evidence policy requires first-class source evidence for the request. */
  record FirstClassEvidenceRequired(BusinessEventKind businessEventKind)
      implements BusinessEventRejection {
    public FirstClassEvidenceRequired {
      Objects.requireNonNull(businessEventKind, "businessEventKind");
    }
  }

  /** Active tax policy requires a configured tax profile. */
  record TaxProfileRequired(TaxCode taxCode) implements BusinessEventRejection {
    public TaxProfileRequired {
      Objects.requireNonNull(taxCode, "taxCode");
    }
  }

  /** Requested tax code is unknown in the active tax profile. */
  record UnknownTaxCode(TaxCode taxCode) implements BusinessEventRejection {
    public UnknownTaxCode {
      Objects.requireNonNull(taxCode, "taxCode");
    }
  }

  /** Active foreign-exchange policy does not support the supplied transaction-currency evidence. */
  record ForeignExchangeUnsupported(
      CurrencyUnit transactionCurrency, CurrencyUnit functionalCurrency)
      implements BusinessEventRejection {
    public ForeignExchangeUnsupported {
      Objects.requireNonNull(transactionCurrency, "transactionCurrency");
      Objects.requireNonNull(functionalCurrency, "functionalCurrency");
    }
  }

  /** Requested account code does not exist in the selected book. */
  record UnknownAccount(AccountCode accountCode) implements BusinessEventRejection {
    public UnknownAccount {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Requested account exists but is inactive. */
  record InactiveAccount(AccountCode accountCode) implements BusinessEventRejection {
    public InactiveAccount {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Requested inventory item does not exist or has no available tracked quantity. */
  record InventoryItemMissing(InventoryItemId inventoryItemId) implements BusinessEventRejection {
    public InventoryItemMissing {
      Objects.requireNonNull(inventoryItemId, "inventoryItemId");
    }
  }

  /** Requested business event does not exist in the selected book. */
  record BusinessEventNotFound(BusinessEventId businessEventId) implements BusinessEventRejection {
    public BusinessEventNotFound {
      Objects.requireNonNull(businessEventId, "businessEventId");
    }
  }

  /** Attempted settlement exceeds the remaining open amount on the referenced event. */
  record SettlementAmountExceedsOpenAmount(BusinessEventId businessEventId)
      implements BusinessEventRejection {
    public SettlementAmountExceedsOpenAmount {
      Objects.requireNonNull(businessEventId, "businessEventId");
    }
  }
}
