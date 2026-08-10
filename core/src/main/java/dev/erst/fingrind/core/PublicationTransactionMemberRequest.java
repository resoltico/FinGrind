package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Supplies one caller-owned secret byte sequence for transaction-owned publication. */
public final class PublicationTransactionMemberRequest {
  private final String memberId;
  private final PublicationTransactionMemberRole role;
  private final Path finalPath;
  private final PublicationMode publicationMode;
  private final byte[] secretBytes;

  /** Copies mutable secret input and normalizes the final artifact pathname. */
  public PublicationTransactionMemberRequest(
      String memberId,
      PublicationTransactionMemberRole role,
      Path finalPath,
      PublicationMode publicationMode,
      byte[] secretBytes) {
    this.memberId = Objects.requireNonNull(memberId, "memberId");
    if (!this.memberId.matches("[a-z][a-z0-9-]{0,63}")) {
      throw new IllegalArgumentException(
          "memberId must contain one lowercase hyphenated identifier of at most 64 characters.");
    }
    this.role = Objects.requireNonNull(role, "role");
    this.finalPath =
        PublicationTransactionStagedArtifact.normalizedArtifactPath(finalPath, "finalPath");
    this.publicationMode = Objects.requireNonNull(publicationMode, "publicationMode");
    this.secretBytes = Objects.requireNonNull(secretBytes, "secretBytes").clone();
  }

  /** Returns the stable journal member identifier. */
  public String memberId() {
    return memberId;
  }

  /** Returns the explicit secret-bearing artifact role. */
  public PublicationTransactionMemberRole role() {
    return role;
  }

  /** Returns the normalized final artifact pathname. */
  public Path finalPath() {
    return finalPath;
  }

  /** Returns the final-publication primitive authorized for this member. */
  public PublicationMode publicationMode() {
    return publicationMode;
  }

  /** Returns a defensive copy of the secret bytes that only the transaction may stage. */
  public byte[] secretBytes() {
    return secretBytes.clone();
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof PublicationTransactionMemberRequest request
            && memberId.equals(request.memberId)
            && role == request.role
            && finalPath.equals(request.finalPath)
            && publicationMode == request.publicationMode
            && Arrays.equals(secretBytes, request.secretBytes));
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(memberId, role, finalPath, publicationMode);
    return 31 * result + Arrays.hashCode(secretBytes);
  }

  @Override
  public String toString() {
    return "PublicationTransactionMemberRequest[memberId="
        + memberId
        + ", role="
        + role
        + ", finalPath="
        + finalPath
        + ", publicationMode="
        + publicationMode
        + ", secretBytes=<redacted>]";
  }
}
