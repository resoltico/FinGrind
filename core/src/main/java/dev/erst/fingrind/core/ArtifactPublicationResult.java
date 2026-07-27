package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Records the canonical physical final artifact and its deliberately retained private stage.
 *
 * <p>Normal construction admits a live filesystem path and resolves its parent so aliases cannot
 * confuse publication facts. {@link #restoreCapturedCanonicalPaths(Path,
 * ArtifactPublicationRetention)} is deliberately separate: it restores a fact captured by an
 * earlier live admission and must not reinterpret it against a later filesystem state.
 */
public final class ArtifactPublicationResult {
  private final Path publishedArtifactPath;
  private final ArtifactPublicationRetention retention;

  /** Admits and canonicalizes one current publication fact against the live filesystem. */
  public ArtifactPublicationResult(
      Path publishedArtifactPath, ArtifactPublicationRetention retention) {
    Path canonicalPublishedArtifactPath =
        canonicalPublicationPath(publishedArtifactPath, "publishedArtifactPath");
    ArtifactPublicationRetention canonicalRetention = canonicalRetention(retention);
    requireSharedParent(canonicalPublishedArtifactPath, canonicalRetention);
    requireDistinctArtifactAndStage(canonicalPublishedArtifactPath, canonicalRetention);
    this.publishedArtifactPath = canonicalPublishedArtifactPath;
    this.retention = canonicalRetention;
  }

  private ArtifactPublicationResult(CapturedPublicationPaths capturedPublicationPaths) {
    CapturedPublicationPaths checkedPaths =
        Objects.requireNonNull(capturedPublicationPaths, "capturedPublicationPaths");
    this.publishedArtifactPath = checkedPaths.publishedArtifactPath();
    this.retention = checkedPaths.retention();
  }

  /**
   * Restores publication paths that were captured after a successful live canonicalization.
   *
   * <p>This method intentionally performs lexical validation only. Exception deserialization is
   * evidence recovery, not a new filesystem admission: resolving the parent again could make a
   * recorded artifact appear under a replacement directory or make restoration fail after cleanup.
   */
  public static ArtifactPublicationResult restoreCapturedCanonicalPaths(
      Path publishedArtifactPath, ArtifactPublicationRetention retention) {
    Path capturedPublicationPath =
        capturedPublicationPath(publishedArtifactPath, "publishedArtifactPath");
    ArtifactPublicationRetention capturedRetention = capturedRetention(retention);
    requireSharedParent(capturedPublicationPath, capturedRetention);
    requireDistinctArtifactAndStage(capturedPublicationPath, capturedRetention);
    return new ArtifactPublicationResult(
        new CapturedPublicationPaths(capturedPublicationPath, capturedRetention));
  }

  /** Returns the canonical final artifact path captured for this publication. */
  public Path publishedArtifactPath() {
    return publishedArtifactPath;
  }

  /** Returns the deliberately retained private stage of this publication. */
  public ArtifactPublicationRetention retention() {
    return retention;
  }

  /**
   * Normalizes a publication name beneath its physical parent when that parent exists.
   *
   * <p>Result values are also used to describe synthetic, not-yet-materialized paths at contract
   * boundaries. Those keep their normalized lexical parent; an existing parent is always resolved
   * so aliases cannot make two distinct staging directories appear to be siblings.
   */
  private static Path canonicalPublicationPath(Path path, String parameterName) {
    Path normalizedPath = Objects.requireNonNull(path, parameterName).toAbsolutePath().normalize();
    Path fileName = normalizedPath.getFileName();
    if (fileName == null) {
      throw new IllegalArgumentException(
          parameterName + " must name an artifact in a parent directory.");
    }
    Path parent =
        Objects.requireNonNull(normalizedPath.getParent(), "normalized publication parent");
    return canonicalParent(parent, parameterName).resolve(fileName);
  }

  private static ArtifactPublicationRetention canonicalRetention(
      ArtifactPublicationRetention retention) {
    ArtifactPublicationRetention checkedRetention = Objects.requireNonNull(retention, "retention");
    return new ArtifactPublicationRetention(
        canonicalPublicationPath(
            checkedRetention.retainedStagePath(), "retention.retainedStagePath"));
  }

  private static Path capturedPublicationPath(Path path, String parameterName) {
    Path normalizedPath = Objects.requireNonNull(path, parameterName).toAbsolutePath().normalize();
    if (normalizedPath.getFileName() == null || normalizedPath.getParent() == null) {
      throw new IllegalArgumentException(
          parameterName + " must name an artifact in a parent directory.");
    }
    return normalizedPath;
  }

  private static ArtifactPublicationRetention capturedRetention(
      ArtifactPublicationRetention retention) {
    ArtifactPublicationRetention checkedRetention = Objects.requireNonNull(retention, "retention");
    return new ArtifactPublicationRetention(
        capturedPublicationPath(
            checkedRetention.retainedStagePath(), "retention.retainedStagePath"));
  }

  private static void requireSharedParent(
      Path publishedArtifactPath, ArtifactPublicationRetention retention) {
    Path publishedArtifactParent =
        Objects.requireNonNull(publishedArtifactPath.getParent(), "artifact parent");
    Path retainedStageParent =
        Objects.requireNonNull(retention.retainedStagePath().getParent(), "retained stage parent");
    if (!publishedArtifactParent.equals(retainedStageParent)) {
      throw new IllegalArgumentException(
          "A published artifact and its retained stage must share the same canonical parent"
              + " directory.");
    }
  }

  private static void requireDistinctArtifactAndStage(
      Path publishedArtifactPath, ArtifactPublicationRetention retention) {
    if (publishedArtifactPath.equals(retention.retainedStagePath())) {
      throw new IllegalArgumentException(
          "A published artifact and its retained stage must name distinct canonical paths.");
    }
  }

  private static Path canonicalParent(Path parent, String parameterName) {
    try {
      Path canonicalParent = parent.toRealPath();
      if (!Files.isDirectory(canonicalParent, LinkOption.NOFOLLOW_LINKS)) {
        throw new IllegalArgumentException(
            parameterName + " must resolve beneath an existing parent directory.");
      }
      return canonicalParent;
    } catch (NoSuchFileException exception) {
      return parent;
    } catch (IOException | SecurityException exception) {
      throw new IllegalArgumentException(
          parameterName + " must have a resolvable parent directory.", exception);
    }
  }

  private record CapturedPublicationPaths(
      Path publishedArtifactPath, ArtifactPublicationRetention retention) {
    private CapturedPublicationPaths {
      Objects.requireNonNull(publishedArtifactPath, "publishedArtifactPath");
      Objects.requireNonNull(retention, "retention");
    }
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof ArtifactPublicationResult result
            && publishedArtifactPath.equals(result.publishedArtifactPath)
            && retention.equals(result.retention));
  }

  @Override
  public int hashCode() {
    return Objects.hash(publishedArtifactPath, retention);
  }

  @Override
  public String toString() {
    return "ArtifactPublicationResult[publishedArtifactPath="
        + publishedArtifactPath
        + ", retention="
        + retention
        + "]";
  }
}
