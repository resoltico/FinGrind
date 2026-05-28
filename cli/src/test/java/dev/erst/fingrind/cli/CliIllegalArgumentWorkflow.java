package dev.erst.fingrind.cli;

/** Workflow stub that reports invalid-request style failures for every workflow operation. */
class CliIllegalArgumentWorkflow extends CliThrowingWorkflow {
  @Override
  protected RuntimeException failure(String operationName) {
    return new IllegalArgumentException("workflow boom");
  }
}
