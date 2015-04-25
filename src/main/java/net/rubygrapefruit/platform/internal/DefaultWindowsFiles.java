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
import net.rubygrapefruit.platform.WindowsFileInfo;
import net.rubygrapefruit.platform.WindowsFiles;
import net.rubygrapefruit.platform.internal.jni.WindowsFileFunctions;

import java.io.File;

public class DefaultWindowsFiles implements WindowsFiles {
    public WindowsFileInfo stat(File file) throws NativeException {
        FunctionResult result = new FunctionResult();
        WindowsFileStat stat = new WindowsFileStat(file.getPath());
        WindowsFileFunctions.stat(file.getPath(), stat, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get file details of %s: %s", file, result.getMessage()));
        }
        return stat;
    }
}
