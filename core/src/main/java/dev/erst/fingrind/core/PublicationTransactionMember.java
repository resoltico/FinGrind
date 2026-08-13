package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Defines one secret-bearing artifact under the exclusive authority of one transaction journal. */
record PublicationTransactionMember(
    String memberId,
    PublicationTransactionMemberRole role,
    Path finalPath,
    Path stagePath,
    String physicalDirectoryIdentity,
    PublicationMode publicationMode,
    Optional<PublicationTransactionFinalizedArtifact> replacementTarget,
    PublicationTransactionMemberProgress progress,
    Optional<PublicationTransactionStagedArtifact> stagedArtifact,
    Optional<PublicationTransactionFinalizedArtifact> finalizedArtifact) {
  private static final Pattern MEMBER_ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");

  PublicationTransactionMember {
    Objects.requireNonNull(memberId, "memberId");
    if (!MEMBER_ID.matcher(memberId).matches()) {
      throw new IllegalArgumentException(
          "memberId must contain one lowercase hyphenated identifier of at most 64 characters.");
    }
    Objects.requireNonNull(role, "role");
    finalPath = PublicationTransactionStagedArtifact.normalizedArtifactPath(finalPath, "finalPath");
    stagePath = PublicationTransactionStagedArtifact.normalizedArtifactPath(stagePath, "stagePath");
    requireDistinctSiblingPaths(finalPath, stagePath);
    physicalDirectoryIdentity =
        PublicationTransactionStagedArtifact.requireNonBlank(
            physicalDirectoryIdentity, "physicalDirectoryIdentity");
    Objects.requireNonNull(publicationMode, "publicationMode");
    Objects.requireNonNull(replacementTarget, "replacementTarget");
    if (publicationMode == PublicationMode.NO_REPLACE_LINK && replacementTarget.isPresent()) {
      throw new IllegalArgumentException(
          "A no-replace publication transaction member cannot name a replacement target.");
    }
    Objects.requireNonNull(progress, "progress");
    Objects.requireNonNull(stagedArtifact, "stagedArtifact");
    Objects.requireNonNull(finalizedArtifact, "finalizedArtifact");
    if (progress == PublicationTransactionMemberProgress.PLANNED) {
      requireArtifacts(stagePath, stagedArtifact, finalizedArtifact, false, false);
    } else if (progress == PublicationTransactionMemberProgress.STAGED
        || progress == PublicationTransactionMemberProgress.ABORTED) {
      requireArtifacts(stagePath, stagedArtifact, finalizedArtifact, true, false);
    } else {
      requireArtifacts(stagePath, stagedArtifact, finalizedArtifact, true, true);
    }
  }

  PublicationTransactionMember(
      String memberId,
      PublicationTransactionMemberRole role,
      Path finalPath,
      Path stagePath,
      String physicalDirectoryIdentity,
      PublicationMode publicationMode,
      PublicationTransactionMemberProgress progress,
      Optional<PublicationTransactionStagedArtifact> stagedArtifact,
      Optional<PublicationTransactionFinalizedArtifact> finalizedArtifact) {
    this(
        memberId,
        role,
        finalPath,
        stagePath,
        physicalDirectoryIdentity,
        publicationMode,
        Optional.empty(),
        progress,
        stagedArtifact,
        finalizedArtifact);
  }

  private static void requireDistinctSiblingPaths(Path finalPath, Path stagePath) {
    Path finalParent = Objects.requireNonNull(finalPath.getParent(), "final artifact parent");
    Path stageParent = Objects.requireNonNull(stagePath.getParent(), "stage artifact parent");
    if (!finalParent.equals(stageParent)) {
      throw new IllegalArgumentException(
          "A transaction member stage and final artifact must share one canonical parent directory.");
    }
    if (finalPath.equals(stagePath)) {
      throw new IllegalArgumentException(
          "A transaction member stage and final artifact must name distinct paths.");
    }
  }

  private static void requireArtifacts(
      Path stagePath,
      Optional<PublicationTransactionStagedArtifact> stagedArtifact,
      Optional<PublicationTransactionFinalizedArtifact> finalizedArtifact,
      boolean stagedRequired,
      boolean finalizedRequired) {
    if (stagedArtifact.isPresent() != stagedRequired
        || finalizedArtifact.isPresent() != finalizedRequired) {
      throw new IllegalArgumentException(
          "Member progress must match its recorded staged and finalized artifacts.");
    }
    if (stagedArtifact.isPresent() && !stagedArtifact.orElseThrow().stagePath().equals(stagePath)) {
      throw new IllegalArgumentException(
          "A staged artifact must use the transaction member's planned stage path.");
    }
  }
}
