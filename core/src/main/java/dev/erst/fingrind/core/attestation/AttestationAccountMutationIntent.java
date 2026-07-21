package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Semantic account-registry mutation intent independently of a transport operation identifier. */
public enum AttestationAccountMutationIntent {
  DECLARATION {
    @Override
    boolean accepts(AttestationEffectMutation effectMutation) {
      return effectMutation == AttestationEffectMutation.CREATE
          || effectMutation == AttestationEffectMutation.AMEND
          || effectMutation == AttestationEffectMutation.REACTIVATE;
    }
  },
  AMENDMENT {
    @Override
    boolean accepts(AttestationEffectMutation effectMutation) {
      return effectMutation == AttestationEffectMutation.AMEND;
    }
  },
  RETIREMENT {
    @Override
    boolean accepts(AttestationEffectMutation effectMutation) {
      return effectMutation == AttestationEffectMutation.RETIRE;
    }
  };

  /** Requires this intent to match the semantic effect persisted in the same transaction. */
  public void requireCompatible(AttestationEffectMutation effectMutation) {
    AttestationEffectMutation checked = Objects.requireNonNull(effectMutation, "effectMutation");
    if (!accepts(checked)) {
      throw new IllegalArgumentException(
          "Account attestation mutation intent and effect mutation are incompatible.");
    }
  }

  abstract boolean accepts(AttestationEffectMutation effectMutation);
}
