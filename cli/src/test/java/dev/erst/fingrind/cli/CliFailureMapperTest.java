package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import dev.erst.fingrind.sqlite.ManagedSqliteRuntimeUnavailableException;
import dev.erst.fingrind.sqlite.SqlitePersistenceInvariantException;
import dev.erst.fingrind.sqlite.SqliteProtectedBookVerificationException;
import dev.erst.fingrind.sqlite.SqliteStorageFailureException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies deterministic public failure classification for the published CLI contract. */
class CliFailureMapperTest {
  @Test
  void runtimeFailure_mapsExistingArtifactDestinationsToDeterministicRefusal() {
    CliFailure failure =
        CliFailureMapper.runtimeFailure(
            new CliArtifactOutputExistsException(
                Path.of("reports/out.pdf"),
                "--pdf-out",
                new ArtifactPublicationRetention(Path.of("reports/.out.pdf-stage")),
                new java.nio.file.FileAlreadyExistsException("reports/out.pdf")));

    assertNotNull(failure);
    assertEquals("artifact-output-already-exists", failure.code());
    assertTrue(failure.message().contains("already exists"));
    assertEquals(Path.of("reports/out.pdf").toAbsolutePath().normalize(), failure.path());
    assertFalse(failure.message().contains("reports/out.pdf"));
    assertEquals("--pdf-out", failure.argument());
    assertNotNull(failure.hint());
    assertTrue(failure.hint().contains("Choose a missing --pdf-out destination"));
    assertTrue(failure.hint().contains("remove the existing artifact"));
  }

  @Test
  void contractFailure_preservesWrappedContractFailures() {
    ContractFailureException exception =
        new ContractFailureException(
            ContractErrors.Descriptor.INVALID_REQUEST.failure(
                "Invalid request.", "Repair it.", "--request-file"));
    CliFailure failure = CliFailureMapper.contractFailure(exception.failure());

    assertEquals("invalid-request", failure.code());
    assertEquals("Invalid request.", failure.message());
    assertEquals("Repair it.", failure.hint());
    assertEquals("--request-file", failure.argument());
  }

  @Test
  void contractFailure_mapsUnsupportedBookFormatDetailsWithoutLosingVersionFacts() {
    CliFailure failure =
        CliFailureMapper.contractFailure(ContractErrors.unsupportedBookFormatVersionFailure(7, 8));

    assertEquals("unsupported-book-format-version", failure.code());
    assertEquals(
        "The selected FinGrind book uses format version 7, but this FinGrind binary supports"
            + " version 8 only.",
        failure.message());
    assertEquals("--book-file", failure.argument());
    var details =
        assertInstanceOf(
            dev.erst.fingrind.cli.json.CliErrorJsonModels.UnsupportedBookFormatVersionDetails.class,
            failure.details());
    assertEquals(7, details.detectedBookFormatVersion());
    assertEquals(8, details.supportedBookFormatVersion());
  }

  @Test
  void contractFailure_projectsEvidenceBlockedProtectedBookPairWithCanonicalFinalPaths() {
    Path bookTarget = Path.of("books/recovered.sqlite").toAbsolutePath().normalize();
    Path secretTarget = Path.of("keys/recovered.book-key").toAbsolutePath().normalize();
    CliFailure failure =
        CliFailureMapper.contractFailure(
            ContractErrors.protectedBookPairPublicationEvidenceBlockedFailure(
                new ContractFailureDetails.PairPublication(
                    new ContractFailureDetails.PairPublicationMember(
                        bookTarget, ProtectedBookPairPublicationMemberState.UNESTABLISHED),
                    new ContractFailureDetails.PairPublicationMember(
                        secretTarget, ProtectedBookPairPublicationMemberState.UNESTABLISHED))));

    assertEquals("protected-book-pair-publication-evidence-blocked", failure.code());
    String hint = java.util.Objects.requireNonNull(failure.hint(), "failure hint");
    assertTrue(hint.contains("Preserve FinGrind pair evidence"));
    assertTrue(hint.contains("manually clean"));
    assertEquals(bookTarget, failure.path());
    assertEquals(List.of(secretTarget), failure.relatedPaths());
    assertNull(failure.argument());
    var details =
        assertInstanceOf(
            dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels
                .ProtectedBookPairPublicationEvidenceBlockedDetails.class,
            failure.details());
    assertEquals(
        CliPublicPaths.absoluteValue(bookTarget), details.pairPublication().bookTarget().path());
    assertEquals("unestablished", details.pairPublication().bookTarget().state().wireValue());
    assertEquals(
        CliPublicPaths.absoluteValue(secretTarget),
        details.pairPublication().generatedSecretTarget().path());
    assertEquals(
        "unestablished", details.pairPublication().generatedSecretTarget().state().wireValue());
  }

