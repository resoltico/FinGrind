package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.core.AccountingPolicyProfile;
import dev.erst.fingrind.core.BookIdentity;
import java.util.Objects;

/** Resolves built-in policy-pack implementations from persisted book policy profiles. */
public final class BuiltInBookkeepingPolicyPacks {
  private BuiltInBookkeepingPolicyPacks() {}

  /** Returns the executable built-in policy pack selected by one initialized book identity. */
  public static BookkeepingPolicyPack forBookIdentity(BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    return forProfile(bookIdentity.policyProfile());
  }

  /** Returns the executable built-in policy pack for one persisted profile. */
  public static BookkeepingPolicyPack forProfile(AccountingPolicyProfile policyProfile) {
    return switch (Objects.requireNonNull(policyProfile, "policyProfile")) {
      case INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1 -> CoreBookkeepingPolicyPack.current();
    };
  }
}
