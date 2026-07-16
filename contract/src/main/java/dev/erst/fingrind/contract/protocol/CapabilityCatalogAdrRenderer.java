package dev.erst.fingrind.contract.protocol;

import java.util.stream.Collectors;

/** Renders the contract-owned capability sections in the accounting-kernel scope ADR. */
final class CapabilityCatalogAdrRenderer {
  static final String BEGIN = "<!-- BEGIN GENERATED CAPABILITY CATALOG -->";
  static final String END = "<!-- END GENERATED CAPABILITY CATALOG -->";
  static final String CURRENT_SCOPE_BEGIN = "<!-- BEGIN GENERATED CURRENT CAPABILITY SCOPE -->";
  static final String CURRENT_SCOPE_END = "<!-- END GENERATED CURRENT CAPABILITY SCOPE -->";
  static final String EXCLUSIONS_BEGIN = "<!-- BEGIN GENERATED CAPABILITY EXCLUSIONS -->";
  static final String EXCLUSIONS_END = "<!-- END GENERATED CAPABILITY EXCLUSIONS -->";

  private CapabilityCatalogAdrRenderer() {}

  static String updatedDocument(String document) {
    String currentScopeUpdated =
        replaceBlock(document, CURRENT_SCOPE_BEGIN, CURRENT_SCOPE_END, renderedCurrentScopeBlock());
    String exclusionsUpdated =
        replaceBlock(
            currentScopeUpdated, EXCLUSIONS_BEGIN, EXCLUSIONS_END, renderedExclusionsBlock());
    return replaceBlock(exclusionsUpdated, BEGIN, END, renderedCapabilityCatalogBlock());
  }

  static String renderedCapabilityCatalogBlock() {
    String rows =
        CapabilityCatalog.entries().stream()
            .map(
                entry ->
                    "| `%s` | `%s` | %s | %s |"
                        .formatted(
                            entry.id(),
                            entry.status().name().toLowerCase(java.util.Locale.ROOT),
                            entry.scopeStatement(),
                            entry.operativeBoundary() == null ? "" : entry.operativeBoundary()))
            .collect(Collectors.joining("\n"));
    return String.join(
        "\n",
        BEGIN,
        "| Capability | Status | Published scope | Operative boundary |",
        "|:-----------|:-------|:----------------|:-------------------|",
        rows,
        END);
  }

  static String renderedCurrentScopeBlock() {
    String capabilities =
        CapabilityCatalog.entries().stream()
            .filter(entry -> entry.status() != CapabilityStatus.EXCLUDED)
            .map(CapabilityCatalogAdrRenderer::currentScopeLine)
            .collect(Collectors.joining("\n"));
    return String.join(
        "\n",
        CURRENT_SCOPE_BEGIN,
        "The current kernel publishes these contract-owned capabilities:",
        capabilities,
        CURRENT_SCOPE_END);
  }

  static String renderedExclusionsBlock() {
    String exclusions =
        CapabilityCatalog.entries().stream()
            .filter(entry -> entry.status() == CapabilityStatus.EXCLUDED)
            .map(entry -> "- `" + entry.id() + "` is excluded: " + entry.scopeStatement())
            .collect(Collectors.joining("\n"));
    return String.join(
        "\n",
        EXCLUSIONS_BEGIN,
        "The current kernel does not publish these first-class capabilities:",
        exclusions,
        EXCLUSIONS_END);
  }

  private static String currentScopeLine(CapabilityCatalogEntry entry) {
    String boundary =
        entry.operativeBoundary() == null
            ? ""
            : " Operative boundary: " + entry.operativeBoundary();
    return "- `%s` is `%s`: %s%s"
        .formatted(
            entry.id(),
            entry.status().name().toLowerCase(java.util.Locale.ROOT),
            entry.scopeStatement(),
            boundary);
  }

  private static String replaceBlock(
      String document, String beginMarker, String endMarker, String renderedBlock) {
    int begin = document.indexOf(beginMarker);
    int end = document.indexOf(endMarker);
    if (begin < 0
        || end < 0
        || end < begin
        || document.indexOf(beginMarker, begin + beginMarker.length()) >= 0
        || document.indexOf(endMarker, end + endMarker.length()) >= 0) {
      throw new IllegalArgumentException(
          "The accounting-kernel scope ADR lacks one unique generated capability section.");
    }
    return document.substring(0, begin)
        + renderedBlock
        + document.substring(end + endMarker.length());
  }
}