  @Test
  void runtimeFailure_mapsPdfExportFailuresToDedicatedPublicCode() {
    CliFailure failure =
        CliFailureMapper.runtimeFailure(
            new CliPdfExportException(
                Path.of("reports/out.pdf"), new java.io.IOException("disk full")));

    assertNotNull(failure);
    assertEquals("pdf-export-failure", failure.code());
    assertEquals("Failed to write the PDF export.", failure.message());
    assertEquals(Path.of("reports/out.pdf").toAbsolutePath().normalize(), failure.path());
    assertEquals("--pdf-out", failure.argument());
    assertNotNull(failure.hint());
    assertTrue(failure.hint().contains("filesystem space"));
  }

  @Test
  void runtimeFailure_mapsPostPublicationPdfDurabilityUncertaintyWithRetainedStageFacts() {
    Path publishedPath = Path.of("reports/out.pdf").toAbsolutePath().normalize();
    Path residualStagePath =
        Path.of("reports/.fingrind-pdf-stage.tmp").toAbsolutePath().normalize();
    CliFailure failure =
        CliFailureMapper.runtimeFailure(
            new CliPdfPublicationDurabilityException(
                new ArtifactPublicationResult(
                    publishedPath, new ArtifactPublicationRetention(residualStagePath)),
                new java.io.IOException("directory force failed")));

    assertNotNull(failure);
    assertEquals("artifact-publication-durability-uncertain", failure.code());
    assertEquals(
        "The requested artifact was published, but FinGrind could not confirm its directory"
            + " durability.",
        failure.message());
    assertEquals(publishedPath, failure.path());
    assertEquals(List.of(residualStagePath), failure.relatedPaths());
    assertEquals("--pdf-out", failure.argument());
    assertNotNull(failure.hint());
    assertTrue(failure.hint().contains("Preserve the reported artifact"));
    assertTrue(failure.hint().contains("do not retry this no-clobber target"));
    var details =
        assertInstanceOf(
            dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels
                .ArtifactPublicationDurabilityUncertainDetails.class,
            failure.details());
    assertEquals(CliPublicPaths.absoluteValue(publishedPath), details.publishedArtifact().path());
    assertEquals(
        CliPublicPaths.absoluteValue(residualStagePath),
        details.publishedArtifact().retainedStage());
    assertEquals(residualStagePath, failure.retainedStage());
  }

  @Test
  void runtimeFailure_mapsIndeterminatePdfPublicationOutcomeWithCandidateAndStageFacts() {
    Path candidatePath = Path.of("reports/out.pdf").toAbsolutePath().normalize();
    Path residualStagePath =
        Path.of("reports/.fingrind-pdf-stage.tmp").toAbsolutePath().normalize();
    CliFailure failure =
        CliFailureMapper.runtimeFailure(
            new CliPdfExportException(
                candidatePath,
                new ArtifactPublicationOutcomeUncertainException(
                    candidatePath,
                    new ArtifactPublicationRetention(residualStagePath),
                    new java.io.IOException("indeterminate link failure"))));

    assertNotNull(failure);
    assertEquals("artifact-publication-outcome-uncertain", failure.code());
    assertEquals(candidatePath, failure.path());
    assertEquals(List.of(residualStagePath), failure.relatedPaths());
    assertEquals("--pdf-out", failure.argument());
    var details =
        assertInstanceOf(
            dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels
                .ArtifactPublicationOutcomeUncertainDetails.class,
            failure.details());
    assertEquals(CliPublicPaths.absoluteValue(candidatePath), details.candidateArtifact());
    assertEquals(CliPublicPaths.absoluteValue(residualStagePath), details.retainedStage());
    assertEquals(residualStagePath, failure.retainedStage());
  }

