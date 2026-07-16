package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.Objects;

/** Projects Account Registry lifecycle outcomes without coupling them to other administration. */
final class CliAccountRegistryMutationResponseWriter {
  private final CliOutputChannel outputChannel;

  CliAccountRegistryMutationResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    switch (result) {
      case DeclareAccountResult.Declared declared ->
          writeAccountSuccess(
              OperationId.DECLARE_ACCOUNT, "declared", declared.account(), outputMode);
      case DeclareAccountResult.Reactivated reactivated ->
          writeAccountSuccess(
              OperationId.DECLARE_ACCOUNT, "reactivated", reactivated.account(), outputMode);
      case DeclareAccountResult.Renamed renamed ->
          writeAccountSuccess(
              OperationId.DECLARE_ACCOUNT, "renamed", renamed.account(), outputMode);
      case DeclareAccountResult.Unchanged unchanged ->
          writeAccountSuccess(
              OperationId.DECLARE_ACCOUNT, "unchanged", unchanged.account(), outputMode);
      case DeclareAccountResult.Rejected rejected ->
          writeRejected(OperationId.DECLARE_ACCOUNT, rejected.rejection(), outputMode);
    }
  }

  void writeAmendAccountResult(AmendAccountResult result, OutputMode outputMode) {
    switch (result) {
      case AmendAccountResult.Amended amended ->
          writeAccountSuccess(OperationId.AMEND_ACCOUNT, "amended", amended.account(), outputMode);
      case AmendAccountResult.Unchanged unchanged ->
          writeAccountSuccess(
              OperationId.AMEND_ACCOUNT, "unchanged", unchanged.account(), outputMode);
      case AmendAccountResult.Rejected rejected ->
          writeRejected(OperationId.AMEND_ACCOUNT, rejected.rejection(), outputMode);
    }
  }

  void writeRetireAccountResult(RetireAccountResult result, OutputMode outputMode) {
    switch (result) {
      case RetireAccountResult.Retired retired ->
          writeAccountSuccess(OperationId.RETIRE_ACCOUNT, "retired", retired.account(), outputMode);
      case RetireAccountResult.Unchanged unchanged ->
          writeAccountSuccess(
              OperationId.RETIRE_ACCOUNT, "unchanged", unchanged.account(), outputMode);
      case RetireAccountResult.Rejected rejected ->
          writeRejected(OperationId.RETIRE_ACCOUNT, rejected.rejection(), outputMode);
    }
  }

  private void writeAccountSuccess(
      OperationId operationId, String outcome, DeclaredAccount account, OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    CliAdministrativeMutationPayloadSupport.declareAccountPayload(
                        outcome, account))),
        () ->
            outputChannel.writeText(
                CliMutationOutputRenderer.renderAccountDeclarationText(outcome, account)),
        () -> {
          throw new IllegalArgumentException(CliOperationText.unsupportedCsvOutput(operationId));
        });
  }

  private void writeRejected(
      OperationId operationId,
      dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection rejection,
      OutputMode outputMode) {
    outputChannel.writeRejectedEnvelope(
        CliRejectionPayloadMapper.administrationRejectedEnvelope(operationId, rejection),
        outputMode);
  }
}
