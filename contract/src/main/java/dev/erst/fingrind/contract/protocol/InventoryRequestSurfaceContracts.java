package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Set;

/** Request-surface facts owned by the inventory costing context. */
final class InventoryRequestSurfaceContracts {
  private InventoryRequestSurfaceContracts() {}

  static List<RequestSurfaceFacts.BookkeepingEntryKindFacts> purchaseEntryKindFacts() {
    return List.of(
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.PURCHASE_SETTLED,
            Set.of(
                ProtocolBusinessEventFields.Core.ENTRY_KIND,
                ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
                ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Inventory.QUANTITY,
                ProtocolBusinessEventFields.Inventory.UNIT_COST,
                ProtocolBusinessEventFields.Core.EVIDENCE,
                ProtocolBusinessEventFields.Core.PROVENANCE),
            Set.of(
                ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
                ProtocolBusinessEventFields.Core.TAX),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("purchase-receipt", "cash-disbursement", "bank-payment-confirmation"),
                "Accepted source-document types for settled-purchase requests.",
                "purchase-receipt"),
            "Settled-purchase writes debit one inventory asset account and credit one cash-and-cash-equivalent asset account on trading-template books. The request carries one exact acquired quantity and one exact per-unit carrying cost so FinGrind can enlarge the inventory pool deterministically."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.PURCHASE_ON_CREDIT,
            Set.of(
                ProtocolBusinessEventFields.Core.ENTRY_KIND,
                ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
                ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Core.PAYABLE_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Inventory.QUANTITY,
                ProtocolBusinessEventFields.Inventory.UNIT_COST,
                ProtocolBusinessEventFields.Core.EVIDENCE,
                ProtocolBusinessEventFields.Core.PROVENANCE),
            Set.of(
                ProtocolBusinessEventFields.Core.TAX,
                ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("supplier-invoice"),
                "Accepted source-document types for purchase-on-credit requests.",
                "supplier-invoice"),
            "Purchase-on-credit writes debit one inventory asset account and credit one trade payable account on trading-template books. The request carries one exact acquired quantity and one exact per-unit carrying cost so FinGrind can enlarge the inventory pool deterministically."));
  }

  static List<RequestSurfaceFacts.BookkeepingEntryKindFacts> maintenanceEntryKindFacts() {
    return List.of(
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
            Set.of(
                ProtocolBusinessEventFields.Core.ENTRY_KIND,
                ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
                ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Core.AMOUNT,
                ProtocolBusinessEventFields.Core.EVIDENCE,
                ProtocolBusinessEventFields.Core.PROVENANCE),
            Set.of(
                ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
                ProtocolBusinessEventFields.Core.TAX),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("landed-cost-invoice", "freight-invoice", "duty-assessment"),
                "Accepted source-document types for settled inventory-capitalization requests.",
                "landed-cost-invoice"),
            "Settled inventory capitalization debits one inventory account and credits one cash-and-cash-equivalent account without changing quantity. The amount is pre-VAT carrying cost; recoverable tax remains outside the pool and nonrecoverable tax is capitalized."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
            Set.of(
                ProtocolBusinessEventFields.Core.ENTRY_KIND,
                ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
                ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Core.PAYABLE_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Core.AMOUNT,
                ProtocolBusinessEventFields.Core.EVIDENCE,
                ProtocolBusinessEventFields.Core.PROVENANCE),
            Set.of(
                ProtocolBusinessEventFields.Core.TAX,
                ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("landed-cost-invoice", "freight-invoice", "duty-assessment"),
                "Accepted source-document types for inventory-capitalization-on-credit requests.",
                "landed-cost-invoice"),
            "Inventory capitalization on credit debits one inventory account and credits one trade payable account without changing quantity. The amount is pre-VAT carrying cost; recoverable tax remains outside the pool and nonrecoverable tax is capitalized."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.INVENTORY_WRITE_DOWN,
            Set.of(
                ProtocolBusinessEventFields.Core.ENTRY_KIND,
                ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
                ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Inventory.WRITE_DOWN_LOSS_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Core.AMOUNT,
                ProtocolBusinessEventFields.Core.EVIDENCE,
                ProtocolBusinessEventFields.Core.PROVENANCE),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("inventory-write-down-assessment", "obsolescence-review"),
                "Accepted source-document types for inventory write-down requests.",
                "inventory-write-down-assessment"),
            "Inventory write-down debits one expense account and credits one inventory account by an admitted carrying-cost decrease without changing quantity."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.INVENTORY_SHRINKAGE,
            Set.of(
                ProtocolBusinessEventFields.Core.ENTRY_KIND,
                ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
                ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Inventory.SHRINKAGE_LOSS_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Inventory.QUANTITY,
                ProtocolBusinessEventFields.Core.EVIDENCE,
                ProtocolBusinessEventFields.Core.PROVENANCE),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("inventory-count-sheet", "shrinkage-report"),
                "Accepted source-document types for inventory shrinkage requests.",
                "inventory-count-sheet"),
            "Inventory shrinkage debits one shrinkage-loss account and credits one inventory account. FinGrind derives the carrying cost from the exact inventory pool for the supplied quantity."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.INVENTORY_COUNT_INCREASE,
            Set.of(
                ProtocolBusinessEventFields.Core.ENTRY_KIND,
                ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
                ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Inventory.COUNT_GAIN_ACCOUNT_CODE,
                ProtocolBusinessEventFields.Inventory.QUANTITY,
                ProtocolBusinessEventFields.Inventory.UNIT_COST,
                ProtocolBusinessEventFields.Core.EVIDENCE,
                ProtocolBusinessEventFields.Core.PROVENANCE),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("inventory-count-sheet", "inventory-count-adjustment"),
                "Accepted source-document types for inventory count-increase requests.",
                "inventory-count-sheet"),
            "Inventory count increase debits one inventory account and credits one count-gain account. The request supplies one exact quantity and pre-tax per-unit carrying cost."));
  }
}
