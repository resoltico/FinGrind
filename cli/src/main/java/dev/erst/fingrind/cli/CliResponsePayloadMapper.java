package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.protocol.ProtocolStatuses;
import java.nio.file.Path;

/** Facade that routes CLI payload mapping to concern-specific JSON mappers. */
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
    return CliRejectionPayloadMapper.postingRejectedEnvelope(requestIdempotencyKey, rejection);
  }

  static CliResponseJsonModels.RejectedEnvelope administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    return CliRejectionPayloadMapper.administrationRejectedEnvelope(rejection);
  }

  static CliResponseJsonModels.RejectedEnvelope queryRejectedEnvelope(
      BookQueryRejection rejection) {
    return CliRejectionPayloadMapper.queryRejectedEnvelope(rejection);
  }

  static Object bookInspectionPayload(Path bookFilePath, BookInspection inspection) {
    return CliBookPayloadMapper.bookInspectionPayload(bookFilePath, inspection);
  }

  static CliResponseJsonModels.DeclaredAccountPayload accountPayload(DeclaredAccount account) {
    return CliBookPayloadMapper.accountPayload(account);
  }

  static CliResponseJsonModels.PostingPayload postingPayload(PostingFact postingFact) {
    return CliBookPayloadMapper.postingPayload(postingFact);
  }

  static CliResponseJsonModels.PostingListPayload postingPagePayload(PostingPage page) {
    return CliBookPayloadMapper.postingPagePayload(page);
  }

  static CliResponseJsonModels.AccountListPayload accountPagePayload(AccountPage page) {
    return CliBookPayloadMapper.accountPagePayload(page);
  }

  static CliResponseJsonModels.AccountBalancePayload accountBalancePayload(
      AccountBalanceSnapshot snapshot) {
    return CliBookPayloadMapper.accountBalancePayload(snapshot);
  }

  static CliResponseJsonModels.TrialBalancePayload trialBalancePayload(TrialBalanceReport report) {
    return CliReportPayloadMapper.trialBalancePayload(report);
  }

  static CliResponseJsonModels.AccountLedgerPayload accountLedgerPayload(
      AccountLedgerReport report) {
    return CliReportPayloadMapper.accountLedgerPayload(report);
  }

  static CliResponseJsonModels.PeriodSummaryPayload periodSummaryPayload(
      PeriodSummaryReport report) {
    return CliReportPayloadMapper.periodSummaryPayload(report);
  }

  static CliResponseJsonModels.LedgerPlanPayload ledgerPlanPayload(LedgerPlanResult result) {
    return CliPlanPayloadMapper.ledgerPlanPayload(result);
  }

  static CliResponseJsonModels.RejectedEnvelope rejectedPlanEnvelope(
      LedgerPlanResult result, String status) {
    return CliPlanPayloadMapper.rejectedPlanEnvelope(result, status);
  }

  static String planRejectionStatus(LedgerPlanStatus status) {
    return CliPlanPayloadMapper.planRejectionStatus(status);
  }
}
