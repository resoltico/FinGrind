package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAdministrationJsonModels;
import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliSuccessPayload;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.BookIdentity;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Test-only compatibility façade for legacy book-payload mapper assertions. */
final class CliBookPayloadMapper {
  private CliBookPayloadMapper() {}

  static CliSuccessPayload bookInspectionPayload(Path bookFilePath, BookInspection inspection) {
    return CliBookInspectionPayloadMapper.bookInspectionPayload(bookFilePath, inspection);
  }

  static CliAdministrationJsonModels.BookIdentityPayload bookIdentityPayload(
      BookIdentity bookIdentity) {
    return CliBookInspectionPayloadMapper.bookIdentityPayload(bookIdentity);
  }

  static CliBookQueryJsonModels.DeclaredAccountPayload accountPayload(DeclaredAccount account) {
    return CliBookQueryPayloadMapper.accountPayload(account);
  }

  static CliBookQueryJsonModels.BookContextPayload bookContextPayload(BookIdentity bookIdentity) {
    return CliBookQueryPayloadMapper.bookContextPayload(bookIdentity);
  }

  static CliBookQueryJsonModels.PostingQueryContextPayload postingQueryContextPayload(
      BookIdentity bookIdentity,
      @Nullable AccountCode accountCodeFilter,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo) {
    return CliBookQueryPayloadMapper.postingQueryContextPayload(
        bookIdentity, accountCodeFilter, effectiveDateFrom, effectiveDateTo);
  }

  static CliBookQueryJsonModels.PostingSummaryPayload postingSummaryPayload(
      PostingFact postingFact) {
    return CliBookPostingPayloadMapper.postingSummaryPayload(postingFact);
  }

  static CliBookQueryJsonModels.AccountingEvidencePayload evidencePayload(
      AccountingEvidence evidence) {
    return CliBookPostingPayloadMapper.evidencePayload(evidence);
  }

  static CliBookQueryJsonModels.PostingDetailsPayload postingDetailsPayload(
      BookIdentity bookIdentity, PostingFact postingFact) {
    return CliBookQueryPayloadMapper.postingDetailsPayload(bookIdentity, postingFact);
  }

  static CliBookQueryJsonModels.PostingListPayload postingPagePayload(PostingPage page) {
    return CliBookQueryPayloadMapper.postingPagePayload(page);
  }

  static CliBookQueryJsonModels.AccountListPayload accountPagePayload(AccountPage page) {
    return CliBookQueryPayloadMapper.accountPagePayload(page);
  }

  static CliBookQueryJsonModels.AccountBalancePayload accountBalancePayload(
      AccountBalanceSnapshot snapshot) {
    return CliBookQueryPayloadMapper.accountBalancePayload(snapshot);
  }

  static List<String> counterpartAccounts(DeclaredAccount account, PostingFact postingFact) {
    return CliBookPostingPayloadMapper.counterpartAccounts(account, postingFact);
  }
}
