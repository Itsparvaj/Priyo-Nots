package com.example.data.drive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object GoogleDriveAuthHelper {

    private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
    private const val SCOPE_PROFILE = "profile"
    private const val SCOPE_OPENID = "openid"

    private val driveScopes = listOf(
        Scope(SCOPE_DRIVE_FILE),
        Scope(SCOPE_USERINFO_EMAIL),
        Scope(SCOPE_PROFILE),
        Scope(SCOPE_OPENID)
    )

    /**
     * Requests Google Drive OAuth2 authorization directly via Google Play Services AuthorizationClient.
     * Avoids Credential Manager's pre-step which can fail or cancel prematurely on certain OEM devices
     * and shows Google's standard account picker + Drive consent sheet reliably.
     */
    fun connectAndAuthorize(
        activity: Activity,
        onNeedResolution: (IntentSenderRequest) -> Unit,
        onAuthorized: (accessToken: String, email: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(driveScopes)
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(authRequest)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent != null) {
                        onNeedResolution(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        onError("Google Drive consent screen could not be opened on this device.")
                    }
                } else {
                    val token = authResult.accessToken
                    if (token.isNullOrBlank()) {
                        onError("Google Drive did not return an access token.")
                    } else {
                        onAuthorized(token, null)
                    }
                }
            }
            .addOnFailureListener { exc ->
                onError(describeAuthException(activity, exc))
            }
    }

    fun extractTokenFromConsentResult(
        context: Context,
        resultCode: Int,
        data: Intent?
    ): Result<String> {
        if (resultCode == Activity.RESULT_CANCELED && data == null) {
            return Result.failure(
                IllegalStateException(
                    "Google Drive sign-in was cancelled or dismissed. You can still back up to Google Drive using 'Save via Drive Picker' below."
                )
            )
        }
        return runCatching {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (token.isNullOrBlank()) {
                error("Google Drive permission was not granted.")
            }
            token
        }.recoverCatching { throwable ->
            throw IllegalStateException(describeAuthException(context, throwable))
        }
    }

    suspend fun clearGoogleSession(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                Identity.getSignInClient(context).signOut()
            }
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }

    fun getSigningSha1(context: Context): String? {
        return runCatching {
            val pm = context.packageManager
            val sigBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = info.signingInfo ?: return null
                val signers = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signers?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            } ?: return null

            val digest = MessageDigest.getInstance("SHA-1").digest(sigBytes)
            digest.joinToString(":") { "%02X".format(it) }
        }.getOrNull()
    }

    private fun describeAuthException(context: Context, throwable: Throwable): String {
        return when (throwable) {
            is ApiException -> when (throwable.statusCode) {
                CommonStatusCodes.CANCELED ->
                    "Google Drive sign-in was cancelled. If your device blocks in-app sign-in, use 'Save via Drive Picker' below."
                CommonStatusCodes.NETWORK_ERROR ->
                    "Network error while connecting to Google Drive. Check your internet connection."
                CommonStatusCodes.DEVELOPER_ERROR -> {
                    val sha1 = getSigningSha1(context) ?: "Unavailable"
                    "Google Drive OAuth client mismatch (Error 10). Add this APK's SHA-1 ($sha1) for package '${context.packageName}' in Google Cloud Console, or use 'Save via Drive Picker' below."
                }
                else ->
                    throwable.localizedMessage ?: "Google Drive sign-in error (${throwable.statusCode})"
            }
            else -> throwable.localizedMessage ?: "Failed to authorize Google Drive"
        }
    }
}
