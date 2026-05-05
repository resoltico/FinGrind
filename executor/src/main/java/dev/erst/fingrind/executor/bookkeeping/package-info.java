/**
 * Local bookkeeping bounded-context model.
 *
 * <p>This package owns the local bookkeeping language used by executor services and storage
 * adapters. Public contract DTOs must be translated at the host boundary rather than imported as
 * this package's working model.
 */
@org.jspecify.annotations.NullMarked
package dev.erst.fingrind.executor.bookkeeping;
