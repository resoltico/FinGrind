package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.PostingId;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Owns exact posting-query fact invariants and current attestation-commitment projection. */
final class BookWorkflowPostingAttestationFactProjection {
  private BookWorkflowPostingAttestationFactProjection() {}

  static List<BookWorkflowFact> hydrate(
      List<BookWorkflowFact> facts,
      Map<PostingId, AttestationCommit> commitments,
      Set<PostingId> freshPostingIds,
      AttestationCommit aggregateCommit) {
    PostingId postingId = postingIdFrom(facts);
    AttestationCommit commitment = commitments.get(postingId);
    AttestationCommit priorCommitment = existingCommitment(facts);
    requireFreshPostingCommitment(postingId, commitment, freshPostingIds, aggregateCommit);
    requireExistingCommitmentPreserved(priorCommitment, commitment);
    return replaceCommitmentFacts(facts, commitment);
  }

  static boolean idempotentReplayFrom(List<BookWorkflowFact> facts) {
    Boolean idempotentReplay = null;
    for (BookWorkflowFact fact : facts) {
      if (fact instanceof BookWorkflowFact.Flag flag && "idempotentReplay".equals(flag.name())) {
        if (idempotentReplay != null) {
          throw new IllegalStateException(
              "Post-entry workflow facts must contain exactly one idempotentReplay flag.");
        }
        idempotentReplay = flag.value();
      }
    }
    if (idempotentReplay == null) {
      throw new IllegalStateException(
          "Post-entry workflow facts must contain one idempotentReplay flag.");
    }
    return idempotentReplay;
  }

  static PostingId postingIdFrom(List<BookWorkflowFact> facts) {
    String postingId = null;
    for (BookWorkflowFact fact : facts) {
      if (fact instanceof BookWorkflowFact.Text text && "postingId".equals(text.name())) {
        if (postingId != null) {
          throw new IllegalStateException(
              "Posting-query facts must contain exactly one postingId.");
        }
        postingId = text.value();
      }
    }
    if (postingId == null) {
      throw new IllegalStateException("Posting-query facts must contain one postingId.");
    }
    return new PostingId(postingId);
  }

  private static void requireFreshPostingCommitment(
      PostingId postingId,
      @Nullable AttestationCommit commitment,
      Set<PostingId> freshPostingIds,
      AttestationCommit aggregateCommit) {
    if (!freshPostingIds.contains(postingId)) {
      return;
    }
    if (!Objects.equals(aggregateCommit, commitment)) {
      throw new IllegalStateException(
          "A newly committed plan posting must resolve to the aggregate attestation commitment.");
    }
  }

  private static void requireExistingCommitmentPreserved(
      @Nullable AttestationCommit priorCommitment, @Nullable AttestationCommit commitment) {
    if (priorCommitment != null && !priorCommitment.equals(commitment)) {
      throw new IllegalStateException(
          "An existing posting-query attestation commitment must remain unchanged after plan aggregation.");
    }
  }

  private static List<BookWorkflowFact> replaceCommitmentFacts(
      List<BookWorkflowFact> facts, @Nullable AttestationCommit commitment) {
    List<BookWorkflowFact> hydratedFacts =
        new ArrayList<>(facts.size() + (commitment == null ? 0 : 1));
    boolean recordedAtFound = copyFactsWithCurrentCommitment(facts, commitment, hydratedFacts);
    requireRecordedAt(recordedAtFound);
    return List.copyOf(hydratedFacts);
  }

  private static boolean copyFactsWithCurrentCommitment(
      List<BookWorkflowFact> facts,
      @Nullable AttestationCommit commitment,
      List<BookWorkflowFact> hydratedFacts) {
    boolean recordedAtFound = false;
    for (BookWorkflowFact fact : facts) {
      if (isAttestationCommitmentGroup(fact)) {
        continue;
      }
      hydratedFacts.add(fact);
      if (isRecordedAtFact(fact)) {
        recordedAtFound = true;
        appendCommitmentIfPresent(commitment, hydratedFacts);
      }
    }
    return recordedAtFound;
  }

