package eu.europa.ec.corelogic.authorization

import android.content.Context
import android.content.Intent
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationHandler
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationResponse
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

class LocalIssuerAuthorizationHandler(
    context: Context,
    private val redirectUri: String,
) : AuthorizationHandler {

    private val appContext = context.applicationContext

    override suspend fun authorize(authorizationUrl: String): Result<AuthorizationResponse> =
        suspendCancellableCoroutine { continuation ->
            val sessionId = UUID.randomUUID().toString()
            LocalIssuerAuthorizationCoordinator.register(sessionId, continuation)
            continuation.invokeOnCancellation {
                LocalIssuerAuthorizationCoordinator.clear(sessionId)
            }

            appContext.startActivity(
                Intent(appContext, LocalIssuerAuthorizationActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(LocalIssuerAuthorizationActivity.EXTRA_SESSION_ID, sessionId)
                    putExtra(LocalIssuerAuthorizationActivity.EXTRA_AUTHORIZATION_URL, authorizationUrl)
                    putExtra(LocalIssuerAuthorizationActivity.EXTRA_REDIRECT_URI, redirectUri)
                }
            )
        }
}

internal object LocalIssuerAuthorizationCoordinator {
    private val lock = Any()
    private var pending: PendingAuthorization? = null

    fun register(
        sessionId: String,
        continuation: CancellableContinuation<Result<AuthorizationResponse>>,
    ) {
        synchronized(lock) {
            pending?.continuation?.resume(
                Result.failure(IllegalStateException("Authorization flow was replaced by a new request."))
            )
            pending = PendingAuthorization(sessionId, continuation)
        }
    }

    fun complete(sessionId: String, result: Result<AuthorizationResponse>) {
        val continuation = synchronized(lock) {
            val current = pending ?: return
            if (current.sessionId != sessionId) {
                return
            }
            pending = null
            current.continuation
        }
        continuation.resume(result)
    }

    fun clear(sessionId: String) {
        synchronized(lock) {
            if (pending?.sessionId == sessionId) {
                pending = null
            }
        }
    }

    private data class PendingAuthorization(
        val sessionId: String,
        val continuation: CancellableContinuation<Result<AuthorizationResponse>>,
    )
}