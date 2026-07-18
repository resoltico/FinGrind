package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import java.time.Instant;
import java.time.LocalDate;

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
        account.accountTaxonomy().contraOfAccountCode().map(AccountCode::value).orElse(null),
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
        account.unitOfMeasure() == null
            ? null
            : new CliBookQueryJsonModels.UnitOfMeasurePayload(
                account.unitOfMeasure().token(), account.unitOfMeasure().quantityScale()),
        account.normalBalance().wireValue(),
        account.active(),
        account.declaredAt().toString());
  }

  static CliBookQueryJsonModels.PostingDetailsPayload postingDetailsPayload(
      BookIdentity bookIdentity, PostingFact postingFact, Instant generatedAt) {
    return new CliBookQueryJsonModels.PostingDetailsPayload(
        OperationId.GET_POSTING.wireName(),
        CliBookInspectionPayloadMapper.bookIdentityPayload(bookIdentity),
        new CliBookQueryJsonModels.GetPostingResolvedQuery(postingFact.postingId().value()),
        CliReportPayloadMappingSupport.instant(generatedAt),
        CliBookPostingPayloadMapper.postingPayload(postingFact));
  }

  static CliBookQueryJsonModels.PostingDetailsPayload postingDetailsPayload(
      GetPostingResult.Found found, Instant generatedAt) {
    return new CliBookQueryJsonModels.PostingDetailsPayload(
        OperationId.GET_POSTING.wireName(),
        CliBookInspectionPayloadMapper.bookIdentityPayload(found.bookIdentity()),
        new CliBookQueryJsonModels.GetPostingResolvedQuery(found.postingFact().postingId().value()),
        CliReportPayloadMappingSupport.instant(generatedAt),
        CliBookPostingPayloadMapper.postingPayload(
            found.postingFact(),
            found.reversedByPostingId().map(dev.erst.fingrind.core.PostingId::value).orElse(null)));
  }

  static CliBookQueryJsonModels.PostingListPayload postingPagePayload(
      ListPostingsQuery query, PostingPage page, Instant generatedAt) {
    return new CliBookQueryJsonModels.PostingListPayload(
        OperationId.LIST_POSTINGS.wireName(),
        CliBookInspectionPayloadMapper.bookIdentityPayload(page.bookIdentity()),
        new CliBookQueryJsonModels.PostingListResolvedQuery(
            query.accountCode().map(AccountCode::value).orElse(null),
            query.effectiveDateFrom().map(LocalDate::toString).orElse(null),
            query.effectiveDateTo().map(LocalDate::toString).orElse(null),
            query.limit(),
            query.cursor().map(PostingPageCursor::wireValue).orElse(null)),
        CliReportPayloadMappingSupport.instant(generatedAt),
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

  static CliBookQueryJsonModels.AccountListPayload accountPagePayload(
      ListAccountsQuery query, AccountPage page, Instant generatedAt) {
    return new CliBookQueryJsonModels.AccountListPayload(
        OperationId.LIST_ACCOUNTS.wireName(),
        CliBookInspectionPayloadMapper.bookIdentityPayload(page.bookIdentity()),
        new CliBookQueryJsonModels.AccountListResolvedQuery(
            query.limit(), query.cursor().map(AccountPageCursor::wireValue).orElse(null)),
        CliReportPayloadMappingSupport.instant(generatedAt),
        page.nextCursor().map(AccountPageCursor::wireValue).orElse(null),
        page.accounts().stream().map(CliBookQueryPayloadMapper::accountPayload).toList());
  }
}
