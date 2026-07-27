package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import java.util.List;

/** Renders every independently established protected-book pair-publication fact. */
final class CliProtectedBookPairPublicationErrorDetailsTextRenderer {
  private CliProtectedBookPairPublicationErrorDetailsTextRenderer() {}

  static void appendRows(
      List<List<String>> rows,
      CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationUncertainDetails details) {
    rows.add(List.of("Operation", details.operation()));
    appendRows(rows, details.pairPublication());
  }

  static void appendRows(
      List<List<String>> rows, CliMaintenanceErrorJsonModels.PairPublication pair) {
    appendMemberRows(rows, "Book target", pair.bookTarget());
    appendMemberRows(rows, "Generated secret target", pair.generatedSecretTarget());
    if (pair.recoveryRecordState() != null) {
      rows.add(List.of("Recovery record state", pair.recoveryRecordState().wireValue()));
    }
    if (pair.pairPublicationRetention() != null) {
      CliMaintenanceErrorJsonModels.PairPublicationRetention retention =
          pair.pairPublicationRetention();
      rows.add(
          List.of(
              "Book retained stage",
              CliTextDisplay.serializedAbsolutePath(retention.bookPublication().retainedStage())));
      rows.add(
          List.of(
              "Generated secret retained stage",
              CliTextDisplay.serializedAbsolutePath(
                  retention.generatedSecretPublication().retainedStage())));
    }
  }

  private static void appendMemberRows(
      List<List<String>> rows,
      String label,
      CliMaintenanceErrorJsonModels.PairPublicationMember member) {
    rows.add(List.of(label, CliTextDisplay.serializedAbsolutePath(member.path())));
    rows.add(List.of(label + " state", member.state().wireValue()));
  }
}
