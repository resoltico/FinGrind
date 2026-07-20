package dev.erst.fingrind.buildlogic

import java.io.File

private val cryptographicPrimitiveTypePattern =
    Regex(
        """\bjava\.security\.(?:Signature|KeyPairGenerator|KeyFactory|MessageDigest|SecureRandom|(?:interfaces\.)?[\w.]*Private[\w.]*Key\w*)\b""",
    )
private val cryptographicPrimitiveSeamSourceSuffixes =
    setOf(
        "/core/src/main/java/dev/erst/fingrind/core/CryptographicPrimitives.java",
        "/core/src/main/java/dev/erst/fingrind/core/attestation/AttestationEd25519.java",
        "/core/src/main/java/dev/erst/fingrind/core/attestation/AttestationFilePkcs8Custodian.java",
    )

internal fun File.usesCryptographicPrimitiveOutsideSeam(line: String): Boolean =
    cryptographicPrimitiveSeamSourceSuffixes.none { invariantSeparatorsPath().endsWith(it) } &&
        cryptographicPrimitiveTypePattern.containsMatchIn(line)
