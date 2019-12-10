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

package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.*;
import net.rubygrapefruit.platform.file.DirEntry;
import net.rubygrapefruit.platform.file.FileInfo;
import net.rubygrapefruit.platform.file.WindowsFileInfo;
import net.rubygrapefruit.platform.file.WindowsFiles;
import net.rubygrapefruit.platform.internal.jni.WindowsFileFunctions;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.util.List;

public class DefaultWindowsFiles extends AbstractFiles implements WindowsFiles {
    private DirectoryLister directoryLister;

    public WindowsFileInfo stat(File file) throws NativeException {
        return stat(file, false);
    }

    public WindowsFileInfo stat(File file, boolean linkTarget) throws NativeException {
        FunctionResult result = new FunctionResult();
        WindowsFileStat stat = new WindowsFileStat(file.getPath());
        WindowsFileFunctions.stat(file.getPath(), linkTarget, stat, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get file details of %s: %s", file, result.getMessage()));
        }
        return stat;
    }

    public List<? extends DirEntry> listDir(File dir) throws NativeException {
        return listDir(dir, false);
    }

    public List<? extends DirEntry> listDir(File dir, boolean linkTarget) throws NativeException {
        if (directoryLister == null) {
            directoryLister = WindowsFileFunctions.fastReaddirIsSupported() ? new FastLister() : new BackwardCompatibleLister();
        }
        return directoryLister.listDir(dir, linkTarget);
    }

    private interface DirectoryLister {
        List<? extends DirEntry> listDir(File dir, boolean linkTarget) throws NativeException;
    }

    private class BackwardCompatibleLister implements DirectoryLister {
        public List<? extends DirEntry> listDir(File dir, boolean linkTarget) throws NativeException {
            FunctionResult result = new FunctionResult();
            WindowsDirList dirList = new WindowsDirList();
            WindowsFileFunctions.readdir(dir.getPath(), linkTarget, dirList, result);
            if (result.isFailed()) {
                throw listDirFailure(dir, result);
            }
            return dirList.files;
        }
    }

    private class FastLister implements DirectoryLister {
        private static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x00000400;
        private static final int FILE_ATTRIBUTE_DIRECTORY = 0x00000010;
        private static final int IO_REPARSE_TAG_SYMLINK = 0xA000000C;

        private static final int SIZEOF_WCHAR = 2;
        private static final int OFFSETOF_NEXT_ENTRY_OFFSET = 0;
        private static final int OFFSETOF_LAST_WRITE_TIME = 24;
        private static final int OFFSETOF_END_OF_FILE = 40;
        private static final int OFFSETOF_FILE_ATTRIBUTES = 56;
        private static final int OFFSETOF_FILENAME_LENGTH = 60;
        private static final int OFFSETOF_EA_SIZE = 64;
        private static final int OFFSETOF_FILENAME = 80;

        /**
         * We use a thread local context to store a weak reference to a {@link NtQueryDirectoryFileContext}
         * instance so that we can avoid an extra direct {@link ByteBuffer} allocation at every
         * invocation of {@link #listDir(File, boolean)}.
         * <P>This has shown to improve performance by a few percents in stress test benchmarks, while at the
         * same time limiting memory usage increase to a minimum (about 8KB per thread using this API)</P>
         * <p>This is safe because {@link #listDir(File, boolean)} invocations are self-contained, i.e. don't
         * call back into external code.</p>
         */
        private final ThreadLocal<WeakReference<NtQueryDirectoryFileContext>> threadLocalContext =
                new ThreadLocal<WeakReference<NtQueryDirectoryFileContext>>();

        public List<? extends DirEntry> listDir(File dir, boolean linkTarget) throws NativeException {
            FunctionResult result = new FunctionResult();
            WindowsDirList dirList = new WindowsDirList();
            String path = dir.getPath();

            long handle = WindowsFileFunctions.fastReaddirOpen(path, result);
            if (result.isFailed()) {
                throw listDirFailure(dir, result);
            }
            try {
                NtQueryDirectoryFileContext context = getNtQueryDirectoryFileContext();

                boolean more = WindowsFileFunctions.fastReaddirNext(handle, context.buffer, result);
                if (result.isFailed()) {
                    throw listDirFailure(dir, result);
                }
                if (more) {
                    int entryOffset = 0;
                    while (true) {
                        // Read entry from buffer
                        entryOffset = addFullDirEntry(context, dir, linkTarget, entryOffset, dirList);

                        // If we reached end of buffer, fetch next set of entries
                        if (entryOffset == 0) {
                            more = WindowsFileFunctions.fastReaddirNext(handle, context.buffer, result);
                            if (result.isFailed()) {
                                throw listDirFailure(dir, result);
                            }
                            if (!more) {
                                break;
                            }
                        }
                    }
                }
            } finally {
                WindowsFileFunctions.fastReaddirClose(handle);
            }

            return dirList.files;
        }

