package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for rejected result-holding selection accessors. */
class RejectedInterimResultTargetSelectionTest {
  @Test
  void candidateAccountCodes_exposesMissingAndAmbiguousCandidates() {
    RejectedInterimResultTargetSelection missingSelection =
        new RejectedInterimResultTargetSelection(
            new CloseTargetAccountCandidateMissing(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200"))));
    RejectedInterimResultTargetSelection ambiguousSelection =
        new RejectedInterimResultTargetSelection(
            new CloseTargetAccountCandidateAmbiguous(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200"), new AccountCode("3210"))));

    assertEquals(List.of(new AccountCode("3200")), missingSelection.candidateAccountCodes());
    assertEquals(
        List.of(new AccountCode("3200"), new AccountCode("3210")),
        ambiguousSelection.candidateAccountCodes());
  }
}
