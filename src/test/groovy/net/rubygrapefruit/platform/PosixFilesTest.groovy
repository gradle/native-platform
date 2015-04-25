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

package net.rubygrapefruit.platform

import spock.lang.Specification
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import net.rubygrapefruit.platform.internal.Platform

@IgnoreIf({Platform.current().windows})
class PosixFilesTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final PosixFiles files = Native.get(PosixFiles.class)

    def "caches file instance"() {
        expect:
        Native.get(PosixFiles.class) == files
        Native.get(Files.class) == files
    }

    def "can get details of a file"() {
        def testFile = tmpDir.newFile(fileName)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.File
        stat.mode != 0
        stat.uid != 0
        stat.gid != 0
        stat.size == testFile.size()
        stat.lastAccessTime
        stat.lastStatusChangeTime
        stat.lastModifiedTime == testFile.lastModified()
        stat.blockSize

        where:
        fileName << ["test.txt", "test\u03b1\u2295.txt"]
    }

    def "can get details of a directory"() {
        def testFile = tmpDir.newFolder(fileName)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Directory
        stat.mode != 0
        stat.uid != 0
        stat.gid != 0
        stat.lastAccessTime
        stat.lastStatusChangeTime
        stat.lastModifiedTime == testFile.lastModified()

        where:
        fileName << ["test-dir", "test\u03b1\u2295-dir"]
    }

    def "can get details of a missing file"() {
        def testFile = new File(tmpDir.root, fileName)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Missing
        stat.mode == 0
        stat.uid == 0
        stat.gid == 0
        stat.size == 0

        where:
        fileName << ["test-dir", "test\u03b1\u2295-dir"]
    }

    def "can set mode on a file"() {
        def testFile = tmpDir.newFile(fileName)

        when:
        files.setMode(testFile, 0740)

        then:
        files.getMode(testFile) == 0740
        files.stat(testFile).mode == 0740

        where:
        fileName << ["test.txt", "test\u03b1\u2295.txt"]
    }

    def "can set mode on a directory"() {
        def testFile = tmpDir.newFolder(fileName)

        when:
        files.setMode(testFile, 0740)

        then:
        files.getMode(testFile) == 0740
        files.stat(testFile).mode == 0740

        where:
        fileName << ["test-dir", "test\u03b1\u2295-dir"]
    }

    def "cannot set mode on file that does not exist"() {
        def testFile = new File(tmpDir.root, "unknown")

        when:
        files.setMode(testFile, 0660)

        then:
        NativeException e = thrown()
        e.message == "Could not set UNIX mode on $testFile: could not chmod file (errno 2: No such file or directory)"
    }

    def "cannot get mode on file that does not exist"() {
        def testFile = new File(tmpDir.root, "unknown")

        when:
        files.getMode(testFile)

        then:
        NativeException e = thrown()
        e.message == "Could not get UNIX mode on $testFile: file does not exist."
    }

    def "can create symbolic link"() {
        def testFile = new File(tmpDir.root, "test.txt")
        testFile.text = "hi"
        def symlinkFile = new File(tmpDir.root, "symlink")

        when:
        files.symlink(symlinkFile, testFile.name)

        then:
        symlinkFile.file
        symlinkFile.text == "hi"
        symlinkFile.canonicalFile == testFile.canonicalFile
    }

    def "can read symbolic link"() {
        def symlinkFile = new File(tmpDir.root, "symlink")

        when:
        files.symlink(symlinkFile, "target")

        then:
        files.readLink(symlinkFile) == "target"
    }

    def "cannot read a symlink that does not exist"() {
        def symlinkFile = new File(tmpDir.root, "symlink")

        when:
        files.readLink(symlinkFile)

        then:
        NativeException e = thrown()
        e.message == "Could not read symlink $symlinkFile: could not lstat file (errno 2: No such file or directory)"
    }

    def "cannot read a symlink that is not a symlink"() {
        def symlinkFile = tmpDir.newFile("not-a-symlink.txt")

        when:
        files.readLink(symlinkFile)

        then:
        NativeException e = thrown()
        e.message == "Could not read symlink $symlinkFile: could not readlink (errno 22: Invalid argument)"
    }

    def "can create and read symlink with unicode in its name"() {
        def testFile = new File(tmpDir.root, "target\u03b2\u2295")
        testFile.text = 'hi'
        def symlinkFile = new File(tmpDir.root, "symlink\u03b2\u2296")

        when:
        files.symlink(symlinkFile, testFile.name)

        then:
        files.readLink(symlinkFile) == testFile.name
        symlinkFile.file
        symlinkFile.canonicalFile == testFile.canonicalFile
    }

    def "can get details of a symlink"() {
        def testFile = new File(tmpDir.newFolder("parent"), fileName)

        given:
        files.symlink(testFile, "target")

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Symlink
        stat.mode != 0
        stat.mode != 0
        stat.uid != 0
        stat.gid != 0
        stat.lastAccessTime
        stat.lastStatusChangeTime
        stat.lastModifiedTime

        where:
        fileName << ["test.txt", "test\u03b1\u2295.txt"]
    }
}
