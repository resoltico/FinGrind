package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Success payloads for non-mutating book-attestation verification and receipt operations. */
public interface CliAttestationJsonModels {
  record VerifyBookPayload(
      String bookId,
      String headOrder,
      String operationHead,
      boolean reviewRequired,
      List<AttestationReviewFindingPayload> reviewFindings,
      AttestationRegistryPayload registry)
      implements CliSuccessPayload {
    public VerifyBookPayload {
      bookId = requireText(bookId, "bookId");
      headOrder = requireText(headOrder, "headOrder");
      operationHead = requireText(operationHead, "operationHead");
      reviewFindings = copyList(reviewFindings, "reviewFindings");
      java.util.Objects.requireNonNull(registry, "registry");
      if (reviewRequired != !reviewFindings.isEmpty()) {
        throw new IllegalArgumentException("reviewRequired must exactly reflect reviewFindings.");
      }
    }
  }

  record AttestationRegistryPayload(
      List<AttestationCredentialPayload> credentials,
      List<AttestationCapabilityPolicyPayload> capabilityPolicies,
      List<AttestationPrincipalCapabilityPayload> principalCapabilities,
      List<AttestationSystemWorkflowPolicyPayload> systemWorkflowPolicies) {
    public AttestationRegistryPayload {
      credentials = copyList(credentials, "credentials");
      capabilityPolicies = copyList(capabilityPolicies, "capabilityPolicies");
      principalCapabilities = copyList(principalCapabilities, "principalCapabilities");
      systemWorkflowPolicies = copyList(systemWorkflowPolicies, "systemWorkflowPolicies");
    }
  }

  record AttestationCredentialPayload(
      String principalId,
      String keyId,
      String credentialSpki,
      String credentialPurpose,
      String bindingAction,
      String acceptedOrder,
      @Nullable String predecessorKeyId,
      String state) {
    public AttestationCredentialPayload {
      principalId = requireText(principalId, "principalId");
      keyId = requireText(keyId, "keyId");
      credentialSpki = requireText(credentialSpki, "credentialSpki");
      credentialPurpose = requireText(credentialPurpose, "credentialPurpose");
      bindingAction = requireText(bindingAction, "bindingAction");
      acceptedOrder = requireText(acceptedOrder, "acceptedOrder");
      predecessorKeyId = requireOptionalText(predecessorKeyId, "predecessorKeyId");
      state = requireText(state, "state");
    }
  }

  record AttestationCapabilityPolicyPayload(
      String capability,
      int quorum,
      int eligiblePrincipalCount,
      int eligibleOperatorPrincipalCount,
      int eligibleSystemPrincipalCount) {
    public AttestationCapabilityPolicyPayload {
      capability = requireText(capability, "capability");
    }
  }

  record AttestationPrincipalCapabilityPayload(
      String principalId, String capability, boolean eligible) {
    public AttestationPrincipalCapabilityPayload {
      principalId = requireText(principalId, "principalId");
      capability = requireText(capability, "capability");
    }
  }

  record AttestationSystemWorkflowPolicyPayload(
      String workflowId,
      String workflowKind,
      String resultHoldingAccountCode,
      @Nullable String capitalAccountCode,
      @Nullable String retainedResultAccountCode,
      boolean active,
      String acceptedOrder) {
    public AttestationSystemWorkflowPolicyPayload {
      workflowId = requireText(workflowId, "workflowId");
      workflowKind = requireText(workflowKind, "workflowKind");
      resultHoldingAccountCode = requireText(resultHoldingAccountCode, "resultHoldingAccountCode");
      capitalAccountCode = requireOptionalText(capitalAccountCode, "capitalAccountCode");
      retainedResultAccountCode =
          requireOptionalText(retainedResultAccountCode, "retainedResultAccountCode");
      acceptedOrder = requireText(acceptedOrder, "acceptedOrder");
    }
  }

  record AttestationReviewPayload(
      String bookId, String headOrder, List<AttestationReviewFindingPayload> findings)
      implements CliSuccessPayload {
    public AttestationReviewPayload {
      bookId = requireText(bookId, "bookId");
      headOrder = requireText(headOrder, "headOrder");
      findings = copyList(findings, "findings");
    }
  }

  record AttestationReviewFindingPayload(
      String credentialKeyId,
      String firstAffectedOrder,
      @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String lastAffectedOrder,
      String operationOrder) {
    public AttestationReviewFindingPayload {
      credentialKeyId = requireText(credentialKeyId, "credentialKeyId");
      firstAffectedOrder = requireText(firstAffectedOrder, "firstAffectedOrder");
      lastAffectedOrder = requireOptionalText(lastAffectedOrder, "lastAffectedOrder");
      operationOrder = requireText(operationOrder, "operationOrder");
    }
  }

  record ExportReceiptPayload(
      String receiptFile,
      String bookId,
      String operationOrder,
      String operationHead,
      List<String> warnings)
      implements CliSuccessPayload {
    public ExportReceiptPayload {
      receiptFile = requireText(receiptFile, "receiptFile");
      bookId = requireText(bookId, "bookId");
      operationOrder = requireText(operationOrder, "operationOrder");
      operationHead = requireText(operationHead, "operationHead");
      warnings = copyList(warnings, "warnings");
    }
  }

  record VerifyReceiptPayload(String bookId, String operationOrder, List<String> findings)
      implements CliSuccessPayload {
    public VerifyReceiptPayload {
      bookId = requireText(bookId, "bookId");
      operationOrder = requireText(operationOrder, "operationOrder");
      findings = copyList(findings, "findings");
    }
  }
}
