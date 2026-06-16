package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
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

/** Shared posting delegation defaults for SQLite capability wrappers. */
interface SqlitePostingCapabilityView extends SqlitePostingSession, SqliteReadCapabilityView {
  /** Returns the mutation operations owner for the underlying SQLite store. */
  SqliteStoreMutationOperations storeMutationOperations();

  @Override
  default BookOpeningOutcome openBook(
      Instant initializedAt, BookIdentity bookIdentity, List<AccountDeclaration> seededAccounts) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().openBook(initializedAt, bookIdentity, seededAccounts);
  }

  @Override
  default AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .declareAccount(
            accountCode, accountName, accountType, accountRole, accountTaxonomy, declaredAt);
  }

  @Override
  default List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().postings(effectiveDateRange);
  }

  @Override
  default Optional<LocalDate> earliestPostingEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().earliestPostingEffectiveDate();
  }

  @Override
  default Optional<LocalDate> transferredThroughEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().transferredThroughEffectiveDate();
  }

  @Override
  default PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().commit(postingDraft, postingIdGenerator);
  }
}
