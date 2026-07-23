package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Owns the one public rendering vocabulary for exact newly appended attestation operations. */
final class CliAttestationCommitPresentation {
  private CliAttestationCommitPresentation() {}

  static @Nullable AttestationCommitPayload payload(@Nullable AttestationCommit attestationCommit) {
    return attestationCommit == null ? null : requiredPayload(attestationCommit);
  }

  static AttestationCommitPayload requiredPayload(AttestationCommit attestationCommit) {
    return new AttestationCommitPayload(
        attestationCommit.operationOrder().toString(), attestationCommit.operationHeadHex());
  }

  static void appendTextRows(
      List<List<String>> rows, @Nullable AttestationCommit attestationCommit, String absentDetail) {
    if (attestationCommit == null) {
      rows.add(List.of("Attestation", absentDetail));
      return;
    }
    rows.add(List.of("Attestation order", attestationCommit.operationOrder().toString()));
    rows.add(List.of("Attestation head", attestationCommit.operationHeadHex()));
  }
}
