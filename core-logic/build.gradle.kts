/*
 * Copyright (c) 2023 European Commission
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

import com.android.build.api.dsl.LibraryExtension
import project.convention.logic.addConfigField
import project.convention.logic.config.LibraryModule
import project.convention.logic.getProperty
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

val localDemoHost = getProperty<String>("localDemoHost")
    ?: System.getenv("LOCAL_DEMO_HOST")
    ?: "127.0.0.1"
val localVerifierApi = getProperty<String>("localVerifierApi")
    ?: System.getenv("LOCAL_VERIFIER_API")
    ?: "https://$localDemoHost"
val localIssuerUrl = getProperty<String>("localIssuerUrl")
    ?: System.getenv("LOCAL_ISSUER_URL")
    ?: "https://$localDemoHost:5003"
val testVerifierApi = getProperty<String>("testVerifierApi")
    ?: System.getenv("TEST_VERIFIER_API")
    ?: "https://verifier.test.instech-eudi-poc.com"
val testIssuerUrl = getProperty<String>("testIssuerUrl")
    ?: System.getenv("TEST_ISSUER_URL")
    ?: "https://issuer.test.instech-eudi-poc.com"
val localVerifierClientId = getProperty<String>("localVerifierClientId")
    ?: System.getenv("LOCAL_VERIFIER_CLIENT_ID")
    ?: "Verifier"
val testVerifierClientId = getProperty<String>("testVerifierClientId")
    ?: System.getenv("TEST_VERIFIER_CLIENT_ID")
    ?: "Verifier"
val localVerifierLegalName = getProperty<String>("localVerifierLegalName")
    ?: System.getenv("LOCAL_VERIFIER_LEGAL_NAME")
    ?: "Local Verifier"
val testVerifierLegalName = getProperty<String>("testVerifierLegalName")
    ?: System.getenv("TEST_VERIFIER_LEGAL_NAME")
    ?: "Instech Test Verifier"
val localIssuerClientId = getProperty<String>("localIssuerClientId")
    ?: System.getenv("LOCAL_ISSUER_CLIENT_ID")
    ?: "wallet-dev-local"
val testIssuerClientId = getProperty<String>("testIssuerClientId")
    ?: System.getenv("TEST_ISSUER_CLIENT_ID")
    ?: "wallet-dev-local"

plugins {
    id("project.android.library")
    id("project.wallet.core")
}

extensions.configure<LibraryExtension>("android") {
    namespace = "eu.europa.ec.corelogic"

    defaultConfig {
        addConfigField("LOCAL_DEMO_HOST", localDemoHost)
    }

    productFlavors {
        named("dev") {
            addConfigField("APP_ENVIRONMENT", "local")
            addConfigField("VERIFIER_API", localVerifierApi)
            addConfigField("ISSUER_URL", localIssuerUrl)
            addConfigField("VERIFIER_CLIENT_ID", localVerifierClientId)
            addConfigField("VERIFIER_LEGAL_NAME", localVerifierLegalName)
            addConfigField("ISSUER_CLIENT_ID", localIssuerClientId)
        }

        named("demo") {
            addConfigField("APP_ENVIRONMENT", "test")
            addConfigField("VERIFIER_API", testVerifierApi)
            addConfigField("ISSUER_URL", testIssuerUrl)
            addConfigField("VERIFIER_CLIENT_ID", testVerifierClientId)
            addConfigField("VERIFIER_LEGAL_NAME", testVerifierLegalName)
            addConfigField("ISSUER_CLIENT_ID", testIssuerClientId)
        }
    }
}

moduleConfig {
    module = LibraryModule.CoreLogic
}

dependencies {
    implementation(project(LibraryModule.ResourcesLogic.path))
    implementation(project(LibraryModule.BusinessLogic.path))
    implementation(project(LibraryModule.StorageLogic.path))
    implementation(project(LibraryModule.AuthenticationLogic.path))
    implementation(project(LibraryModule.NetworkLogic.path))

    implementation(libs.androidx.biometric)

    testImplementation(project(LibraryModule.TestLogic.path))
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.CoreLogic.classes,
    excludedPackages = KoverExclusionRules.CoreLogic.packages,
)