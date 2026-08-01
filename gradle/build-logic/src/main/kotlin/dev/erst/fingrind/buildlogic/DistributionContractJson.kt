package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

internal object DistributionContractJson {
    private val objectMapper = JsonMapper.builder().build()

    fun loadJson(projectRootDirectory: Path, relativePath: String): JsonNode {
        Files.newInputStream(DistributionContractPaths.contractPath(projectRootDirectory, relativePath)).use { stream ->
            val document = objectMapper.readTree(stream)
            if (document == null || !document.isObject) {
                throw IllegalStateException("Contract resource $relativePath must contain one top-level JSON object.")
            }
            return document
        }
    }

    fun requiredProperty(projectRootDirectory: Path, relativePath: String, key: String): String {
        val valueNode = loadJson(projectRootDirectory, relativePath).path(key)
        val value = if (valueNode.isString) valueNode.stringValue()?.trim().orEmpty() else ""
        if (value.isEmpty()) {
            throw IllegalStateException("Missing required contract property $key in $relativePath.")
        }
        return value
    }

    fun requiredText(document: JsonNode, key: String, relativePath: String): String {
        val valueNode = document.path(key)
        val value = if (valueNode.isString) valueNode.stringValue().orEmpty() else ""
        if (value.isBlank()) {
            throw IllegalStateException("Missing required contract property $key in $relativePath.")
        }
        return value
    }

    fun listProperty(
        projectRootDirectory: Path,
        relativePath: String,
        key: String,
        requireExplicitKey: Boolean = false,
        requireNonEmpty: Boolean = false,
    ): List<String> {
        val document = loadJson(projectRootDirectory, relativePath)
        val valuesNode = document.path(key)
        if (valuesNode.isMissingNode || valuesNode.isNull) {
            if (requireExplicitKey) {
                throw IllegalStateException("Missing required contract property $key in $relativePath.")
            }
            return emptyList()
        }
        if (!valuesNode.isArray) {
            throw IllegalStateException("Expected JSON array contract property $key in $relativePath.")
        }
        val values = linkedSetOf<String>()
        valuesNode.forEach { node ->
            val value = if (node.isString) node.stringValue()?.trim().orEmpty() else ""
            if (value.isNotEmpty()) {
                if (!values.add(value)) {
                    throw IllegalStateException(
                        "Duplicate contract list element $value in $key from $relativePath.",
                    )
                }
            } else {
                throw IllegalStateException("Expected JSON string elements in contract property $key in $relativePath.")
            }
        }
        if (requireNonEmpty && values.isEmpty()) {
            throw IllegalStateException("Contract property $key must not be empty in $relativePath.")
        }
        return values.toList()
    }

    fun stringMapProperty(projectRootDirectory: Path, relativePath: String, key: String): Map<String, String> {
        val valuesNode = loadJson(projectRootDirectory, relativePath).path(key)
        if (!valuesNode.isObject) {
            throw IllegalStateException("Contract property $key must be one JSON object in $relativePath.")
        }
        val values = linkedMapOf<String, String>()
        valuesNode.properties().forEach { entry ->
            val mapKey = entry.key.trim()
            val value = if (entry.value.isString) entry.value.stringValue()?.trim().orEmpty() else ""
            if (mapKey.isEmpty() || value.isEmpty()) {
                throw IllegalStateException(
                    "Contract property $key must contain only non-blank string keys and values in $relativePath.",
                )
            }
            if (values.put(mapKey, value) != null) {
                throw IllegalStateException("Duplicate contract map element $mapKey in $key from $relativePath.")
            }
        }
        if (values.isEmpty()) {
            throw IllegalStateException("Contract property $key must not be empty in $relativePath.")
        }
        return values.toMap()
    }

    fun booleanProperty(projectRootDirectory: Path, relativePath: String, key: String): Boolean {
        val valueNode = loadJson(projectRootDirectory, relativePath).path(key)
        if (!valueNode.isBoolean) {
            throw IllegalStateException("Expected JSON boolean contract property $key in $relativePath.")
        }
        return valueNode.booleanValue()
    }

    fun objectProperty(document: JsonNode, key: String, relativePath: String): JsonNode =
        requireObjectNode(document.path(key), key, relativePath)

    fun requireObjectNode(node: JsonNode, key: String, relativePath: String): JsonNode {
        if (!node.isObject) {
            throw IllegalStateException(
                "Contract property $key must be one JSON object in $relativePath.",
            )
        }
        return node
    }

    fun requireOnlyProperties(
        document: JsonNode,
        allowedProperties: Set<String>,
        objectLabel: String,
        relativePath: String,
    ) {
        val unexpectedProperties =
            document.properties().asSequence().map { it.key }
                .filterNot(allowedProperties::contains)
                .sorted()
                .toList()
        if (unexpectedProperties.isNotEmpty()) {
            throw IllegalStateException(
                "Contract $objectLabel must not declare unrecognized properties in $relativePath: ${unexpectedProperties.joinToString(", ")}",
            )
        }
    }
}
