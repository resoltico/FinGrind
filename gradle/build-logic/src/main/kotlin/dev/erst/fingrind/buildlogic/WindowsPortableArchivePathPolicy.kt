package dev.erst.fingrind.buildlogic

import java.util.Locale

/** The entry kind relevant to portable archive admission. */
internal enum class PortableArchiveMemberKind {
    REGULAR_FILE,
    DIRECTORY,
    SYMBOLIC_LINK,
    SPECIAL,
}

/** One staged archive member, expressed relative to the archive root. */
internal data class PortableArchiveMember(
    val relativePath: String,
    val kind: PortableArchiveMemberKind,
)

/**
 * Owns Windows filesystem admission for components in portable bundle archive paths.
 *
 * A target layout may be materialized on a non-Windows host, so archive-path admission must not
 * depend on the host filesystem accepting a target name.
 */
internal object WindowsPortableArchivePathPolicy {
    private const val WINDOWS_FORBIDDEN_CHARACTERS = "<>:\"/\\|?*"
    private const val WINDOWS_MAX_COMPONENT_UTF16_CODE_UNITS = 255

    private val windowsReservedDeviceBaseNames =
        buildSet {
            addAll(setOf("CON", "PRN", "AUX", "NUL"))
            listOf("COM", "LPT").forEach { prefix ->
                (1..9).forEach { number -> add("$prefix$number") }
                listOf("¹", "²", "³").forEach { superscript -> add("$prefix$superscript") }
            }
        }

    fun requireRelativeArchivePath(value: String, label: String): String {
        require(value.isNotBlank()) { "$label must not be blank." }
        require(!value.startsWith('/') && !value.startsWith('\\')) {
            "$label must be relative: $value"
        }
        require(!value.endsWith('/') && !value.contains("//")) {
            "$label must not contain empty path components: $value"
        }
        value.split('/').forEach { component ->
            requireComponent(component, "$label component")
        }
        return value
    }

    fun requireFileName(value: String, label: String): String {
        require(value.isNotBlank()) { "$label must not be blank." }
        return requireComponent(value, label)
    }

    fun requireComponent(value: String, label: String): String {
        require(value.isNotEmpty()) { "$label must not be empty." }
        require(value.length <= WINDOWS_MAX_COMPONENT_UTF16_CODE_UNITS) {
            "$label must not exceed $WINDOWS_MAX_COMPONENT_UTF16_CODE_UNITS UTF-16 code units."
        }
        require(value != "." && value != "..") {
            "$label must not be a current- or parent-directory component: $value"
        }
        require(
            value.codePoints().noneMatch { codePoint -> isWindowsForbiddenCodePoint(codePoint) },
        ) {
            "$label must not contain a path separator, control character, or Windows-reserved " +
                "character."
        }
        require(!value.endsWith('.') && !value.endsWith(' ')) {
            "$label must not end with a space or period: $value"
        }
        require(
            value.substringBefore('.').uppercase(Locale.ROOT) !in windowsReservedDeviceBaseNames,
        ) {
            "$label must not use a Windows-reserved device name: $value"
        }
        return value
    }

    fun requireNoCaseInsensitiveArchivePathCollisions(
        relativeArchivePaths: Iterable<String>,
        label: String,
    ) =
        requireNoCaseInsensitiveArchiveMemberCollisions(
            relativeArchivePaths.map { archivePath ->
                PortableArchiveMember(
                    relativePath = archivePath,
                    kind = PortableArchiveMemberKind.REGULAR_FILE,
                )
            },
            label,
        )

    /**
     * Validates every member that the archive task will actually receive, including dynamic
     * runtime-image output rather than only the fixed staging plan.
     */
    fun requirePortableArchiveMembers(
        archiveRootName: String,
        archiveFormat: String,
        archiveMembers: Iterable<PortableArchiveMember>,
        label: String,
    ) {
        requireComponent(archiveRootName, "$label archive root directory name")
        val members = archiveMembers.toList()
        members.forEach { member ->
            requireRelativeArchivePath(member.relativePath, "$label archive member")
        }
        val specialMember =
            members.firstOrNull { member -> member.kind == PortableArchiveMemberKind.SPECIAL }
        require(specialMember == null) {
            "$label must not contain a special or reparse archive member: " +
                "${specialMember?.relativePath}."
        }
        when (archiveFormat) {
            "zip" -> {
                val symbolicLink =
                    members.firstOrNull { member ->
                        member.kind == PortableArchiveMemberKind.SYMBOLIC_LINK
                }
                require(symbolicLink == null) {
                    "$label ZIP archive must not contain a symbolic-link or reparse archive " +
                        "member: " +
                        "${symbolicLink?.relativePath}."
                }
            }
            "tar.gz" -> Unit
            else ->
                throw IllegalArgumentException(
                    "Unsupported FinGrind bundle archive format: $archiveFormat.",
                )
        }
        requireNoCaseInsensitiveArchiveMemberCollisions(members, label)
    }

    private fun requireNoCaseInsensitiveArchiveMemberCollisions(
        archiveMembers: Iterable<PortableArchiveMember>,
        label: String,
    ) {
        val observedMembers = mutableListOf<PortableArchiveMember>()
        archiveMembers.forEach { archiveMember ->
            requireRelativeArchivePath(archiveMember.relativePath, "$label archive path")
            val collision =
                observedMembers.firstOrNull { observedMember ->
                    archiveMembersCollide(observedMember, archiveMember)
            }
            require(collision == null) {
                "$label contains Windows-case-insensitive archive-path collision: " +
                    "${collision?.relativePath} and ${archiveMember.relativePath}."
            }
            observedMembers += archiveMember
        }
    }

    private fun isWindowsForbiddenCodePoint(codePoint: Int): Boolean =
        Character.isISOControl(codePoint) ||
            codePoint in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code ||
            WINDOWS_FORBIDDEN_CHARACTERS.any { character -> character.code == codePoint }

    private fun archiveMembersCollide(
        left: PortableArchiveMember,
        right: PortableArchiveMember,
    ): Boolean {
        val leftComponents = left.relativePath.split('/')
        val rightComponents = right.relativePath.split('/')
        val sharedComponentCount = minOf(leftComponents.size, rightComponents.size)
        val matchingComponentCount =
            leftComponents
                .zip(rightComponents)
                .takeWhile { (leftComponent, rightComponent) ->
                    leftComponent.equals(rightComponent, ignoreCase = true)
                }
                .size
        if (matchingComponentCount == 0) {
            return false
        }
        // Windows would merge a differently cased shared directory before extracting either child.
        if ((0 until matchingComponentCount).any { index ->
                leftComponents[index] != rightComponents[index]
            }) {
            return true
        }
        if (matchingComponentCount < sharedComponentCount) {
            return false
        }
        if (leftComponents.size == rightComponents.size) {
            return true
        }
        return when {
            leftComponents.size < rightComponents.size ->
                left.kind != PortableArchiveMemberKind.DIRECTORY
            else -> right.kind != PortableArchiveMemberKind.DIRECTORY
        }
    }
}
