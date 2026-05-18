package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliMutationJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.protocol.ProtocolFailureStatus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessStatus;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Facade that routes CLI payload mapping to concern-specific JSON mappers. */
final class CliResponsePayloadMapper {
  private CliResponsePayloadMapper() {}

  static CliEnvelopeJsonModels.SuccessEnvelope<ProtocolSuccessPayload> successEnvelope(
      ProtocolSuccessPayload payload) {
    return successEnvelope(payload, null);
  }

  static CliEnvelopeJsonModels.SuccessEnvelope<ProtocolSuccessPayload> successEnvelope(
      ProtocolSuccessPayload payload, @Nullable Path exportedArtifactPath) {
    List<CliEnvelopeJsonModels.SuccessArtifact> artifacts =
        exportedArtifactPath == null
            ? null
            : List.of(
                new CliEnvelopeJsonModels.SuccessArtifact(
                    "pdf", exportedArtifactPath.toAbsolutePath().normalize().toString()));
    return new CliEnvelopeJsonModels.SuccessEnvelope<>(
        ProtocolSuccessStatus.OK, payload, artifacts);
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

  static CliEnvelopeJsonModels.SuccessEnvelope<ProtocolSuccessPayload> preflightEnvelope(
      PostEntryResult.PreflightAccepted accepted) {
    return successEnvelope(
        new CliMutationJsonModels.PreflightAcceptedPayload(
            accepted.idempotencyKey().value(), accepted.effectiveDate().toString()));
  }

  static CliEnvelopeJsonModels.SuccessEnvelope<ProtocolSuccessPayload> committedEnvelope(
      PostEntryResult.Committed committed) {
    return successEnvelope(
        new CliMutationJsonModels.CommittedPostingPayload(
            committed.postingId().value(),
            committed.idempotencyKey().value(),
            committed.effectiveDate().toString(),
            committed.recordedAt().toString()));
  }

  static CliEnvelopeJsonModels.RejectedEnvelope postingRejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return CliRejectionPayloadMapper.postingRejectedEnvelope(requestIdempotencyKey, rejection);
  }

  static CliEnvelopeJsonModels.RejectedEnvelope administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    return CliRejectionPayloadMapper.administrationRejectedEnvelope(rejection);
  }

  static CliEnvelopeJsonModels.RejectedEnvelope maintenanceRejectedEnvelope(
      BookMaintenanceRejection rejection) {
    return CliRejectionPayloadMapper.maintenanceRejectedEnvelope(rejection);
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

  static CliBookQueryJsonModels.PostingDetailsPayload postingDetailsPayload(
      dev.erst.fingrind.core.BookIdentity bookIdentity, PostingFact postingFact) {
    return CliBookPayloadMapper.postingDetailsPayload(bookIdentity, postingFact);
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

  static CliReportJsonModels.FinancialPositionPayload financialPositionPayload(
      FinancialPositionReport report) {
    return CliReportPayloadMapper.financialPositionPayload(report);
  }

  static CliReportJsonModels.IncomeStatementPayload incomeStatementPayload(
      IncomeStatementReport report) {
    return CliReportPayloadMapper.incomeStatementPayload(report);
  }

  static CliReportJsonModels.ChangesInEquityPayload changesInEquityPayload(
      ChangesInEquityReport report) {
    return CliReportPayloadMapper.changesInEquityPayload(report);
  }

  static CliPlanJsonModels.LedgerPlanPayload ledgerPlanPayload(
      LedgerPlanResult result, PlanResultDetail resultDetail) {
    return CliPlanPayloadMapper.ledgerPlanPayload(result, resultDetail);
  }
}
