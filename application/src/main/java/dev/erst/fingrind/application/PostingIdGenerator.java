package dev.erst.fingrind.application;

import dev.erst.fingrind.core.PostingId;

/** Generates stable identifiers for newly committed posting facts. */
@FunctionalInterface
public interface PostingIdGenerator {
  /** Returns the next posting identifier to assign during commit. */
  PostingId nextPostingId();
}
