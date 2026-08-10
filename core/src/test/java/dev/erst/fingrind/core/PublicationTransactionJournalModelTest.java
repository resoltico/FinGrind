package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verifies the durable publication-journal value model and its state invariants. */
class PublicationTransactionJournalModelTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-08-10T12:34:56Z");
  private static final String NONCE = "0123456789abcdef0123456789abcdef";
  private static final String FINGERPRINT = "a".repeat(64);
  private static final String DIGEST = "b".repeat(64);

  @Test
  void acceptsEveryDeclaredWireVocabularyAndRejectsUnknownValues() {
    for (PublicationMode value : PublicationMode.values()) {
      assertEquals(value, PublicationMode.fromWireValue(value.wireValue()));
    }
    for (PublicationTransactionMemberProgress value :
        PublicationTransactionMemberProgress.values()) {
      assertEquals(value, PublicationTransactionMemberProgress.fromWireValue(value.wireValue()));
    }
    for (PublicationTransactionMemberRole value : PublicationTransactionMemberRole.values()) {
      assertEquals(value, PublicationTransactionMemberRole.fromWireValue(value.wireValue()));
    }
    for (PublicationTransactionState value : PublicationTransactionState.values()) {
      assertEquals(value, PublicationTransactionState.fromWireValue(value.wireValue()));
    }
    for (PublicationCommitOutcome value : PublicationCommitOutcome.values()) {
      assertEquals(value, PublicationCommitOutcome.fromWireValue(value.wireValue()));
    }
    for (PublicationCleanupOutcome value : PublicationCleanupOutcome.values()) {
      assertEquals(value, PublicationCleanupOutcome.fromWireValue(value.wireValue()));
    }

    assertThrows(IllegalArgumentException.class, () -> PublicationMode.fromWireValue("unknown"));
    assertThrows(
        IllegalArgumentException.class,
        () -> PublicationTransactionMemberProgress.fromWireValue("unknown"));
    assertThrows(
        IllegalArgumentException.class,
        () -> PublicationTransactionMemberRole.fromWireValue("unknown"));
    assertThrows(
        IllegalArgumentException.class, () -> PublicationTransactionState.fromWireValue("unknown"));
    assertThrows(
        IllegalArgumentException.class, () -> PublicationCommitOutcome.fromWireValue("unknown"));
    assertThrows(
        IllegalArgumentException.class, () -> PublicationCleanupOutcome.fromWireValue("unknown"));
  }

  @Test
  void recognizesEverySafeAndUnsafeStateTransition() {
    for (PublicationTransactionState current : PublicationTransactionState.values()) {
      for (PublicationTransactionState next : PublicationTransactionState.values()) {
        assertEquals(expectedTransition(current, next), current.permitsOrdinaryTransitionTo(next));
      }
    }
  }

  @Test
  void bindsMemberArtifactsToTheirDurablyKnownProgress() {
    assertEquals(PublicationTransactionMemberProgress.PLANNED, plannedMember().progress());
    assertEquals(PublicationTransactionMemberProgress.STAGED, stagedMember().progress());
    assertEquals(PublicationTransactionMemberProgress.COMMITTED, committedMember().progress());
    assertEquals(
        Path.of("reports", "book.fgb").toAbsolutePath().normalize(), plannedMember().finalPath());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            member(
                "Bad",
                PublicationTransactionMemberProgress.PLANNED,
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            member(
                "protected-book",
                PublicationTransactionMemberProgress.PLANNED,
                Optional.of(stagedArtifact()),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            member(
                "protected-book",
                PublicationTransactionMemberProgress.STAGED,
                Optional.of(stagedArtifact()),
                Optional.of(finalizedArtifact())));
    assertThrows(
        NullPointerException.class,
        () -> member("protected-book", nullOf(), Optional.empty(), Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            member(
                "protected-book",
                PublicationTransactionMemberProgress.COMMITTED,
                Optional.empty(),
                Optional.of(finalizedArtifact())));
  }

  @Test
  void rejectsNonartifactPathsAndNoncanonicalArtifactFacts() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionStagedArtifact(Path.of("/"), "stage-identity", DIGEST));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionStagedArtifact(Path.of("stage"), " ", DIGEST));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionStagedArtifact(
                Path.of("stage"), "stage-identity", "B".repeat(64)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionFinalizedArtifact(" ", DIGEST));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionFinalizedArtifact("final-identity", "b"));
  }

  @Test
  void requiresValidJournalIdentityMembershipAndTransitionHistory() {
    PublicationTransactionJournal prepared = preparedJournal();
    PublicationTransactionJournal staged =
        prepared.transition(transition(PublicationTransactionState.STAGED, noneCommitted()));
    PublicationTransactionJournal committing =
        staged.transition(transition(PublicationTransactionState.COMMITTING, noneCommitted()));
    PublicationTransactionJournal committed =
        committing.transition(transition(PublicationTransactionState.COMMITTED, allCommitted()));
    PublicationTransactionJournal cleaning =
        committed.transition(transition(PublicationTransactionState.CLEANING, allCommitted()));
    PublicationTransactionJournal complete =
        cleaning.transition(transition(PublicationTransactionState.COMPLETE, allCommitted()));

    assertEquals(PublicationTransactionState.COMPLETE, complete.state());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            complete.transition(transition(PublicationTransactionState.COMPLETE, allCommitted())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionJournal(
                2,
                transactionId(),
                NONCE,
                FINGERPRINT,
                RECORDED_AT,
                List.of(plannedMember()),
                List.of(PublicationTransactionTransition.prepared(RECORDED_AT))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionJournal(
                1,
                transactionId(),
                "invalid",
                FINGERPRINT,
                RECORDED_AT,
                List.of(plannedMember()),
                List.of(PublicationTransactionTransition.prepared(RECORDED_AT))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionJournal(
                1,
                transactionId(),
                NONCE,
                "invalid",
                RECORDED_AT,
                List.of(plannedMember()),
                List.of(PublicationTransactionTransition.prepared(RECORDED_AT))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionJournal(
                1,
                transactionId(),
                NONCE,
                FINGERPRINT,
                RECORDED_AT,
                List.of(plannedMember()),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionJournal(
                1,
                transactionId(),
                NONCE,
                FINGERPRINT,
                RECORDED_AT,
                List.of(),
                List.of(PublicationTransactionTransition.prepared(RECORDED_AT))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionJournal(
                1,
                transactionId(),
                NONCE,
                FINGERPRINT,
                RECORDED_AT,
                List.of(plannedMember(), plannedMember()),
                List.of(PublicationTransactionTransition.prepared(RECORDED_AT))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionJournal(
                1,
                transactionId(),
                NONCE,
                FINGERPRINT,
                RECORDED_AT,
                List.of(plannedMember()),
                List.of(transition(PublicationTransactionState.STAGED, noneCommitted()))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionJournal(
                1,
                transactionId(),
                NONCE,
                FINGERPRINT,
                RECORDED_AT,
                List.of(plannedMember()),
                List.of(
                    PublicationTransactionTransition.prepared(RECORDED_AT),
                    transition(PublicationTransactionState.COMMITTED, allCommitted()))));
  }

  @Test
  void bindsFailureStatesToTheirRequiredTwoAxisOutcomes() {
    assertEquals(
        PublicationTransactionState.PREPARED,
        PublicationTransactionTransition.prepared(RECORDED_AT).state());
    assertThrows(
        IllegalArgumentException.class,
        () -> transition(PublicationTransactionState.COMPLETE, noneCommitted()));
    assertThrows(
        IllegalArgumentException.class,
        () -> transition(PublicationTransactionState.COMMIT_UNCERTAIN, noneCommitted()));
    assertThrows(
        IllegalArgumentException.class,
        () -> transition(PublicationTransactionState.CLEANUP_INCOMPLETE, noneCommitted()));
    assertThrows(
        IllegalArgumentException.class,
        () -> transition(PublicationTransactionState.CLEANUP_UNCERTAIN, noneCommitted()));

    assertEquals(
        PublicationTransactionState.COMMIT_UNCERTAIN,
        transition(
                PublicationTransactionState.COMMIT_UNCERTAIN,
                new PublicationTransactionOutcome(
                    PublicationCommitOutcome.COMMIT_UNCERTAIN, PublicationCleanupOutcome.COMPLETE))
            .state());
    assertEquals(
        PublicationTransactionState.CLEANUP_INCOMPLETE,
        transition(
                PublicationTransactionState.CLEANUP_INCOMPLETE,
                new PublicationTransactionOutcome(
                    PublicationCommitOutcome.PARTIALLY_COMMITTED,
                    PublicationCleanupOutcome.INCOMPLETE))
            .state());
    assertEquals(
        PublicationTransactionState.CLEANUP_UNCERTAIN,
        transition(
                PublicationTransactionState.CLEANUP_UNCERTAIN,
                new PublicationTransactionOutcome(
                    PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.UNCERTAIN))
            .state());
  }

  private static boolean expectedTransition(
      PublicationTransactionState current, PublicationTransactionState next) {
    return switch (current) {
      case PREPARED ->
          next == PublicationTransactionState.STAGED || next == PublicationTransactionState.BLOCKED;
      case STAGED ->
          next == PublicationTransactionState.COMMITTING
              || next == PublicationTransactionState.CLEANING
              || next == PublicationTransactionState.BLOCKED
              || next == PublicationTransactionState.CLEANUP_INCOMPLETE
              || next == PublicationTransactionState.CLEANUP_UNCERTAIN;
      case COMMITTING ->
          next == PublicationTransactionState.COMMITTED
              || next == PublicationTransactionState.BLOCKED
              || next == PublicationTransactionState.COMMIT_UNCERTAIN;
      case COMMITTED ->
          next == PublicationTransactionState.CLEANING
              || next == PublicationTransactionState.BLOCKED;
      case CLEANING ->
          next == PublicationTransactionState.COMPLETE
              || next == PublicationTransactionState.CLEANUP_INCOMPLETE
              || next == PublicationTransactionState.CLEANUP_UNCERTAIN;
      case COMPLETE, BLOCKED, COMMIT_UNCERTAIN, CLEANUP_INCOMPLETE, CLEANUP_UNCERTAIN -> false;
    };
  }

  private static PublicationTransactionJournal preparedJournal() {
    return PublicationTransactionJournal.prepared(
        transactionId(), NONCE, FINGERPRINT, RECORDED_AT, List.of(plannedMember()));
  }

  private static PublicationTransactionId transactionId() {
    return new PublicationTransactionId("fedcba9876543210fedcba9876543210");
  }

  private static PublicationTransactionMember plannedMember() {
    return member(
        "protected-book",
        PublicationTransactionMemberProgress.PLANNED,
        Optional.empty(),
        Optional.empty());
  }

  private static PublicationTransactionMember stagedMember() {
    return member(
        "protected-book",
        PublicationTransactionMemberProgress.STAGED,
        Optional.of(stagedArtifact()),
        Optional.empty());
  }

  private static PublicationTransactionMember committedMember() {
    return member(
        "protected-book",
        PublicationTransactionMemberProgress.COMMITTED,
        Optional.of(stagedArtifact()),
        Optional.of(finalizedArtifact()));
  }

  private static PublicationTransactionMember member(
      String memberId,
      PublicationTransactionMemberProgress progress,
      Optional<PublicationTransactionStagedArtifact> stagedArtifact,
      Optional<PublicationTransactionFinalizedArtifact> finalizedArtifact) {
    return new PublicationTransactionMember(
        memberId,
        PublicationTransactionMemberRole.PROTECTED_BOOK,
        Path.of("reports", "book.fgb"),
        PublicationMode.NO_REPLACE_LINK,
        progress,
        stagedArtifact,
        finalizedArtifact);
  }

  private static PublicationTransactionStagedArtifact stagedArtifact() {
    return new PublicationTransactionStagedArtifact(
        Path.of("stage", "book.fgb"), "stage-identity", DIGEST);
  }

  private static PublicationTransactionFinalizedArtifact finalizedArtifact() {
    return new PublicationTransactionFinalizedArtifact("final-identity", DIGEST);
  }

  private static PublicationTransactionTransition transition(
      PublicationTransactionState state, PublicationTransactionOutcome outcome) {
    return new PublicationTransactionTransition(state, RECORDED_AT.plusSeconds(1L), outcome);
  }

  private static PublicationTransactionOutcome noneCommitted() {
    return new PublicationTransactionOutcome(
        PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.COMPLETE);
  }

  private static PublicationTransactionOutcome allCommitted() {
    return new PublicationTransactionOutcome(
        PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE);
  }
}
