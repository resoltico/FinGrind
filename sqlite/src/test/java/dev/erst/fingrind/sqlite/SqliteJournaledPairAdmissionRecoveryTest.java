package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.core.PublicationCleanupOutcome;
import dev.erst.fingrind.core.PublicationCommitOutcome;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionMemberArtifact;
import dev.erst.fingrind.core.PublicationTransactionMemberRole;
import dev.erst.fingrind.core.PublicationTransactionOutcome;
import dev.erst.fingrind.core.PublicationTransactionOwnerContext;
import dev.erst.fingrind.core.PublicationTransactionRecoveryReceipt;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.core.PublicationTransactionStageReservation;
import dev.erst.fingrind.core.PublicationTransactionState;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Proves journal owner-context recovery is the only successful pair-admission recovery path. */
class SqliteJournaledPairAdmissionRecoveryTest extends SqliteArtifactPublicationTestSupport {

  @Test
  void admitsOnlyTheExactFinalPairAuthenticatedByTheRecoveredJournal() throws Exception {
    PairTargets targets = pairTargets();
    ProtectedBookPairPublicationRecoveryRequest.Backup request =
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
            tempDirectory.resolve("source.sqlite"), new UUID(7L, 9L));
    PublicationTransactionResult completed = completedResult();
    PublicationTransactionRecoveryReceipt receipt =
        new PublicationTransactionRecoveryReceipt(
            completed,
            List.of(
                artifact(
                    SqlitePublicationTransactionPair.BOOK_MEMBER_ID,
                    PublicationTransactionMemberRole.PROTECTED_BOOK,
                    targets.bookTargetPath(),
                    completed),
                artifact(
                    SqlitePublicationTransactionPair.SECRET_MEMBER_ID,
                    PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY,
                    targets.secretTargetPath(),
                    completed)));
    ContextRecoveryService service = new ContextRecoveryService(Optional.of(receipt));
    SqliteProtectedBookPairPublicationPreparation preparation =
        SqliteProtectedBookPairPublicationPreparation.journaledForTesting(
            maintenanceStore(), () -> service);

    ProtectedBookPairPublicationAdmission admission =
        preparation.admit(
            targets.bookTargetPath(),
            targets.secretTargetPath(),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            request,
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
            leases(targets));

