package dev.erst.fingrind.core;

import java.util.List;
import java.util.Objects;

/** Explicit member replacement payload for one authenticated journal mutation. */
record PublicationTransactionJournalMembers(List<PublicationTransactionMember> members) {
  PublicationTransactionJournalMembers {
    members = List.copyOf(Objects.requireNonNull(members, "members"));
  }
}
