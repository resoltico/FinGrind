package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.ProtectedBookFormatContract;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regenerates the committed protected-book compatibility fixture from the current SQLite contract.
 */
public final class SqliteProtectedBookFixtureGenerator {
  private static final Instant INITIALIZED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final String CURRENT_DEFAULT_FIXTURE_NAME =
      "current-default-protected-book.sqlite";
  private static final String CURRENT_DEFAULT_METADATA_NAME =
      "current-default-protected-book.metadata.json";
  private static final String UNSUPPORTED_FORMAT_FIXTURE_NAME =
      "unsupported-format-protected-book.sqlite";
  private static final String UNSUPPORTED_FORMAT_METADATA_NAME =
      "unsupported-format-protected-book.metadata.json";
  private static final String FOREIGN_SQLITE_FIXTURE_NAME = "foreign-sqlite.sqlite";
  private static final String FOREIGN_SQLITE_METADATA_NAME = "foreign-sqlite.metadata.json";
  private static final String CORRUPTED_FIXTURE_NAME = "corrupted-protected-book.sqlite";
  private static final String CORRUPTED_METADATA_NAME = "corrupted-protected-book.metadata.json";
  private static final String TRUNCATED_FIXTURE_NAME = "truncated-protected-book.sqlite";
  private static final String TRUNCATED_METADATA_NAME = "truncated-protected-book.metadata.json";
  private static final String ROLLBACK_FIXTURE_NAME =
      CURRENT_DEFAULT_FIXTURE_NAME + ".rekey-rollback-fixture.sqlite";
  private static final String ROLLBACK_METADATA_NAME =
      "current-default-protected-book.rollback-artifact.metadata.json";
  private static final String JOURNAL_FIXTURE_NAME = CURRENT_DEFAULT_FIXTURE_NAME + "-journal";
  private static final String WAL_FIXTURE_NAME = CURRENT_DEFAULT_FIXTURE_NAME + "-wal";
  private static final String SHM_FIXTURE_NAME = CURRENT_DEFAULT_FIXTURE_NAME + "-shm";
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private SqliteProtectedBookFixtureGenerator() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 1) {
      throw new IllegalArgumentException("Expected arguments: <fixtures-directory>.");
    }
    regenerate(Path.of(arguments[0]).toAbsolutePath().normalize());
  }

  static void regenerate(Path fixturesDirectory) throws IOException {
    Objects.requireNonNull(fixturesDirectory, "fixturesDirectory");
    Files.createDirectories(fixturesDirectory);
    Path secureTemporaryDirectory =
        Files.createTempDirectory(
            "fingrind-protected-book-fixture-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    Path keyPath = secureTemporaryDirectory.resolve("current-default-protected-book.regen.key");
    Path fixturePath = secureTemporaryDirectory.resolve(CURRENT_DEFAULT_FIXTURE_NAME);
    Path metadataPath = secureTemporaryDirectory.resolve(CURRENT_DEFAULT_METADATA_NAME);
    try {
      deleteGeneratedFixtureArtifacts(fixturesDirectory);
      SqliteStoreFixtureSupport.writeSecureKeyFile(
          keyPath, SqliteStoreFixtureSupport.TEST_BOOK_KEY);
      buildFixtureBook(fixturePath, keyPath);
      writeFixtureMetadata(fixturePath, metadataPath, keyPath);
      writeUnsupportedFormatFixture(secureTemporaryDirectory, fixturePath, keyPath);
      writeForeignSqliteFixture(secureTemporaryDirectory);
      writeCorruptedFixture(secureTemporaryDirectory, fixturePath);
      writeTruncatedFixture(secureTemporaryDirectory, fixturePath);
      writeRollbackArtifactFixture(secureTemporaryDirectory, fixturePath);
      writeSidecarFixtures(secureTemporaryDirectory);
      stageGeneratedFixtureArtifacts(secureTemporaryDirectory, fixturesDirectory);
    } finally {
      deleteGeneratedFixtureArtifacts(secureTemporaryDirectory);
      Files.deleteIfExists(keyPath);
      Files.deleteIfExists(secureTemporaryDirectory);
    }
  }

  private static void deleteGeneratedFixtureArtifacts(Path fixturesDirectory) throws IOException {
    deleteIfExists(fixturesDirectory.resolve(CURRENT_DEFAULT_FIXTURE_NAME));
    deleteIfExists(fixturesDirectory.resolve(CURRENT_DEFAULT_METADATA_NAME));
    deleteIfExists(fixturesDirectory.resolve(UNSUPPORTED_FORMAT_FIXTURE_NAME));
    deleteIfExists(fixturesDirectory.resolve(UNSUPPORTED_FORMAT_METADATA_NAME));
    deleteIfExists(fixturesDirectory.resolve(FOREIGN_SQLITE_FIXTURE_NAME));
    deleteIfExists(fixturesDirectory.resolve(FOREIGN_SQLITE_METADATA_NAME));
    deleteIfExists(fixturesDirectory.resolve(CORRUPTED_FIXTURE_NAME));
    deleteIfExists(fixturesDirectory.resolve(CORRUPTED_METADATA_NAME));
    deleteIfExists(fixturesDirectory.resolve(TRUNCATED_FIXTURE_NAME));
    deleteIfExists(fixturesDirectory.resolve(TRUNCATED_METADATA_NAME));
    deleteIfExists(fixturesDirectory.resolve(ROLLBACK_FIXTURE_NAME));
    deleteIfExists(fixturesDirectory.resolve(ROLLBACK_METADATA_NAME));
    deleteIfExists(fixturesDirectory.resolve(JOURNAL_FIXTURE_NAME));
    deleteIfExists(fixturesDirectory.resolve(WAL_FIXTURE_NAME));
    deleteIfExists(fixturesDirectory.resolve(SHM_FIXTURE_NAME));
  }

  private static void stageGeneratedFixtureArtifacts(Path sourceDirectory, Path targetDirectory)
      throws IOException {
    copyArtifact(sourceDirectory, targetDirectory, CURRENT_DEFAULT_FIXTURE_NAME);
    copyArtifact(sourceDirectory, targetDirectory, CURRENT_DEFAULT_METADATA_NAME);
    copyArtifact(sourceDirectory, targetDirectory, UNSUPPORTED_FORMAT_FIXTURE_NAME);
    copyArtifact(sourceDirectory, targetDirectory, UNSUPPORTED_FORMAT_METADATA_NAME);
    copyArtifact(sourceDirectory, targetDirectory, FOREIGN_SQLITE_FIXTURE_NAME);
    copyArtifact(sourceDirectory, targetDirectory, FOREIGN_SQLITE_METADATA_NAME);
    copyArtifact(sourceDirectory, targetDirectory, CORRUPTED_FIXTURE_NAME);
    copyArtifact(sourceDirectory, targetDirectory, CORRUPTED_METADATA_NAME);
    copyArtifact(sourceDirectory, targetDirectory, TRUNCATED_FIXTURE_NAME);
    copyArtifact(sourceDirectory, targetDirectory, TRUNCATED_METADATA_NAME);
    copyArtifact(sourceDirectory, targetDirectory, ROLLBACK_FIXTURE_NAME);
    copyArtifact(sourceDirectory, targetDirectory, ROLLBACK_METADATA_NAME);
    copyArtifact(sourceDirectory, targetDirectory, JOURNAL_FIXTURE_NAME);
    copyArtifact(sourceDirectory, targetDirectory, WAL_FIXTURE_NAME);
    copyArtifact(sourceDirectory, targetDirectory, SHM_FIXTURE_NAME);
  }

  private static void copyArtifact(Path sourceDirectory, Path targetDirectory, String fileName)
      throws IOException {
    Files.copy(
        sourceDirectory.resolve(fileName),
        targetDirectory.resolve(fileName),
        StandardCopyOption.REPLACE_EXISTING);
  }

  private static void writeUnsupportedFormatFixture(
      Path fixturesDirectory, Path currentFixturePath, Path keyPath) throws IOException {
    Path unsupportedFixturePath = fixturesDirectory.resolve(UNSUPPORTED_FORMAT_FIXTURE_NAME);
    Files.copy(currentFixturePath, unsupportedFixturePath, StandardCopyOption.REPLACE_EXISTING);
    SqliteStoreFixtureSupport.withStandaloneDatabase(
        new BookAccess(unsupportedFixturePath, new BookAccess.PassphraseSource.KeyFile(keyPath)),
        database ->
            database.executeStatement(
                "pragma user_version = " + (SqliteBookContract.FORMAT_VERSION + 1)));
    writeScenarioMetadata(
        fixturesDirectory.resolve(UNSUPPORTED_FORMAT_METADATA_NAME),
        UNSUPPORTED_FORMAT_FIXTURE_NAME,
        "unsupported-format-protected-book",
        "Committed encrypted fixture that proves unsupported protected-book format rejection.");
  }

  private static void writeForeignSqliteFixture(Path fixturesDirectory) throws IOException {
    Path foreignFixturePath = fixturesDirectory.resolve(FOREIGN_SQLITE_FIXTURE_NAME);
    deleteIfExists(foreignFixturePath);
    SqliteStoreFixtureSupport.createPostingFactOnlyBook(foreignFixturePath);
    writeScenarioMetadata(
        fixturesDirectory.resolve(FOREIGN_SQLITE_METADATA_NAME),
        FOREIGN_SQLITE_FIXTURE_NAME,
        "foreign-sqlite",
        "Committed plain SQLite fixture that proves foreign-SQLite rejection.");
  }

  private static void writeCorruptedFixture(Path fixturesDirectory, Path currentFixturePath)
      throws IOException {
    Path corruptedFixturePath = fixturesDirectory.resolve(CORRUPTED_FIXTURE_NAME);
    byte[] corruptedBytes = Files.readAllBytes(currentFixturePath);
    corruptedBytes[Math.min(200, corruptedBytes.length - 1)] ^= 0x5A;
    Files.write(corruptedFixturePath, corruptedBytes);
    writeScenarioMetadata(
        fixturesDirectory.resolve(CORRUPTED_METADATA_NAME),
        CORRUPTED_FIXTURE_NAME,
        "corrupted-protected-book",
        "Committed encrypted fixture with one deliberate byte corruption.");
  }

  private static void writeTruncatedFixture(Path fixturesDirectory, Path currentFixturePath)
      throws IOException {
    Path truncatedFixturePath = fixturesDirectory.resolve(TRUNCATED_FIXTURE_NAME);
    byte[] intactBytes = Files.readAllBytes(currentFixturePath);
    Files.write(truncatedFixturePath, java.util.Arrays.copyOf(intactBytes, 128));
    writeScenarioMetadata(
        fixturesDirectory.resolve(TRUNCATED_METADATA_NAME),
        TRUNCATED_FIXTURE_NAME,
        "truncated-protected-book",
        "Committed encrypted fixture truncated below one viable protected-book payload.");
  }

  private static void writeRollbackArtifactFixture(Path fixturesDirectory, Path currentFixturePath)
      throws IOException {
    Path rollbackFixturePath = fixturesDirectory.resolve(ROLLBACK_FIXTURE_NAME);
    Files.copy(currentFixturePath, rollbackFixturePath, StandardCopyOption.REPLACE_EXISTING);
    writeScenarioMetadata(
        fixturesDirectory.resolve(ROLLBACK_METADATA_NAME),
        ROLLBACK_FIXTURE_NAME,
        "rollback-artifact",
        "Committed rollback-artifact-shaped encrypted fixture for sibling recovery discovery.");
  }

  private static void writeSidecarFixtures(Path fixturesDirectory) throws IOException {
    Files.writeString(fixturesDirectory.resolve(JOURNAL_FIXTURE_NAME), "stale-journal");
    Files.writeString(fixturesDirectory.resolve(WAL_FIXTURE_NAME), "stale-wal");
    Files.writeString(fixturesDirectory.resolve(SHM_FIXTURE_NAME), "stale-shm");
  }

  private static void buildFixtureBook(Path fixturePath, Path keyPath) throws IOException {
    Files.deleteIfExists(fixturePath);
    try (SqlitePostingFactStore postingFactStore =
        SqliteStoreFixtureSupport.openStore(
            new BookAccess(fixturePath, new BookAccess.PassphraseSource.KeyFile(keyPath)))) {
      if (!(postingFactStore.openBook(
              INITIALIZED_AT, SqlitePostingFactFixtureSupport.bookIdentity(), List.of())
          instanceof BookOpeningOutcome.Opened)) {
        throw new IllegalStateException("Failed to initialize the protected-book fixture.");
      }
      declareAccount(
          postingFactStore,
          new AccountCode("1000"),
          new AccountName("Cash"),
          dev.erst.fingrind.core.AccountType.ASSET,
          NormalBalance.DEBIT,
          SqlitePostingFactFixtureSupport.registeredAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              dev.erst.fingrind.core.AccountType.ASSET,
              NormalBalance.DEBIT,
              true,
              INITIALIZED_AT));
      declareAccount(
          postingFactStore,
          new AccountCode("2000"),
          new AccountName("Revenue"),
          dev.erst.fingrind.core.AccountType.REVENUE,
          NormalBalance.CREDIT,
          SqlitePostingFactFixtureSupport.registeredAccount(
              new AccountCode("2000"),
              new AccountName("Revenue"),
              dev.erst.fingrind.core.AccountType.REVENUE,
              NormalBalance.CREDIT,
              true,
              INITIALIZED_AT));
      CommittedPosting posting =
          SqlitePostingFactFixtureSupport.postingFact(
              "fixture-posting-1",
              "fixture-idem-1",
              Optional.<ReversalReference>empty(),
              Optional.<ReversalReason>empty());
      PostingCommitResult commitResult =
          SqlitePostingFactStoreTestSupport.commitPosting(postingFactStore, posting);
      if (!new PostingCommitResult.Committed(posting).equals(commitResult)) {
        throw new IllegalStateException(
            "Protected-book fixture commit drifted: " + commitResult + ".");
      }
    }
  }

  private static void declareAccount(
      SqlitePostingFactStore postingFactStore,
      AccountCode accountCode,
      AccountName accountName,
      dev.erst.fingrind.core.AccountType accountType,
      NormalBalance normalBalance,
      RegisteredAccount expectedAccount) {
    AccountDeclarationOutcome outcome =
        postingFactStore.declareAccount(
            accountCode,
            accountName,
            accountType,
            SqlitePostingFactFixtureSupport.accountRole(accountType, normalBalance),
            SqlitePostingFactFixtureSupport.accountTaxonomy(accountType),
            INITIALIZED_AT);
    AccountDeclarationOutcome expected = new AccountDeclarationOutcome.Declared(expectedAccount);
    if (!expected.equals(outcome)) {
      throw new IllegalStateException(
          "Protected-book fixture account declaration drifted: " + outcome + ".");
    }
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static void writeFixtureMetadata(Path fixturePath, Path metadataPath, Path keyPath)
      throws IOException {
    MonetaryAmount amount =
        MonetaryAmount.of(SqlitePostingFactFixtureSupport.money("EUR", "10.00"));
    try (SqlitePostingFactStore postingFactStore =
        SqliteStoreFixtureSupport.openStore(
            new BookAccess(fixturePath, new BookAccess.PassphraseSource.KeyFile(keyPath)))) {
      if (!postingFactStore.inspectBook().initialized()) {
        throw new IllegalStateException("Generated protected-book fixture did not reopen cleanly.");
      }
      ProtectedBookFormatContract protectedBookFormat =
          SqliteProtectedBookFormatIntrospection.openedBookFormat(
              SqliteStoreTestIntrospectionSupport.requireStoreDatabase(postingFactStore));
      String schemaFingerprint =
          SqliteBookIntegrityVerifier.liveSchemaFingerprint(
              SqliteStoreTestIntrospectionSupport.requireStoreDatabase(postingFactStore));
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("fixtureName", fixturePath.getFileName().toString());
      metadata.put(
          "purpose",
          "Current default protected-book compatibility fixture for format, reopen, wrong-key, and backup-restore tests.");
      metadata.put("testOnlyPassphraseUtf8", SqliteStoreFixtureSupport.TEST_BOOK_KEY);
      metadata.put("bookFormatVersion", SqliteBookContract.FORMAT_VERSION);
      metadata.put("initializedAt", INITIALIZED_AT.toString());
      metadata.put("schemaFingerprintSha256", schemaFingerprint);
      metadata.put("protectedBookFormat", protectedBookFormatMap(protectedBookFormat));
      metadata.put(
          "accounts",
          List.of(
              orderedMap(
                  "accountCode",
                  "1000",
                  "accountName",
                  "Cash",
                  "normalBalance",
                  NormalBalance.DEBIT.wireValue()),
              orderedMap(
                  "accountCode",
                  "2000",
                  "accountName",
                  "Revenue",
                  "normalBalance",
                  NormalBalance.CREDIT.wireValue())));
      metadata.put(
          "postings",
          List.of(
              orderedMap(
                  "postingId",
                  "fixture-posting-1",
                  "idempotencyKey",
                  "fixture-idem-1",
                  "effectiveDate",
                  "2026-04-07",
                  "amount",
                  orderedMap(
                      "currencyCode", amount.currencyCode(), "minorUnits", amount.minorUnits()))));
      JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), metadata);
    }
  }

  private static void writeScenarioMetadata(
      Path metadataPath, String fixtureName, String fixtureKind, String purpose)
      throws IOException {
    Map<String, Object> metadata =
        orderedMap("fixtureName", fixtureName, "fixtureKind", fixtureKind, "purpose", purpose);
    JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), metadata);
  }

  private static Map<String, Object> protectedBookFormatMap(
      ProtectedBookFormatContract protectedBookFormat) {
    return orderedMap(
        "cipher",
        protectedBookFormat.cipher().wireValue(),
        "legacyMode",
        protectedBookFormat.legacyMode(),
        "pageSize",
        protectedBookFormat.pageSize(),
        "reservedBytes",
        protectedBookFormat.reservedBytes(),
        "legacyPageSize",
        protectedBookFormat.legacyPageSize(),
        "kdfIter",
        protectedBookFormat.kdfIter(),
        "plaintextHeaderSize",
        protectedBookFormat.plaintextHeaderSize());
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> orderedMap(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new IllegalArgumentException("orderedMap requires an even number of arguments.");
    }
    Map<String, Object> values = new LinkedHashMap<>();
    for (int index = 0; index < keyValues.length; index += 2) {
      values.put((String) keyValues[index], keyValues[index + 1]);
    }
    return values;
  }

  private static void deleteIfExists(Path path) throws IOException {
    Files.deleteIfExists(path);
  }
}
