package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Renders contract-owned public package metadata blocks for user install documentation. */
final class ProtocolUserInstallMarkdownRenderer {
  static final String USER_INSTALL_PACKAGE_MATRIX_BEGIN =
      "<!-- BEGIN GENERATED USER_INSTALL PACKAGE MATRIX -->";
  static final String USER_INSTALL_PACKAGE_MATRIX_END =
      "<!-- END GENERATED USER_INSTALL PACKAGE MATRIX -->";
  static final String USER_INSTALL_CONTAINER_SURFACE_BEGIN =
      "<!-- BEGIN GENERATED USER_INSTALL CONTAINER SURFACE -->";
  static final String USER_INSTALL_CONTAINER_SURFACE_END =
      "<!-- END GENERATED USER_INSTALL CONTAINER SURFACE -->";
  static final String USER_QUICK_START_BUNDLE_MATRIX_BEGIN =
      "<!-- BEGIN GENERATED USER_QUICK_START BUNDLE MATRIX -->";
  static final String USER_QUICK_START_BUNDLE_MATRIX_END =
      "<!-- END GENERATED USER_QUICK_START BUNDLE MATRIX -->";

  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
  private static final String RELEASE_PUBLICATION_CONTRACT_PATH =
      "contract/src/main/resources/dev/erst/fingrind/contract/protocol/release-publication-contract.json";
  private static final String BUNDLE_LAYOUT_CONTRACT_PATH =
      "contract/src/main/resources/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json";
  private static final String GRADLE_PROPERTIES_PATH = "gradle.properties";
  private static final String CONTAINER_IMAGE_REFERENCE = "ghcr.io/resoltico/fingrind";

  private ProtocolUserInstallMarkdownRenderer() {}

  static String userInstallPackageMatrixBlock(Path repositoryRoot) throws IOException {
    return String.join(
        "\n",
        USER_INSTALL_PACKAGE_MATRIX_BEGIN,
        renderBundleMatrixTable(repositoryRoot),
        USER_INSTALL_PACKAGE_MATRIX_END);
  }

  static String userInstallContainerSurfaceBlock(Path repositoryRoot) throws IOException {
    return String.join(
        "\n",
        USER_INSTALL_CONTAINER_SURFACE_BEGIN,
        renderContainerSurface(repositoryRoot),
        USER_INSTALL_CONTAINER_SURFACE_END);
  }

  static String userQuickStartBundleMatrixBlock(Path repositoryRoot) throws IOException {
    return String.join(
        "\n",
        USER_QUICK_START_BUNDLE_MATRIX_BEGIN,
        renderBundleMatrixTable(repositoryRoot),
        USER_QUICK_START_BUNDLE_MATRIX_END);
  }

  private static String renderBundleMatrixTable(Path repositoryRoot) throws IOException {
    Map<String, BundleLayoutRow> bundleLayoutRows = loadBundleLayoutRows(repositoryRoot);
    List<PublicCliBundleTarget> supportedTargets =
        ProtocolCatalog.distribution().supportedPublicCliBundleTargets();
    List<PublicCliBundleTarget> unsupportedTargets =
        ProtocolCatalog.distribution().unsupportedPublicCliBundleTargets();

    Stream<String> tableRows =
        Stream.concat(
            supportedTargets.stream().map(target -> supportedBundleRow(target, bundleLayoutRows)),
            unsupportedTargets.stream()
                .map(target -> unsupportedBundleRow(target, bundleLayoutRows)));
    return Stream.concat(
            Stream.of(
                "| Target | Archive name pattern | Launcher path | Compatibility | Status |",
                "|:-------|:---------------------|:--------------|:--------------|:-------|"),
            tableRows)
        .collect(Collectors.joining("\n"));
  }

  private static String supportedBundleRow(
      PublicCliBundleTarget target, Map<String, BundleLayoutRow> bundleLayoutRows) {
    BundleLayoutRow bundleLayout = requiredBundleLayoutRow(target, bundleLayoutRows);
    String archiveName =
        "fingrind-<version>-" + target.wireValue() + "." + bundleLayout.archiveFormat();
    return "| `%s` | `%s` | `%s` | `%s` | published |"
        .formatted(
            target.wireValue(),
            archiveName,
            bundleLayout.launcherPath(),
            compatibilityLabel(bundleLayout));
  }

  private static String unsupportedBundleRow(
      PublicCliBundleTarget target, Map<String, BundleLayoutRow> bundleLayoutRows) {
    BundleLayoutRow bundleLayout = requiredBundleLayoutRow(target, bundleLayoutRows);
    String archiveName =
        "fingrind-<version>-" + target.wireValue() + "." + bundleLayout.archiveFormat();
    return "| `%s` | `%s` | `%s` | `%s` | not published |"
        .formatted(
            target.wireValue(),
            archiveName,
            bundleLayout.launcherPath(),
            compatibilityLabel(bundleLayout));
  }

