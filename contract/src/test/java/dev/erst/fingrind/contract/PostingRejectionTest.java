package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostingRejection}. */
class PostingRejectionTest {
  @Test
  void wireCode_isStableForEverySubtype() {
    assertEquals(
        List.of(
            "posting-book-not-initialized",
            "account-state-violations",
            "duplicate-idempotency-key",
            "posting-kind-reserved",
            "book-functional-currency-mismatch",
            "closed-period-violation",
            "opening-balance-window-closed",
            "opening-balance-touches-nominal-account",
            "closing-equity-account-reserved",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
            PostingRejection.wireCode(
                new PostingRejection.AccountStateViolations(
                    List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))))),
            PostingRejection.wireCode(new PostingRejection.DuplicateIdempotencyKey()),
            PostingRejection.wireCode(
                new PostingRejection.PostingKindReserved(PostingKind.PERIOD_CLOSE)),
            PostingRejection.wireCode(
                new PostingRejection.BookFunctionalCurrencyMismatch(
                    CurrencyUnit.of("EUR"), CurrencyUnit.of("USD"))),
            PostingRejection.wireCode(
                new PostingRejection.ClosedPeriodViolation(
                    java.time.LocalDate.parse("2026-04-30"),
                    java.time.LocalDate.parse("2026-05-01"))),
            PostingRejection.wireCode(
                new PostingRejection.OpeningBalanceWindowClosed(
                    PostingKind.STANDARD, java.time.LocalDate.parse("2026-05-02"))),
            PostingRejection.wireCode(
                new PostingRejection.OpeningBalanceTouchesNominalAccount(
                    new AccountCode("4000"), AccountType.REVENUE)),
            PostingRejection.wireCode(
                new PostingRejection.ClosingEquityAccountReserved(new AccountCode("3000"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalAlreadyExists(new PostingId("posting-2"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-3")))));
  }

  @Test
  void accountStateViolationWireCode_isStableForEverySubtype() {
    assertEquals(
        List.of("unknown-account", "inactive-account", "non-postable-account"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.UnknownAccount(new AccountCode("1000"))),
            PostingRejection.wireCode(
                new PostingRejection.InactiveAccount(new AccountCode("2000"))),
            PostingRejection.wireCode(
                new PostingRejection.NonPostableAccount(
                    new AccountCode("3000"), dev.erst.fingrind.core.AccountNodeKind.HEADER))));
  }

  @Test
  void descriptors_areStableAndComplete() {
    assertEquals(
        List.of(
            "posting-book-not-initialized",
            "account-state-violations",
            "duplicate-idempotency-key",
            "posting-kind-reserved",
            "book-functional-currency-mismatch",
            "closed-period-violation",
            "opening-balance-window-closed",
            "opening-balance-touches-nominal-account",
            "closing-equity-account-reserved",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        PostingRejection.descriptors().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
  }

  @Test
  void bookNotInitializedCode_matchesTheCanonicalDescriptor() {
    assertEquals(
        PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
        PostingRejection.bookNotInitializedCode());
  }
}
