package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;

/** Composes the narrow SQLite validation capabilities required by posting admission. */
interface SqliteTransactionValidationCapabilityView
    extends PostingValidationStore,
        SqliteTransactionValidationLifecycleCapabilityView,
        SqliteTransactionValidationAccountCapabilityView,
        SqliteTransactionValidationPostingCapabilityView,
        SqliteTransactionValidationOwnedContextCapabilityView,
        SqliteTransactionValidationPayrollCapabilityView {}
