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

import jetbrains.buildServer.configs.kotlin.BuildFeatures
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.Requirements
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.freeDiskSpace

fun Requirements.requireAgent(agent: Agent) {
    agent.os.addAgentRequirements(this)
    contains("teamcity.agent.jvm.os.arch", agent.architecture.agentRequirementForOs(agent.os))
}

fun BuildType.runOn(agent: Agent) {
    params {
        if (agent == Agent.MacOsAarch64) {
            param("env.JDK8", "%${agent.os.osType.lowercase()}.java8.zulu.${agent.architecture.paramName}%")
        } else {
            param("env.JDK8", "%${agent.os.osType.lowercase()}.java8.openjdk.${agent.architecture.paramName}%")
        }
        param("env.JAVA_HOME", "%${agent.os.osType.lowercase()}.java21.openjdk.${agent.architecture.paramName}%")
        param("env.JDK21", "%${agent.os.osType.lowercase()}.java21.openjdk.${agent.architecture.paramName}%")
    }

    requirements {
        requireAgent(agent)
    }
}

const val buildReceipt = "build-receipt.properties"

val archiveReports = listOf(
    "native-platform",
    "buildSrc",
    "test-app"
).joinToString("\n") { "$it/build/reports/** => $it/reports" }

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

val agentForJavaPublication = Agent.UbuntuAmd64

val agentsForAllNativePlatformJniPublications = listOf(
    Agent.UbuntuAmd64,
    Agent.MacOsAmd64,
    Agent.MacOsAarch64,
    Agent.WindowsAmd64,
    Agent.AmazonLinuxAarch64,
    Agent.FreeBsdAmd64
)
val agentsForNcursesOnlyPublications = listOf(
    Agent.UbuntuAarch64,
    Agent.AmazonLinuxAmd64
)

val Agent.publishJniTasks
    get() = when (this) {
        in agentsForAllNativePlatformJniPublications -> " :native-platform:uploadJni"
        in agentsForNcursesOnlyPublications -> " :native-platform:uploadNcurses"
        else -> ""
    }

val Agent.allPublishTasks
    get() = when (this) {
        agentForJavaPublication -> "$publishJniTasks :native-platform:uploadMain"
        else -> publishJniTasks
    }

fun BuildFeatures.lowerRequiredFreeDiskSpace() {
    freeDiskSpace {
        // Configure less than the default 3GB, since the disk of the agents is only 5GB big.
        requiredSpace = "1gb"
        failBuild = false
    }
}
