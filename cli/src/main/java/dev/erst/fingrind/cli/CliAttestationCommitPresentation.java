package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Owns newly appended-commit semantics while delegating identity projection to the shared owner.
 */
final class CliAttestationCommitPresentation {
  static final String UNAVAILABLE_REFERENCE_DETAIL = "No authenticated operation reference";

  private CliAttestationCommitPresentation() {}

  static @Nullable AttestationCommitPayload payload(@Nullable AttestationCommit attestationCommit) {
    return attestationCommit == null ? null : requiredPayload(attestationCommit);
  }

  static AttestationCommitPayload requiredPayload(AttestationCommit attestationCommit) {
    return requiredPayload(
        attestationCommit.operationOrder().toString(), attestationCommit.operationHeadHex());
  }

  static AttestationCommitPayload requiredPayload(String operationOrder, String operationHead) {
    return new AttestationCommitPayload(operationOrder, operationHead);
  }

  static void appendTextRows(
      List<List<String>> rows, @Nullable AttestationCommit attestationCommit, String absentDetail) {
    if (attestationCommit == null) {
      appendUnavailableReferenceRow(rows, absentDetail);
      return;
    }
    CliAttestationHeadPresentation.appendCommitRows(rows, attestationCommit);
  }

  static void appendPayloadTextRows(
      List<List<String>> rows,
      @Nullable AttestationCommitPayload attestationCommit,
      String absentDetail) {
    if (attestationCommit == null) {
      appendUnavailableReferenceRow(rows, absentDetail);
      return;
    }
    CliAttestationHeadPresentation.appendIdentityRows(
        rows, attestationCommit.operationOrder(), attestationCommit.operationHead());
  }

  private static void appendUnavailableReferenceRow(List<List<String>> rows, String absentDetail) {
    rows.add(List.of("Attestation", absentDetail));
  }
}
