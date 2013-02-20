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
import net.rubygrapefruit.platform.ThreadSafe;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * A {@link Process} implementation that wraps another to add thread-safety and to update the JVM's internal view of
 * various process properties.
 */
@ThreadSafe
public class WrapperProcess implements Process {
    private final Process process;
    private final boolean windows;
    private final Object workingDirectoryLock = new Object();
    private final Object environmentLock = new Object();
    private Map<String, String> environment;
    private Map<String, String> windowsEnvironment;

    public WrapperProcess(Process process, boolean windows) {
        this.process = process;
        this.windows = windows;
    }

    @Override
    public String toString() {
        return process.toString();
    }

    public int getProcessId() throws NativeException {
        return process.getProcessId();
    }

    public File getWorkingDirectory() throws NativeException {
        synchronized (workingDirectoryLock) {
            return process.getWorkingDirectory();
        }
    }

    public void setWorkingDirectory(File directory) throws NativeException {
        synchronized (workingDirectoryLock) {
            process.setWorkingDirectory(directory);
            System.setProperty("user.dir", directory.getAbsolutePath());
        }
    }

    public String getEnvironmentVariable(String name) throws NativeException {
        synchronized (environmentLock) {
            String value = process.getEnvironmentVariable(name);
            return value == null || value.length() == 0 ? null : value;
        }
    }

    public void setEnvironmentVariable(String name, String value) throws NativeException {
        synchronized (environmentLock) {
            if (value == null || value.length() == 0) {
                process.setEnvironmentVariable(name, null);
                removeEnvInternal(name);
            } else {
                process.setEnvironmentVariable(name, value);
                setEnvInternal(name, value);
            }
        }
    }

    private void removeEnvInternal(String name) {
        getEnv().remove(name);
        if (windows) {
            getWindowsEnv().remove(name);
        }
    }

    private void setEnvInternal(String name, String value) {
        getEnv().put(name, value);
        if (windows) {
            getWindowsEnv().put(name, value);
        }
    }

    private Map<String, String> getEnv() {
        if (environment == null) {
            try {
                Map<String, String> theUnmodifiableEnvironment = System.getenv();
                Class<?> cu = theUnmodifiableEnvironment.getClass();
                Field m = cu.getDeclaredField("m");
                m.setAccessible(true);
                environment = (Map<String, String>) m.get(theUnmodifiableEnvironment);
            } catch (Exception e) {
                throw new NativeException("Unable to get mutable environment variable map.", e);
            }
        }
        return environment;
    }

    private Map<String, String> getWindowsEnv() {
        if (windowsEnvironment == null) {
            try {
                Class<?> sc = Class.forName("java.lang.ProcessEnvironment");
                Field caseinsensitive = sc.getDeclaredField("theCaseInsensitiveEnvironment");
                caseinsensitive.setAccessible(true);
                windowsEnvironment = (Map<String, String>) caseinsensitive.get(null);
            } catch (Exception e) {
                throw new NativeException("Unable to get mutable Windows environment variable map", e);
            }
        }
        return windowsEnvironment;
    }
}
