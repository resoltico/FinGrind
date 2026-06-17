package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliArtifactPathFailureDetails;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenancePathFailure;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Focused maintenance-path failure coverage extracted from the omnibus maintenance test. */
class CliMaintenancePathFailureCoverageTest extends CliResponseWriterTestSupport {
  @Test
  void maintenanceRejectionPayloadMapper_rendersArtifactPathFailureHintAndDetails() {
    CliEnvelopeJsonModels.RejectedEnvelope envelope =
        CliRejectionPayloadMapper.maintenanceRejectedEnvelope(
            new BookMaintenanceRejection.ArtifactPathInvalid(
                dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole.BACKUP_TARGET,
                hint(Path.of("backup/entity.sqlite")),
                BookMaintenancePathFailure.PARENT_PATH_COLLISION));

    assertNotNull(envelope.hint());
    assertTrue(envelope.hint().contains("parent chain is made only of real directories"));
    assertInstanceOf(CliArtifactPathFailureDetails.class, envelope.details());
  }

  @Test
  void maintenanceRejectionPayloadMapper_coversEveryArtifactPathFailureHintVariant() {
    assertMaintenanceHint(
        BookMaintenancePathFailure.MISSING_PARENT_DIRECTORY,
        "already exists or whose missing parent chain");
    assertMaintenanceHint(
        BookMaintenancePathFailure.PARENT_PATH_COLLISION, "made only of real directories");
    assertMaintenanceHint(
        BookMaintenancePathFailure.PARENT_OWNER_ACCESS_REQUIRED, "owner can traverse and write");
    assertMaintenanceHint(
        BookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED, "tighten the existing parent");
    assertMaintenanceHint(
        BookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        "regular non-symlink artifact path");
    assertMaintenanceHint(
        BookMaintenancePathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
        "supports POSIX owner-only permissions or Windows owner-only ACLs");
  }

  @Test
  void maintenanceExitCodes_mapArtifactPathInvalidToExitCodeSix() {
    assertEquals(
        6,
        CliAdministrativeExitCodes.exitCodeFor(
            new BackupBookResult.Rejected(
                new BookMaintenanceRejection.ArtifactPathInvalid(
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole
                        .BACKUP_TARGET,
                    hint(Path.of("backup/entity.sqlite")),
                    BookMaintenancePathFailure.PARENT_PATH_COLLISION))));
  }

  @Test
  void failureOutputRenderer_rendersArtifactPathFailureRows() {
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(
            "maintenance-rejected",
            "Maintenance rejected.",
            "Repair it.",
            null,
            new CliArtifactPathFailureDetails(
                "backup-target", "<redacted>/backup.sqlite", "parent-path-collision"));

    assertTrue(rendered.contains("Artifact role"));
    assertTrue(rendered.contains("backup-target"));
    assertTrue(rendered.contains("Artifact path"));
    assertTrue(rendered.contains("<redacted>/backup.sqlite"));
    assertTrue(rendered.contains("Path failure"));
    assertTrue(rendered.contains("parent-path-collision"));
  }

  private static void assertMaintenanceHint(
      BookMaintenancePathFailure pathFailure, String expectedHintFragment) {
    CliEnvelopeJsonModels.RejectedEnvelope envelope =
        CliRejectionPayloadMapper.maintenanceRejectedEnvelope(
            new BookMaintenanceRejection.ArtifactPathInvalid(
                dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole.BACKUP_TARGET,
                hint(Path.of("backup/entity.sqlite")),
                pathFailure));
    assertNotNull(envelope.hint());
    assertTrue(envelope.hint().contains(expectedHintFragment), envelope.hint());
  }
}
