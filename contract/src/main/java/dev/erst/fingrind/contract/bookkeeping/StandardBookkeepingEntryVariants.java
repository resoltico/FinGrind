package dev.erst.fingrind.contract.bookkeeping;

/** Typed business events outside the inventory and accrual cut-off bounded contexts. */
public sealed interface StandardBookkeepingEntryVariants extends TypedBookkeepingEntry
    permits BookkeepingEntry.SaleSettled,
        BookkeepingEntry.SaleOnCredit,
        BookkeepingEntry.PurchaseSettled,
        BookkeepingEntry.PurchaseOnCredit,
        BookkeepingEntry.ExpenseSettled,
        BookkeepingEntry.ExpenseOnCredit,
        BookkeepingEntry.Receipt,
        BookkeepingEntry.Payment,
        BookkeepingEntry.OwnerContribution,
        BookkeepingEntry.OwnerWithdrawal {}
