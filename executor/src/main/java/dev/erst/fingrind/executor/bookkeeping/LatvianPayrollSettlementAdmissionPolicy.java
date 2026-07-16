package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.PostingLatvianPayrollRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** First-defense admission and exact-obligation resolution for Latvian payroll settlements. */
public final class LatvianPayrollSettlementAdmissionPolicy {
  /** Resolves an admitted payroll settlement or returns its deterministic refusal. */
  public Resolution resolve(
      BookkeepingEntry entry, PostingValidationStore book, String selectorValue) {
    return switch (entry) {
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement settlement ->
          settlementResolution(
              new SettlementRequest(
                  settlement.effectiveDate(),
                  settlement.payrollRunId(),
                  settlement.cashAccountCode(),
                  LatvianPayrollSettlementKind.NET_WAGES,
                  resolved ->
                      new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
                          settlement.effectiveDate(),
                          settlement.payrollRunId(),
                          settlement.cashAccountCode(),
                          resolved)),
              book,
              selectorValue);
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance settlement ->
          settlementResolution(
              new SettlementRequest(
                  settlement.effectiveDate(),
                  settlement.payrollRunId(),
                  settlement.cashAccountCode(),
                  LatvianPayrollSettlementKind.STATE_REMITTANCE,
                  resolved ->
                      new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
                          settlement.effectiveDate(),
                          settlement.payrollRunId(),
                          settlement.cashAccountCode(),
                          resolved)),
              book,
              selectorValue);
      default -> Resolution.accepted(entry);
    };
  }

  private static Resolution settlementResolution(
      SettlementRequest request, PostingValidationStore book, String selectorValue) {
    LatvianPayrollRunRecord run = book.findLatvianPayrollRun(request.payrollRunId()).orElse(null);
    if (run == null) {
      return Resolution.rejected(
          PostingLatvianPayrollRejectionSemantics.runNotFound(
              selectorValue, request.payrollRunId()));
    }
    if (!run.active()) {
      return Resolution.rejected(
          PostingLatvianPayrollRejectionSemantics.runReversed(selectorValue, run.payrollRunId()));
    }
    if (request.effectiveDate().isBefore(run.effectiveDate())) {
      return Resolution.rejected(
          PostingLatvianPayrollRejectionSemantics.settlementPrecedesRun(
              selectorValue, run.payrollRunId(), request.effectiveDate(), run.effectiveDate()));
    }
    if (book.findActiveLatvianPayrollSettlement(run.payrollRunId(), request.settlementKind())
        .isPresent()) {
      return Resolution.rejected(
          PostingLatvianPayrollRejectionSemantics.settlementAlreadyExists(
              selectorValue, run.payrollRunId(), request.settlementKind()));
    }
    ResolvedLatvianPayrollSettlement resolvedSettlement =
        new ResolvedLatvianPayrollSettlement(
            request.settlementKind(),
            run.payrollRunId(),
            request.cashAccountCode(),
            run.netWagesPayableAccountCode(),
            run.employeeSocialContributionPayableAccountCode(),
            run.employerSocialContributionPayableAccountCode(),
            run.personalIncomeTaxPayableAccountCode(),
            run.calculation().netWages(),
            run.calculation().employeeSocialContribution(),
            run.calculation().employerSocialContribution(),
            run.calculation().personalIncomeTax());
    return Resolution.accepted(request.resolvedEntryFactory().apply(resolvedSettlement));
  }

  private record SettlementRequest(
      LocalDate effectiveDate,
      LatvianPayrollRunId payrollRunId,
      AccountCode cashAccountCode,
      LatvianPayrollSettlementKind settlementKind,
      Function<ResolvedLatvianPayrollSettlement, BookkeepingEntry> resolvedEntryFactory) {}

  /** One resolved entry or its deterministic entry-semantics rejection. */
  public record Resolution(
      @Nullable BookkeepingEntry entry, Optional<BookkeepingPostingRejection> rejection) {
    public Resolution {
      rejection =
          Optional.ofNullable(
              java.util.Objects.requireNonNull(rejection, "rejection").orElse(null));
      if (rejection.isEmpty() && entry == null) {
        throw new IllegalArgumentException(
            "Accepted Latvian payroll settlement resolution requires one entry.");
      }
    }

    static Resolution accepted(BookkeepingEntry entry) {
      return new Resolution(entry, Optional.empty());
    }

    static Resolution rejected(
        dev.erst.fingrind.contract.bookkeeping.PostingRejection.EntrySemanticsViolation rejection) {
      return new Resolution(
          null,
          Optional.of(
              new BookkeepingPostingRejection.EntrySemanticsViolations(
                  List.of(
                      new BookkeepingPostingRejection.EntrySemanticsViolation(
                          rejection.code(), rejection.field(), rejection.message())))));
    }
  }
}
