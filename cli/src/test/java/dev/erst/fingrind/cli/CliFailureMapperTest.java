package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import dev.erst.fingrind.sqlite.ManagedSqliteRuntimeUnavailableException;
import dev.erst.fingrind.sqlite.SqlitePersistenceInvariantException;
import dev.erst.fingrind.sqlite.SqliteStorageFailureException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies deterministic public failure classification for the published CLI contract. */
class CliFailureMapperTest {
  @Test
  void runtimeFailure_mapsExistingArtifactDestinationsToDeterministicRefusal() {
    CliFailure failure =
        CliFailureMapper.runtimeFailure(
            new CliArtifactOutputExistsException(Path.of("reports/out.pdf"), "--pdf-out"));

    assertNotNull(failure);
    assertEquals("artifact-output-already-exists", failure.code());
    assertTrue(failure.message().contains("already exists"));
    assertEquals(Path.of("reports/out.pdf"), failure.path());
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
  void runtimeFailure_mapsPdfExportFailuresToDedicatedPublicCode() {
    CliFailure failure =
        CliFailureMapper.runtimeFailure(
            new CliPdfExportException(
                Path.of("reports/out.pdf"), new java.io.IOException("disk full")));

    assertNotNull(failure);
    assertEquals("pdf-export-failure", failure.code());
    assertEquals("Failed to write the PDF export.", failure.message());
    assertEquals(Path.of("reports/out.pdf"), failure.path());
    assertEquals("--pdf-out", failure.argument());
    assertNotNull(failure.hint());
    assertTrue(failure.hint().contains("filesystem space"));
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
