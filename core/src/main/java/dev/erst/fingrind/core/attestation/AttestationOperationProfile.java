package dev.erst.fingrind.core.attestation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Closed request/effect tag admission before chain position or authority is considered. */
final class AttestationOperationProfile {
  private static final Group B = group(0x0100, 0x0120, 0x0124, 0x0020, 0x0021, 0x0025);
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

  private static final Map<Integer, Integer> REQUIRED_REQUEST_BY_EFFECT =
      Map.ofEntries(
          Map.entry(0x0030, 0x0128),
          Map.entry(0x0050, 0x0130),
          Map.entry(0x0051, 0x0130),
          Map.entry(0x0060, 0x0131),
          Map.entry(0x0061, 0x0131),
          Map.entry(0x0062, 0x0131),
          Map.entry(0x0070, 0x0132),
          Map.entry(0x0071, 0x0132),
          Map.entry(0x0072, 0x0132),
          Map.entry(0x0080, 0x0133),
          Map.entry(0x0081, 0x0133),
          Map.entry(0x0082, 0x0133),
          Map.entry(0x0090, 0x0134),
          Map.entry(0x0092, 0x0134));

  private AttestationOperationProfile() {}

  static AttestationVerifiedOperationProvenance requireValid(
      AttestationOperationPayload payload,
      AttestationOperationKind operationKind,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    Objects.requireNonNull(payload, "payload");
    AttestationOperationKind checkedKind = Objects.requireNonNull(operationKind, "operationKind");
    AttestationPreimage checkedRequest = Objects.requireNonNull(requestPreimage, "requestPreimage");
    AttestationPreimage checkedEffect = Objects.requireNonNull(effectPreimage, "effectPreimage");
    AttestationVerifiedOperationProvenance provenance =
        AttestationVerifiedOperationProvenance.verify(payload, checkedRequest);
    if (provenance.sourceChannel() == AttestationSourceChannel.SYSTEM
        && checkedKind != AttestationOperationKind.INTERIM_RESULT_SWEEP
        && checkedKind != AttestationOperationKind.FISCAL_YEAR_CLOSE) {
      throw failure();
    }
    profile(checkedKind).requireTags(checkedRequest, checkedEffect);
    requireNoOrphanLifecycleEffect(checkedRequest, checkedEffect);
    return provenance;
  }

