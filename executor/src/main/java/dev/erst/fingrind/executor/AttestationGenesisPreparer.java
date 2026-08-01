package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationCredentialAdmission;
import dev.erst.fingrind.core.attestation.AttestationCredentialUseException;
import dev.erst.fingrind.core.attestation.AttestationGenesis;
import dev.erst.fingrind.core.attestation.AttestationSigningCredential;
import dev.erst.fingrind.core.attestation.AttestationSigningCredentialOpening;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Coordinates founder credential resolution, genesis signing, and retained failed-preparation
 * evidence.
 */
final class AttestationGenesisPreparer {
  private final BookIdentity bookIdentity;
  private final Instant recordedAt;
  private final List<AttestationFounderInput> founders;
  private final AttestationGenesisFactory.FounderCredentialAccess credentialAccess;

  AttestationGenesisPreparer(
      BookIdentity bookIdentity,
      Instant recordedAt,
      List<AttestationFounderInput> founders,
      AttestationGenesisFactory.FounderCredentialAccess credentialAccess) {
    this.bookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    this.founders = List.copyOf(Objects.requireNonNull(founders, "founders"));
    this.credentialAccess = Objects.requireNonNull(credentialAccess, "credentialAccess");
  }

  /** Builds signed genesis evidence while retaining every new founder-key publication fact. */
  AttestationGenesisPreparation prepare() {
    FounderKeyPublicationJournal publicationJournal = new FounderKeyPublicationJournal();
    try (OpenedFounderCredentials openedCredentials = new OpenedFounderCredentials(founders)) {
      return prepareWithOpenedCredentials(openedCredentials, publicationJournal);
    }
  }

  private AttestationGenesisPreparation prepareWithOpenedCredentials(
      OpenedFounderCredentials openedCredentials, FounderKeyPublicationJournal publicationJournal) {
    try {
      List<AttestationSigningCredential> orderedCredentials =
          resolveFounderCredentials(openedCredentials, publicationJournal);
      return new AttestationGenesisPreparation(
          AttestationGenesis.create(
              UUID.randomUUID(), bookIdentity, recordedAt, orderedCredentials),
          publicationJournal.publications());
    } catch (AttestationFounderKeyRetentionException failure) {
      throw reconcileFailedPreparation(
          failure, publicationJournal.publications(), failure.retainedFounderKeyArtifacts());
    } catch (ArtifactPublicationOutcomeUncertainException failure) {
      throw reconcileFailedPreparation(
          failure, publicationJournal.publications(), List.of(outcomeFailureArtifact(failure)));
    } catch (AttestationCredentialUseException failure) {
      throw reconcileFailedPreparation(
          new AttestationCredentialException(failure.credentialPath(), failure),
          publicationJournal.publications(),
          List.of());
    } catch (RuntimeException failure) {
      throw reconcileFailedPreparation(failure, publicationJournal.publications(), List.of());
    }
  }

  private List<AttestationSigningCredential> resolveFounderCredentials(
      OpenedFounderCredentials openedCredentials, FounderKeyPublicationJournal publicationJournal) {
    openExistingFounderCredentials(openedCredentials);
    openedCredentials.requireDistinctPublicKeyIds();
    openMissingFounderCredentials(openedCredentials, publicationJournal);
    openedCredentials.requireDistinctPublicKeyIds();
    return openedCredentials.orderedCredentials();
  }

  private void openExistingFounderCredentials(OpenedFounderCredentials openedCredentials) {
    for (FounderCredentialSlot slot : openedCredentials.slots()) {
      if (Files.exists(slot.founder().encryptedKeyFilePath())) {
        openedCredentials.retain(slot, credentialAccess.openExisting(slot.founder()));
      }
    }
  }

  private void openMissingFounderCredentials(
      OpenedFounderCredentials openedCredentials, FounderKeyPublicationJournal publicationJournal) {
    for (FounderCredentialSlot slot : openedCredentials.unresolvedSlots()) {
      AttestationSigningCredentialOpening opening = credentialAccess.openOrCreate(slot.founder());
      openedCredentials.retain(slot, opening);
      publicationJournal.record(opening.createdKeyFilePublication());
    }
  }

