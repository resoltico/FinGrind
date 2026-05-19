package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link SqliteBookKeyFileGenerator}. */
class SqliteBookKeyFileGeneratorTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void generate_publicFactoryCreatesSecureUtf8KeyFile() throws Exception {
    Path keyFile = tempDirectory.resolve("public-acme.book-key");
    SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile =
        SqliteBookKeyFileGenerator.generate(keyFile);
    assertEquals(keyFile.toAbsolutePath().normalize(), generatedKeyFile.bookKeyFilePath());
    assertTrue(Files.isRegularFile(keyFile));
  }

  @Test
  void generate_createsOneNewSecureUtf8KeyFile() throws Exception {
    Path keyFile = tempDirectory.resolve("keys").resolve("acme.book-key");
    SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile =
        SqliteBookKeyFileGenerator.generate(keyFile, deterministicRandom());
    assertEquals(keyFile.toAbsolutePath().normalize(), generatedKeyFile.bookKeyFilePath());
    assertEquals("base64url-no-padding", generatedKeyFile.encoding());
    assertEquals(256, generatedKeyFile.entropyBits());
    assertTrue(Files.isRegularFile(keyFile));
    if (supportsPosix(keyFile)) {
      assertEquals("0600", generatedKeyFile.permissions());
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          Files.getPosixFilePermissions(keyFile));
      assertEquals(
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE),
          Files.getPosixFilePermissions(keyFile.getParent()));
    } else {
      assertEquals("owner-only-acl", generatedKeyFile.permissions());
    }
    String generatedSecret = Files.readString(keyFile, StandardCharsets.UTF_8);
    assertTrue(generatedSecret.matches("[A-Za-z0-9_-]{43}"));
    try (SqliteBookPassphrase passphrase = SqliteBookKeyFile.load(keyFile)) {
      assertEquals(
          generatedSecret.getBytes(StandardCharsets.UTF_8).length, passphrase.byteLength());
    }
  }

  @Test
  void generate_rejectsExistingKeyFiles() throws Exception {
    Path keyFile = tempDirectory.resolve("existing.book-key");
    SqliteBookKeyFileGenerator.generate(keyFile, deterministicRandom());
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> SqliteBookKeyFileGenerator.generate(keyFile));
    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains("already exists and will not be overwritten"));
  }

  @Test
  void generate_customMaterializerFactoryReturnsAcceptedMetadataOnSuccess() throws Exception {
    Path keyFile = tempDirectory.resolve("custom-materializer.book-key");
    SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile =
        SqliteBookKeyFileGenerator.generate(
            keyFile,
            deterministicRandom(),
            (normalizedPath, encodedPassphrase) -> Files.write(normalizedPath, encodedPassphrase));
    assertEquals(keyFile.toAbsolutePath().normalize(), generatedKeyFile.bookKeyFilePath());
    assertTrue(Files.isRegularFile(keyFile));
  }

  @Test
  void generate_rejectsKeyPathWhoseParentResolvesToAFile() throws Exception {
    Path blockingParent = tempDirectory.resolve("blocking-parent");
    Files.writeString(blockingParent, "not-a-directory", StandardCharsets.UTF_8);
    Path nestedKeyFile = blockingParent.resolve("entity.book-key");
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteBookKeyFileGenerator.generate(nestedKeyFile, deterministicRandom()));
    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains("must resolve beneath an existing directory"));
    assertFalse(Files.exists(nestedKeyFile));
  }

  @Test
  void generate_cleansUpCreatedKeyFileWhenMaterializationFails() throws Exception {
    Path keyFile = tempDirectory.resolve("cleanup.book-key");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookKeyFileGenerator.generate(
                    keyFile,
                    deterministicRandom(),
                    (normalizedPath, encodedPassphrase) -> {
                      Files.write(normalizedPath, encodedPassphrase);
                      throw new IOException("simulated materialization failure");
                    }));
    assertTrue(
        NullTestSupport.messageOf(exception).contains(PublicPathHint.fromPath(keyFile).value()));
    assertEquals(
        "simulated materialization failure",
        NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    assertFalse(Files.exists(keyFile));
  }

  @Test
  void generateDecision_wrapsIoFailuresBeforeAnyKeyFileWasCreated() {
    Path keyFile = tempDirectory.resolve("precreate-failure.book-key");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookKeyFileGenerator.generateDecision(
                        keyFile,
                        deterministicRandom(),
                        (normalizedPath, encodedPassphrase) -> {},
                        normalizedPath -> {
                          throw new IOException("parent boom");
                        },
                        normalizedPath ->
                            dev.erst.fingrind.contract.runtime.ContractDecision.accepted(
                                normalizedPath))
                    .requireAccepted());

    assertTrue(
        NullTestSupport.messageOf(exception).contains(PublicPathHint.fromPath(keyFile).value()));
    assertEquals("parent boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    assertFalse(Files.exists(keyFile));
  }

  @Test
  void helperBoundaries_enforceSecureFilesystemAndMetadataContracts() throws Exception {
    assertDoesNotThrow(
        () ->
            SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(
                tempDirectory.resolve("ok.book-key")));
    assertDoesNotThrow(() -> SqliteBookKeyFileGenerator.ensureParentDirectory(Path.of("/")));
    Path zipArchive = tempDirectory.resolve("zipfs-book-key.zip");
    try (FileSystem zipFileSystem =
        FileSystems.newFileSystem(
            URI.create("jar:" + zipArchive.toUri()), Map.of("create", "true"))) {
      IllegalArgumentException nonSecureFilesystemException =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(
                      zipFileSystem.getPath("/keys/acme.book-key")));
      assertTrue(
          NullTestSupport.messageOf(nonSecureFilesystemException)
              .contains("supports POSIX owner-only permissions or Windows owner-only ACLs"));
    }
    Path nonEmptyDirectory =
        Files.createDirectory(tempDirectory.resolve("non-empty-delete-target"));
    Files.writeString(nonEmptyDirectory.resolve("child.txt"), "keep", StandardCharsets.UTF_8);
    List<String> cleanupReports = new ArrayList<>();
    assertDoesNotThrow(
        () ->
            SqliteBookKeyFileGenerator.deleteQuietly(
                nonEmptyDirectory,
                (action, exception) ->
                    cleanupReports.add(action + "|" + exception.getClass().getSimpleName())));
    assertTrue(Files.exists(nonEmptyDirectory));
    assertEquals(
        List.of("deleting one partially created book-key path|DirectoryNotEmptyException"),
        cleanupReports);
    if (supportsPosix(tempDirectory)) {
      Path lockedDirectory = tempDirectory.resolve("locked");
      Files.createDirectory(lockedDirectory);
      Path lockedFile = lockedDirectory.resolve("locked.book-key");
      Files.writeString(lockedFile, "keep", StandardCharsets.UTF_8);
      Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(lockedDirectory);
      Files.setPosixFilePermissions(
          lockedDirectory,
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
      try {
        assertDoesNotThrow(() -> SqliteBookKeyFileGenerator.deleteQuietly(lockedFile));
        assertTrue(Files.exists(lockedFile));
      } finally {
        Files.setPosixFilePermissions(lockedDirectory, originalPermissions);
      }
    }
  }

  @Test
  void generatedKeyFile_rejectsInvalidMetadata() throws Exception {
    Path directoryPath = Files.createDirectory(tempDirectory.resolve("book-key-directory"));
    Path absentPath = tempDirectory.resolve("absent.book-key");
    IllegalArgumentException directoryException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteBookKeyFileGenerator.GeneratedKeyFile(
                    directoryPath, "base64url-no-padding", 256, "0600"));
    assertEquals("bookKeyFilePath must identify a regular file.", directoryException.getMessage());
    IllegalArgumentException blankEncodingException =
        assertThrows(
            IllegalArgumentException.class,
            () -> new SqliteBookKeyFileGenerator.GeneratedKeyFile(absentPath, " ", 256, "0600"));
    assertEquals("encoding must not be blank.", blankEncodingException.getMessage());
    IllegalArgumentException nonPositiveEntropyException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteBookKeyFileGenerator.GeneratedKeyFile(
                    absentPath, "base64url-no-padding", 0, "0600"));
    assertEquals("entropyBits must be positive.", nonPositiveEntropyException.getMessage());
    IllegalArgumentException blankPermissionsException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteBookKeyFileGenerator.GeneratedKeyFile(
                    absentPath, "base64url-no-padding", 256, " "));
    assertEquals("permissions must not be blank.", blankPermissionsException.getMessage());
  }

  private static SecureRandom deterministicRandom() {
    return new SecureRandom() {
      private static final long serialVersionUID = 1L;

      @Override
      public void nextBytes(byte[] bytes) {
        for (int index = 0; index < bytes.length; index++) {
          bytes[index] = (byte) index;
        }
      }
    };
  }

  private static boolean supportsPosix(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains("posix");
  }
}
