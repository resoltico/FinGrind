package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.*;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.ProtocolStatuses;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps application results into the package-private JSON model rendered by the CLI. */
final class CliResponsePayloadMapper {
  private CliResponsePayloadMapper() {}

  static CliResponseJsonModels.SuccessEnvelope successEnvelope(Object payload) {
    return new CliResponseJsonModels.SuccessEnvelope(ProtocolStatuses.OK, payload);
  }

  static CliResponseJsonModels.FailureEnvelope failureEnvelope(CliFailure failure) {
    return new CliResponseJsonModels.FailureEnvelope(
        ProtocolStatuses.ERROR,
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument());
  }

  static CliResponseJsonModels.PreflightAcceptedEnvelope preflightEnvelope(
      PostEntryResult.PreflightAccepted accepted) {
    return new CliResponseJsonModels.PreflightAcceptedEnvelope(
        ProtocolStatuses.PREFLIGHT_ACCEPTED,
        accepted.idempotencyKey().value(),
        accepted.effectiveDate().toString());
  }

  static CliResponseJsonModels.CommittedEnvelope committedEnvelope(
      PostEntryResult.Committed committed) {
    return new CliResponseJsonModels.CommittedEnvelope(
        ProtocolStatuses.COMMITTED,
        committed.postingId().value(),
        committed.idempotencyKey().value(),
        committed.effectiveDate().toString(),
        committed.recordedAt().toString());
  }

