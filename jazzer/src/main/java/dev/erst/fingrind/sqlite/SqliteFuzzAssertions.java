package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** Shared SQLite-specific assertions for Jazzer harnesses. */
public final class SqliteFuzzAssertions {
  private static final String TEST_BOOK_KEY = "fingrind-jazzer-book-key";
  private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);

  private SqliteFuzzAssertions() {}

  /** Asserts that a committed FinGrind book file uses the canonical strict-table schema. */
  public static void assertCommittedBookUsesStrictTables(Path bookPath) {
    try (SqliteBookPassphrase passphrase = bookPassphrase();
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, passphrase)) {
      assertQueryInt(
          database,
          "select strict from pragma_table_list('book_meta') where name = 'book_meta'",
          1);
      assertQueryInt(
          database, "select strict from pragma_table_list('account') where name = 'account'", 1);
      assertQueryInt(
          database,
          "select strict from pragma_table_list('posting_fact') where name = 'posting_fact'",
          1);
      assertQueryInt(
          database,
          "select strict from pragma_table_list('journal_line') where name = 'journal_line'",
          1);
      assertQueryInt(
          database, "select count(*) from book_meta where meta_key = 'initialized_at'", 1);
      assertQueryInt(
          database,
          """
          select count(*)
          from pragma_foreign_key_list('journal_line')
          where "table" = 'account'
            and "from" = 'account_code'
            and "to" = 'account_code'
          """,
          1);
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException(
          "Committed SQLite book did not satisfy the strict-schema invariant.", exception);
    }
  }

  /** Deactivates one account directly in SQLite so harnesses can assert reactivation. */
  public static void deactivateAccount(Path bookPath, String accountCode)
      throws java.io.IOException {
    updateAccountActivity(bookPath, accountCode, 0);
  }

  /** Activates one account directly in SQLite for deterministic harness setup. */
  public static void activateAccount(Path bookPath, String accountCode) throws java.io.IOException {
    updateAccountActivity(bookPath, accountCode, 1);
  }

  private static void updateAccountActivity(Path bookPath, String accountCode, int activeFlag)
      throws java.io.IOException {
    if (!Files.exists(bookPath)) {
      throw new IllegalArgumentException("SQLite book does not exist: " + bookPath);
    }
    try (SqliteBookPassphrase passphrase = bookPassphrase();
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, passphrase)) {
      database.executeStatement(
          """
          update account
             set active = %d
           where account_code = '%s'
          """
              .formatted(activeFlag, escapeSqlLiteral(accountCode)));
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException(
          "Failed to update account active flag for SQLite fuzz setup.", exception);
    }
  }

  /** Builds deterministic protected-book passphrase material for one fuzz or replay command. */
  public static SqliteBookPassphrase bookPassphrase() {
    return SqliteBookPassphrase.fromCharacters(
        "jazzer deterministic book passphrase", TEST_BOOK_KEY.toCharArray());
  }

  /** Writes one deterministic secure key file beneath an already-admitted direct parent. */
  public static void writeDeterministicBookKeyFile(Path keyFilePath) throws java.io.IOException {
    Path normalizedKeyFilePath = keyFilePath.toAbsolutePath().normalize();
    Path parentDirectory =
        Objects.requireNonNull(normalizedKeyFilePath.getParent(), "normalizedKeyFilePath parent");
    requireOwnerOnlyArtifactDirectory(parentDirectory);
    if (Files.notExists(normalizedKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
      SqliteBookKeyFileGenerator.generate(normalizedKeyFilePath);
    } else {
      SqliteBookKeyFile.loadDecision(normalizedKeyFilePath).requireAccepted().close();
    }
    replaceFixtureKeyFile(normalizedKeyFilePath);
  }

  /** Atomically writes one bounded passphrase fixture to a new owner-only file. */
  public static void writeNewOwnerOnlyFixturePassphraseFile(
      Path passphraseFilePath, String passphrase)
      throws java.io.IOException {
    Path normalizedPassphraseFilePath =
        Objects.requireNonNull(passphraseFilePath, "passphraseFilePath")
            .toAbsolutePath()
            .normalize();
    Path parentDirectory =
        Objects.requireNonNull(
            normalizedPassphraseFilePath.getParent(), "normalizedPassphraseFilePath parent");
    requireOwnerOnlyArtifactDirectory(parentDirectory);
    byte[] content =
        Objects.requireNonNull(passphrase, "passphrase").getBytes(StandardCharsets.UTF_8);
    try {
      if (content.length > ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES) {
        throw new java.io.IOException(
            "Jazzer fixture passphrase exceeds the supported UTF-8 byte limit.");
      }
      try (FileChannel channel =
          SqliteSecureRegularFileAccess.openNewWrite(normalizedPassphraseFilePath)) {
        writeFullyAndForce(channel, ByteBuffer.wrap(content), "Jazzer fixture passphrase");
      }
    } finally {
      Arrays.fill(content, (byte) 0);
    }
  }

  /** Creates one absent POSIX owner-only artifact directory, then validates the result. */
  public static Path createOwnerOnlyArtifactDirectory(Path directoryPath)
      throws java.io.IOException {
    Path normalizedDirectory = normalizedArtifactDirectory(directoryPath);
    Path parentDirectory =
        Objects.requireNonNull(normalizedDirectory.getParent(), "normalizedDirectory parent");
    requireOwnerOnlyArtifactDirectory(parentDirectory);
    if (Files.getFileAttributeView(
            parentDirectory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
        == null) {
      throw new java.io.IOException(
          "SQLite fuzz artifact directory creation requires POSIX owner-only permissions: "
              + normalizedDirectory);
    }
    Files.createDirectory(
        normalizedDirectory,
        PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_PERMISSIONS));
    return requireOwnerOnlyArtifactDirectory(normalizedDirectory);
  }

  /** Creates one fresh owner-only temporary artifact directory, then validates the result. */
  public static Path createOwnerOnlyTemporaryArtifactDirectory(String prefix)
      throws java.io.IOException {
    Path temporaryRoot =
        Path.of(Objects.requireNonNull(System.getProperty("java.io.tmpdir"), "java.io.tmpdir"))
            .toAbsolutePath()
            .normalize();
    if (!Files.isDirectory(temporaryRoot, LinkOption.NOFOLLOW_LINKS)) {
      throw new java.io.IOException(
          "Jazzer temporary artifact root must be an existing real directory: " + temporaryRoot);
    }
    Path canonicalTemporaryRoot = temporaryRoot.toRealPath();
    PrivateOutputDirectory.requireCreationAncestry(canonicalTemporaryRoot);
    if (Files.getFileAttributeView(
            canonicalTemporaryRoot, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
        == null) {
      throw new java.io.IOException(
          "Jazzer temporary artifact creation requires POSIX owner-only permissions: "
              + canonicalTemporaryRoot);
    }
    Path directory =
        Files.createTempDirectory(
            canonicalTemporaryRoot,
            Objects.requireNonNull(prefix, "prefix"),
            PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_PERMISSIONS));
    return requireOwnerOnlyArtifactDirectory(directory);
  }

  /** Validates one existing owner-only artifact directory without changing it. */
  public static Path requireOwnerOnlyArtifactDirectory(Path directoryPath)
      throws java.io.IOException {
    Path normalizedDirectory = normalizedArtifactDirectory(directoryPath);
    PrivateOutputDirectory.requireExistingOwnerOnly(normalizedDirectory);
    return normalizedDirectory;
  }

  private static Path normalizedArtifactDirectory(Path directoryPath) {
    return Objects.requireNonNull(directoryPath, "directoryPath").toAbsolutePath().normalize();
  }

  private static void replaceFixtureKeyFile(Path keyFilePath) throws java.io.IOException {
    ByteBuffer content = StandardCharsets.UTF_8.encode(TEST_BOOK_KEY);
    try (FileChannel channel = SqliteSecureRegularFileAccess.openTruncatingWrite(keyFilePath)) {
      writeFullyAndForce(channel, content, "deterministic SQLite fuzz key material");
    }
  }

  private static void writeFullyAndForce(
      FileChannel channel, ByteBuffer content, String artifactDescription)
      throws java.io.IOException {
    while (content.hasRemaining()) {
      if (channel.write(content) <= 0) {
        throw new java.io.IOException(
            "Could not make progress writing " + Objects.requireNonNull(artifactDescription) + ".");
      }
    }
    channel.force(true);
  }

  /** Opens one deterministic protected-book store for fuzz and replay flows. */
  public static SqlitePostingSession openStore(Path bookPath) {
    return SqlitePostingSessions.open(bookPath, bookPassphrase());
  }

  /** Asserts that one open store connection keeps FinGrind's connection-hardening pragmas. */
  public static void assertStoreConnectionHardening(AutoCloseable postingSurface) {
    try {
      SqliteNativeDatabase database = requireOwnedStore(postingSurface).activeNativeDatabase();
      assertQueryInt(database, "pragma foreign_keys", 1);
      assertQueryText(database, "pragma journal_mode", "delete");
      assertQueryInt(database, "pragma synchronous", 3);
      assertQueryInt(database, "pragma trusted_schema", 0);
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException(
          "SQLite store connection did not satisfy the pragma-hardening invariant.", exception);
    }
  }

  static void assertQueryInt(SqliteNativeDatabase database, String sql, int expectedValue) {
    try (SqliteNativeStatement statement = SqliteNativeStatements.prepare(database, sql)) {
      if (statement.step() != SqliteNativeResultCode.code("ROW")) {
        throw new IllegalStateException("Expected one SQLite row for hardening assertion: " + sql);
      }
      int actualValue = statement.columnInt(0);
      if (statement.step() != SqliteNativeResultCode.code("DONE")) {
        throw new IllegalStateException(
            "Expected one SQLite row only for hardening assertion: " + sql);
      }
      if (actualValue != expectedValue) {
        throw new IllegalStateException(
            "Unexpected SQLite pragma/query value for '" + sql + "': " + actualValue);
      }
    }
  }

  static void assertQueryText(SqliteNativeDatabase database, String sql, String expectedValue) {
    try (SqliteNativeStatement statement = SqliteNativeStatements.prepare(database, sql)) {
      if (statement.step() != SqliteNativeResultCode.code("ROW")) {
        throw new IllegalStateException("Expected one SQLite row for hardening assertion: " + sql);
      }
      String actualValue = statement.columnText(0);
      if (statement.step() != SqliteNativeResultCode.code("DONE")) {
        throw new IllegalStateException(
            "Expected one SQLite row only for hardening assertion: " + sql);
      }
      if (!expectedValue.equalsIgnoreCase(actualValue)) {
        throw new IllegalStateException(
            "Unexpected SQLite pragma/query value for '" + sql + "': " + actualValue);
      }
    }
  }

  static String escapeSqlLiteral(String text) {
    return text.replace("'", "''");
  }

  static SqlitePostingFactStore requireOwnedStore(AutoCloseable session) {
    try {
      return SqliteCapabilitySessions.storeOf(session);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "Unsupported owned SQLite store or capability wrapper: " + session.getClass().getName(),
          exception);
    }
  }
}
