package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Defines the complete secret-bearing member set owned by one publication transaction. */
public record PublicationTransactionRequest(
    List<PublicationTransactionMemberRequest> members,
    Optional<PublicationTransactionOwnerContext> ownerContext) {
  /** Creates a transaction with no higher-level automatic-recovery lookup context. */
  public PublicationTransactionRequest(List<PublicationTransactionMemberRequest> members) {
    this(members, Optional.empty());
  }

  /** Requires a nonempty member set with distinct identities and final destinations. */
  public PublicationTransactionRequest {
    members = List.copyOf(Objects.requireNonNull(members, "members"));
    Objects.requireNonNull(ownerContext, "ownerContext");
    if (members.isEmpty()) {
      throw new IllegalArgumentException(
          "A publication transaction request must contain one member.");
    }
    Set<String> memberIds = new HashSet<>();
    Set<Path> finalPaths = new HashSet<>();
    for (PublicationTransactionMemberRequest member : members) {
      PublicationTransactionMemberRequest checkedMember = Objects.requireNonNull(member, "member");
      if (!memberIds.add(checkedMember.memberId())) {
        throw new IllegalArgumentException(
            "A publication transaction request cannot repeat a member identifier.");
      }
      if (!finalPaths.add(checkedMember.finalPath())) {
        throw new IllegalArgumentException(
            "A publication transaction request cannot repeat a final artifact path.");
      }
    }
  }
}
