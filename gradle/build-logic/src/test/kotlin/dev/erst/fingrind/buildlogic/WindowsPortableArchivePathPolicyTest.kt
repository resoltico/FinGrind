package dev.erst.fingrind.buildlogic

import kotlin.test.Test
import kotlin.test.assertFailsWith

class WindowsPortableArchivePathPolicyTest {
    @Test
    fun portableArchiveComponentsRejectWindowsOnlyNamesAndCharactersOnEveryHost() {
        val invalidComponents =
            listOf(
                "",
                ".",
                "..",
                "CON",
                "nUl.tar.gz",
                "COM9.dll",
                "lpt³.backup",
                "trailing.",
                "trailing ",
                "invalid<name",
                "invalid>name",
                "invalid:name",
                "invalid\"name",
                "invalid/name",
                "invalid\\name",
                "invalid|name",
                "invalid?name",
                "invalid*name",
                "control\u0000name",
                "control\u0085name",
                "surrogate" + Character.MIN_SURROGATE + "name",
                "a".repeat(256),
            )

        invalidComponents.forEach { component ->
            assertFailsWith<IllegalArgumentException> {
                WindowsPortableArchivePathPolicy.requireComponent(component, "fixture component")
            }
        }
    }

    @Test
    fun portableArchivePathAdmissionRejectsNonRelativeAndNonLeafSyntax() {
        listOf(
            "",
            "/bin/fingrind.ps1",
            "\\bin\\fingrind.ps1",
            "bin/",
            "bin//fingrind.ps1",
            "bin/.",
            "bin/..",
        ).forEach { invalidPath ->
            assertFailsWith<IllegalArgumentException> {
                WindowsPortableArchivePathPolicy.requireRelativeArchivePath(
                    invalidPath,
                    "fixture archive path",
                )
            }
        }
        listOf("", ".", "..", "native/sqlite3.dll").forEach { invalidFileName ->
            assertFailsWith<IllegalArgumentException> {
                WindowsPortableArchivePathPolicy.requireFileName(
                    invalidFileName,
                    "fixture file name",
                )
            }
        }
    }

    @Test
    fun archiveMemberAdmissionHandlesDynamicTreesLinksAndDirectoryAwareCollisions() {
        val ordinaryTree =
            listOf(
                PortableArchiveMember("runtime", PortableArchiveMemberKind.DIRECTORY),
                PortableArchiveMember("runtime/bin", PortableArchiveMemberKind.DIRECTORY),
                PortableArchiveMember(
                    "runtime/bin/java.exe",
                    PortableArchiveMemberKind.REGULAR_FILE,
                ),
                PortableArchiveMember("lib", PortableArchiveMemberKind.DIRECTORY),
                PortableArchiveMember("lib/app", PortableArchiveMemberKind.DIRECTORY),
                PortableArchiveMember(
                    "lib/app/fingrind.jar",
                    PortableArchiveMemberKind.REGULAR_FILE,
                ),
            )

        WindowsPortableArchivePathPolicy.requirePortableArchiveMembers(
            archiveRootName = "fingrind-1.2.3-windows-x86_64",
            archiveFormat = "zip",
            archiveMembers = ordinaryTree,
            label = "fixture staged bundle",
        )
        WindowsPortableArchivePathPolicy.requirePortableArchiveMembers(
            archiveRootName = "fingrind-1.2.3-linux-x86_64",
            archiveFormat = "tar.gz",
            archiveMembers =
                ordinaryTree +
                    PortableArchiveMember(
                        "runtime/current",
                        PortableArchiveMemberKind.SYMBOLIC_LINK,
                    ),
            label = "fixture staged bundle",
        )

        assertFailsWith<IllegalArgumentException> {
            WindowsPortableArchivePathPolicy.requirePortableArchiveMembers(
                archiveRootName = "fingrind-1.2.3-windows-x86_64",
                archiveFormat = "zip",
                archiveMembers =
                    ordinaryTree +
                        PortableArchiveMember(
                            "runtime/current",
                            PortableArchiveMemberKind.SYMBOLIC_LINK,
                        ),
                label = "fixture staged bundle",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowsPortableArchivePathPolicy.requirePortableArchiveMembers(
                archiveRootName = "fingrind-1.2.3-windows-x86_64",
                archiveFormat = "zip",
                archiveMembers =
                    ordinaryTree +
                        PortableArchiveMember(
                            "runtime/CON",
                            PortableArchiveMemberKind.REGULAR_FILE,
                        ),
                label = "fixture staged bundle",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowsPortableArchivePathPolicy.requirePortableArchiveMembers(
                archiveRootName = "fingrind-1.2.3-linux-x86_64",
                archiveFormat = "tar.gz",
                archiveMembers =
                    ordinaryTree +
                        PortableArchiveMember(
                            "runtime/junction",
                            PortableArchiveMemberKind.SPECIAL,
                        ),
                label = "fixture staged bundle",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowsPortableArchivePathPolicy.requirePortableArchiveMembers(
                archiveRootName = "fingrind-1.2.3-windows-x86_64",
                archiveFormat = "zip",
                archiveMembers =
                    listOf(
                        PortableArchiveMember("lib", PortableArchiveMemberKind.DIRECTORY),
                        PortableArchiveMember("LIB", PortableArchiveMemberKind.DIRECTORY),
                    ),
                label = "fixture staged bundle",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowsPortableArchivePathPolicy.requirePortableArchiveMembers(
                archiveRootName = "fingrind-1.2.3-windows-x86_64",
                archiveFormat = "zip",
                archiveMembers =
                    listOf(
                        PortableArchiveMember("lib", PortableArchiveMemberKind.REGULAR_FILE),
                        PortableArchiveMember(
                            "LIB/native/sqlite3.dll",
                            PortableArchiveMemberKind.REGULAR_FILE,
                        ),
                    ),
                label = "fixture staged bundle",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowsPortableArchivePathPolicy.requirePortableArchiveMembers(
                archiveRootName = "fingrind-1.2.3-windows-x86_64",
                archiveFormat = "zip",
                archiveMembers =
                    listOf(
                        PortableArchiveMember("lib", PortableArchiveMemberKind.DIRECTORY),
                        PortableArchiveMember(
                            "LIB/native/sqlite3.dll",
                            PortableArchiveMemberKind.REGULAR_FILE,
                        ),
                    ),
                label = "fixture staged bundle",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowsPortableArchivePathPolicy.requirePortableArchiveMembers(
                archiveRootName = "fingrind-1.2.3-windows-x86_64",
                archiveFormat = "zip",
                archiveMembers =
                    listOf(
                        PortableArchiveMember(
                            "lib/app/fingrind.jar",
                            PortableArchiveMemberKind.REGULAR_FILE,
                        ),
                        PortableArchiveMember(
                            "LIB/native/sqlite3.dll",
                            PortableArchiveMemberKind.REGULAR_FILE,
                        ),
                    ),
                label = "fixture staged bundle",
            )
        }
    }
}
