import jetbrains.buildServer.configs.kotlin.v2019_2.BuildFeatures
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher

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
        when (agent.curses) {
            CursesRequirement.Curses6 -> contains("system.ncurses.version", "ncurses6")
            CursesRequirement.Curses5 -> doesNotContain("system.ncurses.version", "ncurses6")
            CursesRequirement.None -> {}
        }
    }
}

const val buildScanInit = "-I gradle/init-scripts/build-scan.init.gradle.kts"

const val buildReceipt = "build-receipt.properties"

fun BuildFeatures.publishCommitStatus() {
    commitStatusPublisher {
        vcsRootExtId = DslContext.settingsRoot.id?.value
        publisher = github {
            githubUrl = "https://api.github.com"
            authType = personalToken {
                token = "credentialsJSON:5306bfc7-041e-46e8-8d61-1d49424e7b04"
            }
        }
    }
}
