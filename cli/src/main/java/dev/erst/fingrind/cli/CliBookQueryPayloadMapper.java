package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Maps read-model query payloads into CLI JSON models. */
final class CliBookQueryPayloadMapper {
  private CliBookQueryPayloadMapper() {}

  static CliBookQueryJsonModels.DeclaredAccountPayload accountPayload(DeclaredAccount account) {
    return new CliBookQueryJsonModels.DeclaredAccountPayload(
        account.accountCode().value(),
        account.accountName().value(),
        account.accountType().wireValue(),
        account.accountTaxonomy().nodeKind().wireValue(),
        account.accountTaxonomy().parentAccountCode().map(AccountCode::value).orElse(null),
        account
            .accountTaxonomy()
            .financialPositionLineClassification()
            .map(value -> value.wireValue())
            .orElse(null),
        account
            .accountTaxonomy()
            .cashFlowAssetClassification()
            .map(value -> value.wireValue())
            .orElse(null),
        account
            .accountTaxonomy()
            .profitAndLossLineClassification()
            .map(value -> value.wireValue())
            .orElse(null),
        account.normalBalance().wireValue(),
        account.active(),
        account.declaredAt().toString());
  }

  static CliBookQueryJsonModels.BookContextPayload bookContextPayload(BookIdentity bookIdentity) {
    return new CliBookQueryJsonModels.BookContextPayload(
        CliBookInspectionPayloadMapper.bookIdentityPayload(bookIdentity));
  }

  static CliBookQueryJsonModels.PostingQueryContextPayload postingQueryContextPayload(
      BookIdentity bookIdentity,
      @Nullable AccountCode accountCodeFilter,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo) {
    return new CliBookQueryJsonModels.PostingQueryContextPayload(
        CliBookInspectionPayloadMapper.bookIdentityPayload(bookIdentity),
        accountCodeFilter == null ? null : accountCodeFilter.value(),
        effectiveDateFrom == null ? null : effectiveDateFrom.toString(),
        CliQueryScopeText.lowerDateBoundaryMeaning(effectiveDateFrom),
        effectiveDateTo == null ? null : effectiveDateTo.toString(),
        CliQueryScopeText.upperDateBoundaryMeaning(effectiveDateTo));
  }

  static CliBookQueryJsonModels.PostingDetailsPayload postingDetailsPayload(
      BookIdentity bookIdentity, PostingFact postingFact) {
    return new CliBookQueryJsonModels.PostingDetailsPayload(
        bookContextPayload(bookIdentity), CliBookPostingPayloadMapper.postingPayload(postingFact));
  }

  static CliBookQueryJsonModels.PostingDetailsPayload postingDetailsPayload(
      GetPostingResult.Found found) {
    return new CliBookQueryJsonModels.PostingDetailsPayload(
        bookContextPayload(found.bookIdentity()),
        CliBookPostingPayloadMapper.postingPayload(
            found.postingFact(),
            found.reversedByPostingId().map(dev.erst.fingrind.core.PostingId::value).orElse(null)));
  }

  static CliBookQueryJsonModels.PostingListPayload postingPagePayload(PostingPage page) {
    return new CliBookQueryJsonModels.PostingListPayload(
        postingQueryContextPayload(
            page.bookIdentity(),
            page.accountCodeFilter().orElse(null),
            page.effectiveDateRange().effectiveDateFrom().orElse(null),
            page.effectiveDateRange().effectiveDateTo().orElse(null)),
        page.limit(),
        page.nextCursor().map(PostingPageCursor::wireValue).orElse(null),
        page.postings().stream()
            .map(
                posting ->
                    CliBookPostingPayloadMapper.postingSummaryPayload(
                        posting,
                        java.util.Optional.ofNullable(
                                page.reversedByPostingIds().get(posting.postingId()))
                            .map(dev.erst.fingrind.core.PostingId::value)
                            .orElse(null)))
            .toList());
  }

  static CliBookQueryJsonModels.AccountListPayload accountPagePayload(AccountPage page) {
    return new CliBookQueryJsonModels.AccountListPayload(
        bookContextPayload(page.bookIdentity()),
        page.limit(),
        page.nextCursor().map(AccountPageCursor::wireValue).orElse(null),
        page.accounts().stream().map(CliBookQueryPayloadMapper::accountPayload).toList());
  }
}
