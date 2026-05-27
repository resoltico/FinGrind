package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Posting-capable wrapper over the shared SQLite store core. */
class SqlitePostingCapabilitySession extends SqliteReadCapabilitySession
    implements SqlitePostingSession {
  SqlitePostingCapabilitySession(SqlitePostingFactStore store) {
    super(store);
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
  public BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity) {
    return store.openBook(initializedAt, bookIdentity);
  }

  @Override
  public AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    return store.declareAccount(
        accountCode, accountName, accountType, accountRole, accountTaxonomy, declaredAt);
  }

  @Override
  public PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    return store.commit(postingDraft, postingIdGenerator);
  }

  @Override
  public void close() {
    closeStore();
  }
}
