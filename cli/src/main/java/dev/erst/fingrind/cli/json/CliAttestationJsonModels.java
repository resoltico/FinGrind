package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.compareCanonicalUnsigned64Decimals;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireCanonicalUnsigned64Decimal;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalCanonicalUnsigned64Decimal;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalSha256Hex;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireSha256Hex;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Success payloads for non-mutating book-attestation verification and receipt operations. */
public interface CliAttestationJsonModels {
  /** One exact immutable attestation-chain head or historical anchor. */
  record AttestationHeadPayload(String operationOrder, String operationHead) {
    public AttestationHeadPayload {
      operationOrder = requireCanonicalUnsigned64Decimal(operationOrder, "operationOrder");
      operationHead = requireSha256Hex(operationHead, "operationHead");
    }
  }

  /** One authenticated operation reference returned by mutation and provenance query results. */
  record AttestationCommitPayload(String operationOrder, String operationHead) {
    public AttestationCommitPayload {
      operationOrder = requireCanonicalUnsigned64Decimal(operationOrder, "operationOrder");
      operationHead = requireSha256Hex(operationHead, "operationHead");
    }
  }

  record VerifyBookPayload(
      String bookId,
      AttestationHeadPayload verifiedAttestationHead,
      String previousHead,
      boolean reviewRequired,
      List<AttestationReviewFindingPayload> reviewFindings,
      AttestationRegistryPayload registry)
      implements CliSuccessPayload {
    public VerifyBookPayload {
      bookId = requireText(bookId, "bookId");
      java.util.Objects.requireNonNull(verifiedAttestationHead, "verifiedAttestationHead");
      previousHead = requireSha256Hex(previousHead, "previousHead");
      reviewFindings =
          requireReviewFindingsWithinVerifiedHead(reviewFindings, verifiedAttestationHead);
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
      keyId = requireSha256Hex(keyId, "keyId");
      credentialSpki = requireText(credentialSpki, "credentialSpki");
      credentialPurpose = requireText(credentialPurpose, "credentialPurpose");
      bindingAction = requireText(bindingAction, "bindingAction");
      acceptedOrder = requireCanonicalUnsigned64Decimal(acceptedOrder, "acceptedOrder");
      predecessorKeyId = requireOptionalSha256Hex(predecessorKeyId, "predecessorKeyId");
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
      acceptedOrder = requireCanonicalUnsigned64Decimal(acceptedOrder, "acceptedOrder");
    }
  }

  record AttestationReviewPayload(
      String bookId,
      AttestationHeadPayload verifiedAttestationHead,
      List<AttestationReviewFindingPayload> findings)
      implements CliSuccessPayload {
    public AttestationReviewPayload {
      bookId = requireText(bookId, "bookId");
      java.util.Objects.requireNonNull(verifiedAttestationHead, "verifiedAttestationHead");
      findings = requireReviewFindingsWithinVerifiedHead(findings, verifiedAttestationHead);
    }
  }

  record AttestationReviewFindingPayload(
      String credentialKeyId,
      String firstAffectedOrder,
      @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String lastAffectedOrder,
      String operationOrder) {
    public AttestationReviewFindingPayload {
      credentialKeyId = requireSha256Hex(credentialKeyId, "credentialKeyId");
      firstAffectedOrder =
          requireCanonicalUnsigned64Decimal(firstAffectedOrder, "firstAffectedOrder");
      lastAffectedOrder =
          requireOptionalCanonicalUnsigned64Decimal(lastAffectedOrder, "lastAffectedOrder");
      operationOrder = requireCanonicalUnsigned64Decimal(operationOrder, "operationOrder");
      if (lastAffectedOrder != null
          && compareCanonicalUnsigned64Decimals(lastAffectedOrder, firstAffectedOrder) < 0) {
        throw new IllegalArgumentException(
            "lastAffectedOrder must not precede firstAffectedOrder.");
      }
      if (compareCanonicalUnsigned64Decimals(operationOrder, firstAffectedOrder) < 0
          || (lastAffectedOrder != null
              && compareCanonicalUnsigned64Decimals(operationOrder, lastAffectedOrder) > 0)) {
        throw new IllegalArgumentException(
            "operationOrder must fall within the inclusive review interval.");
      }
    }
  }

  record ExportReceiptPayload(
      String receiptFile,
      String bookId,
      AttestationHeadPayload receiptAttestationAnchor,
      List<String> warnings)
      implements CliSuccessPayload {
    public ExportReceiptPayload {
      receiptFile = requireText(receiptFile, "receiptFile");
      bookId = requireText(bookId, "bookId");
      java.util.Objects.requireNonNull(receiptAttestationAnchor, "receiptAttestationAnchor");
      warnings = copyList(warnings, "warnings");
    }
  }

  record VerifyReceiptPayload(
      String receiptFile,
      String bookId,
      AttestationHeadPayload receiptAttestationAnchor,
      List<String> findings)
      implements CliSuccessPayload {
    public VerifyReceiptPayload {
      receiptFile = requireText(receiptFile, "receiptFile");
      bookId = requireText(bookId, "bookId");
      java.util.Objects.requireNonNull(receiptAttestationAnchor, "receiptAttestationAnchor");
      findings = copyList(findings, "findings");
    }
  }

  /** Copies review findings only when each finding is internally coherent and within the chain. */
  static List<AttestationReviewFindingPayload> requireReviewFindingsWithinVerifiedHead(
      List<AttestationReviewFindingPayload> findings,
      AttestationHeadPayload verifiedAttestationHead) {
    List<AttestationReviewFindingPayload> checkedFindings = copyList(findings, "reviewFindings");
    AttestationHeadPayload checkedHead =
        java.util.Objects.requireNonNull(verifiedAttestationHead, "verifiedAttestationHead");
    Set<AttestationReviewFindingPayload> seenFindings = new HashSet<>();
    for (AttestationReviewFindingPayload finding : checkedFindings) {
      if (finding.lastAffectedOrder() != null
          && compareCanonicalUnsigned64Decimals(
                  finding.lastAffectedOrder(), checkedHead.operationOrder())
              > 0) {
        throw new IllegalArgumentException(
            "A bounded review interval must not exceed the verified attestation head order.");
      }
      if (compareCanonicalUnsigned64Decimals(finding.operationOrder(), checkedHead.operationOrder())
          > 0) {
        throw new IllegalArgumentException(
            "A review finding must not exceed the verified attestation head order.");
      }
      if (!seenFindings.add(finding)) {
        throw new IllegalArgumentException(
            "A review declaration must not report the same operation more than once.");
      }
    }
    return checkedFindings;
  }
}
