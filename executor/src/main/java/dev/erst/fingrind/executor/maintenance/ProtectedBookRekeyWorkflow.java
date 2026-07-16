package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Owns generated-secret rekeying through staged replacement rather than in-place mutation. */
final class ProtectedBookRekeyWorkflow {
  private final ProtectedBookMaintenanceWorkflowSupport support;

  ProtectedBookRekeyWorkflow(ProtectedBookMaintenanceWorkflowSupport support) {
    this.support = Objects.requireNonNull(support, "support");
  }

  MaintenanceDecision<ProtectedBookRekeyOutcome> rekeyBook(
      ProtectedBookAccess bookAccess, Path newBookKeyFilePath) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    ProtectedBookMaintenanceStore store = support.store();
    Path normalizedBookPath = store.normalize(bookAccess.bookFilePath(), "bookFilePath");
    Path normalizedNewBookKeyFilePath = store.normalize(newBookKeyFilePath, "newBookKeyFilePath");
    ProtectedBookAccess normalizedAccess =
        new ProtectedBookAccess(normalizedBookPath, bookAccess.passphraseSource());
    try (PreparedPairPublication preparedPublication =
        store.preparePairPublication(
            normalizedNewBookKeyFilePath,
            normalizedBookPath,
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET)) {
      return rekeyWithPreparedPublication(
          normalizedAccess, normalizedBookPath, store, preparedPublication);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRekeyOutcome.Rejected(exception.rejection()));
    } catch (RuntimeException recoveryFailure) {
      return support.storageFailure(
          normalizedNewBookKeyFilePath,
          "Failed to recover or prepare the FinGrind rekeyed-book pair publication.",
          "newBookKeyFilePath");
    }
  }

  private MaintenanceDecision<ProtectedBookRekeyOutcome> rekeyWithPreparedPublication(
      ProtectedBookAccess normalizedAccess,
      Path normalizedBookPath,
      ProtectedBookMaintenanceStore store,
      PreparedPairPublication preparedPublication) {
    List<Path> blockingArtifacts = store.blockingArtifactsForBook(normalizedBookPath);
    if (!blockingArtifacts.isEmpty()) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRekeyOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                  normalizedBookPath, blockingArtifacts)));
    }
    return support.continueWithVerifiedBook(
        normalizedAccess,
        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
        verifiedBook -> stageAndCommit(normalizedBookPath, preparedPublication, verifiedBook),
        ProtectedBookRekeyOutcome.Rejected::new);
  }

  private MaintenanceDecision<ProtectedBookRekeyOutcome> stageAndCommit(
      Path normalizedBookPath,
      PreparedPairPublication preparedPublication,
      ProtectedBookMaintenanceStore.VerifiedBook verifiedBook) {
    ProtectedBookMaintenanceStore store = support.store();
    return store
        .stageRestoredBookPair(verifiedBook, preparedPublication)
        .fold(
            stagedPair ->
                commitStagedPair(
                    normalizedBookPath, preparedPublication.secretTargetPath(), stagedPair),
            MaintenanceDecision::failed);
  }

  private MaintenanceDecision<ProtectedBookRekeyOutcome> commitStagedPair(
      Path normalizedBookPath,
      Path normalizedNewBookKeyFilePath,
      StagedRestoredBookPair stagedPair) {
    try (StagedRestoredBookPair ignored = stagedPair) {
      return stagedPair
          .verifyInitializedRestoredBook()
          .fold(
              verification -> {
                if (verification
                    instanceof
                    ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
                  return MaintenanceDecision.accepted(
                      new ProtectedBookRekeyOutcome.Rejected(
                          support.verificationFailed(
                              ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                              verificationFailure)));
                }
                try (ProtectedBookMaintenanceStore.VerifiedBook verifiedRekeyedBook =
                    (ProtectedBookMaintenanceStore.VerifiedBook) verification) {
                  Instant recordedAt = support.recordedAt();
                  return support
                      .store()
                      .appendMaintenanceAudit(
                          verifiedRekeyedBook,
                          recordedAt,
                          ProtectedBookMaintenanceAuditKind.BOOK_REKEYED)
                      .fold(
                          ignoredAudit -> {
                            stagedPair.commit();
                            return MaintenanceDecision.accepted(
                                new ProtectedBookRekeyOutcome.Rekeyed(
                                    normalizedBookPath, normalizedNewBookKeyFilePath));
                          },
                          MaintenanceDecision::failed);
                }
              },
              MaintenanceDecision::failed);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRekeyOutcome.Rejected(exception.rejection()));
    } catch (RuntimeException commitFailure) {
      return support.storageFailure(
          normalizedBookPath,
          "Failed to publish the staged FinGrind rekeyed book pair.",
          "bookFilePath");
    }
  }
}
