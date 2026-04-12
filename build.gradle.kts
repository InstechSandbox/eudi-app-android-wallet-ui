/*
 * Copyright (c) 2025 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.gms) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.secrets) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.owasp.dependencycheck) apply false
    alias(libs.plugins.kotlinx.kover) apply false
    alias(libs.plugins.sonar) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.dependencyGraph)
}

moduleGraphConfig {
    readmePath.set("wiki/dependency-graph.md")
}

val workspaceClean by tasks.registering {
    group = "build"
    description = "Cleans build outputs for all subprojects in the workspace."
    dependsOn(subprojects.map { "${it.path}:clean" })
}

fun registerBuildAndInstallTask(
    taskName: String,
    variantName: String,
    flavorName: String,
) {
    val assembleTaskName = "assemble${variantName}"
    val installTaskName = "install${variantName}"

    project(":app").tasks.configureEach {
        if (name == assembleTaskName) {
            dependsOn(workspaceClean)
        }
    }

    tasks.register(taskName) {
        group = "install"
        description = "Cleans the workspace, assembles the ${flavorName} debug APK, and installs it on a connected device via adb without removing the other flavor."
        dependsOn(
            ":app:${installTaskName}",
        )
    }
}

registerBuildAndInstallTask(
    taskName = "buildAndInstallDevDebug",
    variantName = "DevDebug",
    flavorName = "Dev",
)

registerBuildAndInstallTask(
    taskName = "buildAndInstallDemoDebug",
    variantName = "DemoDebug",
    flavorName = "Demo",
)

true
