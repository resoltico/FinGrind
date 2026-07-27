package dev.erst.fingrind.cli.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.WireValue;
import org.jspecify.annotations.Nullable;

/** Artifact-publication and protected-book maintenance error details emitted by the CLI. */
public interface CliMaintenanceErrorJsonModels {
  /** An indeterminate no-replace-link candidate and any stage retained before that attempt. */
  record ArtifactPublicationOutcomeUncertainDetails(
      String candidateArtifact, @Nullable String retainedStage)
      implements CliErrorJsonModels.ErrorDetails {
    public ArtifactPublicationOutcomeUncertainDetails {
      candidateArtifact =
          CliJsonModelValidation.requireText(candidateArtifact, "candidateArtifact");
      retainedStage = CliJsonModelValidation.requireOptionalText(retainedStage, "retainedStage");
      if (candidateArtifact.equals(retainedStage)) {
        throw new IllegalArgumentException(
            "candidateArtifact and retainedStage must identify distinct artifacts.");
      }
    }
  }

  /** A published artifact whose final-link durability requires inspection. */
  record ArtifactPublicationDurabilityUncertainDetails(PublishedArtifact publishedArtifact)
      implements CliErrorJsonModels.ErrorDetails {
    public ArtifactPublicationDurabilityUncertainDetails {
      java.util.Objects.requireNonNull(publishedArtifact, "publishedArtifact");
    }
  }

  /** The exact maintenance operation and both final members of an uncertain protected-book pair. */
  record ProtectedBookPairPublicationUncertainDetails(
      String operation, PairPublication pairPublication)
      implements CliErrorJsonModels.ErrorDetails {
    public ProtectedBookPairPublicationUncertainDetails {
      operation = requireProtectedBookPairPublicationOperation(operation);
      java.util.Objects.requireNonNull(pairPublication, "pairPublication");
      if (pairPublication.hasUnestablishedMember()) {
        throw new IllegalArgumentException(
            "Completion uncertainty cannot claim an unestablished pair-member fact.");
      }
    }
  }

  /** Pair evidence that blocks a safe final-member publication classification. */
  record ProtectedBookPairPublicationEvidenceBlockedDetails(PairPublication pairPublication)
      implements CliErrorJsonModels.ErrorDetails {
    public ProtectedBookPairPublicationEvidenceBlockedDetails {
      java.util.Objects.requireNonNull(pairPublication, "pairPublication");
      if (!pairPublication.hasOnlyUnestablishedMembers()) {
        throw new IllegalArgumentException(
            "Evidence-blocked pair-publication details require both members to be unestablished.");
      }
      if (pairPublication.recoveryRecordState() != null) {
        throw new IllegalArgumentException(
            "Evidence-blocked pair-publication details cannot retain recovery-record state.");
      }
    }
  }

  /** One closed pair-publication report with the recovery-record fact kept explicitly nullable. */
  record PairPublication(
      PairPublicationMember bookTarget,
      PairPublicationMember generatedSecretTarget,
      @JsonInclude(JsonInclude.Include.ALWAYS)
          @Nullable PairPublicationRecoveryRecordStatePayload recoveryRecordState,
      @JsonInclude(JsonInclude.Include.ALWAYS)
          @Nullable PairPublicationRetention pairPublicationRetention) {
    public PairPublication {
      java.util.Objects.requireNonNull(bookTarget, "bookTarget");
      java.util.Objects.requireNonNull(generatedSecretTarget, "generatedSecretTarget");
      if (bookTarget.path().equals(generatedSecretTarget.path())) {
        throw new IllegalArgumentException(
            "Protected-book pair publication details require distinct final member paths.");
      }
      boolean neitherMemberAttempted =
          bookTarget.state() == PairPublicationMemberStatePayload.NOT_ATTEMPTED
              && generatedSecretTarget.state() == PairPublicationMemberStatePayload.NOT_ATTEMPTED;
      if (neitherMemberAttempted != (recoveryRecordState != null)) {
        throw new IllegalArgumentException(
            "recoveryRecordState must be present exactly when neither pair member was attempted.");
      }
      boolean hasUnestablishedMember = hasUnestablishedMember(bookTarget, generatedSecretTarget);
      if (hasUnestablishedMember && recoveryRecordState != null) {
        throw new IllegalArgumentException(
            "Unestablished pair members cannot retain recoveryRecordState.");
      }
      if (hasUnestablishedMember && pairPublicationRetention != null) {
        throw new IllegalArgumentException(
            "Unestablished pair members cannot claim authoritative retained-stage evidence.");
      }
      if (recoveryRecordState != null && pairPublicationRetention == null) {
        throw new IllegalArgumentException(
            "A prepublication recovery record requires authoritative pair retained-stage evidence.");
      }
      if (pairPublicationRetention != null
          && (!bookTarget.path().equals(pairPublicationRetention.bookPublication().path())
              || !generatedSecretTarget
                  .path()
                  .equals(pairPublicationRetention.generatedSecretPublication().path()))) {
        throw new IllegalArgumentException(
            "Pair retained-stage evidence must bind the reported final member paths.");
      }
    }

    boolean hasUnestablishedMember() {
      return hasUnestablishedMember(bookTarget, generatedSecretTarget);
    }

    boolean hasOnlyUnestablishedMembers() {
      return bookTarget.state() == PairPublicationMemberStatePayload.UNESTABLISHED
          && generatedSecretTarget.state() == PairPublicationMemberStatePayload.UNESTABLISHED;
    }

    private static boolean hasUnestablishedMember(
        PairPublicationMember bookTarget, PairPublicationMember generatedSecretTarget) {
      return bookTarget.state() == PairPublicationMemberStatePayload.UNESTABLISHED
          || generatedSecretTarget.state() == PairPublicationMemberStatePayload.UNESTABLISHED;
    }
  }

