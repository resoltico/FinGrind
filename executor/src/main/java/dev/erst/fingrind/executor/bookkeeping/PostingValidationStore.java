package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.executor.spi.AccountLookupStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PostingLookupStore;
import dev.erst.fingrind.executor.spi.PostingRangeStore;
import dev.erst.fingrind.executor.spi.TaxRegistrationLookupStore;

/** Composite validation view used by posting preflight and transactional commit policies. */
public interface PostingValidationStore
    extends BookLifecycleReader,
        AccountLookupStore,
        PostingLookupStore,
        PostingRangeStore,
        TaxRegistrationLookupStore {}