        /**
         * Parse the content of {@link NtQueryDirectoryFileContext#buffer} at offset {@code entryOffset}
         * as a <a href="https://docs.microsoft.com/en-us/windows-hardware/drivers/ddi/content/ntifs/ns-ntifs-_file_id_full_dir_information">FILE_ID_FULL_DIR_INFORMATION</a>
         * native structure and adds the parsed entry into the {@link WindowsDirList} collection.
         * <p>Returns the byte offset of the next entry in {@link NtQueryDirectoryFileContext#buffer} if there is one,
         * or {@code 0} if there is no next entry.</p>
         */
        private int addFullDirEntry(NtQueryDirectoryFileContext context, File dir, boolean followLink, int entryOffset, WindowsDirList dirList) {
            // typedef struct _FILE_ID_FULL_DIR_INFORMATION {
            //  ULONG         NextEntryOffset;  // offset = 0
            //  ULONG         FileIndex;        // offset = 4
            //  LARGE_INTEGER CreationTime;     // offset = 8
            //  LARGE_INTEGER LastAccessTime;   // offset = 16
            //  LARGE_INTEGER LastWriteTime;    // offset = 24
            //  LARGE_INTEGER ChangeTime;       // offset = 32
            //  LARGE_INTEGER EndOfFile;        // offset = 40
            //  LARGE_INTEGER AllocationSize;   // offset = 48
            //  ULONG         FileAttributes;   // offset = 56
            //  ULONG         FileNameLength;   // offset = 60
            //  ULONG         EaSize;           // offset = 64
            //  LARGE_INTEGER FileId;           // offset = 72
            //  WCHAR         FileName[1];      // offset = 80
            //} FILE_ID_FULL_DIR_INFORMATION, *PFILE_ID_FULL_DIR_INFORMATION;
            int nextEntryOffset = context.buffer.getInt(entryOffset + OFFSETOF_NEXT_ENTRY_OFFSET);

            long fileSize = context.buffer.getLong(entryOffset + OFFSETOF_END_OF_FILE);
            long lastModified = context.buffer.getLong(entryOffset + OFFSETOF_LAST_WRITE_TIME);

            //
            // See https://docs.microsoft.com/en-us/windows/desktop/fileio/reparse-point-tags
            //  IO_REPARSE_TAG_SYMLINK (0xA000000C)
            //
            int fileAttributes = context.buffer.getInt(entryOffset + OFFSETOF_FILE_ATTRIBUTES);
            int reparseTagData = context.buffer.getInt(entryOffset + OFFSETOF_EA_SIZE);

            FileInfo.Type type = getFileType(fileAttributes, reparseTagData);

            int FileNameByteCount = context.buffer.getInt(entryOffset + OFFSETOF_FILENAME_LENGTH);
            context.charBuffer.position((entryOffset + OFFSETOF_FILENAME) / SIZEOF_WCHAR);
            context.charBuffer.get(context.fileNameArray, 0, FileNameByteCount / SIZEOF_WCHAR);
            String fileName = new String(context.fileNameArray, 0, FileNameByteCount / SIZEOF_WCHAR);

            // Skip "." and ".." entries
            if (!".".equals(fileName) && !"..".equals(fileName)) {
                if (type == FileInfo.Type.Symlink && followLink) {
                    WindowsFileInfo targetInfo = stat(new File(dir, fileName), true);
                    dirList.addFile(fileName, targetInfo);
                } else {
                    dirList.addFile(fileName, type.ordinal(), fileSize, lastModified);
                }
            }

            return nextEntryOffset == 0 ? 0 : entryOffset + nextEntryOffset;
        }

        private FileInfo.Type getFileType(int dwFileAttributes, int reparseTagData) {
            if (((dwFileAttributes & FILE_ATTRIBUTE_REPARSE_POINT) == FILE_ATTRIBUTE_REPARSE_POINT) && (reparseTagData == IO_REPARSE_TAG_SYMLINK)) {
                return FileInfo.Type.Symlink;
            } else if ((dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) == FILE_ATTRIBUTE_DIRECTORY) {
                return FileInfo.Type.Directory;
            } else {
                return FileInfo.Type.File;
            }
        }

        private NtQueryDirectoryFileContext getNtQueryDirectoryFileContext() {
            WeakReference<NtQueryDirectoryFileContext> ref = threadLocalContext.get();
            NtQueryDirectoryFileContext result = (ref == null  ? null : ref.get());
            if (result == null) {
                result = new NtQueryDirectoryFileContext();
                ref = new WeakReference<NtQueryDirectoryFileContext>(result);
                threadLocalContext.set(ref);
            }
            return result;
        }

        private class NtQueryDirectoryFileContext {
            /**
             * The {@code direct} {@link ByteBuffer} used to share memory between C++ and Java code.
             */
            final ByteBuffer buffer;
            /**
             * A {@link CharBuffer} view of the {@link #buffer} above used to retrieve UTF-16 filename strings.
             */
            final CharBuffer charBuffer;
            /**
             * A {@link java.lang.Character} array used to copy characters from the {@link #charBuffer} above
             * before converting into a {@link java.lang.String} instance.
             */
            final char[] fileNameArray;

            NtQueryDirectoryFileContext() {
                // Note: 8KB is enough to store ~100 FILE_ID_FULL_DIR_INFORMATION entries, counting 80 bytes
                // as "fixed" data plus 10 UTF-16 characters for the file name.
                buffer = ByteBuffer.allocateDirect(8192);
                // Win32/x86 is little endian
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                charBuffer = buffer.asCharBuffer();
                // Note: Win32 file names are limited to 256 characters
                fileNameArray = new char[256];
            }
        }
    }
}
