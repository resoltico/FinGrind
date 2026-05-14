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
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private SqliteProtectedBookFixtureGenerator() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 2) {
      throw new IllegalArgumentException(
          "Expected arguments: <fixture-sqlite-path> <fixture-metadata-json-path>.");
    }
    Path fixturePath = Path.of(arguments[0]).toAbsolutePath().normalize();
    Path metadataPath = Path.of(arguments[1]).toAbsolutePath().normalize();
    regenerate(fixturePath, metadataPath);
  }

  static void regenerate(Path fixturePath, Path metadataPath) throws IOException {
    Objects.requireNonNull(fixturePath, "fixturePath");
    Objects.requireNonNull(metadataPath, "metadataPath");
    createParentDirectories(fixturePath);
    createParentDirectories(metadataPath);
    Path keyPath = metadataPath.resolveSibling("current-default-protected-book.regen.key");
    try {
      SqliteStoreFixtureSupport.writeSecureKeyFile(
          keyPath, SqliteStoreFixtureSupport.TEST_BOOK_KEY);
      buildFixtureBook(fixturePath, keyPath);
      writeFixtureMetadata(fixturePath, metadataPath, keyPath);
    } finally {
      Files.deleteIfExists(keyPath);
    }
  }

  private static void buildFixtureBook(Path fixturePath, Path keyPath) throws IOException {
    Files.deleteIfExists(fixturePath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            new BookAccess(fixturePath, new BookAccess.PassphraseSource.KeyFile(keyPath)))) {
      if (!(postingFactStore.openBook(
              INITIALIZED_AT, SqlitePostingFactFixtureSupport.bookIdentity())
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
        new SqlitePostingFactStore(
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

  private static void createParentDirectories(Path path) throws IOException {
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }
}
