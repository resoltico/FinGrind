package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Removes only authenticated transaction-owned stage residue after commit or a verified abort. */
final class PublicationTransactionCleaner {
  private PublicationTransactionCleaner() {}

  static PublicationTransactionJournal cleanAll(
      PublicationTransactionJournal journal, PublicationTransactionRuntime runtime)
      throws IOException {
    PublicationTransactionJournal current = Objects.requireNonNull(journal, "journal");
    for (int index = 0; index < current.members().size(); index++) {
      PublicationTransactionMember member = current.members().get(index);
      if (member.progress() == PublicationTransactionMemberProgress.CLEANED) {
        continue;
      }
      if (member.progress() != PublicationTransactionMemberProgress.COMMITTED) {
        throw new IOException(
            "Publication transaction cannot clean a member that is not committed.");
      }
      PublicationTransactionPlan.requireCurrentPrivateDirectories(current);
      cleanMember(member, runtime);
      current =
          runtime.updateMembers(
              current,
              PublicationTransactionMemberUpdates.cleaned(current, index),
              PublicationTransactionFaultPoint.MEMBER_CLEANED);
    }
    return current;
  }

  /**
   * Removes every transaction-owned stage only after a fresh proof that a no-replace member cannot
   * have published and an unrelated final caused the abort.
   */
  static PublicationTransactionJournal abortNoReplaceCollision(
      PublicationTransactionJournal journal, PublicationTransactionRuntime runtime)
      throws IOException {
    PublicationTransactionJournal current = Objects.requireNonNull(journal, "journal");
    if (!hasVerifiedNoReplaceCollision(current)) {
      throw new IOException(
          "Publication transaction cannot abort without a verified unrelated final collision.");
    }
    for (int index = 0; index < current.members().size(); index++) {
      PublicationTransactionMember member = current.members().get(index);
      if (member.progress() == PublicationTransactionMemberProgress.ABORTED) {
        continue;
      }
      PublicationTransactionPlan.requireCurrentPrivateDirectories(current);
      abortMember(member, runtime);
      current =
          runtime.updateMembers(
              current,
              PublicationTransactionMemberUpdates.aborted(current, index),
              PublicationTransactionFaultPoint.MEMBER_ABORTED);
    }
    return current;
  }

  static boolean hasVerifiedNoReplaceCollision(PublicationTransactionJournal journal)
      throws IOException {
    PublicationTransactionJournal checkedJournal = Objects.requireNonNull(journal, "journal");
    boolean hasDistinctFinal = false;
    for (PublicationTransactionMember member : checkedJournal.members()) {
      requireAbortableNoReplaceMember(member);
      requireCurrentStageWhenMaterialized(member);
      if (requiresNoReplaceCollisionProof(checkedJournal, member)) {
        hasDistinctFinal |= hasVerifiedExternalFinal(member);
      }
    }
    return hasDistinctFinal;
  }

  private static void requireAbortableNoReplaceMember(PublicationTransactionMember member)
      throws IOException {
    if (member.progress() == PublicationTransactionMemberProgress.STAGED
        || member.progress() == PublicationTransactionMemberProgress.ABORTED) {
      return;
    }
    throw new IOException(
        "Publication transaction cannot safely abort this member after a no-replace collision.");
  }

  /**
   * Returns whether this member can prove a safe pre-commit collision.
   *
   * <p>Ordinary no-replace members always qualify. Schema 3 additionally records an explicitly
   * absent replacement target; before any member commits, that replacement has the same safe
   * collision behavior as no-replace. A replacement with an authenticated pre-existing target never
   * proves a no-replace collision, and legacy schema 1 never receives this inference.
   */
  private static boolean requiresNoReplaceCollisionProof(
      PublicationTransactionJournal journal, PublicationTransactionMember member) {
    return switch (member.publicationMode()) {
      case NO_REPLACE_LINK -> true;
      case REPLACE ->
          journal.schemaVersion() >= PublicationTransactionJournal.CURRENT_SCHEMA_VERSION
              && member.replacementTarget().isEmpty();
    };
  }

  private static void requireCurrentStageWhenMaterialized(PublicationTransactionMember member)
      throws IOException {
    if (PublicationTransactionArtifactFiles.evidenceIfPresent(member.stagePath()).isPresent()) {
      PublicationTransactionArtifactFiles.requireCurrentStageEvidence(member);
    }
  }

