package dev.erst.fingrind.core;

import java.io.IOException;

/** Injects an interruption immediately after one durable publication boundary in focused tests. */
@FunctionalInterface
interface PublicationTransactionFaultInjector {
  /** Does nothing after every production publication boundary. */
  PublicationTransactionFaultInjector NONE = point -> {};

  /** Injects after the named durable transition, unlink, or directory force boundary. */
  void after(PublicationTransactionFaultPoint point) throws IOException;
}
