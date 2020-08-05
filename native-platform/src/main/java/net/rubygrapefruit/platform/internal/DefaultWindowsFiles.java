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
import net.rubygrapefruit.platform.file.WindowsFileInfo;
import net.rubygrapefruit.platform.file.WindowsFiles;
import net.rubygrapefruit.platform.internal.jni.WindowsFileFunctions;

import java.io.File;
import java.util.List;

public class DefaultWindowsFiles extends AbstractFiles implements WindowsFiles {
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

    public List<? extends DirEntry> listDir(File dir, boolean linkTarget) throws NativeException {
        FunctionResult result = new FunctionResult();
        WindowsDirList dirList = new WindowsDirList();
        WindowsFileFunctions.readdir(dir.getPath(), linkTarget, dirList, result);
        if (result.isFailed()) {
            throw listDirFailure(dir, result);
        }
        return dirList.files;
    }

    public List<? extends DirEntry> listDir(File dir) throws NativeException {
        return listDir(dir, false);
    }
}
