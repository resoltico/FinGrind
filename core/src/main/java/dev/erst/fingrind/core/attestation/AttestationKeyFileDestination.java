package dev.erst.fingrind.core.attestation;

import java.nio.file.Path;

/**
 * Holds the canonical private directory and final key path selected before transaction planning.
 */
record AttestationKeyFileDestination(Path parent, Path finalPath) {}
