package dev.erst.fingrind.buildlogic

import tools.jackson.databind.JsonNode

internal fun JsonNode.requireText(): String =
    requireNotNull(asString()) { "Expected text node: $this" }

internal fun JsonNode.textValues(): List<String> = toList().map { it.requireText() }
