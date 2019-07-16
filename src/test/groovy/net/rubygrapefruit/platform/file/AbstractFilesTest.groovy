/*
 * Copyright 2012 Adam Murdoch
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.rubygrapefruit.platform.file

import net.rubygrapefruit.platform.internal.Platform
import spock.lang.Specification

import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes

import static java.nio.file.attribute.PosixFilePermission.*

class AbstractFilesTest extends Specification {
    BasicFileAttributes attributes(File file) {
        return java.nio.file.Files.getFileAttributeView(file.toPath(), BasicFileAttributeView, LinkOption.NOFOLLOW_LINKS).readAttributes()
    }

    private long maybeRoundToNearestSecond(long time) {
        if (Platform.current().isLinux() || Platform.current().isMacOs() || Platform.current().isFreeBSD()) {
            return (time / 1000).longValue() * 1000 // round to nearest second
        }
        return time
    }

    void assertTimestampMatches(long statTime, long javaTime) {
        assert maybeRoundToNearestSecond(statTime) == maybeRoundToNearestSecond(javaTime)
    }

    int mode(PosixFileAttributes attributes) {
        int mode = 0
        [OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_WRITE, GROUP_EXECUTE, OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE].each {
            mode = mode << 1
            if (attributes.permissions().contains(it)) {
                mode |= 1
            }
        }
        return mode
    }

    List<String> maybeWithUnicde(List<String> src) {
        return src.collect { maybeWithUnicde(it) }
    }

    String maybeWithUnicde(String src) {
        if (Platform.current().isFreeBSD() && !System.getProperty("file.encoding").toLowerCase().contains("utf-8")) {
            // Don't test unicode names
            return src.collectReplacements { ch ->
                ch > 127 ? '-' : null
            }
        } else {
            return src
        }
    }

    static boolean supportsSymbolicLinks() {
        if (!Platform.current().windows) {
            return true
        }

        def createdFolder = File.createTempFile("junit", "")
        createdFolder.delete()
        createdFolder.mkdir()

        def dir = createdFolder
        def targetFile = new File(dir, "foo-dir")
        def linkFile = new File(dir, "foo-dir.link")
        targetFile.mkdirs()

        try {
            try {
                createDirectorySymbolicLink(linkFile, targetFile.name)
                return true
            } catch (ignored) {
                return false
            }
        }
        finally {
            targetFile.delete()
            linkFile.delete()
            dir.deleteDir()
        }
    }

    private static void createSymbolicLinkHelper(File file, String linkTarget, boolean isDirectory) {
        if (Platform.current().windows) {
            // On Windows, we use the 'mklink' command, which works with both elevated processes
            // and Windows 10 with developer mode enabled (see definition of the
            // SYMBOLIC_LINK_FLAG_ALLOW_UNPRIVILEGED_CREATE flag at
            // https://docs.microsoft.com/en-us/windows/desktop/api/winbase/nf-winbase-createsymboliclinkw).
            //
            // See https://blogs.windows.com/buildingapps/2016/12/02/symlinks-windows-10 for detailed
            // information.
            //
            // Until the java.nio.file.Files.createSymbolicLink API support this flag, we need to resort
            // to using the 'mklink' command. We are limited to path shorted than 260 characters though.
            if (file.absolutePath.length() >= 260) {
                throw new IllegalArgumentException("Error creating symbolic link: Path must be shorter than 260 characters on Windows")
            }
            def outputStringBuilder = new StringBuilder(), errStringBuilder = new StringBuilder()
            def arg = isDirectory ? "/d " : ""
            def cmd = "cmd /c mklink ${arg} \"${file.absolutePath}\" \"${linkTarget}\""
            def process = cmd.execute()
            process.consumeProcessOutput(outputStringBuilder, errStringBuilder)
            process.waitForOrKill(2000)
            if (process.exitValue() != 0) {
                throw new IOException("Error creating symbolic link (${errStringBuilder.toString()})")
            }
        } else {
            java.nio.file.Files.createSymbolicLink(file.toPath(), Paths.get(linkTarget))
        }
    }

    static void createFileSymbolicLink(File file, String linkTarget) {
        createSymbolicLinkHelper(file, linkTarget, false)
    }

    static void createDirectorySymbolicLink(File file, String linkTarget) {
        createSymbolicLinkHelper(file, linkTarget, true)
    }
}
