package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferPlanner;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Mutation surface over one SQLite posting-fact store. */
interface SqlitePostingFactStoreMutationView {
  /** Returns the thread-ownership guard for this store. */
  SqliteThreadOwner storeThreadOwner();

  /** Returns the mutation operations owner for this store. */
  SqliteStoreMutationOperations storeMutationOperations();

  /** Returns the authoritative protected-book path for this store. */
  Path storeBookPath();

  /** Initializes a previously unopened protected book. */
  default BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().openBook(initializedAt, bookIdentity);
  }

  /** Declares a new account in the protected book. */
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

  /** Commits one posting draft into the protected book. */
  default PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().commit(postingDraft, postingIdGenerator);
  }

  /** Commits a generated period-result transfer into the protected book. */
  default PeriodResultTransferOutcome transferPeriodResult(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      PeriodResultTransferPlanner planner,
      LocalDate currentUtcDate,
      Instant transferredAt,
      PostingIdGenerator postingIdGenerator) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .transferPeriodResult(
            reportingPeriod,
            bookIdentity,
            planner,
            currentUtcDate,
            transferredAt,
            postingIdGenerator);
  }

  /** Commits a preplanned period-result transfer into the protected book. */
  default PeriodResultTransferOutcome transferPeriodResult(
      PeriodResultTransferDraft periodResultTransferDraft, PostingIdGenerator postingIdGenerator) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .transferPeriodResult(periodResultTransferDraft, postingIdGenerator);
  }

  /** Rekeys the protected book with a replacement passphrase. */
  default RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase, Instant rekeyedAt) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().rekeyBook(replacementPassphrase, rekeyedAt);
  }

  /** Resolves a replacement passphrase from logical access metadata before rekeying. */
  default ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess.PassphraseSource replacementPassphraseSource,
      SqlitePassphraseResolver passphraseResolver,
      Instant rekeyedAt) {
    storeThreadOwner().requireOwnerThread();
    Objects.requireNonNull(replacementPassphraseSource, "replacementPassphraseSource");
    Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    Objects.requireNonNull(rekeyedAt, "rekeyedAt");
    return passphraseResolver
        .resolve(storeBookPath(), replacementPassphraseSource, SqlitePassphraseIntent.NEW_SECRET)
        .fold(
            replacementPassphrase ->
                ContractDecision.accepted(rekeyBook(replacementPassphrase, rekeyedAt)),
            ContractDecision::rejected);
  }
}
