package dev.erst.fingrind.contract.bookkeeping;

/**
 * A caller-authored business event whose journal is derived from its typed facts.
 *
 * <p>Direct journals, opening positions, and reversals remain distinct because they carry a
 * supplied journal, a collection of opening facts, or a reference to prior facts respectively.
 */
public sealed interface TypedBookkeepingEntry extends BookkeepingEntry
    permits BookkeepingEntry.SaleSettled,
        BookkeepingEntry.SaleOnCredit,
        BookkeepingEntry.PurchaseSettled,
        BookkeepingEntry.PurchaseOnCredit,
        InventoryBookkeepingEntryVariants,
        BookkeepingEntry.ExpenseSettled,
        BookkeepingEntry.ExpenseOnCredit,
        BookkeepingEntry.Receipt,
        BookkeepingEntry.Payment,
        BookkeepingEntry.OwnerContribution,
        BookkeepingEntry.OwnerWithdrawal {}
