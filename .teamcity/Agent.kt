/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

enum class Agent(val agentOsName: String, val java8Home: String, val agentArch: String) {
    Linux("Linux", "%linux.java8.oracle.64bit%", "amd64"),
    Linux_Arm("Linux", "%linux.java8.openjdk.aarch64%", "aarch64"),
    Windows("Windows", "%windows.java8.oracle.64bit%", "amd64"),
    MacOs("Mac OS X", "%macos.java8.oracle.64bit%", "x86_64"),
    FreeBsd("FreeBSD", "%freebsd.java8.openjdk.64bit%", "amd64")
}
