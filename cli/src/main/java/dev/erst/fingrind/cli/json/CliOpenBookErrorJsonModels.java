package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import dev.erst.fingrind.cli.json.CliBookInspectionJsonModels.BookIdentityPayload;
import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels.PublicationTransactionIncompleteDetails;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Open-book uncertainty error details emitted by the CLI. */
public interface CliOpenBookErrorJsonModels {
  /** Closed detail family for protected-book initialization recovery facts. */
  sealed interface OpenBookErrorDetails extends CliErrorJsonModels.ErrorDetails
      permits OpenBookPreparationArtifactsRetainedDetails,
          OpenBookPublicationProgressDetails,
          OpenBookCompletionUncertainDetails {}

  /** Artifacts deliberately retained after book opening did not complete. */
  record OpenBookPreparationArtifactsRetainedDetails(
      List<RetainedOpenBookPreparationArtifact> retainedArtifacts) implements OpenBookErrorDetails {
    public OpenBookPreparationArtifactsRetainedDetails {
      retainedArtifacts = copyList(retainedArtifacts, "retainedArtifacts");
      if (retainedArtifacts.isEmpty()) {
        throw new IllegalArgumentException("retainedArtifacts must not be empty.");
      }
      Set<String> paths = new HashSet<>();
      for (RetainedOpenBookPreparationArtifact artifact : retainedArtifacts) {
        if (!paths.add(artifact.path())) {
          throw new IllegalArgumentException("retainedArtifacts must not repeat an artifact path.");
        }
      }
    }
  }

  /** One canonical artifact retained after an incomplete book-opening attempt. */
  record RetainedOpenBookPreparationArtifact(
      String role, String path, @Nullable String retainedStage) {
    public RetainedOpenBookPreparationArtifact {
      role = requireOpenBookPreparationArtifactRole(role);
      path = CliJsonModelValidation.requireText(path, "path");
      retainedStage = CliJsonModelValidation.requireOptionalText(retainedStage, "retainedStage");
    }
  }

  /** Founder-key publication facts recorded before the open-book operation stopped. */
  record OpenBookPublicationProgressDetails(
      List<CliEnvelopeJsonModels.SuccessArtifact> publishedFounderKeyArtifacts,
      @Nullable PublicationTransactionIncompleteDetails incompleteFounderKeyPublication)
      implements OpenBookErrorDetails {
    public OpenBookPublicationProgressDetails {
      publishedFounderKeyArtifacts =
          copyList(publishedFounderKeyArtifacts, "publishedFounderKeyArtifacts");
      Set<String> paths = new HashSet<>();
      for (CliEnvelopeJsonModels.SuccessArtifact artifact : publishedFounderKeyArtifacts) {
        if (!paths.add(artifact.path())) {
          throw new IllegalArgumentException(
              "publishedFounderKeyArtifacts must not repeat an artifact path.");
        }
      }
      if (incompleteFounderKeyPublication == null && publishedFounderKeyArtifacts.isEmpty()) {
        throw new IllegalArgumentException(
            "Open-book publication progress requires a completed or incomplete publication.");
      }
      if (incompleteFounderKeyPublication != null
          && !paths.add(incompleteFounderKeyPublication.candidateArtifact())) {
        throw new IllegalArgumentException(
            "incompleteFounderKeyPublication must not repeat a completed founder-key path.");
      }
    }
  }

  /** Returned opening facts whose durable completion SQLite could not confirm. */
  record OpenBookCompletionUncertainDetails(
      String bookFile,
      String initializedAt,
      BookIdentityPayload bookIdentity,
      String attestationBookId,
      AttestationCommitPayload attestationCommit,
      ReportedAttestationTrustRoot attestationTrustRoot,
      List<CliEnvelopeJsonModels.SuccessArtifact> publishedFounderKeyArtifacts,
      List<RetainedOpenBookPreparationArtifact> retainedBookArtifacts)
      implements OpenBookErrorDetails {
    public OpenBookCompletionUncertainDetails {
      bookFile = CliJsonModelValidation.requireText(bookFile, "bookFile");
      String canonicalBookFile = bookFile;
      initializedAt = CliJsonModelValidation.requireText(initializedAt, "initializedAt");
      java.util.Objects.requireNonNull(bookIdentity, "bookIdentity");
      attestationBookId =
          CliJsonModelValidation.requireText(attestationBookId, "attestationBookId");
      java.util.Objects.requireNonNull(attestationCommit, "attestationCommit");
      java.util.Objects.requireNonNull(attestationTrustRoot, "attestationTrustRoot");
      if (!attestationBookId.equals(attestationTrustRoot.bookId())) {
        throw new IllegalArgumentException(
            "attestationBookId must equal the reported trust-root bookId.");
      }
      if (!attestationCommit.equals(attestationTrustRoot.attestationCommit())) {
        throw new IllegalArgumentException(
            "attestationCommit must equal the reported trust-root operation reference.");
      }
      publishedFounderKeyArtifacts =
          copyList(publishedFounderKeyArtifacts, "publishedFounderKeyArtifacts");
      retainedBookArtifacts = copyList(retainedBookArtifacts, "retainedBookArtifacts");
      if (retainedBookArtifacts.isEmpty()) {
        throw new IllegalArgumentException("retainedBookArtifacts must not be empty.");
      }
      Set<String> possibleBookArtifactPaths = new HashSet<>();
      for (RetainedOpenBookPreparationArtifact artifact : retainedBookArtifacts) {
        if (!possibleBookArtifactPaths.add(artifact.path())) {
          throw new IllegalArgumentException(
              "retainedBookArtifacts must not repeat an artifact path.");
        }
      }
      Set<String> founderKeyPaths = new HashSet<>();
      for (CliEnvelopeJsonModels.SuccessArtifact founderKey : publishedFounderKeyArtifacts) {
        if (!founderKeyPaths.add(founderKey.path())) {
          throw new IllegalArgumentException(
              "publishedFounderKeyArtifacts must not repeat an artifact path.");
        }
      }
      if (retainedBookArtifacts.stream()
          .noneMatch(
              artifact ->
                  "book-file".equals(artifact.role())
                      && artifact.path().equals(canonicalBookFile))) {
        throw new IllegalArgumentException(
            "retainedBookArtifacts must identify the canonical book-file path.");
      }
    }
  }

  /** Full genesis trust-root facts returned before durable completion became uncertain. */
  record ReportedAttestationTrustRoot(
      String bookId,
      AttestationCommitPayload attestationCommit,
      CliAttestationJsonModels.AttestationRegistryPayload registry) {
    public ReportedAttestationTrustRoot {
      bookId = CliJsonModelValidation.requireText(bookId, "bookId");
      java.util.Objects.requireNonNull(attestationCommit, "attestationCommit");
      java.util.Objects.requireNonNull(registry, "registry");
    }
  }

  private static String requireOpenBookPreparationArtifactRole(String role) {
    String checkedRole = CliJsonModelValidation.requireText(role, "role");
    return switch (checkedRole) {
      case "attestation-founder-key",
          "attestation-founder-key-stage",
          "book-file",
          "book-sidecar" ->
          checkedRole;
      default ->
          throw new IllegalArgumentException(
              "role must identify a current book-initialization preparation artifact role.");
    };
  }
}
