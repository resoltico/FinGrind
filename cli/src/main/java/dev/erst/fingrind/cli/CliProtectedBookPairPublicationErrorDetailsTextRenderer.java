package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import java.util.List;

/** Renders every independently established protected-book pair-publication fact. */
final class CliProtectedBookPairPublicationErrorDetailsTextRenderer {
  private CliProtectedBookPairPublicationErrorDetailsTextRenderer() {}

  static void appendRows(
      List<List<String>> rows, CliMaintenanceErrorJsonModels.EvidenceBlockedPairPublication pair) {
    appendMemberRows(rows, "Book target", pair.bookTarget());
    appendMemberRows(rows, "Generated secret target", pair.generatedSecretTarget());
  }

  private static void appendMemberRows(
      List<List<String>> rows,
      String label,
      CliMaintenanceErrorJsonModels.PairPublicationMember member) {
    rows.add(List.of(label, CliTextDisplay.serializedAbsolutePath(member.path())));
    rows.add(List.of(label + " state", member.state().wireValue()));
  }
}
