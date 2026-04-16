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

package eu.europa.ec.presentationfeature.interactor

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.toDomainConfig
import eu.europa.ec.commonfeature.ui.request.model.DocumentPayloadDomain
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.commonfeature.ui.request.transformer.RequestTransformer
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.eudi.iso18013.transfer.response.RequestedDocument
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

sealed class PresentationRequestInteractorPartialState {
    data class Success(
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
        val requestDocuments: List<RequestDocumentItemUi>
    ) : PresentationRequestInteractorPartialState()

    data class NoData(
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
    ) : PresentationRequestInteractorPartialState()

    data class Failure(val error: String) : PresentationRequestInteractorPartialState()
    data object Disconnect : PresentationRequestInteractorPartialState()
}

interface PresentationRequestInteractor {
    fun getRequestDocuments(): Flow<PresentationRequestInteractorPartialState>
    fun stopPresentation()
    fun updateRequestedDocuments(items: List<RequestDocumentItemUi>)
    fun setConfig(config: RequestUriConfig)
}

class PresentationRequestInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val uuidProvider: UuidProvider,
    private val walletCorePresentationController: WalletCorePresentationController,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val logController: LogController,
) : PresentationRequestInteractor {

    companion object {
        private const val TAG = "PresentationRequestInteractor"
    }

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun setConfig(config: RequestUriConfig) {
        walletCorePresentationController.setConfig(config.toDomainConfig())
    }

    override fun getRequestDocuments(): Flow<PresentationRequestInteractorPartialState> =
        walletCorePresentationController.events.mapNotNull { response ->
            when (response) {
                is TransferEventPartialState.RequestReceived -> {
                    if (response.requestData.all { it.requestedItems.isEmpty() }) {
                        logController.w(TAG) {
                            buildString {
                                append("Presentation request yielded no requested items. ")
                                append("verifierName=")
                                append(response.verifierName)
                                append(", verifierIsTrusted=")
                                append(response.verifierIsTrusted)
                                append(", requestDocuments=")
                                append(requestedDocumentsSummary(response.requestData))
                            }
                        }
                        PresentationRequestInteractorPartialState.NoData(
                            verifierName = response.verifierName,
                            verifierIsTrusted = response.verifierIsTrusted,
                        )
                    } else {
                        val issuedDocuments = walletCoreDocumentsController.getAllIssuedDocuments()
                        val issuedDocumentRevocation = issuedDocuments.associate { document ->
                            document.id to walletCoreDocumentsController.isDocumentRevoked(document.id)
                        }
                        val documentsDomain = RequestTransformer.transformToDomainItems(
                            storageDocuments = issuedDocuments,
                            requestDocuments = response.requestData,
                            resourceProvider = resourceProvider,
                            uuidProvider = uuidProvider
                        ).getOrThrow()
                        val revokedDocumentIds = documentsDomain
                            .mapNotNull { document ->
                                document.docId.takeIf { issuedDocumentRevocation[it] == true }
                            }
                        val activeDocuments = documentsDomain
                            .filterNot { it.docId in revokedDocumentIds }

                        if (activeDocuments.isNotEmpty()) {
                            logController.i(TAG) {
                                buildString {
                                    append("Presentation request matched wallet documents. ")
                                    append("verifierName=")
                                    append(response.verifierName)
                                    append(", verifierIsTrusted=")
                                    append(response.verifierIsTrusted)
                                    append(", requestDocuments=")
                                    append(requestedDocumentsSummary(response.requestData))
                                    append(", issuedDocuments=")
                                    append(issuedDocumentsSummary(issuedDocuments, issuedDocumentRevocation))
                                    append(", activeDocuments=")
                                    append(domainDocumentsSummary(activeDocuments))
                                }
                            }
                            PresentationRequestInteractorPartialState.Success(
                                verifierName = response.verifierName,
                                verifierIsTrusted = response.verifierIsTrusted,
                                requestDocuments = RequestTransformer.transformToUiItems(
                                    documentsDomain = activeDocuments,
                                    resourceProvider = resourceProvider,
                                )
                            )
                        } else {
                            logController.w(TAG) {
                                buildString {
                                    append("Presentation request produced no shareable wallet documents. ")
                                    append("verifierName=")
                                    append(response.verifierName)
                                    append(", verifierIsTrusted=")
                                    append(response.verifierIsTrusted)
                                    append(", requestDocuments=")
                                    append(requestedDocumentsSummary(response.requestData))
                                    append(", issuedDocuments=")
                                    append(issuedDocumentsSummary(issuedDocuments, issuedDocumentRevocation))
                                    append(", transformedDocuments=")
                                    append(domainDocumentsSummary(documentsDomain))
                                    append(", revokedDocumentIds=")
                                    append(revokedDocumentIds)
                                }
                            }
                            PresentationRequestInteractorPartialState.NoData(
                                verifierName = response.verifierName,
                                verifierIsTrusted = response.verifierIsTrusted,
                            )
                        }
                    }
                }

                is TransferEventPartialState.Error -> {
                    PresentationRequestInteractorPartialState.Failure(error = response.error)
                }

                is TransferEventPartialState.Disconnected -> {
                    PresentationRequestInteractorPartialState.Disconnect
                }

                else -> null
            }
        }.safeAsync {
            PresentationRequestInteractorPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun stopPresentation() {
        walletCorePresentationController.stopPresentation()
    }

    override fun updateRequestedDocuments(items: List<RequestDocumentItemUi>) {
        val disclosedDocuments = RequestTransformer.createDisclosedDocuments(items)
        walletCorePresentationController.updateRequestedDocuments(disclosedDocuments.toMutableList())
    }

    private fun requestedDocumentsSummary(requestDocuments: List<RequestedDocument>): String =
        requestDocuments.joinToString(prefix = "[", postfix = "]") { requestDocument ->
            val requestedItems = requestDocument.requestedItems.keys
                .joinToString(prefix = "[", postfix = "]") { item -> item.toString() }
            "{documentId=${requestDocument.documentId}, requestedItemCount=${requestDocument.requestedItems.size}, requestedItems=$requestedItems}"
        }

    private fun issuedDocumentsSummary(
        documents: List<IssuedDocument>,
        revocationMap: Map<String, Boolean>,
    ): String =
        documents.joinToString(prefix = "[", postfix = "]") { document ->
            "{documentId=${document.id}, format=${formatSummary(document)}, revoked=${revocationMap[document.id] == true}}"
        }

    private fun domainDocumentsSummary(documents: List<DocumentPayloadDomain>): String =
        documents.joinToString(prefix = "[", postfix = "]") { document ->
            "{documentId=${document.docId}, format=${document.domainDocFormat}, claimGroupCount=${document.docClaimsDomain.size}}"
        }

    private fun formatSummary(document: IssuedDocument): String =
        when (val format = document.format) {
            is SdJwtVcFormat -> "sd-jwt:${format.vct}"
            is MsoMdocFormat -> "mdoc:${format.docType}"
        }
}