  private static Profile profile(AttestationOperationKind operationKind) {
    return switch (operationKind) {
      case DECLARE_ACCOUNT, AMEND_ACCOUNT, RETIRE_ACCOUNT ->
          profile(
              tags(0x0100, 0x0110),
              tags(0x0100, 0x0110, 0x0111, 0x0112),
              tags(0x0010),
              tags(0x0010, 0x0011, 0x0012));
      case DECLARE_TAX_REGISTRATION ->
          profile(
              tags(0x0100, 0x0113),
              tags(0x0100, 0x0113, 0x0114),
              tags(0x0013),
              tags(0x0013, 0x0014));
      case POST_ENTRY -> posting(List.of(D), List.of(X));
      case EXECUTE_PLAN ->
          profile(
              tags(0x0100, 0x0120, 0x0124),
              tags(
                  0x0100, 0x0120, 0x0121, 0x0122, 0x0123, 0x0124, 0x0126, 0x0127, 0x0128, 0x0129,
                  0x012A, 0x0130, 0x0131, 0x0132, 0x0133, 0x0134),
              tags(0x0020, 0x0021, 0x0025),
              tags(
                  0x0006, 0x0007, 0x0008, 0x0010, 0x0011, 0x0012, 0x0013, 0x0014, 0x0020, 0x0021,
                  0x0022, 0x0023, 0x0024, 0x0025, 0x0030, 0x0031, 0x0040, 0x0041, 0x0042, 0x0043,
                  0x0044, 0x0050, 0x0051, 0x0060, 0x0061, 0x0062, 0x0070, 0x0071, 0x0072, 0x0080,
                  0x0081, 0x0082, 0x0090, 0x0091, 0x0092, 0x0093));
      case RECORD_SALE_SETTLED,
          RECORD_SALE_ON_CREDIT,
          RECORD_PURCHASE_SETTLED,
          RECORD_PURCHASE_ON_CREDIT,
          RECORD_EXPENSE_SETTLED,
          RECORD_EXPENSE_ON_CREDIT ->
          posting(List.of(B), List.of(T));
      case RECORD_RECEIPT, RECORD_PAYMENT -> posting(List.of(B), List.of(S));
      case RECORD_OWNER_CONTRIBUTION, RECORD_OWNER_WITHDRAWAL -> posting(List.of(B), List.of());
      case RECORD_OPENING_POSITION -> posting(List.of(D), List.of());
      case RECORD_INVENTORY_CAPITALIZATION_SETTLED,
          RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
          RECORD_INVENTORY_WRITE_DOWN,
          RECORD_INVENTORY_SHRINKAGE,
          RECORD_INVENTORY_COUNT_INCREASE ->
          posting(List.of(B, I), List.of());
      case RECORD_PREPAYMENT, RECORD_DEFERRED_REVENUE, RECORD_ACCRUED_EXPENSE ->
          posting(List.of(B, A1), List.of());
      case RECORD_ACCRUAL_CUTOFF_RECOGNITION, RECORD_ACCRUED_EXPENSE_SETTLEMENT ->
          posting(List.of(B, A2), List.of());
      case RECORD_LATVIAN_MONTHLY_PAYROLL -> posting(List.of(B, L1), List.of());
      case RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT, RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE ->
          posting(List.of(B, L2), List.of());
      case RECORD_FIXED_ASSET_CAPITALIZATION -> posting(List.of(B, F1), List.of());
      case RECORD_FIXED_ASSET_DEPRECIATION -> posting(List.of(B, F2), List.of());
      case RECORD_FIXED_ASSET_DISPOSAL -> posting(List.of(B, F3), List.of());
      case RECORD_FINANCING_BORROWING -> posting(List.of(B, N1), List.of());
      case RECORD_FINANCING_PRINCIPAL_REPAYMENT,
          RECORD_FINANCING_INTEREST_ACCRUAL,
          RECORD_FINANCING_INTEREST_PAYMENT ->
          posting(List.of(B, N2), List.of());
      case RECORD_FOREIGN_CURRENCY_OBLIGATION -> posting(List.of(B, O1), List.of());
      case RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT -> posting(List.of(B, O2), List.of());
      case RECORD_REVERSAL -> posting(List.of(B), List.of(T, X));
      case ATTACH_POSTING_APPROVAL ->
          profile(tags(0x0100, 0x0125), tags(0x0100, 0x0125), tags(0x0022), tags(0x0022));
      case INTERIM_RESULT_SWEEP ->
          profile(
              tags(0x0100, 0x0120, 0x0140),
              tags(0x0100, 0x0120, 0x0140, 0x0141),
              tags(0x0020, 0x0025, 0x0040, 0x0041, 0x0042),
              tags(0x0020, 0x0025, 0x0040, 0x0041, 0x0042));
      case FISCAL_YEAR_CLOSE ->
          profile(
              tags(0x0100, 0x0120, 0x0140),
              tags(0x0100, 0x0120, 0x0140, 0x0141),
              tags(0x0020, 0x0025, 0x0043, 0x0044),
              tags(0x0020, 0x0025, 0x0043, 0x0044));
      case BACKUP_CREATED ->
          profile(tags(0x0100, 0x0150), tags(0x0100, 0x0150), tags(0x0006), tags(0x0006));
      case RESTORE_BOOK ->
          profile(tags(0x0100, 0x0160), tags(0x0100, 0x0160), tags(0x00A0), tags(0x00A0));
      case REKEY_BOOK ->
          profile(tags(0x0100, 0x0170), tags(0x0100, 0x0170), tags(0x0007), tags(0x0007));
      case ENROLL_KEY, ROLLOVER_KEY ->
          profile(tags(0x0100, 0x0180), tags(0x0100, 0x0180), tags(0x0002), tags(0x0002));
      case REVOKE_KEY ->
          profile(tags(0x0100, 0x0181), tags(0x0100, 0x0181), tags(0x0004), tags(0x0004));
      case ALTER_POLICY ->
          profile(
              tags(0x0100),
              tags(0x0100, 0x0182, 0x0183, 0x0184),
              tags(),
              tags(0x0003, 0x0005, 0x0008),
              true);
      case BOOK_GENESIS -> throw failure();
    };
  }