  private static String renderContainerSurface(Path repositoryRoot) throws IOException {
    JsonNode releasePublication =
        OBJECT_MAPPER.readTree(
            Files.readString(repositoryRoot.resolve(RELEASE_PUBLICATION_CONTRACT_PATH)));
    List<String> platforms =
        readStringArray(releasePublication.path("containerPlatforms"), "containerPlatforms");
    String latestPolicy =
        renderedLatestPublicationPolicy(
            releasePublication.path("latestPublicationPolicy").asText(""));
    if (latestPolicy.isBlank()) {
      throw new IOException(
          RELEASE_PUBLICATION_CONTRACT_PATH + " must declare latestPublicationPolicy.");
    }
    String projectVersion = projectVersion(repositoryRoot);
    return String.join(
        "\n",
        "- image reference: `%s`".formatted(CONTAINER_IMAGE_REFERENCE),
        "- published tags: one exact release tag such as `%s` plus `latest`, where %s"
            .formatted(projectVersion, latestPolicy),
        "- published platforms: `%s`".formatted(String.join("`, `", platforms)),
        "- mounted launcher prefix: `docker run --rm -i -v <host-workdir>:/workspace -w /workspace %s:<tag>`"
            .formatted(CONTAINER_IMAGE_REFERENCE));
  }

  private static String projectVersion(Path repositoryRoot) throws IOException {
    return Files.readAllLines(repositoryRoot.resolve(GRADLE_PROPERTIES_PATH)).stream()
        .filter(line -> line.startsWith("version="))
        .map(line -> line.substring("version=".length()).trim())
        .filter(version -> !version.isEmpty())
        .findFirst()
        .orElseThrow(() -> new IOException("Missing version in " + GRADLE_PROPERTIES_PATH + "."));
  }

  private static Map<String, BundleLayoutRow> loadBundleLayoutRows(Path repositoryRoot)
      throws IOException {
    JsonNode bundleLayout =
        OBJECT_MAPPER.readTree(
            Files.readString(repositoryRoot.resolve(BUNDLE_LAYOUT_CONTRACT_PATH)));
    JsonNode bundleTargets = bundleLayout.path("bundleTargets");
    if (!bundleTargets.isObject()) {
      throw new IOException(BUNDLE_LAYOUT_CONTRACT_PATH + " must declare bundleTargets.");
    }
    return bundleTargets.properties().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> bundleLayoutRowUnchecked(entry.getValue())));
  }

  private static List<String> readStringArray(JsonNode node, String fieldName) throws IOException {
    if (!node.isArray() || node.isEmpty()) {
      throw new IOException(RELEASE_PUBLICATION_CONTRACT_PATH + " must declare " + fieldName + ".");
    }
    return StreamSupport.stream(node.spliterator(), false)
        .map(
            element -> {
              if (!element.isTextual() || element.asText().isBlank()) {
                throw new IllegalArgumentException(
                    RELEASE_PUBLICATION_CONTRACT_PATH
                        + " must declare "
                        + fieldName
                        + " as strings.");
              }
              return element.asText();
            })
        .toList();
  }

  private static BundleLayoutRow requiredBundleLayoutRow(
      PublicCliBundleTarget target, Map<String, BundleLayoutRow> bundleLayoutRows) {
    BundleLayoutRow row = bundleLayoutRows.get(target.wireValue());
    if (row == null) {
      throw new IllegalStateException(
          "Missing bundle-layout row for public target " + target.wireValue() + ".");
    }
    return row;
  }

  private static String requiredText(JsonNode node, String fieldName, String sourcePath)
      throws IOException {
    JsonNode value = node.path(fieldName);
    if (!value.isTextual() || value.asText().isBlank()) {
      throw new IOException(sourcePath + " must declare " + fieldName + " as non-blank text.");
    }
    return value.asText();
  }

  private static BundleLayoutRow bundleLayoutRowUnchecked(JsonNode node) {
    try {
      return new BundleLayoutRow(
          requiredText(node, "operatingSystemId", BUNDLE_LAYOUT_CONTRACT_PATH),
          requiredText(node, "architectureId", BUNDLE_LAYOUT_CONTRACT_PATH),
          requiredText(node, "archiveFormat", BUNDLE_LAYOUT_CONTRACT_PATH),
          requiredText(node, "launcherPath", BUNDLE_LAYOUT_CONTRACT_PATH));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static String compatibilityLabel(BundleLayoutRow bundleLayout) {
    return switch (bundleLayout.operatingSystemId()) {
      case "linux" -> "glibc Linux " + bundleLayout.architectureId();
      case "macos" -> "macOS " + bundleLayout.architectureId();
      case "windows" -> "Windows " + bundleLayout.architectureId();
      default ->
          throw new IllegalStateException(
              "Unsupported bundle-layout operatingSystemId "
                  + bundleLayout.operatingSystemId()
                  + ".");
    };
  }

  private static String renderedLatestPublicationPolicy(String latestPolicy) throws IOException {
    if (latestPolicy.isBlank()) {
      return latestPolicy;
    }
    if ("newest-stable-release-only".equals(latestPolicy)) {
      return "`latest` always points at the newest stable public release";
    }
    throw new IOException(
        RELEASE_PUBLICATION_CONTRACT_PATH
            + " declared unsupported latestPublicationPolicy "
            + latestPolicy
            + ".");
  }

  private record BundleLayoutRow(
      String operatingSystemId, String architectureId, String archiveFormat, String launcherPath) {}
}
