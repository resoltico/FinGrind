package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.sqlite.ManagedSqliteRuntimeUnavailableException;
import dev.erst.fingrind.sqlite.SqliteStorageFailureException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies deterministic public failure classification for the published CLI contract. */
class CliFailureMapperTest {
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
    assertTrue(failure.message().startsWith("Failed to write PDF export to "));
    assertTrue(failure.message().contains("out.pdf"));
    assertEquals("--pdf-out", failure.argument());
    assertNotNull(failure.hint());
    assertTrue(failure.hint().contains("filesystem space"));
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
    assertTrue(managedFailure.hint().contains("prepareManagedSqlite"));
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
  void internalError_mapsToOpaquePublishedFailure() {
    CliFailure failure = CliFailureMapper.internalError("fg-internal-123");

    assertEquals("internal-error", failure.code());
    assertTrue(failure.message().contains("fg-internal-123"));
    assertNotNull(failure.hint());
    assertTrue(failure.hint().contains("diagnostic stream"));
  }

  @Test
  void internalError_rejectsBlankErrorIds() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> CliFailureMapper.internalError("  "));

    assertEquals("errorId must not be blank.", exception.getMessage());
  }
}
