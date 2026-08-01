package dev.erst.fingrind.core.attestation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Catalog-owned closed operation-to-request/effect tag profiles. */
final class AttestationOperationProfileCatalog {
  private static final Group B =
      group(0x0100, 0x0120, 0x0121, 0x0122, 0x0124, 0x0020, 0x0021, 0x0025);
  private static final Group D = group(0x0100, 0x0120, 0x0124, 0x012A, 0x0020, 0x0021, 0x0025);
  private static final Group T = group(0x0126, 0x0023);
  private static final Group X = group(0x0127, 0x0024);
  private static final Group S = group(0x0129);
  private static final Group I = group(0x0123, 0x0128, 0x0030, 0x0031);
  private static final Group A1 = group(0x0130, 0x0050);
  private static final Group A2 = group(0x0130, 0x0051);
  private static final Group L1 = group(0x0134, 0x0090);
  private static final Group L2 = group(0x0134, 0x0092, 0x0093);
  private static final Group F1 = group(0x0131, 0x0060);
  private static final Group F2 = group(0x0131, 0x0061);
  private static final Group F3 = group(0x0131, 0x0062);
  private static final Group N1 = group(0x0132, 0x0070);
  private static final Group N2 = group(0x0132, 0x0071);
  private static final Group O1 = group(0x0133, 0x0127, 0x0024, 0x0080);
  private static final Group O2 = group(0x0133, 0x0127, 0x0024, 0x0081);
  private static final Map<AttestationOperationKind, TagProfile> PROFILES = profiles();

  private AttestationOperationProfileCatalog() {}

  static TagProfile profile(AttestationOperationKind operationKind) {
    return Objects.requireNonNull(
        PROFILES.get(Objects.requireNonNull(operationKind, "operationKind")),
        "operation profile catalog is incomplete");
  }

  private static Map<AttestationOperationKind, TagProfile> profiles() {
    Map<AttestationOperationKind, TagProfile> profiles = new ConcurrentHashMap<>();
    registerIdentityProfiles(profiles);
    registerPostingProfiles(profiles);
    AttestationLifecycleOperationProfileCatalog.register(profiles);
    return Map.copyOf(profiles);
  }

  private static void registerIdentityProfiles(Map<AttestationOperationKind, TagProfile> profiles) {
    associate(
        profiles,
        profile(
            tags(0x0100, 0x0110),
            tags(0x0100, 0x0110, 0x0111, 0x0112),
            tags(0x0010),
            tags(0x0010, 0x0011, 0x0012)),
        AttestationOperationKind.DECLARE_ACCOUNT,
        AttestationOperationKind.AMEND_ACCOUNT,
        AttestationOperationKind.RETIRE_ACCOUNT);
    associate(
        profiles,
        profile(
            tags(0x0100, 0x0113), tags(0x0100, 0x0113, 0x0114), tags(0x0013), tags(0x0013, 0x0014)),
        AttestationOperationKind.DECLARE_TAX_REGISTRATION);
  }

  private static void registerPostingProfiles(Map<AttestationOperationKind, TagProfile> profiles) {
    associate(profiles, posting(List.of(D), List.of(X)), AttestationOperationKind.POST_ENTRY);
    associate(
        profiles,
        AttestationLifecycleOperationProfileCatalog.executePlanProfile(),
        AttestationOperationKind.EXECUTE_PLAN);
    associate(
        profiles,
        posting(List.of(B), List.of(T)),
        AttestationOperationKind.RECORD_SALE_SETTLED,
        AttestationOperationKind.RECORD_SALE_ON_CREDIT,
        AttestationOperationKind.RECORD_PURCHASE_SETTLED,
        AttestationOperationKind.RECORD_PURCHASE_ON_CREDIT,
        AttestationOperationKind.RECORD_EXPENSE_SETTLED,
        AttestationOperationKind.RECORD_EXPENSE_ON_CREDIT);
    associate(
        profiles,
        posting(List.of(B), List.of(S)),
        AttestationOperationKind.RECORD_RECEIPT,
        AttestationOperationKind.RECORD_PAYMENT);
    associate(
        profiles,
        posting(List.of(B), List.of()),
        AttestationOperationKind.RECORD_OWNER_CONTRIBUTION,
        AttestationOperationKind.RECORD_OWNER_WITHDRAWAL);
    associate(
        profiles, posting(List.of(D), List.of()), AttestationOperationKind.RECORD_OPENING_POSITION);
    associate(
        profiles,
        posting(List.of(B, I), List.of()),
        AttestationOperationKind.RECORD_INVENTORY_CAPITALIZATION_SETTLED,
        AttestationOperationKind.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
        AttestationOperationKind.RECORD_INVENTORY_WRITE_DOWN,
        AttestationOperationKind.RECORD_INVENTORY_SHRINKAGE,
        AttestationOperationKind.RECORD_INVENTORY_COUNT_INCREASE);
    associate(
        profiles,
        posting(List.of(B, A1), List.of()),
        AttestationOperationKind.RECORD_PREPAYMENT,
        AttestationOperationKind.RECORD_DEFERRED_REVENUE,
        AttestationOperationKind.RECORD_ACCRUED_EXPENSE);
    associate(
        profiles,
        posting(List.of(B, A2), List.of()),
        AttestationOperationKind.RECORD_ACCRUAL_CUTOFF_RECOGNITION,
        AttestationOperationKind.RECORD_ACCRUED_EXPENSE_SETTLEMENT);
    associate(
        profiles,
        posting(List.of(B, L1), List.of()),
        AttestationOperationKind.RECORD_LATVIAN_MONTHLY_PAYROLL);
    associate(
        profiles,
        posting(List.of(B, L2), List.of()),
        AttestationOperationKind.RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
        AttestationOperationKind.RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE);
    associate(
        profiles,
        posting(List.of(B, F1), List.of()),
        AttestationOperationKind.RECORD_FIXED_ASSET_CAPITALIZATION);
    associate(
        profiles,
        posting(List.of(B, F2), List.of()),
        AttestationOperationKind.RECORD_FIXED_ASSET_DEPRECIATION);
    associate(
        profiles,
        posting(List.of(B, F3), List.of()),
        AttestationOperationKind.RECORD_FIXED_ASSET_DISPOSAL);
    associate(
        profiles,
        posting(List.of(B, N1), List.of()),
        AttestationOperationKind.RECORD_FINANCING_BORROWING);
    associate(
        profiles,
        posting(List.of(B, N2), List.of()),
        AttestationOperationKind.RECORD_FINANCING_PRINCIPAL_REPAYMENT,
        AttestationOperationKind.RECORD_FINANCING_INTEREST_ACCRUAL,
        AttestationOperationKind.RECORD_FINANCING_INTEREST_PAYMENT);
    associate(
        profiles,
        posting(List.of(B, O1), List.of()),
        AttestationOperationKind.RECORD_FOREIGN_CURRENCY_OBLIGATION);
    associate(
        profiles,
        posting(List.of(B, O2), List.of()),
        AttestationOperationKind.RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT);
    associate(
        profiles, posting(List.of(B), List.of(T, X)), AttestationOperationKind.RECORD_REVERSAL);
  }

