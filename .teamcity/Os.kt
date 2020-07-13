import jetbrains.buildServer.configs.kotlin.v2019_2.Requirements

interface Os {
    fun addAgentRequirements(requirements: Requirements)
    val osType: String

    object Ubuntu : Linux(Ncurses.Ncurses5) {
        override fun Requirements.additionalRequirements() {
            contains(osDistributionNameParameter, "ubuntu")
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

enum class Architecture {
    Aarch64 {
        override fun agentRequirementForOs(os: Os): String = "aarch64"
    },
    Amd64 {
        override fun agentRequirementForOs(os: Os): String = when (os) {
            Os.MacOs -> "x86_64"
            else -> "amd64"
        }
    };

    abstract fun agentRequirementForOs(os: Os): String
}
