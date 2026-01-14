import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec

plugins {
    id("targets")
    id("publish")
    id("unicode-data")
}

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()

    jvmToolchain(11)

    sourceSets {
        val normalizeVersion = "1.3.3"

        val commonMain by getting {
            dependencies {
                implementation("com.doist.x:normalize:$normalizeVersion")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

plugins.withType<YarnPlugin> {
    the<YarnRootEnvSpec>().apply {
        yarnLockMismatchReport = YarnLockMismatchReport.WARNING
        yarnLockAutoReplace = true
    }
}

// Sanity check before attempting to publish root target without having all targets enabled.
tasks.matching { it.name.startsWith("publishKotlinMultiplatform") }.configureEach {
    doFirst {
        val enabledTargets = findProperty("targets")
            ?.toString()
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

        require(enabledTargets.contains("all")) {
            "Configuration is set to publish root target without 'all' targets enabled."
        }
    }
}
