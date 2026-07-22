package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.EffectiveDateHorizonPolicy;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingAccountStatePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.posting.BookkeepingPostingService;
import dev.erst.fingrind.executor.bookkeeping.posting.PostingPreflightOutcome;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Application service that owns preflight and commit behavior for posting entries. */
public final class PostingApplicationService {
  private final PostingValidationStore validationStore;
  private final BookkeepingPostingService bookkeepingPostingService;
  private final PostEntrySemanticsPolicy entryAcceptancePolicy;
  private final PostingAccountStatePolicy postingAccountStatePolicy;
  private final java.time.Clock clock;

  /** Creates the posting application service with its application-owned seams. */
  public PostingApplicationService(
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator,
      java.time.Clock clock) {
    this.validationStore = Objects.requireNonNull(validationStore, "validationStore");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.entryAcceptancePolicy = PostEntrySemanticsPolicy.currentKernel();
    this.postingAccountStatePolicy = new PostingAccountStatePolicy();
    this.bookkeepingPostingService =
        new BookkeepingPostingService(
            this.validationStore,
            Objects.requireNonNull(commitStore, "commitStore"),
            Objects.requireNonNull(postingIdGenerator, "postingIdGenerator"),
            this.clock);
  }

  /** Validates a request and reports whether a later commit attempt is admissible. */
  public PreflightEntryResult preflight(PostEntryCommand command) {
    Objects.requireNonNull(command, "command");
    java.util.Optional<PostingRejection> rejection = applicationRejectionFor(command);
    if (rejection.isPresent()) {
      return rejectedPreflight(command, rejection.orElseThrow());
    }
    PostingCommand postingCommand = localPostingCommand(command);
    return switch (bookkeepingPostingService.preflight(postingCommand)) {
      case PostingPreflightOutcome.Accepted accepted ->
          new PostEntryResult.PreflightAccepted(
              accepted.idempotencyKey(),
              accepted.effectiveDate(),
              resolvedJournal(
                  java.util.Objects.requireNonNullElse(
                      postingCommand.resolvedOriginatingEntry().orElse(null), command.entry()),
                  command.evidence()));
      case PostingPreflightOutcome.Rejected rejected ->
          rejectedPreflight(
              command, BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  /** Commits a request as one durable posting fact or returns a deterministic rejection. */
  public CommitEntryResult commit(
      PostEntryCommand command, AttestationOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(command, "command");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    java.util.Optional<PostingRejection> rejection = applicationRejectionFor(command);
    if (rejection.isPresent()) {
      return rejectedCommit(command, rejection.orElseThrow());
    }
    PostingCommand postingCommand = localPostingCommand(command);
    return switch (bookkeepingPostingService.commit(postingCommand, attestationAuthorizer)) {
      case PostingCommitResult.Committed committed ->
          committedResult(
              committed.postingFact(),
              committed.idempotentReplay(),
              committed.attestationVerification(),
              resolvedJournal(
                  java.util.Objects.requireNonNullElse(
                      postingCommand.resolvedOriginatingEntry().orElse(null), command.entry()),
                  command.evidence()));
      case PostingCommitResult.Rejected rejected ->
          rejectedCommit(
              command, BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  private static PostEntryResult.Committed committedResult(
      CommittedPosting committedPosting,
      boolean idempotentReplay,
      @Nullable AttestationVerification attestationVerification,
      ResolvedJournal resolvedJournal) {
    return new PostEntryResult.Committed(
        committedPosting.postingId(),
        committedPosting.provenance().requestProvenance().idempotencyKey(),
        committedPosting.journalEntry().effectiveDate(),
        committedPosting.provenance().recordedAt(),
        idempotentReplay,
        resolvedJournal,
        attestationVerification == null
            ? null
            : new AttestationCommit(
                attestationVerification.headOrder(),
                java.util.HexFormat.of().formatHex(attestationVerification.operationHead())));
  }

  private static PostEntryResult.PreflightRejected rejectedPreflight(
      PostEntryCommand command, PostingRejection rejection) {
    return new PostEntryResult.PreflightRejected(
        command.requestProvenance().idempotencyKey(), rejection);
  }

  private static PostEntryResult.CommitRejected rejectedCommit(
      PostEntryCommand command, PostingRejection rejection) {
    return new PostEntryResult.CommitRejected(
        command.requestProvenance().idempotencyKey(), rejection);
  }

  private java.util.Optional<PostingRejection> applicationRejectionFor(PostEntryCommand command) {
    BookLifecycleInspection inspection = validationStore.inspectBook();
    if (!(inspection instanceof BookLifecycleInspection.Initialized initialized)) {
      return java.util.Optional.of(
          BookkeepingPublishedLanguageTranslator.toPublished(
              new BookkeepingPostingRejection.BookNotInitialized()));
    }
    java.util.Optional<PostingRejection> effectiveDateRejection =
        effectiveDateRejectionFor(command, initialized.bookStartDate());
    if (effectiveDateRejection.isPresent()) {
      return effectiveDateRejection;
    }
    java.util.Optional<PostingRejection> semanticsRejection =
        entryAcceptancePolicy
            .rejectionFor(command, validationStore)
            .map(BookkeepingPublishedLanguageTranslator::toPublished);
    if (semanticsRejection.isPresent()) {
      return semanticsRejection;
    }
    java.util.Optional<PostingRejection> declaredAccountRejection =
        declaredAccountRejectionFor(command);
    if (declaredAccountRejection.isPresent()) {
      return declaredAccountRejection;
    }
    PostEntryResolutionSupport.ResolutionOutcome resolutionOutcome =
        PostEntryResolutionSupport.resolve(command.entry(), validationStore);
    if (resolutionOutcome.rejection().isPresent()) {
      return resolutionOutcome.rejection().map(BookkeepingPublishedLanguageTranslator::toPublished);
    }
    return java.util.Optional.empty();
  }

  private PostingCommand localPostingCommand(PostEntryCommand command) {
    return PostEntryCommandTranslator.toPostingCommand(command, validationStore);
  }

  private ResolvedJournal resolvedJournal(
      dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry entry,
      dev.erst.fingrind.core.AccountingEvidence evidence) {
    PostEntrySemanticContext semanticContext =
        PostEntrySemanticContext.from(entry, ProtocolCatalog.domain().requestSurface());
    return ResolvedJournalSupport.resolve(
        entry, evidence, validationStore.findAccounts(semanticContext.referencedAccounts()));
  }

  private java.util.Optional<PostingRejection> effectiveDateRejectionFor(
      PostEntryCommand command, java.time.LocalDate bookStartEffectiveDate) {
    if (command.entry().effectiveDate().isBefore(bookStartEffectiveDate)) {
      return java.util.Optional.of(
          BookkeepingPublishedLanguageTranslator.toPublished(
              new BookkeepingPostingEffectiveDateBeforeBookStart(
                  command.entry().effectiveDate(), bookStartEffectiveDate)));
    }
    try {
      EffectiveDateHorizonPolicy.requireNotAfterToday(command.entry().effectiveDate(), clock);
      return java.util.Optional.empty();
    } catch (EffectiveDateHorizonPolicy.FutureEffectiveDateException exception) {
      return java.util.Optional.of(
          BookkeepingPublishedLanguageTranslator.toPublished(
              new BookkeepingPostingRejection.PostingEffectiveDateInFuture(
                  exception.attemptedEffectiveDate(), exception.currentUtcDate())));
    }
  }

  private java.util.Optional<PostingRejection> declaredAccountRejectionFor(
      PostEntryCommand command) {
    java.util.Set<dev.erst.fingrind.core.AccountCode> referencedAccounts =
        PostEntrySemanticContext.from(command.entry(), ProtocolCatalog.domain().requestSurface())
            .referencedAccounts();
    if (referencedAccounts.isEmpty()) {
      return java.util.Optional.empty();
    }
    dev.erst.fingrind.core.CurrencyUnit currencyUnit =
        validationStore.requireInitializedBookIdentity().functionalCurrency();
    java.util.List<dev.erst.fingrind.core.AccountCode> orderedAccounts =
        new java.util.ArrayList<>(referencedAccounts);
    dev.erst.fingrind.core.AccountCode debitAccount = orderedAccounts.getFirst();
    dev.erst.fingrind.core.AccountCode creditAccount =
        orderedAccounts.size() > 1 ? orderedAccounts.get(1) : orderedAccounts.getFirst();
    PostingCommand validationProbe =
        new PostingCommand(
            command.entry().postingKind(),
            command.entry().postingOriginKind(),
            new JournalEntry(
                command.entry().effectiveDate(),
                java.util.List.of(
                    new JournalLine(
                        debitAccount,
                        JournalLine.EntrySide.DEBIT,
                        Money.ofMinorUnits(currencyUnit, 1L)),
                    new JournalLine(
                        creditAccount,
                        JournalLine.EntrySide.CREDIT,
                        Money.ofMinorUnits(currencyUnit, 1L)))),
            BookkeepingPublishedLanguageTranslator.fromPublished(command.entry().postingLineage()),
            command.evidence(),
            command.requestProvenance(),
            command.sourceChannel(),
            command.entry(),
            null);
    return postingAccountStatePolicy
        .declaredAccountRejectionFor(validationProbe, validationStore)
        .map(BookkeepingPublishedLanguageTranslator::toPublished);
  }
}
