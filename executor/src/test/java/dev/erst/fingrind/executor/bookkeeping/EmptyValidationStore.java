package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Empty validation-store baseline for direct internal policy helper tests. */
abstract class EmptyValidationStore implements PostingValidationStore {
  @Override
  public BookLifecycleInspection inspectBook() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return Optional.empty();
  }

  @Override
  public Map<AccountCode, RegisteredAccount> findAccounts(java.util.Set<AccountCode> accountCodes) {
    return Map.of();
  }

  @Override
  public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    return Optional.empty();
  }

  @Override
  public Optional<CommittedPosting> findPosting(PostingId postingId) {
    return Optional.empty();
  }

  @Override
  public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    return Optional.empty();
  }

  @Override
  public List<RegisteredAccount> allAccounts() {
    return List.of();
  }

  @Override
  public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    return List.of();
  }

  @Override
  public Optional<LocalDate> earliestPostingEffectiveDate() {
    return Optional.empty();
  }

  @Override
  public Optional<LocalDate> closedThroughEffectiveDate() {
    return Optional.empty();
  }
}
