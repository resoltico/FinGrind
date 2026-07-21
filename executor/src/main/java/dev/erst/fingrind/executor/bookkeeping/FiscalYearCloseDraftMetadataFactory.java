package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/** Builds deterministic provenance and evidence for generated fiscal-year-close drafts. */
final class FiscalYearCloseDraftMetadataFactory {
  private static final String REQUEST_TOKEN = "fiscalYearClose";

  private FiscalYearCloseDraftMetadataFactory() {}

  static RequestProvenance requestProvenance(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      String closeStep,
      Instant closedAt) {
    String closeToken = closeToken(reportingPeriod, currencyUnit, closeStep, closedAt);
    return new RequestProvenance(
        new CommandId(deterministicUuid(REQUEST_TOKEN + ":" + closeToken)),
        new IdempotencyKey(REQUEST_TOKEN + ":" + closeToken),
        new CausationId(REQUEST_TOKEN + ":" + closeToken),
        java.util.Optional.of(new CorrelationId(REQUEST_TOKEN + ":" + closeToken)));
  }

  static AccountingEvidence evidence(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      String closeStep,
      Instant closedAt) {
    String closeToken = closeToken(reportingPeriod, currencyUnit, closeStep, closedAt);
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId(REQUEST_TOKEN + ":" + closeToken),
                new SourceDocumentType("year-end-close-plan"),
                reportingPeriod.effectiveDateTo())),
        List.of());
  }

  private static String closeToken(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      String closeStep,
      Instant closedAt) {
    return reportingPeriod.effectiveDateFrom()
        + ":"
        + reportingPeriod.effectiveDateTo()
        + ":"
        + closeStep
        + ":"
        + currencyUnit.code()
        + ":"
        + closedAt.toEpochMilli();
  }

  private static String deterministicUuid(String value) {
    return java.util.UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
  }
}
