package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import dev.erst.fingrind.cli.json.CliOpenBookErrorJsonModels;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Dispatches structured error-detail families to their semantic text renderers. */
final class CliErrorDetailsTextRenderer {
  private CliErrorDetailsTextRenderer() {}

  static void appendRows(
      List<List<String>> rows, CliErrorJsonModels.@Nullable ErrorDetails details) {
    if (details == null) {
      return;
    }
    switch (details) {
      case CliErrorJsonModels.BasicErrorDetails value ->
          CliBasicErrorDetailsTextRenderer.appendRows(rows, value);
      case CliMaintenanceErrorJsonModels.MaintenanceErrorDetails value ->
          CliMaintenanceErrorDetailsTextRenderer.appendRows(rows, value);
      case CliOpenBookErrorJsonModels.OpenBookErrorDetails value ->
          CliOpenBookErrorDetailsTextRenderer.appendRows(rows, value);
    }
  }

  /** Returns whether the structured error details already render every failure path by role. */
  static boolean rendersFailurePaths(CliErrorJsonModels.@Nullable ErrorDetails details) {
    return details instanceof CliMaintenanceErrorJsonModels.MaintenanceErrorDetails
        || details instanceof CliOpenBookErrorJsonModels.OpenBookErrorDetails;
  }
}
