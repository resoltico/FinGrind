package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Renders the contract-owned generated command table block for docs/USER_CLI.md. */
final class ProtocolUserCliMarkdownRenderer {
  static final String COMMAND_TABLE_BEGIN = "<!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->";
  static final String COMMAND_TABLE_END = "<!-- END GENERATED USER_CLI COMMAND TABLE -->";

  private ProtocolUserCliMarkdownRenderer() {}

  static String commandTableBlock() {
    return String.join("\n", COMMAND_TABLE_BEGIN, renderCommandTable(), COMMAND_TABLE_END);
  }

  private static String renderCommandTable() {
    return Stream.concat(
            Stream.of(
                "<table>",
                "  <thead>",
                "    <tr><th>Command</th><th>Aliases</th><th>Extra Arguments</th><th>Result</th></tr>",
                "  </thead>",
                "  <tbody>"),
            Stream.concat(
                ProtocolCatalog.operations().stream()
                    .map(ProtocolUserCliMarkdownRenderer::renderRow),
                Stream.of("  </tbody>", "</table>")))
        .collect(Collectors.joining("\n"));
  }

  private static String renderRow(ProtocolOperation operation) {
    return "    <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
        .formatted(
            renderCode(operation.id().wireName()),
            renderCodeList(operation.aliases()),
            renderCodeList(operation.options()),
            escapeHtml(operation.analysisSummary()));
  }

  private static String renderCodeList(List<String> values) {
    if (values.isEmpty()) {
      return escapeHtml("none");
    }
    return values.stream()
        .map(ProtocolUserCliMarkdownRenderer::renderCode)
        .collect(Collectors.joining("<br>"));
  }

  private static String renderCode(String value) {
    return "<code>" + escapeHtml(value) + "</code>";
  }

  private static String escapeHtml(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
