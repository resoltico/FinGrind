package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contract coverage for protected-book publication facts and their failure-only projections. */
class ProtectedBookPairPublicationFailureContractTest extends ContractTestSupport {
  @TempDir Path temporaryDirectory;

  @Test
  void failureFactories_projectOnlyAuthoritativePairEvidence() {
    ContractFailureDetails.PairPublication recoveredPair = recoveredPair();
    ContractFailure uncertain =
        ContractErrors.protectedBookPairPublicationUncertainFailure(
            OperationId.BACKUP_BOOK, recoveredPair);
    ContractFailureDetails.ProtectedBookPairPublicationUncertain uncertainDetails =
        assertInstanceOf(
            ContractFailureDetails.ProtectedBookPairPublicationUncertain.class,
            uncertain.details());
    assertEquals(OperationId.BACKUP_BOOK, uncertainDetails.operation());
    ProtectedBookPairPublicationRetention recoveredRetention =
        Objects.requireNonNull(
            recoveredPair.pairPublicationRetention(), "recovered pair retention");
    assertEquals(
        List.of(
            recoveredPair.generatedSecretTarget().path(),
            recoveredRetention.bookPublication().retention().retainedStagePath(),
            recoveredRetention.generatedSecretPublication().retention().retainedStagePath()),
        Objects.requireNonNull(uncertain.paths(), "uncertain failure paths").relatedPaths());

    ContractFailureDetails.PairPublication evidenceBlockedPair = evidenceBlockedPair();
    ContractFailure evidenceBlocked =
        ContractErrors.protectedBookPairPublicationEvidenceBlockedFailure(evidenceBlockedPair);
    assertInstanceOf(
        ContractFailureDetails.ProtectedBookPairPublicationEvidenceBlocked.class,
        evidenceBlocked.details());
    assertEquals(
        List.of(evidenceBlockedPair.generatedSecretTarget().path()),
        Objects.requireNonNull(evidenceBlocked.paths(), "evidence-blocked failure paths")
            .relatedPaths());
  }

  @Test
  void completionUncertainty_requiresOneMaintenanceOperationAndEstablishedMembers() {
    ContractFailureDetails.PairPublication recoveredPair = recoveredPair();
    assertEquals(
        OperationId.BACKUP_BOOK,
        new ContractFailureDetails.ProtectedBookPairPublicationUncertain(
                OperationId.BACKUP_BOOK, recoveredPair)
            .operation());
    assertEquals(
        OperationId.RESTORE_BOOK,
        new ContractFailureDetails.ProtectedBookPairPublicationUncertain(
                OperationId.RESTORE_BOOK, recoveredPair)
            .operation());
    assertEquals(
        OperationId.REKEY_BOOK,
        new ContractFailureDetails.ProtectedBookPairPublicationUncertain(
                OperationId.REKEY_BOOK, recoveredPair)
            .operation());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailureDetails.ProtectedBookPairPublicationUncertain(
                OperationId.OPEN_BOOK, recoveredPair()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailureDetails.ProtectedBookPairPublicationUncertain(
                OperationId.BACKUP_BOOK, evidenceBlockedPair()));
  }

  @Test
  void pairFacts_rejectAmbiguityAndOnlyEvidenceBlockedFacts_are_unestablished() {
    Path book = temporaryDirectory.resolve("book.sqlite");
    Path secret = temporaryDirectory.resolve("book.key");
    ProtectedBookPairPublicationRetention retention = retention(book, secret);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            pair(
                book,
                ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                book,
                ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
                retention));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            pair(
                book,
                ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                secret,
                ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                nullOf(),
                nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            pair(
                book,
                ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
                secret,
                ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE,
                ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
                nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            pair(
                book,
                ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                secret,
                ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                nullOf(),
                retention));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailureDetails.ProtectedBookPairPublicationEvidenceBlocked(
                recoveredPair()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailureDetails.ProtectedBookPairPublicationEvidenceBlocked(
                pair(
                    book,
                    ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                    secret,
                    ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                    nullOf(),
                    nullOf())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailureDetails.ProtectedBookPairPublicationEvidenceBlocked(
                pair(
                    book,
                    ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                    secret,
                    ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                    nullOf(),
                    nullOf())));
  }

  @Test
  void retainedPublicationFacts_reject_all_aliasing_forms_and_require_exact_final_members()
      throws IOException {
    Path book = temporaryDirectory.resolve("book.sqlite");
    Path secret = temporaryDirectory.resolve("book.key");
    Path bookStage = temporaryDirectory.resolve(".book-stage");
    Path secretStage = temporaryDirectory.resolve(".secret-stage");
    ProtectedBookPairPublicationRetention retention = retention(book, secret);
    assertEquals(retention.bookPublication(), retention.requireBookPublication(book));
    assertEquals(
        retention.generatedSecretPublication(),
        retention.requireGeneratedSecretPublication(secret));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublicationRetention(
                publication(book, bookStage), publication(book, secretStage)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublicationRetention(
                publication(book, bookStage), publication(secret, bookStage)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublicationRetention(
                publication(book, secret), publication(secret, secretStage)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublicationRetention(
                publication(book, bookStage), publication(secret, book)));
    assertThrows(
        IllegalArgumentException.class,
        () -> retention.requireBookPublication(temporaryDirectory.resolve("other.sqlite")));
    assertThrows(
        IllegalArgumentException.class,
        () -> retention.requireGeneratedSecretPublication(Path.of("/")));
    Path loop = temporaryDirectory.resolve("path-resolution-loop");
    Files.createSymbolicLink(loop, loop.getFileName());
    assertThrows(
        IllegalArgumentException.class,
        () -> retention.requireGeneratedSecretPublication(loop.resolve("unreachable.key")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion
                .requireRetention(
                    dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion
                        .ALREADY_PUBLISHED,
                    retention));
  }

  private ContractFailureDetails.PairPublication recoveredPair() {
    Path book = temporaryDirectory.resolve("recovered.sqlite");
    Path secret = temporaryDirectory.resolve("recovered.key");
    return pair(
        book,
        ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
        secret,
        ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
        ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
        retention(book, secret));
  }

  private ContractFailureDetails.PairPublication evidenceBlockedPair() {
    Path book = temporaryDirectory.resolve("blocked.sqlite");
    Path secret = temporaryDirectory.resolve("blocked.key");
    return pair(
        book,
        ProtectedBookPairPublicationMemberState.UNESTABLISHED,
        secret,
        ProtectedBookPairPublicationMemberState.UNESTABLISHED,
        nullOf(),
        nullOf());
  }

  private static ContractFailureDetails.PairPublication pair(
      Path book,
      ProtectedBookPairPublicationMemberState bookState,
      Path secret,
      ProtectedBookPairPublicationMemberState secretState,
      ProtectedBookPairPublicationRecoveryRecordState recoveryRecordState,
      ProtectedBookPairPublicationRetention retention) {
    return new ContractFailureDetails.PairPublication(
        new ContractFailureDetails.PairPublicationMember(book, bookState),
        new ContractFailureDetails.PairPublicationMember(secret, secretState),
        recoveryRecordState,
        retention);
  }

  private static ProtectedBookPairPublicationRetention retention(Path book, Path secret) {
    return new ProtectedBookPairPublicationRetention(
        publication(book, book.resolveSibling(".book-stage")),
        publication(secret, secret.resolveSibling(".secret-stage")));
  }

  private static ArtifactPublicationResult publication(Path finalPath, Path stagePath) {
    return new ArtifactPublicationResult(finalPath, new ArtifactPublicationRetention(stagePath));
  }
}
