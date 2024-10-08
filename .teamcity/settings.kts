/*
 * Copyright 2024 the original author or authors.
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

import jetbrains.buildServer.configs.kotlin.project
import jetbrains.buildServer.configs.kotlin.version

/*
The settings script is an entry point for defining a single
TeamCity project. TeamCity looks for the 'settings.kts' file in a
project directory and runs it if it's found, so the script name
shouldn't be changed and its package should be the same as the
project's id.

The script should contain a single call to the project() function
with a Project instance or an init function as an argument.

VcsRoots, BuildTypes, and Templates of this project must be
registered inside project using the vcsRoot(), buildType(), and
template() methods respectively.

Subprojects can be defined either in their own settings.kts or by
calling the subProjects() method in this project.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View ->
Tool Windows -> Maven Projects), find the generate task
node (Plugins -> teamcity-configs -> teamcity-configs:generate),
the 'Debug' option is available in the context menu for the task.
*/

version = "2024.03"

project {
    params {
        param("env.DEVELOCITY_ACCESS_KEY", "%ge.gradle.org.access.key%")
    }

    val buildReceiptSource = NativePlatformBuild(Agent.UbuntuAmd64, buildReceiptSource = true) {
        artifactRules = listOf(artifactRules, buildReceipt).joinToString("\n")
    }
    val testBuilds = listOf(buildReceiptSource) +
        Agent.entries.filter { it !in listOf(Agent.UbuntuAmd64, Agent.CentOsAmd64) }.map { NativePlatformBuild(it) }
    testBuilds.forEach(::buildType)
    val compatibilityTestBuilds = listOf(NativePlatformCompatibilityTest(Agent.CentOsAmd64, testBuilds).also(::buildType))
    buildType(BuildTrigger(testBuilds + compatibilityTestBuilds))

    subProject(Publishing(testBuilds, buildReceiptSource))
}
