package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAdministrationJsonModels;
import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliSuccessPayload;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.BookIdentity;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Shared fixture mappings for CLI payload tests. */
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
    return CliBookQueryPayloadMapper.postingDetailsPayload(
        bookIdentity, postingFact, Instant.EPOCH);
  }

  static List<String> counterpartAccounts(DeclaredAccount account, PostingFact postingFact) {
    return CliBookPostingPayloadMapper.counterpartAccounts(account, postingFact);
  }
}
