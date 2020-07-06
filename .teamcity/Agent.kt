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

enum class Agent(val os: Os, val architecture: Architecture) {
    UbuntuAmd64(os = Os.Ubuntu, architecture = Architecture.Amd64),
    UbuntuAarch64(os = Os.Ubuntu, architecture = Architecture.Aarch64),
    AmazonLinuxAmd64(os = Os.AmazonLinux, architecture = Architecture.Amd64),
    AmazonLinuxAarch64(os = Os.AmazonLinux, architecture = Architecture.Aarch64),
    CentOsAmd64(os = Os.CentOs, architecture = Architecture.Amd64),
    FreeBsdAmd64(os = Os.FreeBsd, architecture = Architecture.Amd64),
    WindowsAmd64(os = Os.Windows, architecture = Architecture.Amd64),
    MacOsAmd64(os = Os.MacOs, architecture = Architecture.Amd64),
}