    ProtectedBookPairPublicationAdmission.Recovered recovered =
        assertInstanceOf(ProtectedBookPairPublicationAdmission.Recovered.class, admission);
    ProtectedBookPairPublication publication = recovered.publication();
    assertEquals(completed, publication.publicationTransaction());
    assertEquals(
        targets.bookTargetPath(),
        publication.requireBookPublication(targets.bookTargetPath()).publishedArtifactPath());
    assertEquals(
        targets.secretTargetPath(),
        publication
            .requireGeneratedSecretPublication(targets.secretTargetPath())
            .publishedArtifactPath());
    assertEquals(
        SqliteProtectedBookPublicationOwnerContext.forPair(
            request,
            targets.bookTargetPath(),
            targets.secretTargetPath(),
            RestoredBookTargetPolicy.REQUIRE_ABSENT),
        service.ownerContext());
  }

  @Test
  void blocksAMatchingButIncompleteJournalWithoutOpeningANewPublication() throws Exception {
    PairTargets targets = pairTargets();
    ProtectedBookPairPublicationRecoveryRequest.Backup request =
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
            tempDirectory.resolve("source.sqlite"), new UUID(7L, 10L));
    PublicationTransactionResult incomplete = incompleteResult();
    ContextRecoveryService service =
        new ContextRecoveryService(
            Optional.of(new PublicationTransactionRecoveryReceipt(incomplete, List.of())));
    SqliteProtectedBookPairPublicationPreparation preparation =
        SqliteProtectedBookPairPublicationPreparation.journaledForTesting(
            maintenanceStore(), () -> service);

    ProtectedBookPairPublicationAdmission admission =
        preparation.admit(
            targets.bookTargetPath(),
            targets.secretTargetPath(),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            request,
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
            leases(targets));

    ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete recovered =
        assertInstanceOf(
            ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete.class,
            admission);
    assertEquals(incomplete, recovered.transactionResult());
    assertEquals(targets.bookTargetPath(), recovered.candidateArtifactPath());
    assertEquals(1, service.ownerContextLookups());
  }

  @Test
  void rejectsARecoveredJournalWhoseMemberRoleDoesNotProveTheSelectedPair() throws Exception {
    PairTargets targets = pairTargets();
    ProtectedBookPairPublicationRecoveryRequest.Backup request =
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
            tempDirectory.resolve("source.sqlite"), new UUID(7L, 11L));
    PublicationTransactionResult completed = completedResult();
    PublicationTransactionRecoveryReceipt receipt =
        new PublicationTransactionRecoveryReceipt(
            completed,
            List.of(
                artifact(
                    SqlitePublicationTransactionPair.BOOK_MEMBER_ID,
                    PublicationTransactionMemberRole.PDF_REPORT,
                    targets.bookTargetPath(),
                    completed),
                artifact(
                    SqlitePublicationTransactionPair.SECRET_MEMBER_ID,
                    PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY,
                    targets.secretTargetPath(),
                    completed)));
    SqliteProtectedBookPairPublicationPreparation preparation =
        SqliteProtectedBookPairPublicationPreparation.journaledForTesting(
            maintenanceStore(), () -> new ContextRecoveryService(Optional.of(receipt)));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            preparation.admit(
                targets.bookTargetPath(),
                targets.secretTargetPath(),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                request,
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                leases(targets)));
  }

  @Test
  void classifiesCompleteAndBlockedTargetsWithoutReservingAnotherJournal() throws Exception {
    PairTargets completeTargets = pairTargets("journal-recovery-complete");
    Files.writeString(completeTargets.bookTargetPath(), "backup");
    Files.writeString(completeTargets.secretTargetPath(), "key");
    SqliteProtectedBookPairPublicationPreparation preparation =
        SqliteProtectedBookPairPublicationPreparation.journaledForTesting(
            maintenanceStore(), () -> new ContextRecoveryService(Optional.empty()));

    assertInstanceOf(
        ProtectedBookPairPublicationAdmission.ExistingCompleteBackup.class,
        preparation.admit(
            completeTargets.bookTargetPath(),
            completeTargets.secretTargetPath(),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            backupRequest(completeTargets.bookTargetPath(), 12L),
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
            leases(completeTargets)));

    PairTargets blockedTargets = pairTargets("journal-recovery-blocked");
    Files.writeString(blockedTargets.bookTargetPath(), "restored book");
    Files.writeString(blockedTargets.secretTargetPath(), "restored key");
    assertInstanceOf(
        ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class,
        preparation.admit(
            blockedTargets.bookTargetPath(),
            blockedTargets.secretTargetPath(),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                blockedTargets.bookTargetPath(),
                blockedTargets.secretTargetPath(),
                new dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement(
                    new UUID(7L, 13L), new byte[32], java.math.BigInteger.ZERO, new byte[32])),
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
            ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET,
            leases(blockedTargets)));
  }

  @Test
  void failsClosedWhenTheJournalCannotOpenAndWhenRetiredResidueRemains() throws Exception {
    PairTargets targets = pairTargets("journal-recovery-residue");
    SqliteProtectedBookPairPublicationPreparation unavailable =
        SqliteProtectedBookPairPublicationPreparation.journaledForTesting(
            maintenanceStore(),
            () -> {
              throw new IOException("journal unavailable");
            });
    assertThrows(
        IllegalStateException.class,
        () ->
            unavailable.admit(
                targets.bookTargetPath(),
                targets.secretTargetPath(),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(targets.bookTargetPath(), 14L),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                leases(targets)));

    Files.writeString(
        targets.bookTargetPath().resolveSibling(".fingrind-protected-book-pair-retired"),
        "opaque residue");
    SqliteProtectedBookPairPublicationPreparation available =
        SqliteProtectedBookPairPublicationPreparation.journaledForTesting(
            maintenanceStore(), () -> new ContextRecoveryService(Optional.empty()));
    assertInstanceOf(
        ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class,
        available.admit(
            targets.bookTargetPath(),
            targets.secretTargetPath(),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            backupRequest(targets.bookTargetPath(), 15L),
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
            leases(targets)));
  }

  @Test
  void failsClosedWhenCleanAdmissionCannotOpenItsJournalReservation() throws Exception {
    PairTargets targets = pairTargets("journal-reservation-open-failure");
    AtomicInteger opens = new AtomicInteger();
    SqliteProtectedBookPairPublicationPreparation preparation =
        SqliteProtectedBookPairPublicationPreparation.journaledForTesting(
            maintenanceStore(),
            () -> {
              if (opens.getAndIncrement() == 0) {
                return new ContextRecoveryService(Optional.empty());
              }
              throw new IOException("reservation journal unavailable");
            });

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                preparation.admit(
                    targets.bookTargetPath(),
                    targets.secretTargetPath(),
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(targets.bookTargetPath(), 16L),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    leases(targets)));
    assertInstanceOf(IOException.class, failure.getCause());
    assertEquals(2, opens.get());
  }

  private PairTargets pairTargets() throws IOException {
    return pairTargets("journal-recovery-targets");
  }

  private PairTargets pairTargets(String directoryName) throws IOException {
    Path parent = tempDirectory.resolve(directoryName);
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    return new PairTargets(parent.resolve("backup.sqlite"), parent.resolve("backup.key"));
  }

  private static SqliteTargetAdmissionLeases leases(PairTargets targets) {
    return new SqliteTargetAdmissionLeases(
        new SqliteHeldLease(targets.bookTargetPath(), () -> {}),
        new SqliteHeldLease(targets.secretTargetPath(), () -> {}));
  }

  private static PublicationTransactionMemberArtifact artifact(
      String memberId,
      PublicationTransactionMemberRole role,
      Path targetPath,
      PublicationTransactionResult result) {
    return new PublicationTransactionMemberArtifact(
        memberId, role, new PublicationTransactionArtifact(targetPath, result));
  }

  private ProtectedBookPairPublicationRecoveryRequest.Backup backupRequest(
      Path sourceBookPath, long backupIdLeastSignificantBits) {
    return new ProtectedBookPairPublicationRecoveryRequest.Backup(
        sourceBookPath, new UUID(7L, backupIdLeastSignificantBits));
  }

  private static PublicationTransactionResult completedResult() {
    return result(
        PublicationTransactionState.COMPLETE,
        PublicationCommitOutcome.ALL_COMMITTED,
        PublicationCleanupOutcome.COMPLETE);
  }

  private static PublicationTransactionResult incompleteResult() {
    return result(
        PublicationTransactionState.BLOCKED,
        PublicationCommitOutcome.NONE_COMMITTED,
        PublicationCleanupOutcome.INCOMPLETE);
  }

  private static PublicationTransactionResult result(
      PublicationTransactionState state,
      PublicationCommitOutcome commitOutcome,
      PublicationCleanupOutcome cleanupOutcome) {
    return new PublicationTransactionResult(
        new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
        state,
        new PublicationTransactionOutcome(commitOutcome, cleanupOutcome));
  }

  private record PairTargets(Path bookTargetPath, Path secretTargetPath) {}

  /** Supplies a controlled owner-context recovery result without publishing a transaction. */
  private static final class ContextRecoveryService implements PublicationTransactionService {
    private final Optional<PublicationTransactionRecoveryReceipt> recovered;
    private @Nullable PublicationTransactionOwnerContext ownerContext;
    private int ownerContextLookups;

    private ContextRecoveryService(Optional<PublicationTransactionRecoveryReceipt> recovered) {
      this.recovered = recovered;
    }

    @Override
    public PublicationTransactionResult publish(PublicationTransactionRequest request) {
      throw new AssertionError("Journal recovery must not publish a second protected-book pair.");
    }

    @Override
    public PublicationTransactionStageReservation reserveStages(
        PublicationTransactionRequest request) {
      throw new AssertionError("Journal recovery must not reserve a second protected-book pair.");
    }

    @Override
    public PublicationTransactionResult publishReservedStages(
        PublicationTransactionStageReservation reservation) {
      throw new AssertionError("Journal recovery must not publish a second protected-book pair.");
    }

    @Override
    public PublicationTransactionResult recover(PublicationTransactionId transactionId) {
      throw new AssertionError(
          "Owner-context recovery must return its immutable receipt directly.");
    }

    @Override
    public PublicationTransactionRecoveryReceipt recoverWithReceipt(
        PublicationTransactionId transactionId) {
      throw new AssertionError(
          "Owner-context recovery must return its immutable receipt directly.");
    }

    @Override
    public Optional<PublicationTransactionRecoveryReceipt> recoverMatchingOwnerContext(
        PublicationTransactionOwnerContext ownerContext) {
      this.ownerContext = ownerContext;
      ownerContextLookups++;
      return recovered;
    }

    private PublicationTransactionOwnerContext ownerContext() {
      return Objects.requireNonNull(ownerContext, "ownerContext");
    }

    private int ownerContextLookups() {
      return ownerContextLookups;
    }
  }
}
