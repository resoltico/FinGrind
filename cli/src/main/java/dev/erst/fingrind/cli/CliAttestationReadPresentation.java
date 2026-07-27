package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import dev.erst.fingrind.core.attestation.AttestationReviewFinding;
import java.util.ArrayList;
import java.util.List;

/** Builds transport payloads and text rows for attestation read results. */
final class CliAttestationReadPresentation {
  private CliAttestationReadPresentation() {}

  static List<CliAttestationJsonModels.AttestationReviewFindingPayload> reviewFindingPayloads(
      List<AttestationReviewFinding> findings) {
    return findings.stream()
        .map(
            finding -> {
              var review = finding.compromiseReview();
              return new CliAttestationJsonModels.AttestationReviewFindingPayload(
                  review.credentialKeyId(),
                  review.firstAffectedOrder().toString(),
                  review.lastAffectedOrder() == null ? null : review.lastAffectedOrder().toString(),
                  finding.operationOrder().toString());
            })
        .toList();
  }

  static List<List<String>> receiptExportRows(ExportAttestationReceiptResult.Exported exported) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Receipt file", CliTextDisplay.path(exported.receiptFilePath())));
    rows.add(List.of("Book ID", exported.bookId().toString()));
    CliAttestationHeadPresentation.appendReceiptAnchorRows(
        rows, exported.operationOrder(), exported.operationHeadHex());
    rows.add(
        List.of(
            "Warnings",
            exported.warnings().isEmpty() ? "(none)" : CliTextFormat.joined(exported.warnings())));
    rows.add(
        List.of(
            "Retained stage", CliTextDisplay.path(exported.retainedStage().retainedStagePath())));
    return List.copyOf(rows);
  }

  static List<List<String>> receiptVerificationRows(VerifyAttestationReceiptResult.Valid valid) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Receipt file", CliTextDisplay.path(valid.receiptFilePath())));
    rows.add(List.of("Book ID", valid.bookId().toString()));
    CliAttestationHeadPresentation.appendReceiptAnchorRows(
        rows, valid.operationOrder(), valid.operationHeadHex());
    rows.add(
        List.of(
            "Findings",
            valid.findings().isEmpty() ? "(none)" : CliTextFormat.joined(valid.findings())));
    return List.copyOf(rows);
  }

  static List<List<String>> verificationRows(
      VerifyBookAttestationResult.Valid valid, String renderedReviewFindings) {
    List<List<String>> rows = verifiedChainRows(valid);
    rows.add(
        List.of("Attestation credentials", renderedCredentials(valid.registry().credentials())));
    rows.add(
        List.of(
            "Effective quorum policy",
            renderedCapabilityPolicies(valid.registry().capabilityPolicies())));
    rows.add(
        List.of(
            "Principal capabilities",
            renderedPrincipalCapabilities(valid.registry().principalCapabilities())));
    rows.add(
        List.of(
            "System workflow policies",
            renderedSystemWorkflowPolicies(valid.registry().systemWorkflowPolicies())));
    rows.add(List.of("Review findings", renderedReviewFindings));
    return List.copyOf(rows);
  }

  static List<List<String>> reviewRequiredRows(VerifyBookAttestationResult.Valid valid) {
    List<List<String>> rows = verifiedChainRows(valid);
    rows.add(
        List.of(
            "Review findings",
            CliAttestationReviewTextRenderer.renderFindings(valid.reviewFindings())));
    return List.copyOf(rows);
  }

  static List<List<String>> reviewRows(AttestationReviewResult.Valid valid) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Book ID", valid.bookId().toString()));
    CliAttestationHeadPresentation.appendVerifiedHeadRows(
        rows, valid.headOrder(), valid.operationHeadHex());
    rows.add(
        List.of(
            "Findings",
            valid.findings().isEmpty()
                ? "(none)"
                : CliAttestationReviewTextRenderer.renderFindings(valid.findings())));
    return List.copyOf(rows);
  }

  static CliAttestationJsonModels.AttestationRegistryPayload registryPayload(
      AttestationRegistryInspection registry) {
    return CliAttestationPayloadMapper.registryPayload(registry);
  }

  private static List<List<String>> verifiedChainRows(VerifyBookAttestationResult.Valid valid) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Book ID", valid.bookId().toString()));
    CliAttestationHeadPresentation.appendVerifiedHeadRows(
        rows, valid.headOrder(), valid.operationHeadHex(), valid.previousHeadHex());
    return rows;
  }

  private static String renderedCapabilityPolicies(
      List<AttestationRegistryInspection.CapabilityPolicy> policies) {
    return CliAttestationPayloadMapper.renderedCapabilityPolicies(policies);
  }

  private static String renderedCredentials(
      List<AttestationRegistryInspection.Credential> credentials) {
    return CliAttestationPayloadMapper.renderedCredentials(credentials);
  }

  private static String renderedPrincipalCapabilities(
      List<AttestationRegistryInspection.PrincipalCapability> principalCapabilities) {
    return principalCapabilities.isEmpty()
        ? "(none)"
        : CliTextFormat.renderBulletedBlock(
            principalCapabilities.stream()
                .map(
                    capability ->
                        "principalId="
                            + capability.principalId()
                            + "\n  capability="
                            + capability.capability()
                            + "\n  eligible="
                            + capability.eligible())
                .toList(),
            Integer.MAX_VALUE);
  }

  private static String renderedSystemWorkflowPolicies(
      List<AttestationRegistryInspection.SystemWorkflowPolicy> policies) {
    if (policies.isEmpty()) {
      return "(none)";
    }
    return CliTextFormat.renderBulletedBlock(
        policies.stream()
            .map(
                policy ->
                    "workflowId="
                        + policy.workflowId()
                        + "\n  kind="
                        + policy.workflowKind()
                        + "\n  active="
                        + policy.active())
            .toList(),
        Integer.MAX_VALUE);
  }
}
