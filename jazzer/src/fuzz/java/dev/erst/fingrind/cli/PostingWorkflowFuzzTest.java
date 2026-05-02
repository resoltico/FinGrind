package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/** Fuzzes posting workflow invariants above the book-session seam using an in-memory book. */
public class PostingWorkflowFuzzTest {
  @FuzzTest
  void exercisePostingWorkflow(FuzzedDataProvider data) {
    PostingWorkflowFuzzAssertions.exercisePostingWorkflow(data.consumeRemainingAsBytes());
  }
}
