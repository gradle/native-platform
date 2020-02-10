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

enum class Agent(val agentOsName: String, val java8Home: String, val agentArch: String, val curses: CursesRequirement = CursesRequirement.None) {
    Linux(agentOsName = "Linux", java8Home = "%linux.java8.oracle.64bit%", agentArch = "amd64", curses = CursesRequirement.Curses5),
    LinuxNcurses6(agentOsName = "Linux", java8Home = "%linux.java8.openjdk.aarch64%", agentArch = "amd64", curses = CursesRequirement.Curses5),
    LinuxAarch64(agentOsName = "Linux", java8Home = "%linux.java8.openjdk.aarch64%", agentArch = "aarch64", curses = CursesRequirement.Curses6),
    LinuxAarch64Ncurses5(agentOsName = "Linux", java8Home = "/usr/lib/jvm/java-8-openjdk-arm64", agentArch = "aarch64", curses = CursesRequirement.Curses5),
    Windows(agentOsName = "Windows", java8Home = "%windows.java8.oracle.64bit%", agentArch = "amd64"),
    MacOs(agentOsName = "Mac OS X", java8Home = "%macos.java8.oracle.64bit%", agentArch = "x86_64"),
    FreeBsd(agentOsName = "FreeBSD", java8Home = "%freebsd.java8.openjdk.64bit%", agentArch = "amd64")
}

enum class CursesRequirement {
    Curses5,
    Curses6,
    None
}
