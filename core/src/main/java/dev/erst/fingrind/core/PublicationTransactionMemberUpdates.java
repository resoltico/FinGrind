package dev.erst.fingrind.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Produces one immutable forward-only journal member update at a time. */
final class PublicationTransactionMemberUpdates {
  private PublicationTransactionMemberUpdates() {}

  static List<PublicationTransactionMember> staged(
      PublicationTransactionJournal journal,
      int index,
      PublicationTransactionStagedArtifact stagedArtifact) {
    PublicationTransactionMember current = memberAt(journal, index);
    return replace(
        journal,
        index,
        member(
            current,
            PublicationTransactionMemberProgress.STAGED,
            Optional.of(Objects.requireNonNull(stagedArtifact, "stagedArtifact")),
            Optional.empty()));
  }

  static List<PublicationTransactionMember> committed(
      PublicationTransactionJournal journal,
      int index,
      PublicationTransactionFinalizedArtifact finalizedArtifact) {
    PublicationTransactionMember current = memberAt(journal, index);
    return replace(
        journal,
        index,
        member(
            current,
            PublicationTransactionMemberProgress.COMMITTED,
            current.stagedArtifact(),
            Optional.of(Objects.requireNonNull(finalizedArtifact, "finalizedArtifact"))));
  }

  static List<PublicationTransactionMember> aborted(
      PublicationTransactionJournal journal, int index) {
    PublicationTransactionMember current = memberAt(journal, index);
    return replace(
        journal,
        index,
        member(
            current,
            PublicationTransactionMemberProgress.ABORTED,
            current.stagedArtifact(),
            Optional.empty()));
  }

  static List<PublicationTransactionMember> cleaned(
      PublicationTransactionJournal journal, int index) {
    PublicationTransactionMember current = memberAt(journal, index);
    return replace(
        journal,
        index,
        member(
            current,
            PublicationTransactionMemberProgress.CLEANED,
            current.stagedArtifact(),
            current.finalizedArtifact()));
  }

  private static PublicationTransactionMember memberAt(
      PublicationTransactionJournal journal, int index) {
    return Objects.requireNonNull(journal, "journal").members().get(index);
  }

  private static List<PublicationTransactionMember> replace(
      PublicationTransactionJournal journal, int index, PublicationTransactionMember replacement) {
    List<PublicationTransactionMember> members =
        new ArrayList<>(Objects.requireNonNull(journal, "journal").members());
    members.set(index, Objects.requireNonNull(replacement, "replacement"));
    return List.copyOf(members);
  }

  private static PublicationTransactionMember member(
      PublicationTransactionMember member,
      PublicationTransactionMemberProgress progress,
      Optional<PublicationTransactionStagedArtifact> stagedArtifact,
      Optional<PublicationTransactionFinalizedArtifact> finalizedArtifact) {
    PublicationTransactionMember checkedMember = Objects.requireNonNull(member, "member");
    return new PublicationTransactionMember(
        checkedMember.memberId(),
        checkedMember.role(),
        checkedMember.finalPath(),
        checkedMember.stagePath(),
        checkedMember.physicalDirectoryIdentity(),
        checkedMember.publicationMode(),
        Objects.requireNonNull(progress, "progress"),
        Objects.requireNonNull(stagedArtifact, "stagedArtifact"),
        Objects.requireNonNull(finalizedArtifact, "finalizedArtifact"));
  }
}