  /** One canonical final member and its strongest established pair-publication fact. */
  record PairPublicationMember(String path, PairPublicationMemberStatePayload state) {
    public PairPublicationMember {
      path = CliJsonModelValidation.requireText(path, "path");
      java.util.Objects.requireNonNull(state, "state");
    }
  }

  /** Exact final-and-retained-stage evidence for both members of a failed pair publication. */
  record PairPublicationRetention(
      PublishedArtifact bookPublication, PublishedArtifact generatedSecretPublication) {
    public PairPublicationRetention {
      java.util.Objects.requireNonNull(bookPublication, "bookPublication");
      java.util.Objects.requireNonNull(generatedSecretPublication, "generatedSecretPublication");
      if (bookPublication.path().equals(generatedSecretPublication.path())
          || bookPublication.retainedStage().equals(generatedSecretPublication.retainedStage())
          || bookPublication.path().equals(generatedSecretPublication.retainedStage())
          || generatedSecretPublication.path().equals(bookPublication.retainedStage())) {
        throw new IllegalArgumentException(
            "Pair retained-stage evidence requires four distinct final and stage paths.");
      }
    }
  }

  /** Closed JSON wire vocabulary for one final protected-book pair-member publication state. */
  enum PairPublicationMemberStatePayload implements dev.erst.fingrind.core.WireValue {
    UNESTABLISHED("unestablished"),
    NOT_ATTEMPTED("not-attempted"),
    OUTCOME_UNCERTAIN("outcome-uncertain"),
    PUBLISHED_DURABILITY_UNCONFIRMED("published-durability-unconfirmed"),
    PUBLISHED_DURABLE("published-durable");

    private final String wireValue;

    PairPublicationMemberStatePayload(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonValue
    public String wireValue() {
      return wireValue;
    }

    /** Maps the public contract state without exposing Java enum identifiers on the wire. */
    public static PairPublicationMemberStatePayload from(
        dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState state) {
      return switch (java.util.Objects.requireNonNull(state, "state")) {
        case UNESTABLISHED -> UNESTABLISHED;
        case NOT_ATTEMPTED -> NOT_ATTEMPTED;
        case OUTCOME_UNCERTAIN -> OUTCOME_UNCERTAIN;
        case PUBLISHED_DURABILITY_UNCONFIRMED -> PUBLISHED_DURABILITY_UNCONFIRMED;
        case PUBLISHED_DURABLE -> PUBLISHED_DURABLE;
      };
    }
  }

  /** Closed JSON wire vocabulary for the live pre-final recovery-record durability fact. */
  enum PairPublicationRecoveryRecordStatePayload implements dev.erst.fingrind.core.WireValue {
    DURABLY_RETAINED("durably-retained"),
    DURABILITY_UNCONFIRMED("durability-unconfirmed");

    private final String wireValue;

    PairPublicationRecoveryRecordStatePayload(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonValue
    public String wireValue() {
      return wireValue;
    }

    /** Maps the public recovery-record state without exposing Java enum identifiers on the wire. */
    public static PairPublicationRecoveryRecordStatePayload from(
        dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState
            state) {
      return switch (java.util.Objects.requireNonNull(state, "state")) {
        case DURABLY_RETAINED -> DURABLY_RETAINED;
        case DURABILITY_UNCONFIRMED -> DURABILITY_UNCONFIRMED;
      };
    }
  }

  /** One final artifact path and its mandatory retained private stage. */
  record PublishedArtifact(String path, String retainedStage) {
    public PublishedArtifact {
      path = CliJsonModelValidation.requireText(path, "path");
      retainedStage = CliJsonModelValidation.requireText(retainedStage, "retainedStage");
      if (path.equals(retainedStage)) {
        throw new IllegalArgumentException(
            "path and retainedStage must identify distinct artifacts.");
      }
    }
  }

  private static String requireProtectedBookPairPublicationOperation(String operation) {
    String checkedOperation = CliJsonModelValidation.requireText(operation, "operation");
    OperationId selectedOperation =
        WireValue.fromWireValue(
            OperationId.class,
            checkedOperation,
            "operation must identify a protected-book maintenance operation");
    return switch (selectedOperation) {
      case BACKUP_BOOK, RESTORE_BOOK, REKEY_BOOK -> selectedOperation.wireName();
      default ->
          throw new IllegalArgumentException(
              "operation must identify a protected-book pair-publication maintenance operation.");
    };
  }
}
