package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;

import java.io.*;
import java.net.URL;

public class NativeLibraryLocator {
    private final File extractDir;

    public NativeLibraryLocator(File extractDir) {
        this.extractDir = extractDir;
    }

    public File find(String libraryName) throws IOException {
        File libFile;
        URL resource = getClass().getClassLoader().getResource(libraryName);
        if (resource != null) {
            File libDir = extractDir;
            if (libDir == null) {
                libDir = File.createTempFile("native-platform", "dir");
                libDir.delete();
                libDir.mkdirs();
            }
            libFile = new File(libDir, libraryName);
            libFile.deleteOnExit();
            copy(resource, libFile);
            return libFile;
        }

        libFile = new File("build/binaries/" + libraryName);
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
