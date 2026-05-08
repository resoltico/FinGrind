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
        val value = if (valueNode.isString) valueNode.stringValue()?.trim().orEmpty() else ""
        if (value.isEmpty()) {
            throw IllegalStateException("Missing required contract property $key in $relativePath.")
        }
        return value
    }

    fun listProperty(projectRootDirectory: Path, relativePath: String, key: String): List<String> {
        val valuesNode = loadJson(projectRootDirectory, relativePath).path(key)
        if (valuesNode.isMissingNode || valuesNode.isNull) {
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
        return values.toList()
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
}
