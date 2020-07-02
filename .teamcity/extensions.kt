import jetbrains.buildServer.configs.kotlin.v2019_2.BuildFeatures
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.freeDiskSpace

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

fun BuildType.runOn(agent: Agent) {
    params {
        param("env.JAVA_HOME", agent.java8Home)
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", agent.agentOsName)
        contains("teamcity.agent.jvm.os.arch", agent.agentArch)
        // Don't run any builds on CentOs 8 for now.
        doesNotContain("teamcity.agent.jvm.os.version", "el8")
        when (agent.curses) {
            CursesRequirement.Curses6 -> contains("system.ncurses.version", "ncurses6")
            CursesRequirement.Curses5 -> doesNotContain("system.ncurses.version", "ncurses6")
            CursesRequirement.None -> {}
        }
    }
}

const val buildScanInit = "-I gradle/init-scripts/build-scan.init.gradle.kts"

const val buildReceipt = "build-receipt.properties"

const val archiveReports = """build/reports/** => reports
buildSrc/build/reports/** => buildSrc/reports
testApp/build/reports/** => testApp/reports"""

fun BuildFeatures.publishCommitStatus() {
    commitStatusPublisher {
        vcsRootExtId = DslContext.settingsRoot.id?.value
        publisher = github {
            githubUrl = "https://api.github.com"
            authType = personalToken {
                token = "%github.bot-teamcity.token%"
            }
        }
    }
}

fun BuildFeatures.lowerRequiredFreeDiskSpace() {
    freeDiskSpace {
        // Configure less than the default 3GB, since the disk of the agents is only 5GB big.
        requiredSpace = "1gb"
        failBuild = false
    }
}
