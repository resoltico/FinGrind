package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Supplies one caller-owned secret payload for transaction-owned publication. */
public final class PublicationTransactionMemberRequest {
  private final String memberId;
  private final PublicationTransactionMemberRole role;
  private final Path finalPath;
  private final PublicationMode publicationMode;
  private final byte @Nullable [] secretBytes;
  private final @Nullable Path privateSourcePath;

  /** Copies mutable secret input and normalizes the final artifact pathname. */
  public PublicationTransactionMemberRequest(
      String memberId,
      PublicationTransactionMemberRole role,
      Path finalPath,
      PublicationMode publicationMode,
      byte[] secretBytes) {
    this.memberId = requireMemberId(memberId);
    this.role = Objects.requireNonNull(role, "role");
    this.finalPath =
        PublicationTransactionStagedArtifact.normalizedArtifactPath(finalPath, "finalPath");
    this.publicationMode = Objects.requireNonNull(publicationMode, "publicationMode");
    this.secretBytes = Objects.requireNonNull(secretBytes, "secretBytes").clone();
    this.privateSourcePath = null;
  }

  /**
   * Supplies one existing owner-only regular source file for exact transaction-owned staging.
   *
   * <p>The source is admitted immediately before its bytes are copied through the retained
   * no-follow channel. The source path is never journaled, returned as a recovery handle, or
   * rendered by this request.
   */
  public static PublicationTransactionMemberRequest fromPrivateSource(
      String memberId,
      PublicationTransactionMemberRole role,
      Path finalPath,
      PublicationMode publicationMode,
      Path privateSourcePath) {
    return new PublicationTransactionMemberRequest(
        memberId, role, finalPath, publicationMode, privateSourcePath);
  }

  /**
   * Reserves one transaction-owned stage for a producer that must write its secret directly.
   *
   * <p>Only {@link PublicationTransactionPublisher#reserveStages(PublicationTransactionRequest)}
   * accepts this member shape. The returned reservation is an in-process producer capability, not a
   * recovery handle: callers must never render or persist its private stage pathname.
   */
  public static PublicationTransactionMemberRequest reserveStage(
      String memberId,
      PublicationTransactionMemberRole role,
      Path finalPath,
      PublicationMode publicationMode) {
    return new PublicationTransactionMemberRequest(memberId, role, finalPath, publicationMode);
  }

  private PublicationTransactionMemberRequest(
      String memberId,
      PublicationTransactionMemberRole role,
      Path finalPath,
      PublicationMode publicationMode,
      Path privateSourcePath) {
    this.memberId = requireMemberId(memberId);
    this.role = Objects.requireNonNull(role, "role");
    this.finalPath =
        PublicationTransactionStagedArtifact.normalizedArtifactPath(finalPath, "finalPath");
    this.publicationMode = Objects.requireNonNull(publicationMode, "publicationMode");
    this.secretBytes = null;
    this.privateSourcePath =
        PublicationTransactionStagedArtifact.normalizedArtifactPath(
            privateSourcePath, "privateSourcePath");
    if (this.finalPath.equals(this.privateSourcePath)) {
      throw new IllegalArgumentException(
          "privateSourcePath must not name the final artifact path of the same transaction member.");
    }
  }

  private PublicationTransactionMemberRequest(
      String memberId,
      PublicationTransactionMemberRole role,
      Path finalPath,
      PublicationMode publicationMode) {
    this.memberId = requireMemberId(memberId);
    this.role = Objects.requireNonNull(role, "role");
    this.finalPath =
        PublicationTransactionStagedArtifact.normalizedArtifactPath(finalPath, "finalPath");
    this.publicationMode = Objects.requireNonNull(publicationMode, "publicationMode");
    this.secretBytes = null;
    this.privateSourcePath = null;
  }

  private static String requireMemberId(String memberId) {
    String checkedMemberId = Objects.requireNonNull(memberId, "memberId");
    if (!checkedMemberId.matches("[a-z][a-z0-9-]{0,63}")) {
      throw new IllegalArgumentException(
          "memberId must contain one lowercase hyphenated identifier of at most 64 characters.");
    }
    return checkedMemberId;
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

  /** Returns a defensive copy of the in-memory secret that only transaction staging may consume. */
  byte[] secretBytesForStaging() {
    if (secretBytes == null) {
      throw new IllegalStateException(
          "This publication member stages an admitted private source file.");
    }
    return secretBytes.clone();
  }

  /** Returns the normalized private source that only transaction staging may admit and copy. */
  Path privateSourcePathForStaging() {
    if (privateSourcePath == null) {
      throw new IllegalStateException(
          "This publication member stages an in-memory secret payload.");
    }
    return privateSourcePath;
  }

  /** Returns whether transaction staging must admit and copy an existing private source file. */
  boolean hasPrivateSource() {
    return privateSourcePath != null;
  }

  /** Returns whether a transaction reservation, rather than this value, owns staging input. */
  boolean reservesStage() {
    return secretBytes == null && privateSourcePath == null;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof PublicationTransactionMemberRequest request
            && memberId.equals(request.memberId)
            && role == request.role
            && finalPath.equals(request.finalPath)
            && publicationMode == request.publicationMode
            && Arrays.equals(secretBytes, request.secretBytes)
            && Objects.equals(privateSourcePath, request.privateSourcePath));
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(memberId, role, finalPath, publicationMode, privateSourcePath);
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
        + ", secretPayload=<redacted>]";
  }
}
