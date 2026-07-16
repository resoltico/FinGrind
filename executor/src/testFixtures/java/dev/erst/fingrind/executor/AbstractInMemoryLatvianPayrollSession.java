package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollRunRecord;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollSettlementRecord;
import dev.erst.fingrind.executor.spi.LatvianPayrollLookupStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Shared in-memory payroll lifecycle lookup fixture reconstructed from committed postings. */
abstract class AbstractInMemoryLatvianPayrollSession
    extends AbstractInMemoryAccountRegistryLifecycleSession implements LatvianPayrollLookupStore {
  protected abstract Map<PostingId, CommittedPosting> postingsByPostingId();

  protected abstract Map<PostingId, CommittedPosting> reversalsByPriorPostingId();

  @Override
  public Optional<LatvianPayrollRunRecord> findLatvianPayrollRun(LatvianPayrollRunId payrollRunId) {
    Objects.requireNonNull(payrollRunId, "payrollRunId");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            latvianPayrollRuns().stream()
                .filter(run -> run.payrollRunId().equals(payrollRunId))
                .findFirst());
  }

  @Override
  public Optional<LatvianPayrollRunRecord> findActiveLatvianPayrollRun(
      LatvianPayrollEmployeeReference employeeReference, LatvianPayrollMonth payrollMonth) {
    Objects.requireNonNull(employeeReference, "employeeReference");
    Objects.requireNonNull(payrollMonth, "payrollMonth");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            latvianPayrollRuns().stream()
                .filter(LatvianPayrollRunRecord::active)
                .filter(run -> run.employeeReference().equals(employeeReference))
                .filter(run -> run.payrollMonth().equals(payrollMonth))
                .findFirst());
  }

  @Override
  public List<LatvianPayrollRunRecord> latvianPayrollRuns() {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            postingsByPostingId().values().stream()
                .flatMap(this::payrollRun)
                .sorted(
                    Comparator.comparing(
                            (LatvianPayrollRunRecord run) -> run.payrollMonth().wireValue())
                        .thenComparing(run -> run.payrollRunId().value()))
                .toList());
  }

  @Override
  public Optional<LatvianPayrollSettlementRecord> findActiveLatvianPayrollSettlement(
      LatvianPayrollRunId payrollRunId, LatvianPayrollSettlementKind settlementKind) {
    Objects.requireNonNull(payrollRunId, "payrollRunId");
    Objects.requireNonNull(settlementKind, "settlementKind");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            latvianPayrollSettlements().stream()
                .filter(LatvianPayrollSettlementRecord::active)
                .filter(settlement -> settlement.payrollRunId().equals(payrollRunId))
                .filter(settlement -> settlement.settlementKind() == settlementKind)
                .findFirst());
  }

  @Override
  public Optional<LatvianPayrollSettlementRecord> findLatvianPayrollSettlementByPosting(
      PostingId originPostingId) {
    Objects.requireNonNull(originPostingId, "originPostingId");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            latvianPayrollSettlements().stream()
                .filter(settlement -> settlement.originPostingId().equals(originPostingId))
                .findFirst());
  }

  @Override
  public Optional<LatvianPayrollRunRecord> findLatvianPayrollRunByOriginPosting(
      PostingId originPostingId) {
    Objects.requireNonNull(originPostingId, "originPostingId");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            latvianPayrollRuns().stream()
                .filter(run -> run.originPostingId().equals(originPostingId))
                .findFirst());
  }

  @Override
  public List<LatvianPayrollSettlementRecord> latvianPayrollSettlements() {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            postingsByPostingId().values().stream()
                .flatMap(this::payrollSettlement)
                .sorted(
                    Comparator.comparing(
                            (LatvianPayrollSettlementRecord settlement) ->
                                settlement.payrollRunId().value())
                        .thenComparing(settlement -> settlement.settlementKind().wireValue())
                        .thenComparing(settlement -> settlement.originPostingId().value()))
                .toList());
  }

  private Stream<LatvianPayrollRunRecord> payrollRun(CommittedPosting posting) {
    return posting.resolvedOriginatingEntry().stream()
        .flatMap(
            entry -> {
              if (entry instanceof LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll) {
                return Stream.of(
                    new LatvianPayrollRunRecord(
                        payroll.payrollRunId(),
                        payroll.employeeReference(),
                        payroll.payrollMonth(),
                        posting.journalEntry().effectiveDate(),
                        payroll.wageExpenseAccountCode(),
                        payroll.employerSocialContributionExpenseAccountCode(),
                        payroll.netWagesPayableAccountCode(),
                        payroll.employeeSocialContributionPayableAccountCode(),
                        payroll.employerSocialContributionPayableAccountCode(),
                        payroll.personalIncomeTaxPayableAccountCode(),
                        Objects.requireNonNull(
                            payroll.resolvedCalculation(), "resolvedCalculation"),
                        posting.postingId(),
                        Optional.ofNullable(reversalsByPriorPostingId().get(posting.postingId()))
                            .map(CommittedPosting::postingId)));
              }
              return Stream.empty();
            });
  }

  private Stream<LatvianPayrollSettlementRecord> payrollSettlement(CommittedPosting posting) {
    return posting.resolvedOriginatingEntry().stream()
        .flatMap(
            entry ->
                switch (entry) {
                  case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement settlement ->
                      Stream.of(
                          settlementRecord(
                              LatvianPayrollSettlementKind.NET_WAGES,
                              settlement.payrollRunId(),
                              posting,
                              settlement.cashAccountCode()));
                  case LatvianPayrollBookkeepingEntryVariants.StateRemittance settlement ->
                      Stream.of(
                          settlementRecord(
                              LatvianPayrollSettlementKind.STATE_REMITTANCE,
                              settlement.payrollRunId(),
                              posting,
                              settlement.cashAccountCode()));
                  default -> Stream.empty();
                });
  }

  private LatvianPayrollSettlementRecord settlementRecord(
      LatvianPayrollSettlementKind settlementKind,
      LatvianPayrollRunId payrollRunId,
      CommittedPosting posting,
      dev.erst.fingrind.core.AccountCode cashAccountCode) {
    return new LatvianPayrollSettlementRecord(
        settlementKind,
        payrollRunId,
        posting.postingId(),
        posting.journalEntry().effectiveDate(),
        cashAccountCode,
        Optional.ofNullable(reversalsByPriorPostingId().get(posting.postingId()))
            .map(CommittedPosting::postingId));
  }
}
