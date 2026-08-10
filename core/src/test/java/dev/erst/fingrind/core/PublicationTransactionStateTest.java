package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Verifies the recovery and terminal classification of every durable transaction state. */
class PublicationTransactionStateTest {
  @Test
  void classifiesTerminalRecoveryStates() {
    for (PublicationTransactionState state : PublicationTransactionState.values()) {
      boolean recovery =
          switch (state) {
            case BLOCKED, COMMIT_UNCERTAIN, CLEANUP_INCOMPLETE, CLEANUP_UNCERTAIN -> true;
            case PREPARED, STAGED, COMMITTING, COMMITTED, CLEANING, COMPLETE -> false;
          };
      assertEquals(recovery, state.requiresRecovery());
      assertEquals(recovery || state == PublicationTransactionState.COMPLETE, state.terminal());
    }
  }
}
