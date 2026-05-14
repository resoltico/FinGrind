package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.sqlite.SqliteBookKeyFile;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/** Shared CLI fixture helpers and sample payloads for split command tests. */
class CliIoFixtureSupport {
  protected static final String TEST_BOOK_KEY = "cli-test-book-key";
  @TempDir protected Path tempDirectory;

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
      if (bookKeyFilePath.getParent() != null) {
        Files.createDirectories(bookKeyFilePath.getParent());
      }
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
    if (keyFilePath.getParent() != null) {
      Files.createDirectories(keyFilePath.getParent());
    }
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

  protected static BookIdentity bookIdentity() {
    return new BookIdentity(
        new BookEntityName("Acme Studio"), CurrencyUnit.of("EUR"), FiscalYearStart.parse("01-01"));
  }

  protected static OpenBookCommand openBookCommand() {
    return new OpenBookCommand(bookIdentity());
  }

  protected static String[] openBookKeyFileArguments(Path bookFilePath, Path bookKeyFilePath) {
    return new String[] {
      "open-book",
      ProtocolOptions.BOOK_FILE,
      bookFilePath.toString(),
      ProtocolOptions.BOOK_KEY_FILE,
      bookKeyFilePath.toString(),
      ProtocolOptions.ENTITY_NAME,
      bookIdentity().entityName().value(),
      ProtocolOptions.FUNCTIONAL_CURRENCY,
      bookIdentity().functionalCurrency().code(),
      ProtocolOptions.FISCAL_YEAR_START,
      bookIdentity().fiscalYearStart().wireValue()
    };
  }

  protected static String[] openBookStandardInputArguments(Path bookFilePath) {
    return new String[] {
      "open-book",
      ProtocolOptions.BOOK_FILE,
      bookFilePath.toString(),
      ProtocolOptions.BOOK_PASSPHRASE_STDIN,
      ProtocolOptions.ENTITY_NAME,
      bookIdentity().entityName().value(),
      ProtocolOptions.FUNCTIONAL_CURRENCY,
      bookIdentity().functionalCurrency().code(),
      ProtocolOptions.FISCAL_YEAR_START,
      bookIdentity().fiscalYearStart().wireValue()
    };
  }

  protected static String[] openBookPromptArguments(Path bookFilePath) {
    return new String[] {
      "open-book",
      ProtocolOptions.BOOK_FILE,
      bookFilePath.toString(),
      ProtocolOptions.BOOK_PASSPHRASE_PROMPT,
      ProtocolOptions.ENTITY_NAME,
      bookIdentity().entityName().value(),
      ProtocolOptions.FUNCTIONAL_CURRENCY,
      bookIdentity().functionalCurrency().code(),
      ProtocolOptions.FISCAL_YEAR_START,
      bookIdentity().fiscalYearStart().wireValue()
    };
  }

  protected static OpenBookResult.Opened openedBookResult(Instant initializedAt) {
    return new OpenBookResult.Opened(initializedAt, bookIdentity());
  }

  protected static BookInspection.Initialized initializedBookInspection(
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      Instant initializedAt) {
    return new BookInspection.Initialized(
        applicationId,
        detectedBookFormatVersion,
        supportedBookFormatVersion,
        initializedAt,
        bookIdentity());
  }

  protected static PostingCoverage allPostingKinds() {
    return PostingCoverage.ALL_POSTING_KINDS;
  }

  protected static PostingCoverage standardOnly() {
    return PostingCoverage.NON_CLOSING_POSTINGS;
  }

  protected static List<String> readTextArray(JsonNode node) {
    List<String> values = new ArrayList<>();
    node.forEach(element -> values.add(element.stringValue()));
    return List.copyOf(values);
  }

  protected static String validRequestJson() {
    return """
            {
              "postingKind": "STANDARD",
              "effectiveDate": "2026-04-07",
              "lines": [
                {
                  "accountCode": "1000",
                  "side": "DEBIT",
                  "amount": {
                    "currencyCode": "EUR",
                    "minorUnits": "1000"
                  }
                },
                {
                  "accountCode": "2000",
                  "side": "CREDIT",
                  "amount": {
                    "currencyCode": "EUR",
                    "minorUnits": "1000"
                  }
                }
              ],
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-1",
                "causationId": "cause-1"
              }
            }
            """;
  }

  protected static String validPlanJson() {
    return """
            {
              "planId": "plan-1",
              "steps": [
                {
                  "stepId": "declare-cash",
                  "kind": "declare-account",
                  "declareAccount": {
                    "accountCode": "1000",
                    "accountName": "Cash",
                    "accountType": "ASSET",
                    "accountRole": "ORDINARY"
                  }
                }
              ]
            }
            """;
  }

  protected static String openOnlyPlanJson() {
    return """
            {
              "planId": "plan-1",
              "steps": [
                {
                  "stepId": "open",
                  "kind": "open-book",
                  "openBook": {
                    "entityName": "Acme Studio",
                    "functionalCurrency": "EUR",
                    "fiscalYearStart": "01-01"
                  }
                }
              ]
            }
            """;
  }

  protected static String listAccountsPlanJson(int limit) {
    return """
            {
              "planId": "plan-list-accounts",
              "steps": [
                {
                  "stepId": "accounts",
                  "kind": "list-accounts",
                  "query": {
                    "limit": %d
                  }
                }
              ]
            }
            """
        .formatted(limit);
  }

  protected static String declareAccountJson(
      String accountCode, String accountName, String normalBalance) {
    return declareAccountJson(
        accountCode,
        accountName,
        fixtureAccountTypeWireValue(normalBalance),
        fixtureAccountRoleWireValue(normalBalance));
  }

  protected static String declareAccountJson(
      String accountCode, String accountName, String accountType, String accountRole) {
    return """
            {
              "accountCode": "%s",
              "accountName": "%s",
              "accountType": "%s",
              "accountRole": "%s"
            }
            """
        .formatted(accountCode, accountName, accountType, accountRole);
  }

  private static String fixtureAccountTypeWireValue(String normalBalance) {
    return switch (normalBalance) {
      case "DEBIT" -> "ASSET";
      case "CREDIT" -> "REVENUE";
      default -> "ASSET";
    };
  }

  protected static AccountRole fixtureAccountRole(
      AccountType accountType, NormalBalance normalBalance) {
    for (AccountRole accountRole : List.of(AccountRole.ORDINARY, AccountRole.CONTRA)) {
      if (AccountSemantics.normalBalance(accountType, accountRole) == normalBalance) {
        return accountRole;
      }
    }
    throw new IllegalArgumentException(
        "No supported fixture accountRole matches %s/%s."
            .formatted(accountType.wireValue(), normalBalance.name()));
  }

  protected static DeclaredAccount declaredAccount(
      String accountCode,
      String accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return new DeclaredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        fixtureAccountRole(accountType, normalBalance),
        active,
        declaredAt);
  }

  private static String fixtureAccountRoleWireValue(String normalBalance) {
    AccountType accountType = AccountType.fromWireValue(fixtureAccountTypeWireValue(normalBalance));
    NormalBalance parsedNormalBalance = NormalBalance.valueOf(normalBalance);
    return fixtureAccountRole(accountType, parsedNormalBalance).wireValue();
  }

  protected static PrintStream utf8PrintStream(ByteArrayOutputStream outputStream) {
    return new PrintStream(outputStream, false, StandardCharsets.UTF_8);
  }
}