  private RuntimeException reconcileFailedPreparation(
      RuntimeException failure,
      List<ArtifactPublicationResult> retainedFounderKeyArtifacts,
      List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> failureArtifacts) {
    if (retainedFounderKeyArtifacts.isEmpty() && failureArtifacts.isEmpty()) {
      return failure;
    }
    List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> retainedArtifacts =
        new ArrayList<>(
            retainedFounderKeyArtifacts.stream()
                .map(OpenBookFailureDetails.RetainedOpenBookPreparationArtifact::founderKey)
                .toList());
    retainedArtifacts.addAll(failureArtifacts);
    return new AttestationFounderKeyRetentionException(retainedArtifacts, failure);
  }

  private static OpenBookFailureDetails.RetainedOpenBookPreparationArtifact outcomeFailureArtifact(
      ArtifactPublicationOutcomeUncertainException failure) {
    return OpenBookFailureDetails.retainedArtifact(
        OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY,
        failure.candidateArtifactPath(),
        failure.retainedStage());
  }

  /** Tracks retained founder-key publication facts until preparation transfers them to opening. */
  private static final class FounderKeyPublicationJournal {
    private final List<ArtifactPublicationResult> retainedFounderKeyArtifacts = new ArrayList<>();

    void record(@Nullable ArtifactPublicationResult publication) {
      if (publication != null) {
        retainedFounderKeyArtifacts.add(publication);
      }
    }

    List<ArtifactPublicationResult> publications() {
      return List.copyOf(retainedFounderKeyArtifacts);
    }
  }

  /** Owns every opened credential until signing and failure reconciliation have completed. */
  private static final class OpenedFounderCredentials implements AutoCloseable {
    private final List<FounderCredentialSlot> slots;

    OpenedFounderCredentials(List<AttestationFounderInput> founders) {
      slots =
          Objects.requireNonNull(founders, "founders").stream()
              .map(FounderCredentialSlot::new)
              .toList();
    }

    List<FounderCredentialSlot> slots() {
      return slots;
    }

    List<FounderCredentialSlot> unresolvedSlots() {
      return slots.stream().filter(FounderCredentialSlot::isUnresolved).toList();
    }

    void retain(FounderCredentialSlot slot, AttestationSigningCredentialOpening opening) {
      Objects.requireNonNull(slot, "slot")
          .retain(Objects.requireNonNull(opening, "opening").credential());
    }

    List<AttestationSigningCredential> orderedCredentials() {
      return slots.stream().map(FounderCredentialSlot::credential).toList();
    }

    void requireDistinctPublicKeyIds() {
      AttestationCredentialAdmission.requireDistinctPublicKeyIds(
          slots.stream()
              .filter(FounderCredentialSlot::isResolved)
              .map(FounderCredentialSlot::credential)
              .map(AttestationSigningCredential::publicCredential)
              .toList());
    }

    /** Clears every retained credential's decrypted passphrase material. */
    @Override
    public void close() {
      slots.forEach(FounderCredentialSlot::close);
    }
  }

  /** Holds one founder's credential until the preparer transfers it to signed genesis evidence. */
  private static final class FounderCredentialSlot {
    private final AttestationFounderInput founder;
    private @Nullable AttestationSigningCredential credential;

    FounderCredentialSlot(AttestationFounderInput founder) {
      this.founder = Objects.requireNonNull(founder, "founder");
    }

    AttestationFounderInput founder() {
      return founder;
    }

    boolean isUnresolved() {
      return credential == null;
    }

    boolean isResolved() {
      return credential != null;
    }

    void retain(AttestationSigningCredential credential) {
      this.credential = Objects.requireNonNull(credential, "credential");
    }

    AttestationSigningCredential credential() {
      return Objects.requireNonNull(credential, "credential");
    }

    void close() {
      if (credential != null) {
        credential.close();
      }
    }
  }
}
