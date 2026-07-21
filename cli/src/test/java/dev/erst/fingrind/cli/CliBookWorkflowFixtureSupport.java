package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.PostingCoverage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Shared test support for book lifecycle, inspection, and read-model fixtures. */
class CliBookWorkflowFixtureSupport extends CliFilesystemFixtureSupport {
  private static final String TEST_FOUNDER_PRINCIPAL_ID = "10213243-5465-7687-98a9-babcbddceeff";
  private static final String TEST_FOUNDER_PASSPHRASE = "cli-test-attestation-founder-passphrase";

  protected static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        java.time.LocalDate.parse("2026-01-01"));
  }

  protected static BookIdentity tradingBookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        java.time.LocalDate.parse("2026-01-01"));
  }

  protected static OpenBookCommand openBookCommand() {
    return new OpenBookCommand(
        bookIdentity(),
        List.of(
            new dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput(
                java.util.UUID.fromString("10213243-5465-7687-98a9-babcbddceeff"),
                Path.of("/tmp/fingrind-cli-founder.fgatk"),
                Path.of("/tmp/fingrind-cli-founder.passphrase"))));
  }

  protected static String[] openBookKeyFileArguments(Path bookFilePath, Path bookKeyFilePath) {
    Path founderKeyFilePath = attestationKeyFilePath(bookFilePath);
    Path founderPassphraseFilePath = writeAttestationPassphraseFile(bookFilePath);
    return new String[] {
      "open-book",
      ProtocolBookAccessOptions.BOOK_FILE,
      bookFilePath.toString(),
      ProtocolBookAccessOptions.BOOK_KEY_FILE,
      bookKeyFilePath.toString(),
      ProtocolOptions.BookDefinition.ENTITY_NAME,
      bookIdentity().entityName().value(),
      ProtocolOptions.BookDefinition.TEMPLATE_ID,
      bookIdentity().bookDoctrine().bookTemplateId().wireValue(),
      ProtocolOptions.BookDefinition.ACCOUNTING_BASIS,
      bookIdentity().bookDoctrine().accountingBasis().wireValue(),
      ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY,
      bookIdentity().functionalCurrency().code(),
      ProtocolOptions.BookDefinition.FISCAL_YEAR_START,
      bookIdentity().fiscalYearStart().wireValue(),
      ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE,
      bookIdentity().bookStartEffectiveDate().toString(),
      ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
      TEST_FOUNDER_PRINCIPAL_ID,
      ProtocolOptions.Attestation.FOUNDER_KEY_FILE,
      founderKeyFilePath.toString(),
      ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE,
      founderPassphraseFilePath.toString()
    };
  }

  protected static String[] openBookStandardInputArguments(Path bookFilePath) {
    Path founderKeyFilePath = attestationKeyFilePath(bookFilePath);
    Path founderPassphraseFilePath = writeAttestationPassphraseFile(bookFilePath);
    return new String[] {
      "open-book",
      ProtocolBookAccessOptions.BOOK_FILE,
      bookFilePath.toString(),
      ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN,
      ProtocolOptions.BookDefinition.ENTITY_NAME,
      bookIdentity().entityName().value(),
      ProtocolOptions.BookDefinition.TEMPLATE_ID,
      bookIdentity().bookDoctrine().bookTemplateId().wireValue(),
      ProtocolOptions.BookDefinition.ACCOUNTING_BASIS,
      bookIdentity().bookDoctrine().accountingBasis().wireValue(),
      ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY,
      bookIdentity().functionalCurrency().code(),
      ProtocolOptions.BookDefinition.FISCAL_YEAR_START,
      bookIdentity().fiscalYearStart().wireValue(),
      ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE,
      bookIdentity().bookStartEffectiveDate().toString(),
      ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
      TEST_FOUNDER_PRINCIPAL_ID,
      ProtocolOptions.Attestation.FOUNDER_KEY_FILE,
      founderKeyFilePath.toString(),
      ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE,
      founderPassphraseFilePath.toString()
    };
  }

  private static Path attestationKeyFilePath(Path bookFilePath) {
    return bookFilePath.resolveSibling(bookFilePath.getFileName() + ".founder.fgatk");
  }

  private static Path writeAttestationPassphraseFile(Path bookFilePath) {
    Path passphrasePath =
        bookFilePath.resolveSibling(bookFilePath.getFileName() + ".founder-passphrase");
    try {
      writeSecureKey(passphrasePath, TEST_FOUNDER_PASSPHRASE);
      return passphrasePath;
    } catch (java.io.IOException exception) {
      throw new java.io.UncheckedIOException(exception);
    }
  }

  protected static String[] openBookPromptArguments(Path bookFilePath) {
    return new String[] {
      "open-book",
      ProtocolBookAccessOptions.BOOK_FILE,
      bookFilePath.toString(),
      ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT,
      ProtocolOptions.Presentation.OUTPUT,
      "text",
      ProtocolOptions.BookDefinition.ENTITY_NAME,
      bookIdentity().entityName().value(),
      ProtocolOptions.BookDefinition.TEMPLATE_ID,
      bookIdentity().bookDoctrine().bookTemplateId().wireValue(),
      ProtocolOptions.BookDefinition.ACCOUNTING_BASIS,
      bookIdentity().bookDoctrine().accountingBasis().wireValue(),
      ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY,
      bookIdentity().functionalCurrency().code(),
      ProtocolOptions.BookDefinition.FISCAL_YEAR_START,
      bookIdentity().fiscalYearStart().wireValue(),
      ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE,
      bookIdentity().bookStartEffectiveDate().toString()
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
        bookIdentity(),
        readyCloseReadiness());
  }

  protected static BookInspection.CloseReadiness readyCloseReadiness() {
    return new BookInspection.CloseReadiness(
        readyCloseTarget(
            FinancialPositionLineClassification.RESULT_HOLDING, new AccountCode("3200")),
        readyCloseTarget(
            FinancialPositionLineClassification.RETAINED_ACCUMULATED, new AccountCode("3300")));
  }

  protected static BookInspection.CloseTargetReadiness readyCloseTarget(
      FinancialPositionLineClassification classification, AccountCode accountCode) {
    return new BookInspection.CloseTargetReadiness(
        true, classification, accountCode, null, null, List.of());
  }

  protected static BookInspection.CloseTargetReadiness blockedCloseTarget(
      FinancialPositionLineClassification classification,
      String blockingCode,
      String blockingMessage,
      List<AccountCode> candidateAccountCodes) {
    return new BookInspection.CloseTargetReadiness(
        false, classification, null, blockingCode, blockingMessage, candidateAccountCodes);
  }

  protected static PostingCoverage allPostingKinds() {
    return PostingCoverage.ALL_POSTING_KINDS;
  }

  protected static PostingCoverage standardOnly() {
    return PostingCoverage.NON_CLOSING_POSTINGS;
  }

  protected static AccountPage accountPage(
      List<dev.erst.fingrind.contract.bookkeeping.DeclaredAccount> accounts,
      int limit,
      Optional<AccountPageCursor> nextCursor) {
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
        bookIdentity(),
        accountCodeFilter,
        effectiveDateRange,
        postings,
        limit,
        nextCursor,
        java.util.Map.of());
  }

  protected static ListAccountsResult.Listed listedAccounts(AccountPage page) {
    return new ListAccountsResult.Listed(
        new ListAccountsQuery(page.limit(), Optional.empty()), page);
  }

  protected static ListPostingsResult.Listed listedPostings(PostingPage page) {
    return new ListPostingsResult.Listed(
        new ListPostingsQuery(
            page.accountCodeFilter(), page.effectiveDateRange(), page.limit(), Optional.empty()),
        page);
  }

  protected static ListTaxRegistrationsResult.Listed listedTaxRegistrations(
      TaxRegistrationPage page) {
    return new ListTaxRegistrationsResult.Listed(
        new ListTaxRegistrationsQuery(page.limit(), Optional.empty()), page);
  }

  protected static GetPostingResult.Found foundPosting(PostingFact postingFact) {
    return new GetPostingResult.Found(bookIdentity(), postingFact, Optional.empty());
  }
}
