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
import net.rubygrapefruit.platform.Process;
import net.rubygrapefruit.platform.internal.jni.PosixProcessFunctions;

import java.io.File;

public class DefaultProcess implements Process {
    public int getProcessId() throws NativeException {
        return PosixProcessFunctions.getPid();
    }

    public File getWorkingDirectory() throws NativeException {
        FunctionResult result = new FunctionResult();
        String dir = PosixProcessFunctions.getWorkingDirectory(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get process working directory: %s",
                    result.getMessage()));
        }
        return new File(dir);
    }

    public void setWorkingDirectory(File directory) throws NativeException {
        FunctionResult result = new FunctionResult();
        PosixProcessFunctions.setWorkingDirectory(directory.getAbsolutePath(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not set process working directory to '%s': %s",
                    directory.getAbsoluteFile(), result.getMessage()));
        }
    }

    public String getEnvironmentVariable(String name) throws NativeException {
        FunctionResult result = new FunctionResult();
        String value = PosixProcessFunctions.getEnvironmentVariable(name, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get the value of environment variable '%s': %s", name,
                    result.getMessage()));
        }
        return value;
    }

    public void setEnvironmentVariable(String name, String value) throws NativeException {
        FunctionResult result = new FunctionResult();
        PosixProcessFunctions.setEnvironmentVariable(name, value, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not set the value of environment variable '%s': %s", name,
                    result.getMessage()));
        }
    }
}
