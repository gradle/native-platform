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

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;

import java.io.*;
import java.net.URL;
import java.nio.channels.FileLock;

public class NativeLibraryLocator {
    private final File extractDir;

    public NativeLibraryLocator(File extractDir) {
        this.extractDir = extractDir;
    }

    public File find(LibraryDef libraryDef) throws IOException {
        String resourceName = String.format("net/rubygrapefruit/platform/%s/%s", libraryDef.platform, libraryDef.name);
        if (extractDir != null) {
            File libFile = new File(extractDir, String.format("%s/%s/%s", NativeLibraryFunctions.VERSION, libraryDef.platform, libraryDef.name));
            File lockFile = new File(libFile.getParentFile(), libFile.getName() + ".lock");
            lockFile.getParentFile().mkdirs();
            lockFile.createNewFile();
            RandomAccessFile lockFileAccess = new RandomAccessFile(lockFile, "rw");
            try {
                // Take exclusive lock on lock file
                FileLock lock = lockFileAccess.getChannel().lock();
                if (lockFile.length() > 0 && lockFileAccess.readBoolean()) {
                    // Library has been extracted
                    return libFile;
                }
                URL resource = getClass().getClassLoader().getResource(resourceName);
                if (resource != null) {
                    // Extract library and write marker to lock file
                    libFile.getParentFile().mkdirs();
                    copy(resource, libFile);
                    lockFileAccess.seek(0);
                    lockFileAccess.writeBoolean(true);
                    return libFile;
                }
            } finally {
                // Also releases lock
                lockFileAccess.close();
            }
        } else {
            URL resource = getClass().getClassLoader().getResource(resourceName);
            if (resource != null) {
                File libFile;
                File libDir = File.createTempFile("native-platform", "dir");
                libDir.delete();
                libDir.mkdirs();
                libFile = new File(libDir, libraryDef.name);
                libFile.deleteOnExit();
                copy(resource, libFile);
                return libFile;
            }
        }

        String componentName = libraryDef.name.replaceFirst("^lib", "").replaceFirst("\\.\\w+$", "");
        int pos = componentName.indexOf("-");
        while (pos >= 0) {
            componentName = componentName.substring(0, pos) + Character.toUpperCase(componentName.charAt(pos + 1)) + componentName.substring(pos + 2);
            pos = componentName.indexOf("-", pos);
        }
        File libFile = new File(String.format("build/libs/%s/shared/%s/%s", componentName, libraryDef.platform.replace("-", "_"), libraryDef.name));
        if (libFile.isFile()) {
            return libFile;
        }

        return null;
    }

    private static void copy(URL source, File dest) {
        try {
            InputStream inputStream = source.openStream();
            try {
                OutputStream outputStream = new FileOutputStream(dest);
                try {
                    byte[] buffer = new byte[4096];
                    while (true) {
                        int nread = inputStream.read(buffer);
                        if (nread < 0) {
                            break;
                        }
                        outputStream.write(buffer, 0, nread);
                    }
                } finally {
                    outputStream.close();
                }
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new NativeException(String.format("Could not extract native JNI library."), e);
        }
    }
}
