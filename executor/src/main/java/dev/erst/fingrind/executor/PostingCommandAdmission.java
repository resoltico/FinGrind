package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.EffectiveDateHorizonPolicy;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.PostingAccountStatePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared application admission for direct and aggregate-plan post-entry commands. */
final class PostingCommandAdmission {
  private final PostingValidationStore validationStore;
  private final PostEntrySemanticsPolicy entryAcceptancePolicy;
  private final PostingAccountStatePolicy postingAccountStatePolicy;
  private final Clock clock;

  PostingCommandAdmission(PostingValidationStore validationStore, Clock clock) {
    this.validationStore = Objects.requireNonNull(validationStore, "validationStore");
    this.clock = Objects.requireNonNull(clock, "clock");
    entryAcceptancePolicy = PostEntrySemanticsPolicy.currentKernel();
    postingAccountStatePolicy = new PostingAccountStatePolicy();
  }

  Optional<PostingRejection> rejectionFor(PostEntryCommand command) {
    Objects.requireNonNull(command, "command");
    dev.erst.fingrind.executor.spi.BookLifecycleInspection inspection =
        validationStore.inspectBook();
    if (!(inspection
        instanceof
        dev.erst.fingrind.executor.spi.BookLifecycleInspection.Initialized initialized)) {
      inspection.allowsInitializedWorkflow();
      return Optional.of(
          BookkeepingPublishedLanguageTranslator.toPublished(
              new BookkeepingPostingRejection.BookNotInitialized()));
    }
    Optional<PostingRejection> effectiveDateRejection =
        effectiveDateRejectionFor(command, initialized.bookStartDate());
    if (effectiveDateRejection.isPresent()) {
      return effectiveDateRejection;
    }
    Optional<PostingRejection> semanticsRejection =
        entryAcceptancePolicy
            .rejectionFor(command, validationStore)
            .map(BookkeepingPublishedLanguageTranslator::toPublished);
    if (semanticsRejection.isPresent()) {
      return semanticsRejection;
    }
    Optional<PostingRejection> declaredAccountRejection = declaredAccountRejectionFor(command);
    if (declaredAccountRejection.isPresent()) {
      return declaredAccountRejection;
    }
    PostEntryResolutionSupport.ResolutionOutcome resolutionOutcome =
        PostEntryResolutionSupport.resolve(command.entry(), validationStore);
    if (resolutionOutcome.rejection().isPresent()) {
      return resolutionOutcome.rejection().map(BookkeepingPublishedLanguageTranslator::toPublished);
    }
    return Optional.empty();
  }

  PostingCommand localPostingCommand(PostEntryCommand command) {
    return PostEntryCommandTranslator.toPostingCommand(
        Objects.requireNonNull(command, "command"), validationStore);
  }

  ResolvedJournal resolvedJournal(PostEntryCommand command, PostingCommand postingCommand) {
    PostEntryCommand checkedCommand = Objects.requireNonNull(command, "command");
    PostingCommand checkedPostingCommand = Objects.requireNonNull(postingCommand, "postingCommand");
    dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry resolvedEntry =
        Objects.requireNonNullElse(
            checkedPostingCommand.resolvedOriginatingEntry().orElse(null), checkedCommand.entry());
    PostEntrySemanticContext semanticContext =
        PostEntrySemanticContext.from(resolvedEntry, ProtocolCatalog.domain().requestSurface());
    return ResolvedJournalSupport.resolve(
        resolvedEntry,
        checkedCommand.evidence(),
        validationStore.findAccounts(semanticContext.referencedAccounts()));
  }

  private Optional<PostingRejection> effectiveDateRejectionFor(
      PostEntryCommand command, java.time.LocalDate bookStartEffectiveDate) {
    if (command.entry().effectiveDate().isBefore(bookStartEffectiveDate)) {
      return Optional.of(
          BookkeepingPublishedLanguageTranslator.toPublished(
              new BookkeepingPostingEffectiveDateBeforeBookStart(
                  command.entry().effectiveDate(), bookStartEffectiveDate)));
    }
    try {
      EffectiveDateHorizonPolicy.requireNotAfterToday(command.entry().effectiveDate(), clock);
      return Optional.empty();
    } catch (EffectiveDateHorizonPolicy.FutureEffectiveDateException exception) {
      return Optional.of(
          BookkeepingPublishedLanguageTranslator.toPublished(
              new BookkeepingPostingRejection.PostingEffectiveDateInFuture(
                  exception.attemptedEffectiveDate(), exception.currentUtcDate())));
    }
  }

  private Optional<PostingRejection> declaredAccountRejectionFor(PostEntryCommand command) {
    java.util.Set<dev.erst.fingrind.core.AccountCode> referencedAccounts =
        PostEntrySemanticContext.from(command.entry(), ProtocolCatalog.domain().requestSurface())
            .referencedAccounts();
    if (referencedAccounts.isEmpty()) {
      return Optional.empty();
    }
    dev.erst.fingrind.core.CurrencyUnit currencyUnit =
        validationStore.requireInitializedBookIdentity().functionalCurrency();
    List<dev.erst.fingrind.core.AccountCode> orderedAccounts = new ArrayList<>(referencedAccounts);
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