  private static boolean hasVerifiedExternalFinal(PublicationTransactionMember member)
      throws IOException {
    PublicationTransactionStagedArtifact staged = member.stagedArtifact().orElseThrow();
    try {
      java.util.Optional<PublicationTransactionFileEvidence> finalEvidence =
          PublicationTransactionArtifactFiles.evidenceIfPresent(member.finalPath());
      if (finalEvidence.isEmpty()) {
        return false;
      }
      if (staged.physicalIdentity().equals(finalEvidence.orElseThrow().physicalIdentity())) {
        throw new IOException(
            "Publication transaction cannot abort because a final may be transaction-owned.");
      }
      return true;
    } catch (PrivateOutputFile.OwnerOnlyFileViolation unadmittedFinal) {
      return true;
    }
  }

  private static void abortMember(
      PublicationTransactionMember member, PublicationTransactionRuntime runtime)
      throws IOException {
    Path parent = Objects.requireNonNull(member.stagePath().getParent(), "stage artifact parent");
    if (PublicationTransactionArtifactFiles.evidenceIfPresent(member.stagePath()).isPresent()) {
      PublicationTransactionArtifactFiles.requireCurrentStageEvidence(member);
      Files.delete(member.stagePath());
      runtime.faultInjector().after(PublicationTransactionFaultPoint.STAGE_UNLINKED);
    }
    runtime.forceDirectory(parent, PublicationTransactionFaultPoint.CLEANUP_DIRECTORY_FORCED);
  }

  private static void cleanMember(
      PublicationTransactionMember member, PublicationTransactionRuntime runtime)
      throws IOException {
    Path parent = Objects.requireNonNull(member.stagePath().getParent(), "stage artifact parent");
    if (member.publicationMode() == PublicationMode.NO_REPLACE_LINK) {
      cleanNoReplaceLink(member, parent, runtime);
    } else {
      cleanReplacement(member, parent, runtime);
    }
  }

  private static void cleanNoReplaceLink(
      PublicationTransactionMember member, Path parent, PublicationTransactionRuntime runtime)
      throws IOException {
    if (PublicationTransactionArtifactFiles.evidenceIfPresent(member.stagePath()).isPresent()) {
      PublicationTransactionArtifactFiles.deleteStageAfterFreshValidation(member);
      runtime.faultInjector().after(PublicationTransactionFaultPoint.STAGE_UNLINKED);
    } else {
      requireCurrentFinalContent(member);
    }
    runtime.forceDirectory(parent, PublicationTransactionFaultPoint.CLEANUP_DIRECTORY_FORCED);
  }

  private static void cleanReplacement(
      PublicationTransactionMember member, Path parent, PublicationTransactionRuntime runtime)
      throws IOException {
    if (PublicationTransactionArtifactFiles.evidenceIfPresent(member.stagePath()).isPresent()) {
      PublicationTransactionArtifactFiles.deleteStageAfterFreshValidation(member);
      runtime.faultInjector().after(PublicationTransactionFaultPoint.STAGE_UNLINKED);
    } else {
      requireCurrentFinalContent(member);
    }
    runtime.forceDirectory(parent, PublicationTransactionFaultPoint.CLEANUP_DIRECTORY_FORCED);
  }

  /**
   * Proves that an already-published final retains its authenticated content when no stage remains
   * for recovery to delete.
   *
   * <p>The final's private no-follow admission and digest remain mandatory. Its physical identity
   * is deliberately not required here because a filesystem may re-home an atomic replacement after
   * publication. Recovery performs no destructive action in this branch; strict identity equality
   * remains mandatory for branches that remove a still-materialized stage.
   */
  private static void requireCurrentFinalContent(PublicationTransactionMember member)
      throws IOException {
    PublicationTransactionMember checkedMember = Objects.requireNonNull(member, "member");
    PublicationTransactionFinalizedArtifact finalized =
        checkedMember.finalizedArtifact().orElseThrow();
    PublicationTransactionFileEvidence currentFinal =
        PublicationTransactionArtifactFiles.evidence(checkedMember.finalPath());
    if (!finalized.sha256Hex().equals(currentFinal.sha256Hex())) {
      throw new IOException(
          "Publication transaction final no longer matches its authenticated content.");
    }
  }
}
