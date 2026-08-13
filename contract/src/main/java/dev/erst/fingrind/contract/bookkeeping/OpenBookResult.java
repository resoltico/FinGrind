package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Closed result family for explicit book initialization. */
public sealed interface OpenBookResult permits OpenBookResult.Opened, OpenBookResult.Rejected {

  /** Success result for a newly initialized book. */
  record Opened(
      Instant initializedAt,
      BookIdentity bookIdentity,
      AttestationRegistryInspection attestationTrustRoot,
      AttestationCommit attestationCommit,
      List<PublicationTransactionArtifact> publishedFounderKeyArtifacts)
      implements OpenBookResult {
    /** Validates the initialization timestamp. */
    public Opened {
      Objects.requireNonNull(initializedAt, "initializedAt");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(attestationTrustRoot, "attestationTrustRoot");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
      publishedFounderKeyArtifacts =
          List.copyOf(
              Objects.requireNonNull(publishedFounderKeyArtifacts, "publishedFounderKeyArtifacts"));
      if (new LinkedHashSet<>(
                  publishedFounderKeyArtifacts.stream()
                      .map(PublicationTransactionArtifact::publishedArtifactPath)
                      .toList())
              .size()
          != publishedFounderKeyArtifacts.size()) {
        throw new IllegalArgumentException(
            "Published founder-key artifacts must not repeat an artifact path.");
      }
      if (!attestationTrustRoot.headOrder().equals(attestationCommit.operationOrder())
          || !attestationTrustRoot
              .operationHeadHex()
              .equals(attestationCommit.operationHeadHex())) {
        throw new IllegalArgumentException(
            "The book-opening attestation commitment must identify the published genesis trust root.");
      }
    }
  }

  /** Deterministic refusal for open-book. */
  record Rejected(BookAdministrationRejection rejection) implements OpenBookResult {
    /** Validates the deterministic rejection. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
