import jetbrains.buildServer.configs.kotlin.v2019_2.Requirements

interface Os {
    fun addAgentRequirements(requirements: Requirements)
    val java8Home: String
    val osType: String

    object Ubuntu : Linux(Ncurses.Ncurses5) {
        override fun Requirements.additionalRequirements() {
            doesNotContain("system.ncurses.version", "ncurses6")
            doesNotContain(osVersionParameter, "el8")
        }
        override val java8Home: String = "%linux.java8.oracle.64bit%"
    }

    object AmazonLinux : Linux(Ncurses.Ncurses6) {
        override fun Requirements.additionalRequirements() {
            contains("system.ncurses.version", "ncurses6")
            doesNotContain(osVersionParameter, "el8")
        }

        override val java8Home: String = "%linux.java8.openjdk.aarch64%"
    }

    object CentOs : Linux(Ncurses.Ncurses6) {
        override fun Requirements.additionalRequirements() {
            contains(osVersionParameter, "el8")
        }

        override val java8Home: String = "/usr/lib/jvm/java"
    }

    object MacOs : OsWithNameRequirement("Mac OS X", "MacOs") {
        override val java8Home: String = "%macos.java8.oracle.64bit%"
    }

    object Windows : OsWithNameRequirement("Windows", "Windows") {
        override val java8Home: String = "%windows.java8.oracle.64bit%"
    }

    object FreeBsd : OsWithNameRequirement("FreeBSD", "FreeBsd") {
        override val java8Home: String = "%freebsd.java8.openjdk.64bit%"
    }
}

private const val osVersionParameter = "teamcity.agent.jvm.os.version"

abstract class OsWithNameRequirement(private val osName: String, override val osType: String) : Os {
    override fun addAgentRequirements(requirements: Requirements) {
        requirements.contains("teamcity.agent.jvm.os.name", osName)
        requirements.additionalRequirements()
    }

    open fun Requirements.additionalRequirements() {}
}

abstract class Linux(val ncurses: Ncurses): OsWithNameRequirement("Linux", "Linux")

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
