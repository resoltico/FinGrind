package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.core.attestation.AttestationEvidence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** Shared isolated attestation-inspection fixture and private-output setup. */
class AttestationInspectionServiceTestSupport {
  static final Instant RECORDED_AT = Instant.parse("2026-07-21T00:00:00Z");
  static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  final AttestationInspectionService service(Path bookPath, List<AttestationEvidence> evidence) {
    return new AttestationInspectionService(
        CLOCK, new AttestationMaintenanceTestSupport.Store(bookPath, evidence));
  }

  final AttestationMaintenanceTestSupport.CredentialFixture credential() throws IOException {
    return AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
  }

  final Path privateOutputDirectory(String name) throws IOException {
    return privateOutputDirectory(Files.createDirectories(temporaryDirectory.resolve(name)));
  }

  static Path privateOutputChildDirectory(Path parent, String name) throws IOException {
    return privateOutputDirectory(Files.createDirectories(parent.resolve(name)));
  }

  static Path privateOutputDirectory(Path directory) throws IOException {
    assumeTrue(
        directory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "POSIX permissions are unavailable on this filesystem.");
    Files.setPosixFilePermissions(
        directory,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
    return directory;
  }

  static AttestationEvidence genesis(
      AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT);
  }
}
