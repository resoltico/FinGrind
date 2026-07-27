package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.contract.tax.TaxRegistrationPageCursor;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Contract coverage for accepted and continuation pagination cursor ownership. */
class CliPaginationResponseContractTest extends CliResponseWriterTestSupport {
  @Test
  void paginatedQueryPayloads_distinguishAcceptedAndContinuationCursors() throws IOException {
    AccountPageCursor acceptedAccountCursor = new AccountPageCursor(new AccountCode("0999"));
    AccountPageCursor nextAccountCursor = new AccountPageCursor(new AccountCode("1001"));
    ByteArrayOutputStream accountOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriterFixture(utf8PrintStream(accountOutput))
        .writeListAccountsResult(
            new ListAccountsResult.Listed(
                new ListAccountsQuery(1, Optional.of(acceptedAccountCursor)),
                accountPage(List.of(declaredCashAccount()), 1, Optional.of(nextAccountCursor))),
            OutputMode.JSON);
    assertPaginationPayload(
        readJson(accountOutput), acceptedAccountCursor.wireValue(), nextAccountCursor.wireValue());

    PostingPageCursor acceptedPostingCursor =
        new PostingPageCursor(
            LocalDate.parse("2026-04-06"),
            Instant.parse("2026-04-06T10:15:30Z"),
            new PostingId("e888fd00-a501-341d-9a6b-8d9059757d1b"));
    PostingPageCursor nextPostingCursor =
        new PostingPageCursor(
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"));
    ByteArrayOutputStream postingOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriterFixture(utf8PrintStream(postingOutput))
        .writeListPostingsResult(
            new ListPostingsResult.Listed(
                new ListPostingsQuery(
                    Optional.empty(),
                    EffectiveDateRange.of(null, null),
                    1,
                    Optional.of(acceptedPostingCursor)),
                postingPage(List.of(postingFact()), 1, Optional.of(nextPostingCursor))),
            OutputMode.JSON);
    assertPaginationPayload(
        readJson(postingOutput), acceptedPostingCursor.wireValue(), nextPostingCursor.wireValue());

    TaxRegistrationPageCursor acceptedRegistrationCursor =
        new TaxRegistrationPageCursor(new TaxRegistrationId("vat-before"));
    TaxRegistrationPageCursor nextRegistrationCursor =
        new TaxRegistrationPageCursor(new TaxRegistrationId("vat-after"));
    ByteArrayOutputStream registrationOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriterFixture(utf8PrintStream(registrationOutput))
        .writeListTaxRegistrationsResult(
            new ListTaxRegistrationsResult.Listed(
                new ListTaxRegistrationsQuery(1, Optional.of(acceptedRegistrationCursor)),
                new TaxRegistrationPage(
                    bookIdentity(), List.of(), 1, Optional.of(nextRegistrationCursor))),
            OutputMode.JSON);
    assertPaginationPayload(
        readJson(registrationOutput),
        acceptedRegistrationCursor.wireValue(),
        nextRegistrationCursor.wireValue());
  }

  private static void assertPaginationPayload(
      JsonNode envelope, String acceptedCursor, String nextCursor) {
    JsonNode payload = envelope.path("payload");
    assertEquals(acceptedCursor, payload.path("resolvedQuery").path("cursor").stringValue());
    assertEquals(nextCursor, payload.path("nextCursor").stringValue());
  }
}
