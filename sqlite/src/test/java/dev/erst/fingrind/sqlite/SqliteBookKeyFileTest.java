package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for the SQLite book-key file loader. */
class SqliteBookKeyFileTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void load_acceptsUtf8PassphraseAndStripsOneTrailingLineEnding() throws Exception {
    Path keyFile = tempDirectory.resolve("book.key");
    writeSecureString(keyFile, "swordfish\n");
    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile);
        Arena arena = Arena.ofConfined()) {
      assertEquals(
          keyFile.toAbsolutePath().normalize().toString(), keyMaterial.sourceDescription());
      assertEquals(9, keyMaterial.byteLength());
      assertEquals(
          "swordfish",
          new String(
              keyMaterial
                  .copyToCString(arena)
                  .asSlice(0, keyMaterial.byteLength())
                  .toArray(java.lang.foreign.ValueLayout.JAVA_BYTE),
              StandardCharsets.UTF_8));
    }
  }

  @Test
  void load_acceptsUtf8PassphraseAndStripsOneTrailingCrLf() throws Exception {
    Path keyFile = tempDirectory.resolve("book-crlf.key");
    writeSecureString(keyFile, "swordfish\r\n");
    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile)) {
      assertEquals(9, keyMaterial.byteLength());
    }
  }

  @Test
  void load_acceptsUtf8PassphraseWithoutTrailingLineEnding() throws Exception {
    Path keyFile = tempDirectory.resolve("book-no-newline.key");
    writeSecureString(keyFile, "swordfish");
    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile)) {
      assertEquals(9, keyMaterial.byteLength());
    }
  }

  @Test
  void load_rejectsEmptyPassphraseAfterTrailingLineEndingNormalization() throws Exception {
    Path keyFile = tempDirectory.resolve("empty.key");
    writeSecureString(keyFile, "\r\n");
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));
    assertTrue(
        NullTestSupport.messageOf(exception).contains("must contain a non-empty UTF-8 passphrase"));
  }

  @Test
  void load_rejectsSingleLineFeedThatNormalizesToEmptyPassphrase() throws Exception {
    Path keyFile = tempDirectory.resolve("line-feed-only.key");
    writeSecureString(keyFile, "\n");
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));
    assertTrue(
        NullTestSupport.messageOf(exception).contains("must contain a non-empty UTF-8 passphrase"));
  }

  @Test
  void load_rejectsTrulyEmptyKeyFile() throws Exception {
    Path keyFile = tempDirectory.resolve("empty-file.key");
    writeSecureBytes(keyFile, new byte[0]);
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));
    assertTrue(
        NullTestSupport.messageOf(exception).contains("must contain a non-empty UTF-8 passphrase"));
  }

  @Test
  void load_rejectsInvalidUtf8() throws Exception {
    Path keyFile = tempDirectory.resolve("invalid-utf8.key");
    writeSecureBytes(keyFile, new byte[] {(byte) 0xC3, (byte) 0x28});
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));
    assertTrue(NullTestSupport.messageOf(exception).contains("must contain a UTF-8 passphrase"));
  }

  @Test
  void load_rejectsControlCharactersInsidePassphrase() throws Exception {
    Path keyFile = tempDirectory.resolve("control-character.key");
    writeSecureString(keyFile, "sword\u0007fish");
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));
    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains(
                "must contain a single-line UTF-8 text passphrase without control characters"));
  }

  @Test
  void load_rejectsOversizedKeyFiles() throws Exception {
    Path keyFile = tempDirectory.resolve("oversized.key");
    writeSecureString(keyFile, "x".repeat(SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES + 1));
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));
    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains(
                "selected key file exceeded the %d-byte limit"
                    .formatted(SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES)));
  }

  @Test
  void load_rejectsEmbeddedLineFeedsAfterTrailingNormalization() throws Exception {
    Path keyFile = tempDirectory.resolve("embedded-line-feed.key");
    writeSecureString(keyFile, "first\nsecond\n");
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));
    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains(
                "must contain a single-line UTF-8 text passphrase without control characters"));
  }

  @Test
  void load_rethrowsMissingKeyFileAsStableIllegalState() {
    Path keyFile = tempDirectory.resolve("missing.key");
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));
    assertTrue(
        NullTestSupport.messageOf(exception).contains("The FinGrind book key file does not exist"));
  }

  @Test
  void readBytes_wrapsIoFailuresAsInvalidBookKeyFileFailures() {
    Path keyFile = tempDirectory.resolve("read-io-failure.key");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookKeyFile.readBytes(
                        keyFile,
                        ignored -> {
                          throw new IOException("boom");
                        })
                    .requireAccepted());
    assertTrue(
        NullTestSupport.messageOf(exception).contains("Failed to read the FinGrind book key file"));
  }

  @Test
  void load_rejectsDirectoryTargets() throws IOException {
    Path keyDirectory = tempDirectory.resolve("not-a-file.key");
    Files.createDirectories(keyDirectory);
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyDirectory));
    assertTrue(NullTestSupport.messageOf(exception).contains("regular non-symlink file"));
  }

  @Test
  void load_rejectsOwnerUnreadableKeyFiles() throws IOException {
    assumePosixFileSystem();
    Path keyFile = tempDirectory.resolve("owner-unreadable.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(keyFile, Set.of(PosixFilePermission.OWNER_WRITE));
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));
    assertTrue(NullTestSupport.messageOf(exception).contains("owner-readable"));
  }

  @Test
  void load_rejectsGroupReadableKeyFiles() throws IOException {
    assumePosixFileSystem();
    Path keyFile = tempDirectory.resolve("group-readable.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        keyFile,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ));
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));
    assertTrue(NullTestSupport.messageOf(exception).contains("owner-only permissions"));
  }

  @Test
  void load_rejectsKeyFilesInsideSharedParentDirectories() throws IOException {
    assumePosixFileSystem();
    Path secretsDirectory = tempDirectory.resolve("shared-secrets");
    Files.createDirectories(secretsDirectory);
    Files.setPosixFilePermissions(
        secretsDirectory,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE));
    Path keyFile = secretsDirectory.resolve("book.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        keyFile, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));
    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains("parent directory must use owner-only permissions"));
  }

  @Test
  void requireSecureKeyFile_wrapsUnsupportedSecurityInspection() throws IOException {
    Path keyFile = tempDirectory.resolve("zipfs.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookKeyFileSecurity.requireSecureKeyFile(
                        keyFile,
                        path -> {
                          throw new UnsupportedOperationException("no owner-only security view");
                        })
                    .requireAccepted());
    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains("supports POSIX owner-only permissions or Windows owner-only ACLs"));
  }

  @Test
  void requireSecureKeyFile_rejectsMissingKeyFiles() {
    Path keyFile = tempDirectory.resolve("missing-direct.key");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyFile).requireAccepted());
    assertTrue(NullTestSupport.messageOf(exception).contains("does not exist"));
  }

  @Test
  void requireSecureKeyFile_wrapsPermissionInspectionIoFailures() throws IOException {
    Path keyFile = tempDirectory.resolve("permission-io.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookKeyFileSecurity.requireSecureKeyFile(
                        keyFile,
                        path -> {
                          throw new IOException("boom");
                        })
                    .requireAccepted());
    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains("Failed to inspect the FinGrind book key file permissions"));
  }

  @Test
  void requireSecureKeyFile_acceptsOwnerOnlyAclSecurity() throws IOException {
    Path keyFile = tempDirectory.resolve("owner-only-acl.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);
    UserPrincipal owner = new TestPrincipal("owner");
    UserPrincipal other = new TestPrincipal("other");
    assertDoesNotThrow(
        () ->
            SqliteBookKeyFileSecurity.requireSecureKeyFile(
                    keyFile,
                    aclSecurityInspector(
                        keyFile,
                        owner,
                        List.of(
                            deny(other, AclEntryPermission.READ_DATA),
                            allow(other, Set.of()),
                            allow(owner, AclEntryPermission.READ_DATA))))
                .requireAccepted());
  }

  @Test
  void requireSecureKeyFile_rejectsAclWithoutOwnerReadAccess() throws IOException {
    Path keyFile = tempDirectory.resolve("owner-unreadable-acl.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);
    UserPrincipal owner = new TestPrincipal("owner");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookKeyFileSecurity.requireSecureKeyFile(
                        keyFile,
                        aclSecurityInspector(
                            keyFile, owner, List.of(allow(owner, AclEntryPermission.WRITE_DATA))))
                    .requireAccepted());
    assertTrue(
        NullTestSupport.messageOf(exception).contains("ACL must grant the file owner read access"));
  }

  @Test
  void requireSecureKeyFile_rejectsAclThatGrantsSecretAccessToOtherPrincipals() throws IOException {
    Path keyFile = tempDirectory.resolve("shared-acl.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);
    UserPrincipal owner = new TestPrincipal("owner");
    UserPrincipal other = new TestPrincipal("other");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookKeyFileSecurity.requireSecureKeyFile(
                        keyFile,
                        aclSecurityInspector(
                            keyFile,
                            owner,
                            List.of(
                                allow(owner, AclEntryPermission.READ_DATA),
                                allow(other, AclEntryPermission.READ_DATA))))
                    .requireAccepted());
    assertTrue(NullTestSupport.messageOf(exception).contains("ACL must grant secret access only"));
    assertTrue(NullTestSupport.messageOf(exception).contains("other"));
  }

  private static void writeSecureString(Path keyFile, String content) throws IOException {
    SqliteBookKeyFileGenerator.generate(keyFile);
    Files.writeString(keyFile, content, StandardCharsets.UTF_8);
  }

  private static void writeSecureBytes(Path keyFile, byte[] content) throws IOException {
    SqliteBookKeyFileGenerator.generate(keyFile);
    Files.write(keyFile, content);
  }

  private static AclEntry allow(UserPrincipal principal, AclEntryPermission permission) {
    return allow(principal, Set.of(permission));
  }

  private static AclEntry allow(UserPrincipal principal, Set<AclEntryPermission> permissions) {
    return AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(principal)
        .setPermissions(permissions)
        .build();
  }

  private static AclEntry deny(UserPrincipal principal, AclEntryPermission permission) {
    return AclEntry.newBuilder()
        .setType(AclEntryType.DENY)
        .setPrincipal(principal)
        .setPermissions(permission)
        .build();
  }

  private static SqliteBookKeyFileSecurity.SecurityInspector aclSecurityInspector(
      Path keyFile, UserPrincipal owner, List<AclEntry> fileAcl) {
    Path normalizedKeyFile = keyFile.toAbsolutePath().normalize();
    return path ->
        normalizedKeyFile.equals(path.toAbsolutePath().normalize())
            ? new SqliteBookKeyFileSecurity.AclSecurity(owner, fileAcl)
            : new SqliteBookKeyFileSecurity.AclSecurity(
                owner,
                List.of(
                    allow(
                        owner,
                        Set.of(AclEntryPermission.LIST_DIRECTORY, AclEntryPermission.EXECUTE))));
  }

  private void assumePosixFileSystem() {
    assumeTrue(tempDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"));
  }

  private record TestPrincipal(String name) implements UserPrincipal {
    @Override
    public String getName() {
      return name;
    }
  }
}
