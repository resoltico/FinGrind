package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared CLI fixture helpers and sample payloads for split command tests. */
class CliIoFixtureSupport {
  protected static final String TEST_BOOK_KEY = "cli-test-book-key";
  private static final ObjectMapper TEST_JSON_MAPPER = new ObjectMapper();
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

  protected static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(
            new BookEntityName("Acme Studio"),
            List.of(new BusinessActivityTag("translation-services"))),
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"));
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
      ProtocolOptions.BUSINESS_ACTIVITY_TAG,
      "translation-services",
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
      ProtocolOptions.BUSINESS_ACTIVITY_TAG,
      "translation-services",
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
      ProtocolOptions.OUTPUT,
      "text",
      ProtocolOptions.ENTITY_NAME,
      bookIdentity().entityName().value(),
      ProtocolOptions.BUSINESS_ACTIVITY_TAG,
      "translation-services",
      ProtocolOptions.FUNCTIONAL_CURRENCY,
      bookIdentity().functionalCurrency().code(),
      ProtocolOptions.FISCAL_YEAR_START,
      bookIdentity().fiscalYearStart().wireValue()
    };
  }

  protected static String canonicalJsonText(ByteArrayOutputStream outputStream) {
    return canonicalJsonText(outputStream.toString(StandardCharsets.UTF_8));
  }

  protected static String canonicalJsonText(String document) {
    return TEST_JSON_MAPPER.writeValueAsString(TEST_JSON_MAPPER.readTree(document));
  }

  protected static void assertJsonContains(ByteArrayOutputStream outputStream, String fragment) {
    assertJsonContains(outputStream.toString(StandardCharsets.UTF_8), fragment);
  }

  protected static void assertJsonContains(String document, String fragment) {
    String canonical = canonicalJsonText(document);
    assertTrue(canonical.contains(fragment), canonical);
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
        bookIdentity(),
        resultTransferReadyInspection());
  }

  protected static BookInspection.ResultTransferReadiness resultTransferReadyInspection() {
    return new BookInspection.ResultTransferReadiness(
        true,
        FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
        new AccountCode("3200"),
        null,
        null,
        List.of());
  }

  protected static PostingCoverage allPostingKinds() {
    return PostingCoverage.ALL_POSTING_KINDS;
  }

  protected static PostingCoverage standardOnly() {
    return PostingCoverage.NON_CLOSING_POSTINGS;
  }

  protected static AccountPage accountPage(
      List<DeclaredAccount> accounts, int limit, Optional<AccountPageCursor> nextCursor) {
    return new AccountPage(bookIdentity(), accounts, limit, nextCursor);
  }

  protected static PostingPage postingPage(
      List<PostingFact> postings, int limit, Optional<PostingPageCursor> nextCursor) {
    return postingPage(
        Optional.empty(), EffectiveDateRange.unbounded(), postings, limit, nextCursor);
  }

  protected static PostingPage postingPage(
      Optional<AccountCode> accountCodeFilter,
      EffectiveDateRange effectiveDateRange,
      List<PostingFact> postings,
      int limit,
      Optional<PostingPageCursor> nextCursor) {
    return new PostingPage(
        bookIdentity(), accountCodeFilter, effectiveDateRange, postings, limit, nextCursor);
  }

  protected static GetPostingResult.Found foundPosting(PostingFact postingFact) {
    return new GetPostingResult.Found(bookIdentity(), postingFact);
  }

  protected static List<String> readTextArray(JsonNode node) {
    List<String> values = new ArrayList<>();
    node.forEach(element -> values.add(element.stringValue()));
    return List.copyOf(values);
  }

  protected static String validRequestJson() {
    return """
            {
              "entryKind": "CASH_REVENUE",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "revenueAccountCode": "2000",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              },
              "evidence": %s,
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-1",
                "causationId": "cause-1"
              }
            }
            """
        .formatted(evidenceJson().indent(14).stripLeading());
  }

  protected static String evidenceJson() {
    return """
        {
          "sourceDocuments": [
            {
              "sourceDocumentId": "document-1",
              "sourceDocumentType": "cash-receipt",
              "documentDate": "2026-04-07",
              "capturedAt": "2026-04-07T10:15:30Z",
              "storageLocator": "vault://fixtures/document-1",
              "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
          ],
          "approvals": []
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
                    "accountRole": "ORDINARY",
                    "accountNodeKind": "POSTABLE",
                    "financialPositionLineClassification": "CURRENT_ASSET"
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
                    "businessActivityTags": ["translation-services"],
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
    return declareAccountJson(
        accountCode,
        accountName,
        accountType,
        accountRole,
        fixtureFinancialPositionLineClassificationWireValue(accountType),
        fixtureProfitAndLossLineClassificationWireValue(accountType));
  }

  protected static String declareAccountJson(
      String accountCode,
      String accountName,
      String accountType,
      String accountRole,
      @org.jspecify.annotations.Nullable String financialPositionLineClassification,
      @org.jspecify.annotations.Nullable String profitAndLossLineClassification) {
    return """
            {
              "accountCode": "%s",
              "accountName": "%s",
              "accountType": "%s",
              "accountRole": "%s",
              "accountNodeKind": "POSTABLE",
              "financialPositionLineClassification": %s,
              "profitAndLossLineClassification": %s
            }
            """
        .formatted(
            accountCode,
            accountName,
            accountType,
            accountRole,
            quotedOrNull(financialPositionLineClassification),
            quotedOrNull(profitAndLossLineClassification));
  }

  private static String fixtureAccountTypeWireValue(String normalBalance) {
    return switch (normalBalance) {
      case "DEBIT" -> "ASSET";
      case "CREDIT" -> "REVENUE";
      default -> "ASSET";
    };
  }

  private static @org.jspecify.annotations.Nullable String
      fixtureFinancialPositionLineClassificationWireValue(String accountType) {
    return switch (accountType) {
      case "ASSET" -> "CURRENT_ASSET";
      case "LIABILITY" -> "CURRENT_LIABILITY";
      case "EQUITY" -> "OTHER_EQUITY";
      case "REVENUE", "EXPENSE" -> null;
      default ->
          throw new IllegalArgumentException("Unsupported fixture accountType: " + accountType);
    };
  }

  private static @org.jspecify.annotations.Nullable String
      fixtureProfitAndLossLineClassificationWireValue(String accountType) {
    return switch (accountType) {
      case "REVENUE" -> "OPERATING_REVENUE";
      case "EXPENSE" -> "OPERATING_EXPENSE";
      case "ASSET", "LIABILITY", "EQUITY" -> null;
      default ->
          throw new IllegalArgumentException("Unsupported fixture accountType: " + accountType);
    };
  }

  private static String quotedOrNull(@org.jspecify.annotations.Nullable String value) {
    return value == null ? "null" : "\"" + value + "\"";
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
        fixtureAccountTaxonomy(accountType),
        active,
        declaredAt);
  }

  protected static AccountTaxonomy fixtureAccountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty());
      case LIABILITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
      case EXPENSE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    };
  }

  private static String fixtureAccountRoleWireValue(String normalBalance) {
    AccountType accountType = AccountType.fromWireValue(fixtureAccountTypeWireValue(normalBalance));
    NormalBalance parsedNormalBalance = NormalBalance.valueOf(normalBalance);
    return fixtureAccountRole(accountType, parsedNormalBalance).wireValue();
  }

  protected static TrialBalanceReport trialBalanceReport(
      Optional<LocalDate> effectiveDateAsOf,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage,
      List<TrialBalanceRow> rows,
      List<TrialBalanceRow> comparativeRows) {
    return trialBalanceReport(
        bookIdentity(),
        effectiveDateAsOf,
        comparativeEffectiveDateRange,
        postingCoverage,
        rows,
        comparativeRows);
  }

  protected static TrialBalanceReport trialBalanceReport(
      BookIdentity bookIdentity,
      Optional<LocalDate> effectiveDateAsOf,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage,
      List<TrialBalanceRow> rows,
      List<TrialBalanceRow> comparativeRows) {
    List<CurrencyBalance> totals = trialBalanceTotals(rows);
    List<CurrencyBalance> comparativeTotals = trialBalanceTotals(comparativeRows);
    return new TrialBalanceReport(
        bookIdentity,
        effectiveDateAsOf,
        comparativeEffectiveDateRange,
        postingCoverage,
        rows,
        totals,
        isBalanced(totals),
        comparativeRows,
        comparativeTotals,
        isBalanced(comparativeTotals));
  }

  private static List<CurrencyBalance> trialBalanceTotals(List<TrialBalanceRow> rows) {
    List<CurrencyBalance> totalsByCurrency = new ArrayList<>();
    for (TrialBalanceRow row : rows) {
      mergeCurrencyBalance(totalsByCurrency, row.balance());
    }
    return List.copyOf(totalsByCurrency);
  }

  private static void mergeCurrencyBalance(
      List<CurrencyBalance> totalsByCurrency, CurrencyBalance nextBalance) {
    CurrencyUnit nextCurrency = nextBalance.debitTotal().currencyUnit();
    for (int index = 0; index < totalsByCurrency.size(); index++) {
      CurrencyBalance existingBalance = totalsByCurrency.get(index);
      if (existingBalance.debitTotal().currencyUnit().equals(nextCurrency)) {
        totalsByCurrency.set(index, sumCurrencyBalances(existingBalance, nextBalance));
        return;
      }
    }
    totalsByCurrency.add(nextBalance);
  }

  private static CurrencyBalance sumCurrencyBalances(CurrencyBalance left, CurrencyBalance right) {
    return CurrencyBalance.ofTotals(
        sumMoney(left.debitTotal(), right.debitTotal()),
        sumMoney(left.creditTotal(), right.creditTotal()));
  }

  private static Money sumMoney(Money left, Money right) {
    return left.plus(right);
  }

  private static boolean isBalanced(List<CurrencyBalance> totals) {
    return totals.stream().allMatch(balance -> balance.balanceSide() == BalanceSide.ZERO);
  }

  protected static PrintStream utf8PrintStream(ByteArrayOutputStream outputStream) {
    return new PrintStream(outputStream, false, StandardCharsets.UTF_8);
  }
}
