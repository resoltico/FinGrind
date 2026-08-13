package dev.erst.fingrind.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports an ID-only recovery attempt and, only on complete success, its immutable finals. */
public record PublicationTransactionRecoveryReceipt(
    PublicationTransactionResult transactionResult,
    List<PublicationTransactionMemberArtifact> publishedArtifacts) {
  /** Requires every reported final to belong to the recovered complete transaction. */
  public PublicationTransactionRecoveryReceipt {
    Objects.requireNonNull(transactionResult, "transactionResult");
    publishedArtifacts =
        List.copyOf(Objects.requireNonNull(publishedArtifacts, "publishedArtifacts"));
    if (!transactionResult.successful() && !publishedArtifacts.isEmpty()) {
      throw new IllegalArgumentException(
          "An incomplete publication transaction cannot report published artifacts.");
    }
    if (transactionResult.successful() && publishedArtifacts.isEmpty()) {
      throw new IllegalArgumentException(
          "A complete publication transaction must report every published artifact.");
    }
    Set<String> memberIds = new HashSet<>();
    for (PublicationTransactionMemberArtifact publishedArtifact : publishedArtifacts) {
      PublicationTransactionMemberArtifact checkedArtifact =
          Objects.requireNonNull(publishedArtifact, "publishedArtifact");
      if (!memberIds.add(checkedArtifact.memberId())) {
        throw new IllegalArgumentException(
            "A publication transaction recovery receipt cannot repeat a member identifier.");
      }
      if (!transactionResult.equals(checkedArtifact.artifact().transactionResult())) {
        throw new IllegalArgumentException(
            "A publication transaction recovery receipt artifact must belong to its transaction result.");
      }
    }
  }
}
