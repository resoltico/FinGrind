package dev.erst.fingrind.jazzer.tool;

import java.util.List;

/** Renders classified raw finding inventories for text-mode Jazzer CLI output. */
final class JazzerFindingListingTextRenderer {
  private JazzerFindingListingTextRenderer() {}

  static String render(String targetKey, List<FindingArtifact> findings) {
    long unexpectedFailures =
        findings.stream()
            .filter(
                finding ->
                    finding.replayClassification()
                        == ReplayFindingClassification.UNEXPECTED_FAILURE)
            .count();
    long expectedInvalid =
        findings.stream()
            .filter(
                finding ->
                    finding.replayClassification() == ReplayFindingClassification.EXPECTED_INVALID)
            .count();
    long replayClean =
        findings.stream()
            .filter(
                finding ->
                    finding.replayClassification() == ReplayFindingClassification.REPLAY_CLEAN)
            .count();

    StringBuilder builder =
        new StringBuilder(256)
            .append("Target: ")
            .append(targetKey)
            .append(System.lineSeparator())
            .append("Summary: actionable=")
            .append(unexpectedFailures)
            .append(" expected-invalid=")
            .append(expectedInvalid)
            .append(" replay-clean=")
            .append(replayClean);
    if (findings.isEmpty()) {
      builder
          .append(System.lineSeparator())
          .append("No raw libFuzzer artifacts are currently recorded for this target.");
      return builder.toString();
    }

    for (FindingArtifact finding : findings) {
      builder
          .append(System.lineSeparator())
          .append(System.lineSeparator())
          .append(finding.rawArtifactName())
          .append(" | ")
          .append(finding.replayClassification().wireValue())
          .append(" | ")
          .append(finding.message())
          .append(System.lineSeparator())
          .append("Path: ")
          .append(finding.rawArtifactPath());
    }
    return builder.toString();
  }
}
