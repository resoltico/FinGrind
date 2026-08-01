package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Behavioral coverage for maintenance-lease authority failure boundaries. */
class SqliteMaintenanceLeaseAuthorityTest {
  @Test
  void physicalDirectoryIdentityFailuresNeverAuthorizeOrSilentlyBypassLeaseAdmission()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parent = fileSystem.path("\\targets");
      parent.exists = true;
      parent.regularFile = false;
      IOException identityFailure = new IOException("injected canonical directory failure");
      parent.failToRealPathWith(identityFailure);
      AclFixturePath target = fileSystem.path("\\targets\\book.sqlite");

      IllegalStateException admissionFailure =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteMaintenanceLeaseAuthority.requireNoActiveLease(target));
      assertEquals(identityFailure, admissionFailure.getCause());
      assertFalse(SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(target));
    }
  }

  @Test
  void existingArtifactIntentRefusesNonregularArtifacts() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parent = fileSystem.path("\\targets");
      parent.exists = true;
      parent.regularFile = false;
      AclFixturePath target = fileSystem.path("\\targets\\book.sqlite");
      target.exists = true;
      target.regularFile = false;

      SqliteCallerPathContractException exception =
          assertThrows(
              SqliteCallerPathContractException.class,
              () ->
                  SqliteMaintenanceLeaseAuthority.validateArtifactForLeaseIntent(
                      target, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT));

      assertEquals(
          SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
          exception.pathFailure());
    }
  }
}
