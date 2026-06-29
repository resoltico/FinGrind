package dev.erst.fingrind.cli;

/** Composite workflow role used by test doubles that exercise every CLI book seam together. */
interface CliBookWorkflow
    extends CliBookLifecycleWorkflow, CliBookMutationWorkflow, CliBookReadWorkflow {}
