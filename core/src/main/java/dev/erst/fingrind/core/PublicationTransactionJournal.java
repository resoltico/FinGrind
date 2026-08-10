package dev.erst.fingrind.core;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable authenticated payload of one private publication transaction journal. */
record PublicationTransactionJournal(
    int schemaVersion,
    PublicationTransactionId transactionId,
    String nonceHex,
    String ownerKeyFingerprint,
    Instant createdAt,
    List<PublicationTransactionMember> members,
    List<PublicationTransactionTransition> transitions) {
  static final int CURRENT_SCHEMA_VERSION = 1;
  private static final Pattern NONCE_HEX = Pattern.compile("[0-9a-f]{32}");
  private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

  PublicationTransactionJournal {
    if (schemaVersion != CURRENT_SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Unsupported publication transaction journal schema version.");
    }
    Objects.requireNonNull(transactionId, "transactionId");
    Objects.requireNonNull(nonceHex, "nonceHex");
    if (!NONCE_HEX.matcher(nonceHex).matches()) {
      throw new IllegalArgumentException(
          "nonceHex must contain 32 lowercase hexadecimal characters.");
    }
    Objects.requireNonNull(ownerKeyFingerprint, "ownerKeyFingerprint");
    if (!SHA_256_HEX.matcher(ownerKeyFingerprint).matches()) {
      throw new IllegalArgumentException(
          "ownerKeyFingerprint must contain 64 lowercase hexadecimal characters.");
    }
    Objects.requireNonNull(createdAt, "createdAt");
    members = List.copyOf(Objects.requireNonNull(members, "members"));
    transitions = List.copyOf(Objects.requireNonNull(transitions, "transitions"));
    if (members.isEmpty()) {
      throw new IllegalArgumentException(
          "A publication transaction journal must own at least one member.");
    }
    requireDistinctMemberIds(members);
    requireTransitionSequence(transitions);
    requireCompleteJournalHasNoUncleanedMember(members, transitions);
  }

  static PublicationTransactionJournal prepared(
      PublicationTransactionId transactionId,
      String nonceHex,
      String ownerKeyFingerprint,
      Instant createdAt,
      List<PublicationTransactionMember> members) {
    Instant checkedCreatedAt = Objects.requireNonNull(createdAt, "createdAt");
    return new PublicationTransactionJournal(
        CURRENT_SCHEMA_VERSION,
        transactionId,
        nonceHex,
        ownerKeyFingerprint,
        checkedCreatedAt,
        members,
        List.of(PublicationTransactionTransition.prepared(checkedCreatedAt)));
  }

  PublicationTransactionState state() {
    return transitions.getLast().state();
  }

  PublicationTransactionJournal transition(PublicationTransactionTransition nextTransition) {
    return transition(nextTransition, members);
  }

  PublicationTransactionJournal transition(
      PublicationTransactionTransition nextTransition,
      List<PublicationTransactionMember> updatedMembers) {
    PublicationTransactionTransition checkedTransition =
        Objects.requireNonNull(nextTransition, "nextTransition");
    if (!state().permitsOrdinaryTransitionTo(checkedTransition.state())) {
      throw new IllegalArgumentException(
          "The publication transaction journal cannot make the requested ordinary state transition.");
    }
    List<PublicationTransactionMember> checkedMembers =
        requireValidMemberUpdate(updatedMembers, members);
    List<PublicationTransactionTransition> nextTransitions =
        java.util.stream.Stream.concat(
                transitions.stream(), java.util.stream.Stream.of(checkedTransition))
            .toList();
    return new PublicationTransactionJournal(
        schemaVersion,
        transactionId,
        nonceHex,
        ownerKeyFingerprint,
        createdAt,
        checkedMembers,
        nextTransitions);
  }

  PublicationTransactionJournal updateMembers(List<PublicationTransactionMember> updatedMembers) {
    if (state().terminal()) {
      throw new IllegalArgumentException(
          "A terminal publication transaction journal cannot update its members.");
    }
    return new PublicationTransactionJournal(
        schemaVersion,
        transactionId,
        nonceHex,
        ownerKeyFingerprint,
        createdAt,
        requireValidMemberUpdate(updatedMembers, members),
        transitions);
  }

  private static void requireDistinctMemberIds(List<PublicationTransactionMember> members) {
    Set<String> seen = new HashSet<>();
    for (PublicationTransactionMember member : members) {
      if (!seen.add(Objects.requireNonNull(member, "member").memberId())) {
        throw new IllegalArgumentException(
            "Publication transaction journal member ids must be distinct.");
      }
    }
  }

  private static List<PublicationTransactionMember> requireValidMemberUpdate(
      List<PublicationTransactionMember> updatedMembers,
      List<PublicationTransactionMember> priorMembers) {
    List<PublicationTransactionMember> checkedMembers =
        List.copyOf(Objects.requireNonNull(updatedMembers, "updatedMembers"));
    if (checkedMembers.size() != priorMembers.size()) {
      throw new IllegalArgumentException(
          "A publication transaction journal cannot add or remove members after preparation.");
    }
    for (int index = 0; index < priorMembers.size(); index++) {
      requireValidMemberUpdate(priorMembers.get(index), checkedMembers.get(index));
    }
    return checkedMembers;
  }

  private static void requireValidMemberUpdate(
      PublicationTransactionMember prior, PublicationTransactionMember updated) {
    PublicationTransactionMember checkedPrior = Objects.requireNonNull(prior, "prior member");
    PublicationTransactionMember checkedUpdated = Objects.requireNonNull(updated, "updated member");
    requireStablePlan(checkedPrior, checkedUpdated);
    requireForwardProgress(checkedPrior, checkedUpdated);
    requireImmutableEvidence(checkedPrior, checkedUpdated);
  }

  private static void requireStablePlan(
      PublicationTransactionMember prior, PublicationTransactionMember updated) {
    if (!prior.memberId().equals(updated.memberId())
        || prior.role() != updated.role()
        || !prior.finalPath().equals(updated.finalPath())
        || !prior.stagePath().equals(updated.stagePath())
        || !prior.physicalDirectoryIdentity().equals(updated.physicalDirectoryIdentity())
        || prior.publicationMode() != updated.publicationMode()) {
      throw new IllegalArgumentException(
          "A publication transaction member's planned identity cannot change after preparation.");
    }
  }

  private static void requireForwardProgress(
      PublicationTransactionMember prior, PublicationTransactionMember updated) {
    if (!updated.progress().canFollow(prior.progress())) {
      throw new IllegalArgumentException(
          "A publication transaction member cannot move backwards after its journaled progress.");
    }
  }

  private static void requireImmutableEvidence(
      PublicationTransactionMember prior, PublicationTransactionMember updated) {
    if (prior.stagedArtifact().isPresent()
        && !prior.stagedArtifact().equals(updated.stagedArtifact())) {
      throw new IllegalArgumentException(
          "A publication transaction member's recorded staged artifact cannot change.");
    }
    if (prior.finalizedArtifact().isPresent()
        && !prior.finalizedArtifact().equals(updated.finalizedArtifact())) {
      throw new IllegalArgumentException(
          "A publication transaction member's recorded finalized artifact cannot change.");
    }
  }

  private static void requireTransitionSequence(
      List<PublicationTransactionTransition> transitions) {
    if (transitions.isEmpty()) {
      throw new IllegalArgumentException(
          "A publication transaction journal must record its PREPARED transition.");
    }
    PublicationTransactionTransition firstTransition = transitions.getFirst();
    if (firstTransition.state() != PublicationTransactionState.PREPARED) {
      throw new IllegalArgumentException(
          "A publication transaction journal must begin with its PREPARED transition.");
    }
    PublicationTransactionState priorState = firstTransition.state();
    for (int index = 1; index < transitions.size(); index++) {
      PublicationTransactionTransition transition =
          Objects.requireNonNull(transitions.get(index), "transition");
      if (!priorState.permitsOrdinaryTransitionTo(transition.state())) {
        throw new IllegalArgumentException(
            "Publication transaction journal transitions must follow the durable state sequence.");
      }
      priorState = transition.state();
    }
  }

  private static void requireCompleteJournalHasNoUncleanedMember(
      List<PublicationTransactionMember> members,
      List<PublicationTransactionTransition> transitions) {
    if (transitions.getLast().state() == PublicationTransactionState.COMPLETE
        && members.stream()
            .anyMatch(
                member -> member.progress() != PublicationTransactionMemberProgress.CLEANED)) {
      throw new IllegalArgumentException(
          "A complete publication transaction journal cannot retain an uncleaned member.");
    }
  }
}
