package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies that a publication result always pairs its final artifact with its retained stage. */
class ArtifactPublicationResultTest {
  @TempDir Path temporaryDirectory;

  @Test
  void rejectsARetainedStageOutsideThePublishedArtifactCanonicalParent() throws IOException {
    Path publicationParent = Files.createDirectories(temporaryDirectory.resolve("published"));
    Path unrelatedStageParent = Files.createDirectories(temporaryDirectory.resolve("unrelated"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ArtifactPublicationResult(
                    publicationParent.resolve("receipt.fgar"),
                    new ArtifactPublicationRetention(
                        unrelatedStageParent.resolve(".receipt-stage.tmp"))));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("same canonical parent directory"));
  }

  @Test
  void rejectsASelfReferentialRetainedStageForLiveAndCapturedPublicationFacts() {
    Path selfReferentialPath = temporaryDirectory.resolve("receipt.fgar");
    ArtifactPublicationRetention selfReferentialRetention =
        new ArtifactPublicationRetention(selfReferentialPath);

    IllegalArgumentException liveException =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ArtifactPublicationResult(selfReferentialPath, selfReferentialRetention));
    IllegalArgumentException capturedException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ArtifactPublicationResult.restoreCapturedCanonicalPaths(
                    selfReferentialPath, selfReferentialRetention));

    assertTrue(
        Objects.requireNonNull(liveException.getMessage(), "live exception message")
            .contains("distinct canonical paths"));
    assertTrue(
        Objects.requireNonNull(capturedException.getMessage(), "captured exception message")
            .contains("distinct canonical paths"));
  }

  @Test
  void canonicalizesParentAliasesBeforeCheckingTheRetainedStageSiblingInvariant()
      throws IOException {
    Path physicalParent = Files.createDirectories(temporaryDirectory.resolve("physical"));
    Path aliasedParent = temporaryDirectory.resolve("physical-alias");
    try {
      Files.createSymbolicLink(aliasedParent, physicalParent);
    } catch (UnsupportedOperationException | SecurityException | FileSystemException exception) {
      assumeTrue(false, "The filesystem does not permit symbolic-link test fixtures.");
      return;
    }

    ArtifactPublicationResult publication =
        new ArtifactPublicationResult(
            aliasedParent.resolve("receipt.fgar"),
            new ArtifactPublicationRetention(physicalParent.resolve(".receipt-stage.tmp")));

    Path canonicalParent = physicalParent.toRealPath();
    assertEquals(canonicalParent.resolve("receipt.fgar"), publication.publishedArtifactPath());
    assertEquals(
        canonicalParent.resolve(".receipt-stage.tmp"), publication.retention().retainedStagePath());
  }

  @Test
  void preservesNormalizedSyntheticSiblingPathsWhenTheirParentDoesNotExist() {
    Path syntheticParent = temporaryDirectory.resolve("not-yet-created");

    ArtifactPublicationResult publication =
        new ArtifactPublicationResult(
            syntheticParent.resolve("receipt.fgar"),
            new ArtifactPublicationRetention(syntheticParent.resolve(".receipt-stage.tmp")));

    assertEquals(
        syntheticParent.resolve("receipt.fgar").toAbsolutePath().normalize(),
        publication.publishedArtifactPath());
    assertEquals(
        syntheticParent.resolve(".receipt-stage.tmp").toAbsolutePath().normalize(),
        publication.retention().retainedStagePath());
  }

  @Test
  void rejectsAnExistingRegularFileThatIsPresentedAsAPublicationParent() throws IOException {
    Path nonDirectoryParent = temporaryDirectory.resolve("not-a-directory");
    Files.writeString(nonDirectoryParent, "not a directory");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ArtifactPublicationResult(
                    nonDirectoryParent.resolve("receipt.fgar"),
                    new ArtifactPublicationRetention(
                        nonDirectoryParent.resolve(".receipt-stage.tmp"))));

    assertEquals(
        "publishedArtifactPath must resolve beneath an existing parent directory.",
        exception.getMessage());
  }

  @Test
  void wrapsAParentCanonicalizationFailureWithoutTreatingThePathAsSynthetic() {
    IOException canonicalizationFailure = new IOException("simulated canonicalization failure");
    Path fileName = temporaryDirectory.resolve("receipt.fgar").getFileName();
    ClassLoader proxyClassLoader =
        Objects.requireNonNull(
            Thread.currentThread().getContextClassLoader(), "context class loader");
    Path parent =
        (Path)
            Proxy.newProxyInstance(
                proxyClassLoader,
                new Class<?>[] {Path.class},
                (proxy, method, ignored) -> {
                  if ("toRealPath".equals(method.getName())) {
                    throw canonicalizationFailure;
                  }
                  throw new AssertionError(
                      "Unexpected parent Path invocation: " + method.getName());
                });
    Path artifact =
        (Path)
            Proxy.newProxyInstance(
                proxyClassLoader,
                new Class<?>[] {Path.class},
                (proxy, method, ignored) ->
                    switch (method.getName()) {
                      case "toAbsolutePath", "normalize" -> proxy;
                      case "getFileName" -> fileName;
                      case "getParent" -> parent;
                      default ->
                          throw new AssertionError(
                              "Unexpected artifact Path invocation: " + method.getName());
                    });

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ArtifactPublicationResult(
                    artifact,
                    new ArtifactPublicationRetention(
                        temporaryDirectory.resolve(".receipt-stage.tmp"))));

    assertSame(canonicalizationFailure, exception.getCause());
  }

  @Test
  void restoresCapturedPublicationFactsWithoutResolvingAReplacementParent() throws IOException {
    Path capturedParent = Files.createDirectories(temporaryDirectory.resolve("captured"));
    Path replacementParent = Files.createDirectories(temporaryDirectory.resolve("replacement"));
    ArtifactPublicationResult captured =
        new ArtifactPublicationResult(
            capturedParent.resolve("receipt.fgar"),
            new ArtifactPublicationRetention(capturedParent.resolve(".receipt-stage.tmp")));
    Path movedParent = temporaryDirectory.resolve("captured-before-replacement");
    try {
      Files.move(capturedParent, movedParent);
      Files.createSymbolicLink(capturedParent, replacementParent);
    } catch (UnsupportedOperationException | SecurityException | FileSystemException exception) {
      assumeTrue(false, "The filesystem does not permit symbolic-link test fixtures.");
      return;
    }

    ArtifactPublicationResult restored =
        ArtifactPublicationResult.restoreCapturedCanonicalPaths(
            captured.publishedArtifactPath(), captured.retention());

    assertEquals(captured.publishedArtifactPath(), restored.publishedArtifactPath());
    assertEquals(
        captured.retention().retainedStagePath(), restored.retention().retainedStagePath());
    assertFalse(restored.publishedArtifactPath().startsWith(replacementParent));
  }

  @Test
  void rejectsAPathThatNamesOnlyTheFilesystemRoot() {
    Path root = temporaryDirectory.getRoot();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ArtifactPublicationResult(
                    root,
                    new ArtifactPublicationRetention(
                        temporaryDirectory.resolve(".receipt-stage.tmp"))));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("must name an artifact in a parent directory"));
  }

  @Test
  void rejectsACapturedPathThatNamesOnlyTheFilesystemRoot() {
    Path root = temporaryDirectory.getRoot();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ArtifactPublicationResult.restoreCapturedCanonicalPaths(
                    root,
                    new ArtifactPublicationRetention(
                        temporaryDirectory.resolve(".receipt-stage.tmp"))));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("must name an artifact in a parent directory"));
  }

  @Test
  void rejectsACapturedArtifactNameWithoutAParentDirectory() {
    Path fileName =
        Objects.requireNonNull(
            temporaryDirectory.resolve("receipt.fgar").getFileName(), "fixture file name");
    ClassLoader proxyClassLoader =
        Objects.requireNonNull(
            Thread.currentThread().getContextClassLoader(), "context class loader");
    Path pathWithoutParent =
        (Path)
            Proxy.newProxyInstance(
                proxyClassLoader,
                new Class<?>[] {Path.class},
                (proxy, method, ignored) ->
                    switch (method.getName()) {
                      case "toAbsolutePath", "normalize" -> proxy;
                      case "getFileName" -> fileName;
                      case "getParent" -> null;
                      default ->
                          throw new AssertionError(
                              "Unexpected Path invocation: " + method.getName());
                    });

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ArtifactPublicationResult.restoreCapturedCanonicalPaths(
                    pathWithoutParent,
                    new ArtifactPublicationRetention(
                        temporaryDirectory.resolve(".receipt-stage.tmp"))));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("must name an artifact in a parent directory"));
  }

  @Test
  void publicationResultUsesValueSemanticsForPublicationAndRetentionFacts() {
    Path publicationPath = temporaryDirectory.resolve("receipt.fgar");
    ArtifactPublicationRetention retention =
        new ArtifactPublicationRetention(temporaryDirectory.resolve(".receipt-stage.tmp"));
    ArtifactPublicationResult publication =
        ArtifactPublicationResult.restoreCapturedCanonicalPaths(publicationPath, retention);
    ArtifactPublicationResult equivalent =
        ArtifactPublicationResult.restoreCapturedCanonicalPaths(publicationPath, retention);
    ArtifactPublicationResult differentArtifact =
        ArtifactPublicationResult.restoreCapturedCanonicalPaths(
            temporaryDirectory.resolve("other-receipt.fgar"), retention);
    ArtifactPublicationResult differentRetention =
        ArtifactPublicationResult.restoreCapturedCanonicalPaths(
            publicationPath,
            new ArtifactPublicationRetention(temporaryDirectory.resolve(".other-stage.tmp")));

    assertEquals(publication, publication);
    assertEquals(publication, equivalent);
    assertEquals(publication.hashCode(), equivalent.hashCode());
    assertNotEquals(publication, differentArtifact);
    assertNotEquals(publication, differentRetention);
    assertNotEquals(publication, "not a publication result");
    assertEquals(
        "ArtifactPublicationResult[publishedArtifactPath="
            + publication.publishedArtifactPath()
            + ", retention="
            + publication.retention()
            + "]",
        publication.toString());
  }
}
