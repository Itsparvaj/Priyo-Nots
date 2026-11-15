package com.example.data.drive

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleDriveAuthHelper {

    private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"

    private val driveScopes = listOf(
        Scope(SCOPE_DRIVE_FILE),
        Scope(SCOPE_USERINFO_EMAIL)
    )

    /**
     * Prompts the user with Google account selection via Credential Manager if requested,
     * then requests Google Drive OAuth2 authorization via AuthorizationClient.
     */
    suspend fun connectAndAuthorize(
        activity: Activity,
        promptAccountSelection: Boolean,
        onNeedResolution: (IntentSenderRequest) -> Unit,
        onAuthorized: (accessToken: String, email: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        var discoveredEmail: String? = null

        if (promptAccountSelection) {
            val webClientId = activity.getString(R.string.google_web_client_id)
            if (webClientId.isNotBlank()) {
                val signInOutcome = runCatching {
                    val credentialManager = CredentialManager.create(activity)
                    val googleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleOption)
                        .build()
                    val result = credentialManager.getCredential(activity, request)
                    val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
                    googleCred.id
                }
                signInOutcome.onSuccess { email ->
                    discoveredEmail = email
                }.onFailure { err ->
                    if (err is GetCredentialCancellationException) {
                        onError("Google sign-in cancelled.")
                        return
                    }
                    // Continue to AuthorizationClient below even if Credential Manager falls back
                }
            }
        }

        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(driveScopes)
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(authRequest)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent != null) {
                        onNeedResolution(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    } else {
                        onError("Google Drive authorization requires user consent, but no consent dialog was returned.")
                    }
                } else {
                    val token = authResult.accessToken
                    if (token.isNullOrBlank()) {
                        onError("Google Drive did not return an access token.")
                    } else {
                        onAuthorized(token, discoveredEmail)
                    }
                }
            }
            .addOnFailureListener { exc ->
                onError(describeAuthException(exc))
            }
    }

    fun extractTokenFromConsentResult(
        context: Context,
        data: Intent?
    ): Result<String> {
        return runCatching {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (token.isNullOrBlank()) {
                error("Google Drive permission was not granted.")
            }
            token
        }.recoverCatching { throwable ->
            throw IllegalStateException(describeAuthException(throwable))
        }
    }

    suspend fun clearGoogleSession(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }

    private fun describeAuthException(throwable: Throwable): String {
        return when (throwable) {
            is ApiException -> when (throwable.statusCode) {
                CommonStatusCodes.CANCELED -> "Google Drive authorization was cancelled."
                CommonStatusCodes.NETWORK_ERROR -> "Network error while connecting to Google Drive."
                CommonStatusCodes.DEVELOPER_ERROR ->
                    "Google Drive OAuth configuration mismatch (DEVELOPER_ERROR: 10). Register this APK's SHA-1 signing fingerprint for package 'com.aistudio.priyonots.mprvj' in Google Cloud Console, or use 'Choose File' / 'Select Folder' to save directly to Google Drive via Android's file picker."
                else -> throwable.localizedMessage ?: "Google Drive sign-in error (${throwable.statusCode})"
            }
            else -> throwable.localizedMessage ?: "Failed to authorize Google Drive"
        }
    }
}
