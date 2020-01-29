import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType

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

fun BuildType.runOn(os: Os): Unit {
    params {
        param("env.JAVA_HOME", os.java8Home)
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", os.agentOsName)
    }
}

const val buildScanInit = "-I gradle/init-scripts/build-scan.init.gradle.kts"

const val buildReceipt = "build-receipt.properties"
