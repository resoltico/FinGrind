package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.contract.protocol.AccountingPolicyPackFacts;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/** Named seam for bookkeeping policy that may vary by standards or jurisdictional layer. */
@NullMarked
public interface BookkeepingPolicyPack {
  /** Returns the structured public facts for this policy pack. */
  AccountingPolicyPackFacts facts();

  /** Returns the policy that derives comparative reporting windows from one book identity. */
  StatementComparativePolicy statementComparativePolicy();

  /** Returns the policy that governs explicit accounting-basis support. */
  AccountingBasisPolicy accountingBasisPolicy();

  /** Returns the policy that governs chart hierarchy and taxonomy support. */
  ChartPolicy chartPolicy();

  /** Returns the policy that governs period-close account-type behavior. */
  ClosePolicy closePolicy();

  /** Returns the policy that governs statement presentation classification. */
  StatementPresentationPolicy statementPresentationPolicy();

  /** Returns the policy that governs first-class tax modeling. */
  TaxPolicy taxPolicy();

  /** Returns the policy that governs foreign-exchange support. */
  ForeignExchangePolicy foreignExchangePolicy();

  /** Returns the policy that governs source-evidence requirements. */
  EvidencePolicy evidencePolicy();

  /** Validates one policy-pack reference before use. */
  static BookkeepingPolicyPack requirePolicyPack(BookkeepingPolicyPack policyPack) {
    return Objects.requireNonNull(policyPack, "policyPack");
  }
}
