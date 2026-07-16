package dev.erst.fingrind.contract.bookkeeping;

/**
 * A caller-authored business event whose journal is derived from its typed facts.
 *
 * <p>Direct journals, opening positions, and reversals remain distinct because they carry a
 * supplied journal, a collection of opening facts, or a reference to prior facts respectively.
 */
public sealed interface TypedBookkeepingEntry extends BookkeepingEntry
    permits StandardBookkeepingEntryVariants,
        InventoryBookkeepingEntryVariants,
        AccrualCutoffBookkeepingEntryVariants,
        LatvianPayrollBookkeepingEntryVariants,
        FixedAssetBookkeepingEntryVariants,
        FinancingBookkeepingEntryVariants,
        RealizedForeignExchangeBookkeepingEntryVariants {}
