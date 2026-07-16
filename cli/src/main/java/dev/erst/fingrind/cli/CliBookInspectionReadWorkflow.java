package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;

/** Inspection capability over one protected book. */
@FunctionalInterface
interface CliBookInspectionReadWorkflow {
  /** Inspects one protected book without mutating its durable contents. */
  ContractDecision<BookInspection> inspectBook(BookAccess bookAccess);
}
