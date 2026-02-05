import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkTask

plugins {
    id("targets")
    id("publish")
    id("unicode-data")
}

repositories {
    mavenCentral()
}

val xcframeworkName = "DoistxConfusables"
val xcf = XCFramework(xcframeworkName)

kotlin {
    explicitApi()

    jvmToolchain(11)

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        watchosX64(),
        watchosSimulatorArm64(),
        watchosArm32(),
        watchosArm64(),
        watchosDeviceArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = xcframeworkName
            isStatic = false
            binaryOption("bundleId", "com.doist.doistx-confusables")
            xcf.add(this)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.doist.x:normalize:1.3.3")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType(XCFrameworkTask::class).configureEach {
    doLast {
        val destination = File("${outputDir.resolve(buildType.getName())}/Package.swift")
        destination.delete()
        File("$projectDir/Package.swift.template").copyTo(destination)
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
