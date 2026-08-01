package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Tests that bundle target identifiers preserve their declared spelling. */
class BundleLayoutContractExactTextTest {
  @Test
  void exactJsonText_rejectsBoundaryWhitespaceWithoutNormalizingIt() {
    JsonNode exact =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream(
                "{\"launcherPath\":\"bin/fingrind\"}".getBytes(StandardCharsets.UTF_8)),
            "/exact-bundle-target.json",
            "test contract");
    JsonNode boundaryWhitespace =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream(
                "{\"launcherPath\":\"bin/fingrind \"}".getBytes(StandardCharsets.UTF_8)),
            "/boundary-whitespace-bundle-target.json",
            "test contract");

    assertEquals(
        "bin/fingrind", JsonContractResourceSupport.requireExactText(exact, "launcherPath"));
    IllegalArgumentException rejected =
        assertThrows(
            IllegalArgumentException.class,
            () -> JsonContractResourceSupport.requireExactText(boundaryWhitespace, "launcherPath"));
    assertEquals(
        "launcherPath must not contain leading or trailing whitespace.", rejected.getMessage());
  }

  @Test
  void exactJsonText_requiresOneNonBlankString() {
    JsonNode missing = jsonObject("{}");
    JsonNode nullValue = jsonObject("{\"launcherPath\":null}");
    JsonNode numericValue = jsonObject("{\"launcherPath\":42}");
    JsonNode blankValue = jsonObject("{\"launcherPath\":\"   \"}");

    for (JsonNode document : java.util.List.of(missing, nullValue, numericValue)) {
      IllegalArgumentException rejected =
          assertThrows(
              IllegalArgumentException.class,
              () -> JsonContractResourceSupport.requireExactText(document, "launcherPath"));
      assertEquals("launcherPath must be a non-blank JSON string.", rejected.getMessage());
    }
    IllegalArgumentException blank =
        assertThrows(
            IllegalArgumentException.class,
            () -> JsonContractResourceSupport.requireExactText(blankValue, "launcherPath"));
    assertEquals("launcherPath must not be blank.", blank.getMessage());
  }

  @Test
  void bundleTarget_rejectsBoundaryWhitespaceInTargetAndFilesystemContractValues() {
    assertBoundaryWhitespaceRejected(
        () ->
            bundleTarget(
                "linux ", "x86_64", "tar.gz", "bin/fingrind", "./bin/fingrind", "libsqlite3.so.0"),
        "operatingSystemId");
    assertBoundaryWhitespaceRejected(
        () ->
            bundleTarget(
                "linux", " x86_64", "tar.gz", "bin/fingrind", "./bin/fingrind", "libsqlite3.so.0"),
        "architectureId");
    assertBoundaryWhitespaceRejected(
        () ->
            bundleTarget(
                "linux", "x86_64", "tar.gz ", "bin/fingrind", "./bin/fingrind", "libsqlite3.so.0"),
        "archiveFormat");
    assertBoundaryWhitespaceRejected(
        () ->
            bundleTarget(
                "linux", "x86_64", "tar.gz", " bin/fingrind", "./bin/fingrind", "libsqlite3.so.0"),
        "launcherPath");
    assertBoundaryWhitespaceRejected(
        () ->
            bundleTarget(
                "linux", "x86_64", "tar.gz", "bin/fingrind", "./bin/fingrind ", "libsqlite3.so.0"),
        "launcherCommand");
    assertBoundaryWhitespaceRejected(
        () ->
            bundleTarget(
                "linux", "x86_64", "tar.gz", "bin/fingrind", "./bin/fingrind", " libsqlite3.so.0"),
        "sqliteLibraryFileName");
    assertBoundaryWhitespaceRejected(
        () ->
            bundleTarget(
                "linux",
                "x86_64",
                "tar.gz",
                "bin/fingrind",
                "./bin/fingrind",
                "libsqlite3.so.0",
                Optional.of("2.34 "),
                Optional.of("rockylinux:9@sha256:floor-proof")),
        "minimumGlibcVersion");
    assertBoundaryWhitespaceRejected(
        () ->
            bundleTarget(
                "linux",
                "x86_64",
                "tar.gz",
                "bin/fingrind",
                "./bin/fingrind",
                "libsqlite3.so.0",
                Optional.of("2.34"),
                Optional.of(" rockylinux:9@sha256:floor-proof")),
        "compatibilitySmokeContainerImage");
  }

  @Test
  void bundleLayoutLoader_rejectsBoundaryWhitespaceInTargetContractValues() throws IOException {
    String resourcePath = "/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json";
    InputStream resource =
        Objects.requireNonNull(
            BundleLayoutContractExactTextTest.class.getResourceAsStream(resourcePath),
            resourcePath);
    String canonicalContract;
    try (resource) {
      canonicalContract = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    }
    String malformedLauncherPath =
        canonicalContract.replace(
            "\"launcherPath\": \"bin/fingrind.ps1\"", "\"launcherPath\": \"bin/fingrind.ps1 \"");
    assertBundleLayoutLoaderRejectsBoundaryWhitespace(
        malformedLauncherPath, resourcePath, "launcherPath");

    String malformedGlibcVersion =
        canonicalContract.replace(
            "\"minimumGlibcVersion\": \"2.34\"", "\"minimumGlibcVersion\": \"2.34 \"");
    assertBundleLayoutLoaderRejectsBoundaryWhitespace(
        malformedGlibcVersion, resourcePath, "minimumGlibcVersion");
  }

  private static void assertBundleLayoutLoaderRejectsBoundaryWhitespace(
      String malformedContract, String resourcePath, String fieldName) {
    IllegalArgumentException rejected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BundleLayoutContracts.loadFromResource(
                    new ByteArrayInputStream(malformedContract.getBytes(StandardCharsets.UTF_8)),
                    resourcePath));
    assertEquals(
        fieldName + " must not contain leading or trailing whitespace.", rejected.getMessage());
  }

  private static JsonNode jsonObject(String json) {
    return JsonContractResourceSupport.loadObject(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
        "/exact-text-test.json",
        "test contract");
  }

  private static void assertBoundaryWhitespaceRejected(
      ThrowingBundleTargetFactory factory, String fieldName) {
    IllegalArgumentException rejected =
        assertThrows(IllegalArgumentException.class, () -> factory.create());
    assertEquals(
        fieldName + " must not contain leading or trailing whitespace.", rejected.getMessage());
  }

  private static BundleLayoutContract.BundleTarget bundleTarget(
      String operatingSystemId,
      String architectureId,
      String archiveFormat,
      String launcherPath,
      String launcherCommand,
      String sqliteLibraryFileName) {
    return bundleTarget(
        operatingSystemId,
        architectureId,
        archiveFormat,
        launcherPath,
        launcherCommand,
        sqliteLibraryFileName,
        Optional.of("2.34"),
        Optional.of("rockylinux:9@sha256:floor-proof"));
  }

  private static BundleLayoutContract.BundleTarget bundleTarget(
      String operatingSystemId,
      String architectureId,
      String archiveFormat,
      String launcherPath,
      String launcherCommand,
      String sqliteLibraryFileName,
      Optional<String> minimumGlibcVersion,
      Optional<String> compatibilitySmokeContainerImage) {
    return new BundleLayoutContract.BundleTarget(
        operatingSystemId,
        architectureId,
        archiveFormat,
        launcherPath,
        launcherCommand,
        sqliteLibraryFileName,
        "glibc 2.34+ Linux x86_64",
        minimumGlibcVersion,
        compatibilitySmokeContainerImage,
        new BundleLayoutContract.PublicBundlePublication(PublicBundlePublicationStatus.PUBLISHED));
  }

  /** Creates one bundle target whose validation outcome is under test. */
  @FunctionalInterface
  private interface ThrowingBundleTargetFactory {
    BundleLayoutContract.BundleTarget create();
  }
}
