package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.runtime.AccountRegistryDescriptor;
import dev.erst.fingrind.contract.runtime.AuditDescriptor;
import dev.erst.fingrind.contract.runtime.PlanExecutionDescriptor;
import dev.erst.fingrind.contract.runtime.PreflightDescriptor;
import dev.erst.fingrind.contract.runtime.ResponseModelDescriptor;
import dev.erst.fingrind.contract.runtime.ReversalDescriptor;
import java.util.List;

/** Response-contract capability slice JSON records emitted by the CLI transport layer. */
public interface CliDiscoveryResponseContractSliceJsonModels {

  record CapabilitiesResponseContractSlicePayload(
      ResponseModelDescriptor responseModel,
      PlanExecutionDescriptor planExecution,
      AuditDescriptor audit,
      AccountRegistryDescriptor accountRegistry,
      ReversalDescriptor reversals,
      PreflightDescriptor preflight)
      implements ProtocolSuccessPayload {
    public CapabilitiesResponseContractSlicePayload {
      responseModel = requireValue(responseModel, "responseModel");
      planExecution = requireValue(planExecution, "planExecution");
      audit = requireValue(audit, "audit");
      accountRegistry = requireValue(accountRegistry, "accountRegistry");
      reversals = requireValue(reversals, "reversals");
      preflight = requireValue(preflight, "preflight");
    }
  }

  record CapabilitiesResponseContractCompactPayload(
      ResponseModelDescriptor responseModel,
      String preflightSemantics,
      String planJournal,
      String reversalModel,
      int requestProvenanceFieldCount,
      int committedFieldCount)
      implements ProtocolSuccessPayload {
    public CapabilitiesResponseContractCompactPayload {
      responseModel = requireValue(responseModel, "responseModel");
      preflightSemantics = requireText(preflightSemantics, "preflightSemantics");
      planJournal = requireText(planJournal, "planJournal");
      reversalModel = requireText(reversalModel, "reversalModel");
      if (requestProvenanceFieldCount < 0) {
        throw new IllegalArgumentException(
            "requestProvenanceFieldCount must be greater than or equal to zero.");
      }
      if (committedFieldCount < 0) {
        throw new IllegalArgumentException(
            "committedFieldCount must be greater than or equal to zero.");
      }
    }
  }

  record CapabilitiesResponseContractSummaryPayload(
      List<String> envelopeStatusCodes,
      String preflightSemantics,
      String planJournal,
      String reversalModel,
      int requestProvenanceFieldCount,
      int committedFieldCount)
      implements ProtocolSuccessPayload {
    public CapabilitiesResponseContractSummaryPayload {
      envelopeStatusCodes = copyList(envelopeStatusCodes, "envelopeStatusCodes");
      preflightSemantics = requireText(preflightSemantics, "preflightSemantics");
      planJournal = requireText(planJournal, "planJournal");
      reversalModel = requireText(reversalModel, "reversalModel");
      if (requestProvenanceFieldCount < 0) {
        throw new IllegalArgumentException(
            "requestProvenanceFieldCount must be greater than or equal to zero.");
      }
      if (committedFieldCount < 0) {
        throw new IllegalArgumentException(
            "committedFieldCount must be greater than or equal to zero.");
      }
    }
  }
}
