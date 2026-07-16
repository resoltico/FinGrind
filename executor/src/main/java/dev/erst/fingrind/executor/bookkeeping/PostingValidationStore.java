package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.executor.spi.AccountLookupStore;
import dev.erst.fingrind.executor.spi.AccrualCutoffLookupStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.FinancingLookupStore;
import dev.erst.fingrind.executor.spi.FixedAssetLookupStore;
import dev.erst.fingrind.executor.spi.InventoryMovementLookupStore;
import dev.erst.fingrind.executor.spi.InventoryStateLookupStore;
import dev.erst.fingrind.executor.spi.LatvianPayrollLookupStore;
import dev.erst.fingrind.executor.spi.PostingLookupStore;
import dev.erst.fingrind.executor.spi.PostingRangeStore;
import dev.erst.fingrind.executor.spi.RealizedForeignExchangeLookupStore;
import dev.erst.fingrind.executor.spi.TaxRegistrationLookupStore;

/** Composite validation view used by posting preflight and transactional commit policies. */
public interface PostingValidationStore
    extends BookLifecycleReader,
        AccountLookupStore,
        AccrualCutoffLookupStore,
        FinancingLookupStore,
        FixedAssetLookupStore,
        InventoryMovementLookupStore,
        InventoryStateLookupStore,
        LatvianPayrollLookupStore,
        PostingLookupStore,
        PostingRangeStore,
        RealizedForeignExchangeLookupStore,
        TaxRegistrationLookupStore {}
