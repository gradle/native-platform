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

import jetbrains.buildServer.configs.kotlin.Requirements

interface Os {
    fun addAgentRequirements(requirements: Requirements)
    val osType: String

    object Ubuntu16 : Linux(Ncurses.Ncurses5) {
        override fun Requirements.additionalRequirements() {
            contains(osDistributionNameParameter, "ubuntu")
            contains(osDistributionVersionParameter, "16")
        }
    }

    object AmazonLinux : Linux(Ncurses.Ncurses6) {
        override fun Requirements.additionalRequirements() {
            contains(osDistributionNameParameter, "amazon")
        }
    }

    object CentOs : Linux(Ncurses.Ncurses6) {
        override fun Requirements.additionalRequirements() {
            contains(osDistributionNameParameter, "centos")
        }
    }

    object MacOs : OsWithNameRequirement("Mac OS X", "MacOs")

    object Windows : OsWithNameRequirement("Windows", "Windows")

    object FreeBsd : OsWithNameRequirement("FreeBSD", "FreeBsd")
}

private const val osDistributionNameParameter = "system.agent.os.distribution.name"
private const val osDistributionVersionParameter = "system.agent.os.distribution.version"

abstract class OsWithNameRequirement(private val osName: String, override val osType: String) : Os {
    override fun addAgentRequirements(requirements: Requirements) {
        requirements.contains("teamcity.agent.jvm.os.name", osName)
        requirements.additionalRequirements()
    }

    open fun Requirements.additionalRequirements() {}
}

abstract class Linux(val ncurses: Ncurses) : OsWithNameRequirement("Linux", "Linux")

enum class Ncurses {
    Ncurses5,
    Ncurses6
}

enum class Architecture(val paramName: String) {
    Aarch64("aarch64") {
        override fun agentRequirementForOs(os: Os): String = "aarch64"
    },
    Amd64("64bit") {
        override fun agentRequirementForOs(os: Os): String = when (os) {
            Os.MacOs -> "x86_64"
            else -> "amd64"
        }
    };

    abstract fun agentRequirementForOs(os: Os): String
}
