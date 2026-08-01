package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Small, side-effect-bounded facts shared by recovered pair publication. */
final class SqliteProtectedBookPairPublicationRecoverySupport {
  private SqliteProtectedBookPairPublicationRecoverySupport() {}

  static MemberRecoveryPlan secretPlan(SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    if (checkedRecord.finalSecretMatches()) {
      return MemberRecoveryPlan.MATCHED_OR_FORCEABLE;
    }
    if (!checkedRecord.stagedSecretMatches()
        || Files.exists(checkedRecord.secretTargetPath, LinkOption.NOFOLLOW_LINKS)) {
      return MemberRecoveryPlan.BLOCKED;
    }
    return MemberRecoveryPlan.PUBLISH_ELIGIBLE;
  }

  static MemberRecoveryPlan bookPlan(SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    if (checkedRecord.finalBookMatches()) {
      return MemberRecoveryPlan.MATCHED_OR_FORCEABLE;
    }
    if (!checkedRecord.stagedBookMatches()) {
      return MemberRecoveryPlan.BLOCKED;
    }
    return switch (checkedRecord.bookTargetPolicy) {
      case REQUIRE_ABSENT ->
          Files.exists(checkedRecord.bookTargetPath, LinkOption.NOFOLLOW_LINKS)
              ? MemberRecoveryPlan.BLOCKED
              : MemberRecoveryPlan.PUBLISH_ELIGIBLE;
      case REPLACE_SELECTED ->
          checkedRecord.replaceTargetMatches()
              ? MemberRecoveryPlan.PUBLISH_ELIGIBLE
              : MemberRecoveryPlan.BLOCKED;
    };
  }

  static boolean hasOwnedStages(SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    return ownedStage(checkedRecord.bookTargetPath, checkedRecord.bookStagePath) != null
        && ownedStage(checkedRecord.secretTargetPath, checkedRecord.secretStagePath) != null;
  }

  static @org.jspecify.annotations.Nullable SqliteOwnedStageRecord ownedStage(
      Path finalPath, Path stagedPath) {
    return SqliteOwnedStageRecord.findFor(finalPath).stream()
        .filter(
            candidate ->
                SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
                    candidate.stagedPath(), stagedPath))
        .findFirst()
        .orElse(null);
  }

  static MemberReconciliation forceRecoveredMember(
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep step,
      Path targetPath) {
    try {
      Objects.requireNonNull(directoryForcer, "directoryForcer")
          .force(step, SqlitePairPublicationRecordIntegrity.parentOf(targetPath));
      return MemberReconciliation.DURABLE;
    } catch (IOException | RuntimeException failure) {
      return MemberReconciliation.DURABILITY_UNCONFIRMED;
    }
  }

  static ProtectedBookPairPublicationMemberState bookState(
      SqliteProtectedBookPairPublicationRecord record) {
    return Objects.requireNonNull(record, "record").finalBookMatches()
        ? ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED
        : ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN;
  }

  static ProtectedBookPairPublicationMemberState secretState(
      SqliteProtectedBookPairPublicationRecord record) {
    return Objects.requireNonNull(record, "record").finalSecretMatches()
        ? ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED
        : ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN;
  }

  /** Permitted recovery action for one final pair member. */
  enum MemberRecoveryPlan {
    MATCHED_OR_FORCEABLE,
    PUBLISH_ELIGIBLE,
    BLOCKED;

    boolean canReconcile() {
      return this != BLOCKED;
    }
  }

  /** Reconciled evidence state for one final pair member. */
  enum MemberReconciliation {
    DURABLE(ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE),
    DURABILITY_UNCONFIRMED(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED),
    OUTCOME_UNCERTAIN(ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN);

    private final ProtectedBookPairPublicationMemberState state;

    MemberReconciliation(ProtectedBookPairPublicationMemberState state) {
      this.state = state;
    }

    boolean isDurable() {
      return this == DURABLE;
    }

    ProtectedBookPairPublicationMemberState state() {
      return state;
    }
  }
}
