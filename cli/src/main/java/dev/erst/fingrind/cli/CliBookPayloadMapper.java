package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAdministrationJsonModels;
import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliSuccessPayload;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.BookIdentity;
import java.nio.file.Path;
import java.util.List;

/** Maps book, account, posting, and query payloads into CLI JSON models. */
final class CliBookPayloadMapper {
  private CliBookPayloadMapper() {}

  static CliSuccessPayload bookInspectionPayload(Path bookFilePath, BookInspection inspection) {
    BookInspection.Status status = inspection.status();
    return switch (inspection) {
      case BookInspection.Missing missing ->
          new CliAdministrationJsonModels.MissingBookInspectionPayload(
              absolutePath(bookFilePath),
              status.wireValue(),
              status.compatibleWithCurrentBinary(),
              status.canInitializeWithOpenBook(),
              missing.supportedBookFormatVersion());
      case BookInspection.Existing existing ->
          new CliAdministrationJsonModels.ExistingBookInspectionPayload(
              absolutePath(bookFilePath),
              status.wireValue(),
              status.compatibleWithCurrentBinary(),
              status.canInitializeWithOpenBook(),
              existing.applicationId(),
              existing.detectedBookFormatVersion(),
              existing.supportedBookFormatVersion());
      case BookInspection.Initialized initialized ->
          new CliAdministrationJsonModels.InitializedBookInspectionPayload(
              absolutePath(bookFilePath),
              status.wireValue(),
              status.compatibleWithCurrentBinary(),
              status.canInitializeWithOpenBook(),
              initialized.applicationId(),
              initialized.detectedBookFormatVersion(),
              initialized.supportedBookFormatVersion(),
              initialized.initializedAt().toString(),
              bookIdentityPayload(initialized.bookIdentity()));
    };
  }

  static CliAdministrationJsonModels.BookIdentityPayload bookIdentityPayload(
      BookIdentity bookIdentity) {
    return new CliAdministrationJsonModels.BookIdentityPayload(
        bookIdentity.entityName().value(),
        bookIdentity.functionalCurrency().code(),
        bookIdentity.fiscalYearStart().wireValue());
  }

  static CliBookQueryJsonModels.DeclaredAccountPayload accountPayload(DeclaredAccount account) {
    return new CliBookQueryJsonModels.DeclaredAccountPayload(
        account.accountCode().value(),
        account.accountName().value(),
        account.accountType().wireValue(),
        account.accountRole().wireValue(),
        account.normalBalance().wireValue(),
        account.active(),
        account.declaredAt().toString());
  }

  static CliBookQueryJsonModels.PostingPayload postingPayload(PostingFact postingFact) {
    return new CliBookQueryJsonModels.PostingPayload(
        postingFact.postingId().value(),
        postingFact.postingKind().wireValue(),
        postingFact.reversalReference().isPresent() ? "reversal" : "direct",
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.provenance().requestProvenance().actorId().value(),
        postingFact.provenance().requestProvenance().actorType().wireValue(),
        postingFact.provenance().requestProvenance().commandId().value(),
        postingFact.provenance().requestProvenance().idempotencyKey().value(),
        postingFact.provenance().requestProvenance().causationId().value(),
        postingFact
            .provenance()
            .requestProvenance()
            .correlationId()
            .map(value -> value.value())
            .orElse(null),
        postingFact.provenance().sourceChannel().wireValue(),
        postingFact
            .postingLineage()
            .reversalReference()
            .map(
                reference ->
                    new CliBookQueryJsonModels.ReversalPayload(
                        reference.priorPostingId().value(),
                        postingFact.postingLineage().reversalReason().orElseThrow().value()))
            .orElse(null),
        postingFact.journalEntry().lines().stream()
            .map(CliBookPayloadMapper::linePayload)
            .toList());
  }

  static CliBookQueryJsonModels.PostingListPayload postingPagePayload(PostingPage page) {
    return new CliBookQueryJsonModels.PostingListPayload(
        page.limit(),
        page.nextCursor().map(PostingPageCursor::wireValue).orElse(null),
        page.postings().stream().map(CliBookPayloadMapper::postingPayload).toList());
  }

  static CliBookQueryJsonModels.AccountListPayload accountPagePayload(AccountPage page) {
    return new CliBookQueryJsonModels.AccountListPayload(
        page.limit(),
        page.nextCursor().map(AccountPageCursor::wireValue).orElse(null),
        page.accounts().stream().map(CliBookPayloadMapper::accountPayload).toList());
  }

  static CliBookQueryJsonModels.AccountBalancePayload accountBalancePayload(
      AccountBalanceSnapshot snapshot) {
    return new CliBookQueryJsonModels.AccountBalancePayload(
        CliReportPayloadMapper.reportContextPayload(
            snapshot.bookIdentity(), snapshot.postingCoverage()),
        snapshot.account().accountCode().value(),
        snapshot.account().accountName().value(),
        snapshot.account().accountType().wireValue(),
        snapshot.account().accountRole().wireValue(),
        snapshot.account().normalBalance().wireValue(),
        snapshot.account().active(),
        snapshot.account().declaredAt().toString(),
        snapshot.effectiveDateFrom().map(Object::toString).orElse(null),
        snapshot.effectiveDateTo().map(Object::toString).orElse(null),
        snapshot.balances().stream().map(CliPayloadAssembler::balancePayload).toList());
  }

  static List<String> counterpartAccounts(DeclaredAccount account, PostingFact postingFact) {
    return postingFact.journalEntry().lines().stream()
        .map(line -> line.accountCode().value())
        .filter(accountCode -> !accountCode.equals(account.accountCode().value()))
        .distinct()
        .toList();
  }

  private static CliBookQueryJsonModels.JournalLinePayload linePayload(
      dev.erst.fingrind.core.JournalLine line) {
    return new CliBookQueryJsonModels.JournalLinePayload(
        line.accountCode().value(),
        line.side().wireValue(),
        MonetaryAmount.of(line.amount().money()));
  }

  private static String absolutePath(Path bookFilePath) {
    return bookFilePath.toAbsolutePath().normalize().toString();
  }
}
