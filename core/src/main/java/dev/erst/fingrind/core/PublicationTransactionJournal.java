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
    PublicationTransactionTransition checkedTransition =
        Objects.requireNonNull(nextTransition, "nextTransition");
    if (!state().permitsOrdinaryTransitionTo(checkedTransition.state())) {
      throw new IllegalArgumentException(
          "The publication transaction journal cannot make the requested ordinary state transition.");
    }
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
        members,
        nextTransitions);
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
}
