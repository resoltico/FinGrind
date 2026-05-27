package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Period-close wrapper over the shared SQLite store core. */
final class SqlitePeriodResultTransferCapabilitySession extends SqliteDelegatingSession
    implements SqlitePeriodResultTransferSession {
  SqlitePeriodResultTransferCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }

  @Override
  public BookLifecycleInspection inspectBook() {
    return store.inspectBook();
  }

  @Override
  public List<RegisteredAccount> allAccounts() {
    return store.allAccounts();
  }

  @Override
  public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    return store.listAccounts(query);
  }

  @Override
  public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    return store.postings(effectiveDateRange);
  }

  @Override
  public Optional<LocalDate> earliestPostingEffectiveDate() {
    return store.earliestPostingEffectiveDate();
  }

  @Override
  public Optional<LocalDate> transferredThroughEffectiveDate() {
    return store.transferredThroughEffectiveDate();
  }

  @Override
  public PeriodResultTransferOutcome transferPeriodResult(
      PeriodResultTransferDraft periodResultTransferDraft, PostingIdGenerator postingIdGenerator) {
    return store.transferPeriodResult(periodResultTransferDraft, postingIdGenerator);
  }

  @Override
  public void close() {
    closeStore();
  }
}