  private static boolean isAttestationCommitmentGroup(BookWorkflowFact fact) {
    return fact instanceof BookWorkflowFact.Group group && "attestationCommit".equals(group.name());
  }

  private static boolean isRecordedAtFact(BookWorkflowFact fact) {
    return fact instanceof BookWorkflowFact.Text text && "recordedAt".equals(text.name());
  }

  private static void appendCommitmentIfPresent(
      @Nullable AttestationCommit commitment, List<BookWorkflowFact> hydratedFacts) {
    if (commitment != null) {
      hydratedFacts.add(attestationCommitFact(commitment));
    }
  }

  private static void requireRecordedAt(boolean recordedAtFound) {
    if (!recordedAtFound) {
      throw new IllegalStateException(
          "Posting-query facts must contain recordedAt before attestation commitments can be projected.");
    }
  }

  private static @Nullable AttestationCommit existingCommitment(List<BookWorkflowFact> facts) {
    AttestationCommit commitment = null;
    for (BookWorkflowFact fact : facts) {
      if (fact instanceof BookWorkflowFact.Group group
          && "attestationCommit".equals(group.name())) {
        if (commitment != null) {
          throw new IllegalStateException(
              "Posting-query facts must contain at most one attestationCommit group.");
        }
        commitment = commitmentFrom(group.facts());
      }
    }
    return commitment;
  }

  private static AttestationCommit commitmentFrom(List<BookWorkflowFact> facts) {
    AttestationCommitmentFactReader reader = new AttestationCommitmentFactReader();
    for (BookWorkflowFact fact : facts) {
      reader.accept(fact);
    }
    return reader.toCommitment();
  }

  private static BookWorkflowFact.Group attestationCommitFact(AttestationCommit commitment) {
    return BookWorkflowFact.group(
        "attestationCommit",
        List.of(
            BookWorkflowFact.text("operationOrder", commitment.operationOrder().toString()),
            BookWorkflowFact.text("operationHead", commitment.operationHeadHex())));
  }

  /** Accumulates the two exact text fields that encode one posting attestation commitment. */
  private static final class AttestationCommitmentFactReader {
    private @Nullable String operationOrder;
    private @Nullable String operationHead;

    void accept(BookWorkflowFact fact) {
      if (!(fact instanceof BookWorkflowFact.Text text)) {
        throw new IllegalStateException(
            "Posting-query attestationCommit facts must use text-valued fields.");
      }
      acceptText(text);
    }

    AttestationCommit toCommitment() {
      String requiredOrder = requirePresent(operationOrder);
      String requiredHead = requirePresent(operationHead);
      try {
        return new AttestationCommit(new BigInteger(requiredOrder), requiredHead);
      } catch (IllegalArgumentException exception) {
        throw new IllegalStateException(
            "Posting-query attestationCommit facts must encode a valid commitment.", exception);
      }
    }

    private void acceptText(BookWorkflowFact.Text text) {
      switch (text.name()) {
        case "operationOrder" -> operationOrder = requireAbsent(operationOrder, text);
        case "operationHead" -> operationHead = requireAbsent(operationHead, text);
        default ->
            throw new IllegalStateException(
                "Posting-query attestationCommit facts contain an unknown field: " + text.name());
      }
    }

    private static String requireAbsent(@Nullable String priorValue, BookWorkflowFact.Text text) {
      if (priorValue != null) {
        throw new IllegalStateException(
            "Posting-query attestationCommit facts must contain one " + text.name() + ".");
      }
      return text.value();
    }

    private static String requirePresent(@Nullable String value) {
      if (value == null) {
        throw new IllegalStateException(
            "Posting-query attestationCommit facts must contain operationOrder and operationHead.");
      }
      return value;
    }
  }
}