  @Test
  void runtimeFailure_mapsStaleHeadToThePublishedCasRefusal() {
    CliFailure failure =
        CliFailureMapper.runtimeFailure(
            new AttestationStaleHeadException(
                new byte[32],
                new byte[] {
                  1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
                  24, 25, 26, 27, 28, 29, 30, 31, 32
                },
                BigInteger.valueOf(17L)));

    assertNotNull(failure);
    assertEquals("stale-head", failure.code());
    assertNotNull(failure.hint());
    assertTrue(failure.hint().contains("re-sign"));
    var details =
        assertInstanceOf(
            dev.erst.fingrind.cli.json.CliErrorJsonModels.StaleHeadDetails.class,
            failure.details());
    assertEquals("0".repeat(64), details.observedHead());
    assertEquals(
        "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20", details.currentHead());
    assertEquals("17", details.currentOrder());
  }

  @Test
  void runtimeFailure_mapsManagedRuntimeAndStorageCategoriesToDedicatedHints() {
    CliFailure managedFailure =
        CliFailureMapper.runtimeFailure(
            new RuntimeException(new ManagedSqliteRuntimeUnavailableException("runtime missing")));
    CliFailure storageFailure =
        CliFailureMapper.runtimeFailure(
            new RuntimeException(new SqliteStorageFailureException("storage broken")));

    assertNotNull(managedFailure);
    assertEquals("managed-runtime-failure", managedFailure.code());
    assertNotNull(managedFailure.hint());
    assertTrue(managedFailure.hint().contains(":cli:prepareSourceCheckoutCliRuntime"));
    assertNotNull(storageFailure);
    assertEquals("storage-runtime-failure", storageFailure.code());
    assertNotNull(storageFailure.hint());
    assertTrue(storageFailure.hint().contains("book file path"));
  }

  @Test
  void runtimeFailure_mapsProtectedBookVerificationToTheOpaquePublicFailure() {
    CliFailure failure =
        CliFailureMapper.runtimeFailure(
            new SqliteProtectedBookVerificationException(
                new IllegalStateException("native failure withheld")));

    assertNotNull(failure);
    assertEquals("protected-book-verification-failed", failure.code());
    assertTrue(failure.message().contains("authenticate and verify"));
    assertNotNull(failure.hint());
    assertTrue(failure.hint().contains("damaged or tampered"));
    assertFalse(failure.message().contains("native failure withheld"));
    assertFalse(failure.hint().contains("native failure withheld"));
  }

  @Test
  void runtimeFailure_returnsNullWhenNoPublicRuntimeClassifierApplies() {
    assertNull(CliFailureMapper.runtimeFailure(new RuntimeException()));
  }

  @Test
  void runtimeFailure_returnsNullForPersistenceInvariantWithoutGeneratedErrorId() {
    assertNull(
        CliFailureMapper.runtimeFailure(
            new RuntimeException(
                new SqlitePersistenceInvariantException("constraint leaked past validation"))));
  }

  @Test
  void runtimeFailure_mapsPersistenceInvariantBreachesToInternalErrorFamily() {
    CliFailure failure =
        CliFailureMapper.runtimeFailure(
            new RuntimeException(
                new SqlitePersistenceInvariantException("constraint leaked past validation")),
            "fg-internal-123");

    assertNotNull(failure);
    assertEquals("internal-error", failure.code());
    assertTrue(failure.message().contains("fg-internal-123"));
    assertTrue(failure.message().contains("An upstream invariant should have rejected"));
    assertNotNull(failure.hint());
    assertTrue(failure.hint().contains("a deterministic invariant leaked"));
    assertTrue(failure.hint().contains("pre-commit validation"));
  }

  @Test
  void internalError_mapsToOpaquePublishedFailure() {
    CliFailure failure = CliFailureMapper.internalError("fg-internal-123");

    assertEquals("internal-error", failure.code());
    assertTrue(failure.message().contains("fg-internal-123"));
    assertNotNull(failure.hint());
    assertTrue(failure.hint().contains("omitted raw stack traces"));
  }

  @Test
  void internalError_rejectsBlankErrorIds() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> CliFailureMapper.internalError("  "));

    assertEquals("errorId must not be blank.", exception.getMessage());
  }

  @Test
  void internalError_machineModesPreserveParseableDiagnosticsStream() {
    CliFailure failure = CliFailureMapper.internalError("fg-internal-123");

    assertEquals("internal-error", failure.code());
    assertNotNull(failure.hint());
    assertTrue(failure.hint().contains("the machine-readable error envelope on stderr"));
  }
}
