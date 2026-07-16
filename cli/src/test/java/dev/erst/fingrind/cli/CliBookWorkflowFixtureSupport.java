package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookInspection;
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
  protected static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"));
  }

  protected static BookIdentity tradingBookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"));
  }

  protected static OpenBookCommand openBookCommand() {
    return new OpenBookCommand(bookIdentity());
  }

  protected static String[] openBookKeyFileArguments(Path bookFilePath, Path bookKeyFilePath) {
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
      bookIdentity().fiscalYearStart().wireValue()
    };
  }

  protected static String[] openBookStandardInputArguments(Path bookFilePath) {
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
      bookIdentity().fiscalYearStart().wireValue()
    };
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

  protected static GetPostingResult.Found foundPosting(PostingFact postingFact) {
    return new GetPostingResult.Found(bookIdentity(), postingFact, Optional.empty());
  }
}
