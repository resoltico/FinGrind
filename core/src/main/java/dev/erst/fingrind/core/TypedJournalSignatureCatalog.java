package dev.erst.fingrind.core;

import dev.erst.fingrind.core.JournalLine.EntrySide;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Canonical typed-event anchor signatures and their matching rules. */
final class TypedJournalSignatureCatalog {
  private static final Set<AnchorEntry> INVENTORY_EXPENSE_SIGNATURE =
      anchorSignature(AccountRole.EXPENSE, AccountRole.INVENTORY);
  private static final Set<AnchorEntry> INVENTORY_COUNT_INCREASE_SIGNATURE =
      anchorSignature(AccountRole.INVENTORY, AccountRole.REVENUE);
  private static final Set<AnchorEntry> SETTLED_SALE_SIGNATURE =
      anchorSignature(AccountRole.CASH, AccountRole.REVENUE);
  private static final Set<AnchorEntry> CREDIT_SALE_SIGNATURE =
      anchorSignature(AccountRole.RECEIVABLE, AccountRole.REVENUE);
  private static final Set<AnchorEntry> PAYROLL_RUN_SIGNATURE =
      anchorSignature(AccountRole.EXPENSE, EntrySide.DEBIT);
  private static final Set<AnchorEntry> PAYROLL_SETTLEMENT_SIGNATURE =
      anchorSignature(AccountRole.CASH, EntrySide.CREDIT);
  private static final Set<AnchorEntry> FIXED_ASSET_CAPITALIZATION_SIGNATURE =
      anchorSignature(AccountRole.CASH, EntrySide.CREDIT);
  private static final Set<AnchorEntry> FIXED_ASSET_DEPRECIATION_SIGNATURE =
      anchorSignature(AccountRole.EXPENSE, EntrySide.DEBIT);
  private static final Set<AnchorEntry> FIXED_ASSET_DISPOSAL_NEUTRAL_SIGNATURE =
      anchorSignature(AccountRole.CASH, EntrySide.DEBIT);
  private static final Set<AnchorEntry> FIXED_ASSET_DISPOSAL_GAIN_SIGNATURE =
      anchorSignature(AccountRole.CASH, AccountRole.REVENUE);
  private static final Set<AnchorEntry> FIXED_ASSET_DISPOSAL_LOSS_SIGNATURE =
      Set.of(
          new AnchorEntry(AccountRole.CASH, EntrySide.DEBIT),
          new AnchorEntry(AccountRole.EXPENSE, EntrySide.DEBIT));
  private static final Set<AnchorEntry> FINANCING_BORROWING_SIGNATURE =
      anchorSignature(AccountRole.CASH, EntrySide.DEBIT);
  private static final Set<AnchorEntry> FINANCING_PRINCIPAL_REPAYMENT_SIGNATURE =
      anchorSignature(AccountRole.CASH, EntrySide.CREDIT);
  private static final Set<AnchorEntry> FINANCING_INTEREST_ACCRUAL_SIGNATURE =
      anchorSignature(AccountRole.EXPENSE, EntrySide.DEBIT);
  private static final Set<AnchorEntry> FINANCING_INTEREST_PAYMENT_SIGNATURE =
      anchorSignature(AccountRole.CASH, EntrySide.CREDIT);
  private static final Set<AnchorEntry> FOREIGN_CURRENCY_OBLIGATION_SIGNATURE =
      anchorSignature(AccountRole.RECEIVABLE, AccountRole.REVENUE);
  private static final Set<AnchorEntry> REALIZED_FOREIGN_EXCHANGE_GAIN_SIGNATURE =
      Set.of(
          new AnchorEntry(AccountRole.CASH, EntrySide.DEBIT),
          new AnchorEntry(AccountRole.RECEIVABLE, EntrySide.CREDIT),
          new AnchorEntry(AccountRole.REVENUE, EntrySide.CREDIT));
  private static final Set<AnchorEntry> REALIZED_FOREIGN_EXCHANGE_LOSS_SIGNATURE =
      Set.of(
          new AnchorEntry(AccountRole.CASH, EntrySide.DEBIT),
          new AnchorEntry(AccountRole.RECEIVABLE, EntrySide.CREDIT),
          new AnchorEntry(AccountRole.EXPENSE, EntrySide.DEBIT));
  private static final Set<AnchorEntry> REALIZED_FOREIGN_EXCHANGE_NEUTRAL_SIGNATURE =
      anchorSignature(AccountRole.CASH, AccountRole.RECEIVABLE);
  private static final List<TypedSignature> TYPED_SIGNATURES =
      List.of(
          typedSignature(EconomicEventClass.SETTLED_SALE, SETTLED_SALE_SIGNATURE),
          typedSignature(EconomicEventClass.CREDIT_SALE, CREDIT_SALE_SIGNATURE),
          typedSignature(
              EconomicEventClass.SETTLED_PURCHASE, AccountRole.INVENTORY, AccountRole.CASH),
          typedSignature(
              EconomicEventClass.CREDIT_PURCHASE, AccountRole.INVENTORY, AccountRole.PAYABLE),
          typedSignature(EconomicEventClass.SETTLED_EXPENSE, AccountRole.EXPENSE, AccountRole.CASH),
          typedSignature(
              EconomicEventClass.CREDIT_EXPENSE, AccountRole.EXPENSE, AccountRole.PAYABLE),
          typedSignature(
              EconomicEventClass.AR_SETTLEMENT, AccountRole.CASH, AccountRole.RECEIVABLE),
          typedSignature(EconomicEventClass.AP_SETTLEMENT, AccountRole.PAYABLE, AccountRole.CASH),
          typedSignature(
              EconomicEventClass.OWNER_CONTRIBUTION,
              AccountRole.CASH,
              AccountRole.EQUITY_CONTRIBUTED),
          typedSignature(
              EconomicEventClass.OWNER_WITHDRAWAL, AccountRole.EQUITY_DRAWS, AccountRole.CASH),
          typedSignature(
              EconomicEventClass.PREPAYMENT, AccountRole.PREPAID_EXPENSE, AccountRole.CASH),
          typedSignature(
              EconomicEventClass.DEFERRED_REVENUE, AccountRole.CASH, AccountRole.DEFERRED_REVENUE),
          typedSignature(
              EconomicEventClass.ACCRUED_EXPENSE, AccountRole.EXPENSE, AccountRole.ACCRUED_EXPENSE),
          typedSignature(
              EconomicEventClass.ACCRUAL_CUTOFF_RECOGNITION,
              AccountRole.EXPENSE,
              AccountRole.PREPAID_EXPENSE),
          typedSignature(
              EconomicEventClass.ACCRUAL_CUTOFF_RECOGNITION,
              AccountRole.DEFERRED_REVENUE,
              AccountRole.REVENUE),
          typedSignature(
              EconomicEventClass.ACCRUED_EXPENSE_SETTLEMENT,
              AccountRole.ACCRUED_EXPENSE,
              AccountRole.CASH));
  private static final Map<EconomicEventClass, Predicate<Set<AnchorEntry>>>
      ASSERTED_EVENT_SIGNATURE_POLICIES =
          Map.ofEntries(
              Map.entry(
                  EconomicEventClass.INVENTORY_CAPITALIZATION,
                  signature ->
                      matchesExactSignature(
                          signature,
                          EconomicEventClass.SETTLED_PURCHASE,
                          EconomicEventClass.CREDIT_PURCHASE)),
              Map.entry(
                  EconomicEventClass.INVENTORY_WRITE_DOWN, INVENTORY_EXPENSE_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.INVENTORY_SHRINKAGE, INVENTORY_EXPENSE_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.INVENTORY_COUNT_INCREASE,
                  INVENTORY_COUNT_INCREASE_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.PREPAYMENT,
                  signature -> matchesExactSignature(signature, EconomicEventClass.PREPAYMENT)),
              Map.entry(
                  EconomicEventClass.DEFERRED_REVENUE,
                  signature ->
                      matchesExactSignature(signature, EconomicEventClass.DEFERRED_REVENUE)),
              Map.entry(
                  EconomicEventClass.ACCRUED_EXPENSE,
                  signature ->
                      matchesExactSignature(signature, EconomicEventClass.ACCRUED_EXPENSE)),
              Map.entry(
                  EconomicEventClass.ACCRUAL_CUTOFF_RECOGNITION,
                  signature ->
                      matchesExactSignature(
                          signature, EconomicEventClass.ACCRUAL_CUTOFF_RECOGNITION)),
              Map.entry(
                  EconomicEventClass.ACCRUED_EXPENSE_SETTLEMENT,
                  signature ->
                      matchesExactSignature(
                          signature, EconomicEventClass.ACCRUED_EXPENSE_SETTLEMENT)),
              Map.entry(EconomicEventClass.LATVIAN_MONTHLY_PAYROLL, PAYROLL_RUN_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
                  PAYROLL_SETTLEMENT_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.LATVIAN_PAYROLL_STATE_REMITTANCE,
                  PAYROLL_SETTLEMENT_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.FIXED_ASSET_CAPITALIZATION,
                  FIXED_ASSET_CAPITALIZATION_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.FIXED_ASSET_DEPRECIATION,
                  FIXED_ASSET_DEPRECIATION_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.FIXED_ASSET_DISPOSAL,
                  signature ->
                      matchesAny(
                          signature,
                          FIXED_ASSET_DISPOSAL_NEUTRAL_SIGNATURE,
                          FIXED_ASSET_DISPOSAL_GAIN_SIGNATURE,
                          FIXED_ASSET_DISPOSAL_LOSS_SIGNATURE,
                          Set.of())),
              Map.entry(
                  EconomicEventClass.FINANCING_BORROWING, FINANCING_BORROWING_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.FINANCING_PRINCIPAL_REPAYMENT,
                  FINANCING_PRINCIPAL_REPAYMENT_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.FINANCING_INTEREST_ACCRUAL,
                  signature ->
                      matchesAny(signature, FINANCING_INTEREST_ACCRUAL_SIGNATURE, Set.of())),
              Map.entry(
                  EconomicEventClass.FINANCING_INTEREST_PAYMENT,
                  FINANCING_INTEREST_PAYMENT_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.FOREIGN_CURRENCY_OBLIGATION,
                  FOREIGN_CURRENCY_OBLIGATION_SIGNATURE::equals),
              Map.entry(
                  EconomicEventClass.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
                  signature ->
                      matchesAny(
                          signature,
                          REALIZED_FOREIGN_EXCHANGE_GAIN_SIGNATURE,
                          REALIZED_FOREIGN_EXCHANGE_LOSS_SIGNATURE,
                          REALIZED_FOREIGN_EXCHANGE_NEUTRAL_SIGNATURE)));

