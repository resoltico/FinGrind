package dev.erst.fingrind.core.attestation;

import java.nio.file.Path;

/** Holds the canonical parent and final file name selected for one key-publication attempt. */
record AttestationKeyFileDestination(Path parent, Path finalPath) {}