  static CliResponseJsonModels.RejectedEnvelope postingRejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return new CliResponseJsonModels.RejectedEnvelope(
        ProtocolStatuses.REJECTED,
        PostingRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        requestIdempotencyKey,
        postingRejectionDetails(rejection));
  }

  static CliResponseJsonModels.RejectedEnvelope administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    return new CliResponseJsonModels.RejectedEnvelope(
        ProtocolStatuses.REJECTED,
        BookAdministrationRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        null,
        administrationRejectionDetails(rejection));
  }

  static CliResponseJsonModels.RejectedEnvelope queryRejectedEnvelope(
      BookQueryRejection rejection) {
    return new CliResponseJsonModels.RejectedEnvelope(
        ProtocolStatuses.REJECTED,
        BookQueryRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        null,
        queryRejectionDetails(rejection));
  }

  static Object bookInspectionPayload(Path bookFilePath, BookInspection inspection) {
    return switch (inspection) {
      case BookInspection.Missing missing ->
          new CliResponseJsonModels.MissingBookInspectionPayload(
              absolutePath(bookFilePath),
              missing.status().wireValue(),
              missing.initialized(),
              missing.compatibleWithCurrentBinary(),
              missing.canInitializeWithOpenBook(),
              missing.supportedBookFormatVersion(),
              missing.migrationPolicy().wireValue());
      case BookInspection.Existing existing ->
          new CliResponseJsonModels.ExistingBookInspectionPayload(
              absolutePath(bookFilePath),
              existing.status().wireValue(),
              existing.initialized(),
              existing.compatibleWithCurrentBinary(),
              existing.canInitializeWithOpenBook(),
              existing.applicationId(),
              existing.detectedBookFormatVersion(),
              existing.supportedBookFormatVersion(),
              existing.migrationPolicy().wireValue());
      case BookInspection.Initialized initialized ->
          new CliResponseJsonModels.InitializedBookInspectionPayload(
              absolutePath(bookFilePath),
              initialized.status().wireValue(),
              initialized.initialized(),
              initialized.compatibleWithCurrentBinary(),
              initialized.canInitializeWithOpenBook(),
              initialized.applicationId(),
              initialized.detectedBookFormatVersion(),
              initialized.supportedBookFormatVersion(),
              initialized.migrationPolicy().wireValue(),
              initialized.initializedAt().toString());
    };
  }

  static CliResponseJsonModels.DeclaredAccountPayload accountPayload(DeclaredAccount account) {
    return new CliResponseJsonModels.DeclaredAccountPayload(
        account.accountCode().value(),
        account.accountName().value(),
        account.normalBalance().wireValue(),
        account.active(),
        account.declaredAt().toString());
  }

  static CliResponseJsonModels.PostingPayload postingPayload(PostingFact postingFact) {
    return new CliResponseJsonModels.PostingPayload(
        postingFact.postingId().value(),
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.provenance().requestProvenance().actorId().value(),
        postingFact.provenance().requestProvenance().actorType().wireValue(),
        postingFact.provenance().requestProvenance().commandId().value(),
        postingFact.provenance().requestProvenance().idempotencyKey().value(),
        postingFact.provenance().requestProvenance().causationId().value(),
        postingFact
            .provenance()
            .requestProvenance()
            .correlationId()
            .map(value -> value.value())
            .orElse(null),
        postingFact.provenance().sourceChannel().wireValue(),
        postingFact
            .postingLineage()
            .reversalReference()
            .map(
                reference ->
                    new CliResponseJsonModels.ReversalPayload(
                        reference.priorPostingId().value(),
                        postingFact.postingLineage().reversalReason().orElseThrow().value()))
            .orElse(null),
        postingFact.journalEntry().lines().stream()
            .map(CliResponsePayloadMapper::linePayload)
            .toList());
  }

  static CliResponseJsonModels.PostingListPayload postingPagePayload(PostingPage page) {
    return new CliResponseJsonModels.PostingListPayload(
        page.limit(),
        page.offset(),
        page.hasMore(),
        page.postings().stream().map(CliResponsePayloadMapper::postingPayload).toList());
  }

  static CliResponseJsonModels.AccountListPayload accountPagePayload(AccountPage page) {
    return new CliResponseJsonModels.AccountListPayload(
        page.limit(),
        page.offset(),
        page.hasMore(),
        page.accounts().stream().map(CliResponsePayloadMapper::accountPayload).toList());
  }

  static CliResponseJsonModels.AccountBalancePayload accountBalancePayload(
      AccountBalanceSnapshot snapshot) {
    return new CliResponseJsonModels.AccountBalancePayload(
        snapshot.account().accountCode().value(),
        snapshot.account().accountName().value(),
        snapshot.account().normalBalance().wireValue(),
        snapshot.account().active(),
        snapshot.account().declaredAt().toString(),
        snapshot.effectiveDateFrom().map(Object::toString).orElse(null),
        snapshot.effectiveDateTo().map(Object::toString).orElse(null),
        snapshot.balances().stream().map(CliResponsePayloadMapper::balancePayload).toList());
  }

  static CliResponseJsonModels.LedgerPlanPayload ledgerPlanPayload(LedgerPlanResult result) {
    return new CliResponseJsonModels.LedgerPlanPayload(
        result.planId(),
        result.status().wireValue(),
        ledgerExecutionJournalPayload(result.journal()));
  }

  static CliResponseJsonModels.RejectedEnvelope rejectedPlanEnvelope(
      LedgerPlanResult result, String status) {
    CliResponseJsonModels.LedgerPlanPayload payload = ledgerPlanPayload(result);
    LedgerStepFailure failure = result.journal().requiredFailedStep().requiredFailure();
    return new CliResponseJsonModels.RejectedEnvelope(
        status,
        failure.code(),
        failure.message(),
        null,
        new CliResponseJsonModels.PlanRejectionDetails(payload));
  }

  static String planRejectionStatus(LedgerPlanStatus status) {
    return switch (status) {
      case SUCCEEDED ->
          throw new IllegalArgumentException("Succeeded plans do not have a rejection status.");
      case REJECTED -> ProtocolStatuses.PLAN_REJECTED;
      case ASSERTION_FAILED -> ProtocolStatuses.PLAN_ASSERTION_FAILED;
    };
  }

  private static @Nullable Object postingRejectionDetails(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ -> null;
      case PostingRejection.AccountStateViolations violations ->
          new CliResponseJsonModels.AccountStateViolationsDetails(
              violations.violations().stream()
                  .map(CliResponsePayloadMapper::accountStateViolationPayload)
                  .toList());
      case PostingRejection.DuplicateIdempotencyKey _ -> null;
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          new CliResponseJsonModels.PriorPostingDetails(
              reversalTargetNotFound.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          new CliResponseJsonModels.PriorPostingDetails(
              reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          new CliResponseJsonModels.PriorPostingDetails(
              reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }

  private static CliResponseJsonModels.AccountStateViolationPayload accountStateViolationPayload(
      PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount unknownAccount ->
          new CliResponseJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(unknownAccount), unknownAccount.accountCode().value());
      case PostingRejection.InactiveAccount inactiveAccount ->
          new CliResponseJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(inactiveAccount), inactiveAccount.accountCode().value());
    };
  }

  private static @Nullable Object administrationRejectionDetails(
      BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.BookAlreadyInitialized _ -> null;
      case BookAdministrationRejection.BookNotInitialized _ -> null;
      case BookAdministrationRejection.BookContainsSchema _ -> null;
      case BookAdministrationRejection.NormalBalanceConflict conflict ->
          new CliResponseJsonModels.NormalBalanceConflictDetails(
              conflict.accountCode().value(),
              conflict.existingNormalBalance().wireValue(),
              conflict.requestedNormalBalance().wireValue());
    };
  }

  private static @Nullable Object queryRejectionDetails(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ -> null;
      case BookQueryRejection.UnknownAccount unknownAccount ->
          new CliResponseJsonModels.UnknownAccountDetails(unknownAccount.accountCode().value());
      case BookQueryRejection.PostingNotFound postingNotFound ->
          new CliResponseJsonModels.PostingNotFoundDetails(postingNotFound.postingId().value());
    };
  }

  private static CliResponseJsonModels.JournalLinePayload linePayload(
      dev.erst.fingrind.core.JournalLine line) {
    return new CliResponseJsonModels.JournalLinePayload(
        line.accountCode().value(),
        line.side().wireValue(),
        line.amount().currencyCode().value(),
        line.amount().amount().toPlainString());
  }

  private static CliResponseJsonModels.BalanceBucketPayload balancePayload(
      CurrencyBalance balance) {
    return new CliResponseJsonModels.BalanceBucketPayload(
        balance.debitTotal().currencyCode().value(),
        balance.debitTotal().amount().toPlainString(),
        balance.creditTotal().amount().toPlainString(),
        balance.netAmount().amount().toPlainString(),
        balance.balanceSide().wireValue());
  }

  private static CliResponseJsonModels.LedgerExecutionJournalPayload ledgerExecutionJournalPayload(
      LedgerExecutionJournal journal) {
    return new CliResponseJsonModels.LedgerExecutionJournalPayload(
        journal.planId(),
        journal.status().wireValue(),
        journal.startedAt().toString(),
        journal.finishedAt().toString(),
        journal.steps().stream().map(CliResponsePayloadMapper::ledgerJournalEntryPayload).toList());
  }

  private static CliResponseJsonModels.LedgerJournalEntryPayload ledgerJournalEntryPayload(
      LedgerJournalEntry entry) {
    return new CliResponseJsonModels.LedgerJournalEntryPayload(
        entry.stepId(),
        entry.kind().wireValue(),
        entry.detailKind().map(LedgerAssertionKind::wireValue).orElse(null),
        entry.status().wireValue(),
        entry.startedAt().toString(),
        entry.finishedAt().toString(),
        factPayloads(entry.facts()),
        entry instanceof LedgerJournalEntry.Failed
            ? ledgerStepFailurePayload(entry.requiredFailure())
            : null);
  }

  private static CliResponseJsonModels.LedgerStepFailurePayload ledgerStepFailurePayload(
      LedgerStepFailure failure) {
    return new CliResponseJsonModels.LedgerStepFailurePayload(
        failure.code(), failure.message(), factPayloads(failure.facts()));
  }

  private static List<CliResponseJsonModels.LedgerFactPayload> factPayloads(
      List<LedgerFact> facts) {
    return facts.stream().map(CliResponsePayloadMapper::ledgerFactPayload).toList();
  }

  private static CliResponseJsonModels.LedgerFactPayload ledgerFactPayload(LedgerFact fact) {
    return switch (fact) {
      case LedgerFact.Text text ->
          new CliResponseJsonModels.TextLedgerFactPayload(text.name(), text.value());
      case LedgerFact.Flag flag ->
          new CliResponseJsonModels.FlagLedgerFactPayload(flag.name(), flag.value());
      case LedgerFact.Count count ->
          new CliResponseJsonModels.CountLedgerFactPayload(count.name(), count.value());
      case LedgerFact.Group group ->
          new CliResponseJsonModels.GroupLedgerFactPayload(
              group.name(), factPayloads(group.facts()));
    };
  }

  private static String absolutePath(Path bookFilePath) {
    return bookFilePath.toAbsolutePath().normalize().toString();
  }
}
