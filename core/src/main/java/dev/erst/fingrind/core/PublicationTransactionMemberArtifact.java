package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** Identifies one completed journal member without re-exposing its private staging evidence. */
public record PublicationTransactionMemberArtifact(
    String memberId,
    PublicationTransactionMemberRole role,
    PublicationTransactionArtifact artifact) {
  private static final Pattern MEMBER_ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");

  /** Requires one stable member identity, its declared role, and a completed final artifact. */
  public PublicationTransactionMemberArtifact {
    Objects.requireNonNull(memberId, "memberId");
    if (!MEMBER_ID.matcher(memberId).matches()) {
      throw new IllegalArgumentException(
          "memberId must contain one lowercase hyphenated identifier of at most 64 characters.");
    }
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(artifact, "artifact");
  }
}
