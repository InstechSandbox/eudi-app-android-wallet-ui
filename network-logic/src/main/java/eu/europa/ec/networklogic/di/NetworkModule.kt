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

package eu.europa.ec.networklogic.di

import android.content.Context
import android.net.http.X509TrustManagerExtensions
import android.util.Log
import eu.europa.ec.businesslogic.config.AppBuildType
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.networklogic.R
import eu.europa.ec.networklogic.repository.WalletAttestationRepository
import eu.europa.ec.networklogic.repository.WalletAttestationRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext

@Module
@ComponentScan("eu.europa.ec.networklogic")
class LogicNetworkModule

@Single
fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    isLenient = true
}

fun createWalletCoreHttpClient(context: Context): HttpClient {
    val localAwareTrustManager = createLocalAwareTrustManager(context)
    val sslSocketFactory = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(localAwareTrustManager), SecureRandom())
    }.socketFactory

    return HttpClient(OkHttp) {
        engine {
            config {
                sslSocketFactory(sslSocketFactory, localAwareTrustManager)
            }
        }
    }
}

@Single
fun provideHttpClient(json: Json, configLogic: ConfigLogic, context: Context): HttpClient {
    val localAwareTrustManager = createLocalAwareTrustManager(context)
    val sslSocketFactory = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(localAwareTrustManager), SecureRandom())
    }.socketFactory

    return HttpClient(Android) {

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("KtorHttp", message)
                }
            }
            level = when (configLogic.appBuildType) {
                AppBuildType.DEBUG -> LogLevel.BODY
                AppBuildType.RELEASE -> LogLevel.NONE
            }
        }

        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, request ->
                Log.e(
                    "KtorHttp",
                    "Request failed: ${request.method.value} ${request.url}",
                    cause
                )
            }
        }

        install(ContentNegotiation) {
            json(
                json = json,
                contentType = ContentType.Application.Json
            )
        }

        engine {
            sslManager = { httpsURLConnection ->
                httpsURLConnection.sslSocketFactory = sslSocketFactory
            }
        }
    }
}

private fun createLocalAwareTrustManager(context: Context): X509TrustManager {
    val localCertificate = loadLocalBackendCertificate(context)
    val systemTrustManager = createTrustManager(null)
    val systemTrustManagerExtensions = X509TrustManagerExtensions(systemTrustManager)
    val localTrustManager = createTrustManager(
        KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("local-backend-cert", localCertificate)
        }
    )
    val localTrustManagerExtensions = X509TrustManagerExtensions(localTrustManager)

    return object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                systemTrustManager.checkClientTrusted(chain, authType)
            } catch (_: CertificateException) {
                localTrustManager.checkClientTrusted(chain, authType)
            }
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                systemTrustManager.checkServerTrusted(chain, authType)
            } catch (systemError: CertificateException) {
                val trustedLocalCertificate = chain.firstOrNull()?.encoded?.contentEquals(localCertificate.encoded) == true
                if (trustedLocalCertificate) {
                    return
                }

                try {
                    localTrustManager.checkServerTrusted(chain, authType)
                } catch (_: CertificateException) {
                    throw systemError
                }
            }
        }

        @Suppress("unused")
        fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, host: String): List<X509Certificate> {
            return try {
                systemTrustManagerExtensions.checkServerTrusted(chain, authType, host)
            } catch (systemError: CertificateException) {
                val trustedLocalCertificate = chain.firstOrNull()?.encoded?.contentEquals(localCertificate.encoded) == true
                if (trustedLocalCertificate) {
                    chain.toList()
                } else {
                    try {
                        localTrustManagerExtensions.checkServerTrusted(chain, authType, host)
                    } catch (_: CertificateException) {
                        throw systemError
                    }
                }
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            systemTrustManager.acceptedIssuers + localTrustManager.acceptedIssuers
    }
}

private fun loadLocalBackendCertificate(context: Context): X509Certificate =
    context.resources.openRawResource(R.raw.backend_cert).use { inputStream ->
        CertificateFactory.getInstance("X.509").generateCertificate(inputStream) as X509Certificate
    }

private fun createTrustManager(keyStore: KeyStore?): X509TrustManager {
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(keyStore)
    }

    return trustManagerFactory.trustManagers
        .filterIsInstance<X509TrustManager>()
        .first()
}

@Single
fun provideWalletAttestationRepository(httpClient: HttpClient): WalletAttestationRepository =
    WalletAttestationRepositoryImpl(httpClient)