  private static Profile posting(List<Group> requiredGroups, List<Group> optionalGroups) {
    Set<Integer> requiredRequestTags = unionRequest(requiredGroups);
    Set<Integer> requiredEffectTags = unionEffect(requiredGroups);
    Set<Integer> allowedRequestTags = unionRequest(requiredGroups, optionalGroups);
    Set<Integer> allowedEffectTags = unionEffect(requiredGroups, optionalGroups);
    List<TagPair> optionalPairs = new ArrayList<>();
    for (Group group : optionalGroups) {
      if (!group.requestTags().isEmpty() && !group.effectTags().isEmpty()) {
        optionalPairs.add(new TagPair(group.requestTags(), group.effectTags()));
      }
    }
    return new Profile(
        requiredRequestTags,
        allowedRequestTags,
        requiredEffectTags,
        allowedEffectTags,
        optionalPairs,
        false);
  }

  private static Profile profile(
      Set<Integer> requiredRequestTags,
      Set<Integer> allowedRequestTags,
      Set<Integer> requiredEffectTags,
      Set<Integer> allowedEffectTags) {
    return profile(
        requiredRequestTags, allowedRequestTags, requiredEffectTags, allowedEffectTags, false);
  }

  private static Profile profile(
      Set<Integer> requiredRequestTags,
      Set<Integer> allowedRequestTags,
      Set<Integer> requiredEffectTags,
      Set<Integer> allowedEffectTags,
      boolean requireAnEffect) {
    return new Profile(
        requiredRequestTags,
        allowedRequestTags,
        requiredEffectTags,
        allowedEffectTags,
        List.of(),
        requireAnEffect);
  }

  private static Set<Integer> tags(int... tags) {
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

  @SafeVarargs
  private static Set<Integer> unionRequest(List<Group>... groups) {
    return union(groups, Group::requestTags);
  }

  @SafeVarargs
  private static Set<Integer> unionEffect(List<Group>... groups) {
    return union(groups, Group::effectTags);
  }

  private static Set<Integer> union(
      List<Group>[] groups, java.util.function.Function<Group, Set<Integer>> selector) {
    return java.util.Arrays.stream(groups)
        .flatMap(List::stream)
        .flatMap(group -> selector.apply(group).stream())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static void requireNoOrphanLifecycleEffect(
      AttestationPreimage requestPreimage, AttestationPreimage effectPreimage) {
    for (Map.Entry<Integer, Integer> relation : REQUIRED_REQUEST_BY_EFFECT.entrySet()) {
      if (!AttestationPreimageFields.records(effectPreimage, relation.getKey()).isEmpty()
          && AttestationPreimageFields.records(requestPreimage, relation.getValue()).isEmpty()) {
        throw failure();
      }
    }
  }

  private static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  private record Group(Set<Integer> requestTags, Set<Integer> effectTags) {}

  private record TagPair(Set<Integer> requestTags, Set<Integer> effectTags) {}

  private record Profile(
      Set<Integer> requiredRequestTags,
      Set<Integer> allowedRequestTags,
      Set<Integer> requiredEffectTags,
      Set<Integer> allowedEffectTags,
      List<TagPair> optionalPairs,
      boolean requireAnEffect) {
    void requireTags(AttestationPreimage requestPreimage, AttestationPreimage effectPreimage) {
      Set<Integer> actualRequestTags = tags(requestPreimage);
      Set<Integer> actualEffectTags = tags(effectPreimage);
      if (!actualRequestTags.containsAll(requiredRequestTags)
          || !allowedRequestTags.containsAll(actualRequestTags)
          || !actualEffectTags.containsAll(requiredEffectTags)
          || !allowedEffectTags.containsAll(actualEffectTags)
          || (requireAnEffect && actualEffectTags.isEmpty())) {
        throw failure();
      }
      for (TagPair optionalPair : optionalPairs) {
        boolean hasRequest =
            !java.util.Collections.disjoint(actualRequestTags, optionalPair.requestTags());
        boolean hasEffect =
            !java.util.Collections.disjoint(actualEffectTags, optionalPair.effectTags());
        if (hasRequest != hasEffect) {
          throw failure();
        }
      }
    }

    private static Set<Integer> tags(AttestationPreimage preimage) {
      return preimage.records().stream()
          .map(AttestationPreimage.Fact::recordTypeTag)
          .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
  }
}