  private TypedJournalSignatureCatalog() {}

  static void requireCompatible(EconomicEventClass assertedEventClass, Set<AnchorEntry> signature) {
    Predicate<Set<AnchorEntry>> policy = ASSERTED_EVENT_SIGNATURE_POLICIES.get(assertedEventClass);
    if (policy == null || !policy.test(signature)) {
      throw new IllegalArgumentException(
          "Asserted typed event "
              + assertedEventClass.wireValue()
              + " is incompatible with the resolved journal anchor signature.");
    }
  }

  static Set<EconomicEventClass> containedEvents(Set<AnchorEntry> anchorSignature) {
    Set<EconomicEventClass> contained = new LinkedHashSet<>();
    for (TypedSignature typedSignature : TYPED_SIGNATURES) {
      if (anchorSignature.containsAll(typedSignature.anchorSignature)) {
        contained.add(typedSignature.eventClass);
      }
    }
    return Set.copyOf(contained);
  }

  static Optional<EconomicEventClass> exactSingleton(
      Set<AnchorEntry> signature, Set<EconomicEventClass> containedTypedEvents) {
    if (containedTypedEvents.size() != 1) {
      return Optional.empty();
    }
    EconomicEventClass candidate = containedTypedEvents.iterator().next();
    if (candidate == EconomicEventClass.SETTLED_SALE
        && tradingSaleSignature(signature, SETTLED_SALE_SIGNATURE)) {
      return Optional.of(candidate);
    }
    if (candidate == EconomicEventClass.CREDIT_SALE
        && tradingSaleSignature(signature, CREDIT_SALE_SIGNATURE)) {
      return Optional.of(candidate);
    }
    return matchesExactSignature(signature, candidate) ? Optional.of(candidate) : Optional.empty();
  }

