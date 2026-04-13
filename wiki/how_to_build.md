# Building the Reference apps to interact with issuing and verifying services.
## Table of contents
* [Overview](#overview)
* [Setup Apps](#setup-apps)
* [How to work with self signed certificates](#how-to-work-with-self-signed-certificates)
## Overview
This guide aims to assist developers in building the Android Wallet application.

## Setup Apps
### EUDI Android Wallet reference application
You need [Android Studio](https://developer.android.com/studio) and its associated tools installed on your machine. We recommend the latest stable version.
Clone the [Android repository](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui)
Open the project in Android Studio.

The application has two product flavors:
- "Dev", which is the local engineering build in this workspace and should target the local issuer and local verifier hosts on your LAN.
- "Demo", which is the shared test build in this workspace and should target the public cloud issuer and verifier hosts.

and two Build Types:
- "Debug", which has full logging enabled.
- "Release", which has no logging enabled.

For the Instech cloud-build workflow, treat the flavors like this:

- `devDebug` or `devRelease`: local-device and local-reader validation against the local stack
- `demoDebug`: shared-cloud smoke build for ad hoc tester installs
- `demoRelease`: GitHub-driven cloud tester build for publication through GitHub Releases

The launcher labels are intentionally different so both variants can stay installed on one device:

- Dev installs as `EUDI Wallet Local`
- Demo installs as `EUDI Wallet Test`

which, ultimately, result in the following Build Variants:

- "devDebug", "devRelease", "demoDebug", "demoRelease".

To change the Build Variant, go to Build -> Select Build Variant and from the tool window you can click on the "Active Build Variant" of the module ":app" and select the one you prefer.
It will automatically apply it to the other modules as well.

### One-click clean, build, and install from Android Studio

The project now exposes Gradle wrapper tasks that clean the workspace, build the APK, and install it on a connected device or emulator using adb:

- `buildAndInstallDevDebug`
- `buildAndInstallDemoDebug`

In Android Studio, open the Gradle tool window and run either task from the `install` group.
The repository also includes a shared run configuration named `Build and Install Dev Debug` under `.run/`, so the default development flow is available directly from the run configuration picker.

Android Studio Gradle sync itself is still an IDE action rather than a Gradle task. Opening the project or refreshing the Gradle project performs the sync before running these tasks.

For local phone testing in this workspace, the default install command is:

```bash
LOCAL_DEMO_HOST="$(ipconfig getifaddr en0 || ipconfig getifaddr en1)" ./gradlew buildAndInstallDevDebug --console=plain
```

That builds the local `Dev` wallet, points it at the current LAN-backed local stack, and installs it without replacing the cloud `Demo` app.

### Manual APK install troubleshooting

If Android Studio installs fail or you want to sideload a specific build manually, you can assemble the APK and install it with `adb`.

For example, for the `demoDebug` variant:

```bash
./gradlew :app:assembleDemoDebug
adb devices
adb -s <device-serial> install -r -d app/build/outputs/apk/demo/debug/app-demo-debug.apk
```

For the `devDebug` variant, use:

```bash
./gradlew :app:assembleDevDebug
adb devices
adb -s <device-serial> install -r -d app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

Notes:
- Replace `<device-serial>` with the serial shown by `adb devices`.
- If `adb` is not on your `PATH`, use the full path to the Android SDK platform-tools `adb` binary instead.
- The `-r` flag reinstalls the app while keeping app data when possible, and `-d` allows version-code downgrade for local debug builds.

To run the App on a device, firstly you must connect your device with the Android Studio, and then go to Run -> Run 'app'.
To run the App on an emulator, simply go to Run -> Run 'app'.

### Running with remote services
The app is configured to use some configuration in the two ***ConfigWalletCoreImpl.kt*** files (located in the "**core-logic**" module, in either
*src\dev\java\eu\europa\ec\corelogic\config* or
*src\demo\java\eu\europa\ec\corelogic\config*,
depending on the flavor of your choice).

For document readers and verifier flows, the environment split matters:

- local reader and verifier requests are environment-bound to the `Dev` flavor build because that build bakes the current LAN verifier and issuer URLs into `BuildConfig`
- shared cloud reader and verifier requests are environment-bound to the `Demo` flavor build because that build bakes the public `test.instech-eudi-poc.com` hosts into `BuildConfig`
- do not use the local `Dev` APK against the public verifier or issuer, and do not use the cloud `Demo` APK against the local verifier stack, because the preregistered verifier and redirect expectations are not interchangeable

These are the contents of the ConfigWalletCoreImpl file (dev flavor), and you don't need to change anything:

```kotlin
override val issuersConfig: List<VciConfig>
    get() = listOf(
       VciConfig(
          config = OpenId4VciManager.Config.Builder()
             .withIssuerUrl(issuerUrl = "https://ec.dev.issuer.eudiw.dev")
             .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.AttestationBased)
             .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
             .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
             .withDPopConfig(DPopConfig.Default)
             .build(),
          order = 0
       )
)
```

### Running with local services
The first step here is to have the issuer and verifier services running locally. In the Instech cloud-build workspace, use the shared wrapper flow instead of hand-editing wallet source files:

```bash
cd "$CODE_ROOT/project-docs/scripts"
./build-local-all.sh
./start-local-all.sh
./smoke-local-all.sh
./install-wallet-local-apk.sh --fresh
```

Do not hand-edit `ConfigWalletCoreImpl.kt` for the normal local-versus-cloud switch in this workspace. The `Dev` and `Demo` flavors already carry that environment split.

## How to work with self-signed certificates

This section describes configuring the application to interact with services utilizing self-signed certificates.

*To enable support for self-signed certificates, you must customize the existing Ktor `HttpClient`
used by the application.*

1. Open the `NetworkModule.kt` file of the `network-logic` module.
2. Add the following imports:

    ```kotlin
    import android.annotation.SuppressLint
    import java.security.SecureRandom
    import javax.net.ssl.HostnameVerifier
    import javax.net.ssl.SSLContext
    import javax.net.ssl.TrustManager
    import javax.net.ssl.X509TrustManager
    import javax.security.cert.CertificateException
    ```

3. Replace the `provideHttpClient` function with the following:

    ```kotlin
    @SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    @Single
    fun provideHttpClient(json: Json): HttpClient {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {
                }
    
                @Throws(CertificateException::class)
                override fun checkServerTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {
                }
    
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                    return arrayOf()
                }
            }
        )
    
        return HttpClient(Android) {
            install(Logging)
            install(ContentNegotiation) {
                json(
                    json = json,
                    contentType = ContentType.Application.Json
                )
            }
            engine {
                requestConfig
                sslManager = { httpsURLConnection ->
                    httpsURLConnection.sslSocketFactory = SSLContext.getInstance("TLS").apply {
                        init(null, trustAllCerts, SecureRandom())
                    }.socketFactory
                    httpsURLConnection.hostnameVerifier = HostnameVerifier { _, _ -> true }
                }
            }
        }
    }
    ```

4. Finally, you need to use the preregistered clientId scheme instead of X509.
   
   Change this:

   ```kotlin
   withClientIdSchemes(
    listOf(ClientIdScheme.X509SanDns)
   )
    ```
   
   into something like this:

   ```kotlin
   withClientIdSchemes(
    listOf(
        ClientIdScheme.Preregistered(
            preregisteredVerifiers =
                listOf(
                    PreregisteredVerifier(
                        clientId = "Verifier",
                        legalName = "Verifier",
                        verifierApi = "https://10.0.2.2"
                    )
                )
            )
        )
   )
   ```

   For all configuration options, please refer to [this document](configuration.md)
