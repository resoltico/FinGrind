package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Open-book failure facts, isolated from general artifact-publication and format failures. */
public final class OpenBookFailureDetails {
  private OpenBookFailureDetails() {}

  /** Creates one normalized retained-artifact fact for an incomplete opening attempt. */
  public static RetainedOpenBookPreparationArtifact retainedArtifact(
      OpenBookPreparationArtifactRole role,
      Path path,
      @Nullable ArtifactPublicationRetention retainedStage) {
    return new RetainedOpenBookPreparationArtifact(role, path, retainedStage);
  }

  /** Artifacts intentionally retained after a book-opening attempt did not complete. */
  public record OpenBookPreparationArtifactsRetained(
      List<RetainedOpenBookPreparationArtifact> retainedArtifacts)
      implements ContractFailureDetails {
    /** Validates the non-empty, canonical retained facts that an operator must preserve. */
    public OpenBookPreparationArtifactsRetained {
      retainedArtifacts =
          List.copyOf(Objects.requireNonNull(retainedArtifacts, "retainedArtifacts"));
      if (retainedArtifacts.isEmpty()) {
        throw new IllegalArgumentException(
            "Open-book preparation retention details require at least one artifact.");
      }
      Set<Path> publicationPaths = new LinkedHashSet<>();
      for (RetainedOpenBookPreparationArtifact artifact : retainedArtifacts) {
        RetainedOpenBookPreparationArtifact checkedArtifact =
            Objects.requireNonNull(artifact, "retainedArtifacts element");
        if (!publicationPaths.add(checkedArtifact.path())) {
          throw new IllegalArgumentException(
              "Open-book preparation retention details must not repeat an artifact path.");
        }
      }
    }
  }

  /** One canonical artifact path intentionally retained after book opening did not complete. */
  public record RetainedOpenBookPreparationArtifact(
      OpenBookPreparationArtifactRole role,
      Path path,
      @Nullable ArtifactPublicationRetention retainedStage) {
    /** Normalizes a public retained-artifact location without claiming its later state. */
    public RetainedOpenBookPreparationArtifact {
      Objects.requireNonNull(role, "role");
      path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    /** Converts one founder-key publication into its retained opening-artifact fact. */
    public static RetainedOpenBookPreparationArtifact founderKey(
        ArtifactPublicationResult publication) {
      ArtifactPublicationResult checkedPublication =
          Objects.requireNonNull(publication, "publication");
      return new RetainedOpenBookPreparationArtifact(
          OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY,
          checkedPublication.publishedArtifactPath(),
          checkedPublication.retention());
    }
  }

  /** Stable roles for filesystem artifacts retained after book opening did not complete. */
  public enum OpenBookPreparationArtifactRole {
    ATTESTATION_FOUNDER_KEY("attestation-founder-key"),
    ATTESTATION_FOUNDER_KEY_STAGE("attestation-founder-key-stage"),
    BOOK_FILE("book-file"),
    BOOK_SIDECAR("book-sidecar");

    private final String wireRole;

    OpenBookPreparationArtifactRole(String wireRole) {
      this.wireRole = wireRole;
    }

    /** Returns the stable public role of this uncertain artifact. */
    public String wireRole() {
      return wireRole;
    }
  }

  /** Facts returned before SQLite could confirm durable initialization completion. */
  public record OpenBookCompletionUncertain(
      Path bookFilePath,
      java.time.Instant initializedAt,
      BookIdentity bookIdentity,
      AttestationRegistryInspection reportedAttestationTrustRoot,
      AttestationCommit reportedAttestationCommit,
      List<ArtifactPublicationResult> retainedFounderKeyArtifacts,
      List<RetainedOpenBookPreparationArtifact> retainedBookArtifacts)
      implements ContractFailureDetails {
    /**
     * Preserves every opening fact without claiming that SQLite durably completed initialization.
     */
    public OpenBookCompletionUncertain {
      Path canonicalBookFilePath =
          Objects.requireNonNull(bookFilePath, "bookFilePath").toAbsolutePath().normalize();
      bookFilePath = canonicalBookFilePath;
      Objects.requireNonNull(initializedAt, "initializedAt");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(reportedAttestationTrustRoot, "reportedAttestationTrustRoot");
      Objects.requireNonNull(reportedAttestationCommit, "reportedAttestationCommit");
      retainedFounderKeyArtifacts =
          List.copyOf(
              Objects.requireNonNull(retainedFounderKeyArtifacts, "retainedFounderKeyArtifacts"));
      retainedBookArtifacts =
          List.copyOf(Objects.requireNonNull(retainedBookArtifacts, "retainedBookArtifacts"));
      if (retainedBookArtifacts.isEmpty()) {
        throw new IllegalArgumentException(
            "Open-book completion uncertainty requires at least the canonical retained book artifact.");
      }
      Set<Path> possibleBookArtifactPaths = new LinkedHashSet<>();
      for (RetainedOpenBookPreparationArtifact artifact : retainedBookArtifacts) {
        RetainedOpenBookPreparationArtifact checkedArtifact =
            Objects.requireNonNull(artifact, "retainedBookArtifacts element");
        if (!possibleBookArtifactPaths.add(checkedArtifact.path())) {
          throw new IllegalArgumentException(
              "Open-book completion uncertainty must not repeat a retained book artifact path.");
        }
      }
      if (!retainedBookArtifacts.stream()
          .anyMatch(
              artifact ->
                  artifact.role() == OpenBookPreparationArtifactRole.BOOK_FILE
                      && artifact.path().equals(canonicalBookFilePath))) {
        throw new IllegalArgumentException(
            "Open-book completion uncertainty must identify the canonical book-file artifact.");
      }
      if (!reportedAttestationTrustRoot
              .headOrder()
              .equals(reportedAttestationCommit.operationOrder())
          || !reportedAttestationTrustRoot
              .operationHeadHex()
              .equals(reportedAttestationCommit.operationHeadHex())) {
        throw new IllegalArgumentException(
            "Reported completion commitment must identify the reported attestation trust root.");
      }
      Set<Path> founderKeyPaths = new LinkedHashSet<>();
      for (ArtifactPublicationResult founderKey : retainedFounderKeyArtifacts) {
        ArtifactPublicationResult checkedFounderKey =
            Objects.requireNonNull(founderKey, "retainedFounderKeyArtifacts element");
        if (!founderKeyPaths.add(checkedFounderKey.publishedArtifactPath())) {
          throw new IllegalArgumentException(
              "Open-book completion uncertainty must not repeat a founder-key artifact.");
        }
      }
    }
  }
}