  private static TypedSignature typedSignature(
      EconomicEventClass eventClass, AccountRole debitRole, AccountRole creditRole) {
    return new TypedSignature(eventClass, anchorSignature(debitRole, creditRole));
  }

  private static TypedSignature typedSignature(
      EconomicEventClass eventClass, Set<AnchorEntry> anchorSignature) {
    return new TypedSignature(eventClass, anchorSignature);
  }

  private static Set<AnchorEntry> anchorSignature(AccountRole debitRole, AccountRole creditRole) {
    return Set.of(
        new AnchorEntry(debitRole, EntrySide.DEBIT), new AnchorEntry(creditRole, EntrySide.CREDIT));
  }

  private static Set<AnchorEntry> anchorSignature(AccountRole role, EntrySide side) {
    return Set.of(new AnchorEntry(role, side));
  }

  private static boolean matchesExactSignature(
      Set<AnchorEntry> signature, EconomicEventClass... eventClasses) {
    for (EconomicEventClass eventClass : eventClasses) {
      for (TypedSignature typedSignature : TYPED_SIGNATURES) {
        if (typedSignature.eventClass == eventClass
            && typedSignature.anchorSignature.equals(signature)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean tradingSaleSignature(
      Set<AnchorEntry> signature, Set<AnchorEntry> saleSignature) {
    return signature.size() == saleSignature.size() + 2
        && signature.contains(new AnchorEntry(AccountRole.EXPENSE, EntrySide.DEBIT))
        && signature.contains(new AnchorEntry(AccountRole.INVENTORY, EntrySide.CREDIT));
  }

  @SafeVarargs
  private static boolean matchesAny(Set<AnchorEntry> signature, Set<AnchorEntry>... candidates) {
    for (Set<AnchorEntry> candidate : candidates) {
      if (candidate.equals(signature)) {
        return true;
      }
    }
    return false;
  }

  private record TypedSignature(EconomicEventClass eventClass, Set<AnchorEntry> anchorSignature) {
    private TypedSignature {
      Objects.requireNonNull(eventClass, "eventClass");
      anchorSignature = Set.copyOf(Objects.requireNonNull(anchorSignature, "anchorSignature"));
    }
  }
}
