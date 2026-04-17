package dev.erst.fingrind.contract.protocol;

/** Core-owned structured facts describing preflight semantics. */
public record PreflightFacts(String semantics, boolean commitGuarantee, String description) {}