  static void associate(
      Map<AttestationOperationKind, TagProfile> profiles,
      TagProfile profile,
      AttestationOperationKind first,
      AttestationOperationKind... remaining) {
    profiles.put(first, profile);
    for (AttestationOperationKind operationKind : remaining) {
      profiles.put(operationKind, profile);
    }
  }

  private static TagProfile posting(List<Group> requiredGroups, List<Group> optionalGroups) {
    List<Group> allowedGroups = new ArrayList<>(requiredGroups);
    allowedGroups.addAll(optionalGroups);
    List<TagPair> optionalPairs = new ArrayList<>();
    for (Group group : optionalGroups) {
      optionalPairs.add(new TagPair(group.requestTags(), group.effectTags()));
    }
    return new TagProfile(
        union(requiredGroups, Group::requestTags),
        union(allowedGroups, Group::requestTags),
        union(requiredGroups, Group::effectTags),
        union(allowedGroups, Group::effectTags),
        optionalPairs,
        false);
  }

  static TagProfile profile(
      Set<Integer> requiredRequestTags,
      Set<Integer> allowedRequestTags,
      Set<Integer> requiredEffectTags,
      Set<Integer> allowedEffectTags) {
    return profile(
        requiredRequestTags, allowedRequestTags, requiredEffectTags, allowedEffectTags, false);
  }

  static TagProfile profile(
      Set<Integer> requiredRequestTags,
      Set<Integer> allowedRequestTags,
      Set<Integer> requiredEffectTags,
      Set<Integer> allowedEffectTags,
      boolean requireAnEffect) {
    return new TagProfile(
        requiredRequestTags,
        allowedRequestTags,
        requiredEffectTags,
        allowedEffectTags,
        List.of(),
        requireAnEffect);
  }

  static Set<Integer> tags(int... tags) {
    return java.util.Arrays.stream(tags)
        .boxed()
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static Group group(int... tags) {
    List<Integer> request = new ArrayList<>();
    List<Integer> effect = new ArrayList<>();
    for (int tag : tags) {
      (tag >= 0x0100 ? request : effect).add(tag);
    }
    return new Group(Set.copyOf(request), Set.copyOf(effect));
  }

  private static Set<Integer> union(
      List<Group> groups, java.util.function.Function<Group, Set<Integer>> selector) {
    return groups.stream()
        .flatMap(group -> selector.apply(group).stream())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private record Group(Set<Integer> requestTags, Set<Integer> effectTags) {}

  private record TagPair(Set<Integer> requestTags, Set<Integer> effectTags) {}

  record TagProfile(
      Set<Integer> requiredRequestTags,
      Set<Integer> allowedRequestTags,
      Set<Integer> requiredEffectTags,
      Set<Integer> allowedEffectTags,
      List<TagPair> optionalPairs,
      boolean requireAnEffect) {
    void requireTags(AttestationPreimage requestPreimage, AttestationPreimage effectPreimage) {
      Set<Integer> actualRequestTags = tags(requestPreimage);
      Set<Integer> actualEffectTags = tags(effectPreimage);
      List<Boolean> tagRequirements =
          List.of(
              actualRequestTags.containsAll(requiredRequestTags),
              allowedRequestTags.containsAll(actualRequestTags),
              actualEffectTags.containsAll(requiredEffectTags),
              allowedEffectTags.containsAll(actualEffectTags),
              effectRequirementIsSatisfied(actualEffectTags));
      if (tagRequirements.contains(false)) {
        throw AttestationOperationProfile.failure();
      }
      for (TagPair optionalPair : optionalPairs) {
        boolean hasRequest =
            !java.util.Collections.disjoint(actualRequestTags, optionalPair.requestTags());
        boolean hasEffect =
            !java.util.Collections.disjoint(actualEffectTags, optionalPair.effectTags());
        if (hasRequest != hasEffect) {
          throw AttestationOperationProfile.failure();
        }
      }
    }

    private static Set<Integer> tags(AttestationPreimage preimage) {
      return preimage.records().stream()
          .map(AttestationPreimage.Fact::recordTypeTag)
          .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean effectRequirementIsSatisfied(Set<Integer> actualEffectTags) {
      return !requireAnEffect || !actualEffectTags.isEmpty();
    }
  }
}
