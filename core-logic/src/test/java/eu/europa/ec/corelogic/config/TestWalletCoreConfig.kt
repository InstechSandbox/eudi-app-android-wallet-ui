/*
 * Copyright (c) 2026 European Commission
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

package eu.europa.ec.corelogic.config

import android.content.Context
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.eudi.wallet.document.CreateDocumentSettings.CredentialPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class TestWalletCoreConfig {

    private val context: Context = mock()

    @Test
    fun `SdJwt PID stays reusable while mdoc PID keeps the pooled one-time-use rule`() {
        val config = WalletCoreConfigImpl(context)

        val mdocRule = config.documentIssuanceConfig.getRuleForDocument(DocumentIdentifier.MdocPid)
        val sdJwtRule = config.documentIssuanceConfig.getRuleForDocument(DocumentIdentifier.SdJwtPid)

        assertEquals(CredentialPolicy.OneTimeUse, mdocRule.policy)
        assertEquals(60, mdocRule.numberOfCredentials)
        assertEquals(CredentialPolicy.RotateUse, sdJwtRule.policy)
        assertEquals(1, sdJwtRule.numberOfCredentials)
    }
}