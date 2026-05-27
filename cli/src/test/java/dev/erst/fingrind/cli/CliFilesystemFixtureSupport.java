package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.sqlite.secret.SqliteBookKeyFile;
import dev.erst.fingrind.sqlite.secret.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** Shared test support for secure temporary files, key material, and JSON output capture. */
class CliFilesystemFixtureSupport {
  protected static final String TEST_BOOK_KEY = "cli-test-book-key";

  @TempDir protected Path tempDirectory;

  protected static PublicPathHint hint(Path path) {
    return PublicPathHint.fromPath(path);
  }

  @BeforeEach
  void hardenTempDirectory() {
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  protected Path writeRequest(String payload) throws IOException {
    return writeNamedRequest("request.json", payload);
  }

  protected Path writeNamedRequest(String fileName, String payload) throws IOException {
    Path requestFile = tempDirectory.resolve(fileName);
    Files.writeString(requestFile, payload, StandardCharsets.UTF_8);
    return requestFile;
  }

  protected Path writeBookKey(Path bookFilePath) {
    return writeBookKey(bookFilePath, TEST_BOOK_KEY);
  }

  protected Path writeBookKey(Path bookFilePath, String keyText) {
    try {
      Path bookKeyFilePath = bookFilePath.resolveSibling(bookFilePath.getFileName() + ".key");
      writeSecureKey(bookKeyFilePath, keyText);
      return bookKeyFilePath;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  protected Path writeNamedBookKey(String fileName, String keyText) {
    try {
      Path keyFilePath = tempDirectory.resolve(fileName);
      writeSecureKey(keyFilePath, keyText);
      return keyFilePath;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  protected static void writeSecureKey(Path keyFilePath, String keyText) throws IOException {
    if (Files.notExists(keyFilePath)) {
      SqliteBookKeyFileGenerator.generate(keyFilePath);
    }
    Files.writeString(keyFilePath, keyText, StandardCharsets.UTF_8);
  }

  protected static void assertGeneratedKeyFileIsSecure(Path keyFilePath, String permissions)
      throws IOException {
    try (SqliteBookPassphrase ignored = SqliteBookKeyFile.load(keyFilePath)) {
      if (supportsPosix(keyFilePath)) {
        assertEquals("0600", permissions);
        assertEquals(
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(keyFilePath));
        return;
      }
      assertEquals("owner-only-acl", permissions);
      assertOwnerOnlyAcl(keyFilePath);
    }
  }

  protected static void assertOwnerOnlyAcl(Path keyFilePath) throws IOException {
    AclFileAttributeView view = Files.getFileAttributeView(keyFilePath, AclFileAttributeView.class);
    UserPrincipal owner = view.getOwner();
    assertTrue(
        view.getAcl().stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> owner.equals(entry.principal()))
            .anyMatch(entry -> entry.permissions().contains(AclEntryPermission.READ_DATA)));
    assertFalse(
        view.getAcl().stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> !owner.equals(entry.principal()))
            .map(AclEntry::permissions)
            .anyMatch(permissions -> permissions.contains(AclEntryPermission.READ_DATA)));
  }

  protected static boolean supportsPosix(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains("posix");
  }

  protected static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);
  }

  protected static PrintStream utf8PrintStream(ByteArrayOutputStream outputStream) {
    return new PrintStream(outputStream, false, StandardCharsets.UTF_8);
  }
}
