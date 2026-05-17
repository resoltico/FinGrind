package dev.erst.fingrind.contract.operations;

import dev.erst.fingrind.core.BusinessEventStatus;
import dev.erst.fingrind.core.CashFlowActivity;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.OtherComprehensiveIncomeClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Persisted public projection of one typed business event plus its generated postings. */
public record BusinessEventRecord(
    BusinessEventRequest businessEventRequest,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel,
    Instant recordedAt,
    BusinessEventStatus businessEventStatus,
    Optional<CashFlowActivity> cashFlowActivity,
    Optional<Money> cashFlowAmount,
    Optional<OtherComprehensiveIncomeClassification> otherComprehensiveIncomeClassification,
    Optional<Money> otherComprehensiveIncomeAmount,
    List<PostingId> postingIds) {
  /** Validates and defensively copies one business-event record. */
  public BusinessEventRecord {
    Objects.requireNonNull(businessEventRequest, "businessEventRequest");
    Objects.requireNonNull(requestProvenance, "requestProvenance");
    Objects.requireNonNull(sourceChannel, "sourceChannel");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(businessEventStatus, "businessEventStatus");
    Objects.requireNonNull(cashFlowActivity, "cashFlowActivity");
    Objects.requireNonNull(cashFlowAmount, "cashFlowAmount");
    Objects.requireNonNull(
        otherComprehensiveIncomeClassification, "otherComprehensiveIncomeClassification");
    Objects.requireNonNull(otherComprehensiveIncomeAmount, "otherComprehensiveIncomeAmount");
    postingIds = List.copyOf(Objects.requireNonNull(postingIds, "postingIds"));
    if (cashFlowActivity.isPresent() != cashFlowAmount.isPresent()) {
      throw new IllegalArgumentException(
          "cashFlowActivity and cashFlowAmount must either both be present or both be absent.");
    }
    if (otherComprehensiveIncomeClassification.isPresent()
        != otherComprehensiveIncomeAmount.isPresent()) {
      throw new IllegalArgumentException(
          "otherComprehensiveIncomeClassification and otherComprehensiveIncomeAmount must either both be present or both be absent.");
    }
  }
}
