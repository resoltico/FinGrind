package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.PostingLookupStore;
import dev.erst.fingrind.executor.spi.PostingRangeStore;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import dev.erst.fingrind.executor.spi.TaxRegistrationLookupStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Posting, close-horizon, and tax-registration defaults for transaction-scoped validation. */
interface SqliteTransactionValidationPostingCapabilityView
    extends TaxRegistrationLookupStore, PostingLookupStore, PostingRangeStore {
  @Override
  default Optional<DeclaredTaxRegistration> findTaxRegistration(
      TaxRegistrationId taxRegistrationId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .findTaxRegistration(taxRegistrationId);
  }

  @Override
  default Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .findExistingPosting(idempotencyKey);
  }

  @Override
  default Optional<CommittedPosting> findPosting(PostingId postingId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .findPosting(postingId);
  }

  @Override
  default Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .findReversalFor(priorPostingId);
  }

  @Override
  default List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .postings(effectiveDateRange);
  }

  @Override
  default Optional<LocalDate> earliestPostingEffectiveDate() {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .earliestPostingEffectiveDate();
  }

  @Override
  default Optional<LocalDate> transferredThroughEffectiveDate() {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .transferredThroughEffectiveDate();
  }
}
