package dev.erst.fingrind.testsupport;

import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;

/** Canonical reachability contract shared by in-memory and SQLite-backed write-path tests. */
public interface PostingRouteReachabilityContract {
  /** Verifies opening-position reachability for every published matrix cell. */
  default void verifyOpeningPositionReachabilityMatrix() {
    for (RequestSurfaceFacts.ReachabilityCellFacts cell :
        PostingRouteReachabilityTestSupport.reachabilityMatrix()) {
      assertOpeningPositionReachability(cell);
    }
  }

  /** Verifies direct-journal reachability for every published matrix cell. */
  default void verifyDirectJournalReachabilityMatrix() {
    for (RequestSurfaceFacts.ReachabilityCellFacts cell :
        PostingRouteReachabilityTestSupport.reachabilityMatrix()) {
      assertDirectJournalReachability(cell);
    }
  }

  /** Verifies reversal reachability for every published matrix cell. */
  default void verifyReversalReachabilityMatrix() {
    for (RequestSurfaceFacts.ReachabilityCellFacts cell :
        PostingRouteReachabilityTestSupport.reachabilityMatrix()) {
      assertReversalReachability(cell);
    }
  }

  /** Verifies the opening-position route for one published reachability cell. */
  void assertOpeningPositionReachability(RequestSurfaceFacts.ReachabilityCellFacts cell);

  /** Verifies the direct-journal route for one published reachability cell. */
  void assertDirectJournalReachability(RequestSurfaceFacts.ReachabilityCellFacts cell);

  /** Verifies the reversal route for one published reachability cell. */
  void assertReversalReachability(RequestSurfaceFacts.ReachabilityCellFacts cell);
}
