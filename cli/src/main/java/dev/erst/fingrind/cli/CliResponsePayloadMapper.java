package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.cli.json.CliReportJsonModels;
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
import dev.erst.fingrind.contract.protocol.ProtocolFailureStatus;
import dev.erst.fingrind.contract.protocol.ProtocolRejectionStatus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessStatus;
import java.nio.file.Path;

/** Facade that routes CLI payload mapping to concern-specific JSON mappers. */
final class CliResponsePayloadMapper {
  private CliResponsePayloadMapper() {}

  static CliEnvelopeJsonModels.SuccessEnvelope<ProtocolSuccessPayload> successEnvelope(
      ProtocolSuccessPayload payload) {
    return new CliEnvelopeJsonModels.SuccessEnvelope(ProtocolSuccessStatus.OK, payload);
  }

  static CliEnvelopeJsonModels.FailureEnvelope failureEnvelope(CliFailure failure) {
    CliErrorJsonModels.@org.jspecify.annotations.Nullable ErrorDetails details = failure.details();
    return new CliEnvelopeJsonModels.FailureEnvelope(
        ProtocolFailureStatus.ERROR,
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        details);
  }

  static CliEnvelopeJsonModels.PreflightAcceptedEnvelope preflightEnvelope(
      PostEntryResult.PreflightAccepted accepted) {
    return new CliEnvelopeJsonModels.PreflightAcceptedEnvelope(
        ProtocolSuccessStatus.PREFLIGHT_ACCEPTED,
        accepted.idempotencyKey().value(),
        accepted.effectiveDate().toString());
  }

  static CliEnvelopeJsonModels.CommittedEnvelope committedEnvelope(
      PostEntryResult.Committed committed) {
    return new CliEnvelopeJsonModels.CommittedEnvelope(
        ProtocolSuccessStatus.COMMITTED,
        committed.postingId().value(),
        committed.idempotencyKey().value(),
        committed.effectiveDate().toString(),
        committed.recordedAt().toString());
  }

  static CliEnvelopeJsonModels.RejectedEnvelope postingRejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return CliRejectionPayloadMapper.postingRejectedEnvelope(requestIdempotencyKey, rejection);
  }

  static CliEnvelopeJsonModels.RejectedEnvelope administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    return CliRejectionPayloadMapper.administrationRejectedEnvelope(rejection);
  }

  static CliEnvelopeJsonModels.RejectedEnvelope queryRejectedEnvelope(
      BookQueryRejection rejection) {
    return CliRejectionPayloadMapper.queryRejectedEnvelope(rejection);
  }

  static ProtocolSuccessPayload bookInspectionPayload(
      Path bookFilePath, BookInspection inspection) {
    return CliBookPayloadMapper.bookInspectionPayload(bookFilePath, inspection);
  }

  static CliBookQueryJsonModels.DeclaredAccountPayload accountPayload(DeclaredAccount account) {
    return CliBookPayloadMapper.accountPayload(account);
  }

  static CliBookQueryJsonModels.PostingPayload postingPayload(PostingFact postingFact) {
    return CliBookPayloadMapper.postingPayload(postingFact);
  }

  static CliBookQueryJsonModels.PostingListPayload postingPagePayload(PostingPage page) {
    return CliBookPayloadMapper.postingPagePayload(page);
  }

  static CliBookQueryJsonModels.AccountListPayload accountPagePayload(AccountPage page) {
    return CliBookPayloadMapper.accountPagePayload(page);
  }

  static CliBookQueryJsonModels.AccountBalancePayload accountBalancePayload(
      AccountBalanceSnapshot snapshot) {
    return CliBookPayloadMapper.accountBalancePayload(snapshot);
  }

  static CliReportJsonModels.TrialBalancePayload trialBalancePayload(TrialBalanceReport report) {
    return CliReportPayloadMapper.trialBalancePayload(report);
  }

  static CliReportJsonModels.AccountLedgerPayload accountLedgerPayload(AccountLedgerReport report) {
    return CliReportPayloadMapper.accountLedgerPayload(report);
  }

  static CliReportJsonModels.PeriodSummaryPayload periodSummaryPayload(PeriodSummaryReport report) {
    return CliReportPayloadMapper.periodSummaryPayload(report);
  }

  static CliPlanJsonModels.LedgerPlanPayload ledgerPlanPayload(LedgerPlanResult result) {
    return CliPlanPayloadMapper.ledgerPlanPayload(result);
  }

  static CliEnvelopeJsonModels.RejectedEnvelope rejectedPlanEnvelope(
      LedgerPlanResult result, ProtocolRejectionStatus status) {
    return CliPlanPayloadMapper.rejectedPlanEnvelope(result, status);
  }

  static ProtocolRejectionStatus planRejectionStatus(LedgerPlanStatus status) {
    return CliPlanPayloadMapper.planRejectionStatus(status);
  }
}
