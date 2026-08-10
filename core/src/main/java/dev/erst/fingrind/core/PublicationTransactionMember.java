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
    PublicationMode publicationMode,
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
    Objects.requireNonNull(publicationMode, "publicationMode");
    Objects.requireNonNull(progress, "progress");
    Objects.requireNonNull(stagedArtifact, "stagedArtifact");
    Objects.requireNonNull(finalizedArtifact, "finalizedArtifact");
    if (progress == PublicationTransactionMemberProgress.PLANNED) {
      requireArtifacts(stagedArtifact, finalizedArtifact, false, false);
    } else if (progress == PublicationTransactionMemberProgress.STAGED) {
      requireArtifacts(stagedArtifact, finalizedArtifact, true, false);
    } else {
      requireArtifacts(stagedArtifact, finalizedArtifact, true, true);
    }
  }

  private static void requireArtifacts(
      Optional<PublicationTransactionStagedArtifact> stagedArtifact,
      Optional<PublicationTransactionFinalizedArtifact> finalizedArtifact,
      boolean stagedRequired,
      boolean finalizedRequired) {
    if (stagedArtifact.isPresent() != stagedRequired
        || finalizedArtifact.isPresent() != finalizedRequired) {
      throw new IllegalArgumentException(
          "Member progress must match its recorded staged and finalized artifacts.");
    }
  }
}
