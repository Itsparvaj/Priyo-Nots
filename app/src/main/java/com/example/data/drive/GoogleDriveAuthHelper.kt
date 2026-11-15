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

data class GoogleDriveAuthError(
    val message: String,
    val isConsoleRegistrationIssue: Boolean = false,
    val appSha1: String? = null,
    val packageName: String = ""
)

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

    fun connectAndAuthorize(
        activity: Activity,
        onNeedResolution: (IntentSenderRequest) -> Unit,
        onAuthorized: (accessToken: String, email: String?) -> Unit,
        onError: (GoogleDriveAuthError) -> Unit
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
                        onError(
                            GoogleDriveAuthError(
                                "Google Drive consent screen could not be opened on this device."
                            )
                        )
                    }
                } else {
                    val token = authResult.accessToken
                    if (token.isNullOrBlank()) {
                        onError(
                            GoogleDriveAuthError("Google Drive did not return an access token.")
                        )
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
                GoogleAuthFailureException(
                    GoogleDriveAuthError(
                        "Google Drive sign-in was cancelled. You can use 'Save via Drive Picker' to back up directly."
                    )
                )
            )
        }
        return try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (token.isNullOrBlank()) {
                Result.failure(
                    GoogleAuthFailureException(
                        GoogleDriveAuthError("Google Drive permission was not granted.")
                    )
                )
            } else {
                Result.success(token)
            }
        } catch (throwable: Throwable) {
            Result.failure(GoogleAuthFailureException(describeAuthException(context, throwable)))
        }
    }

    class GoogleAuthFailureException(val authError: GoogleDriveAuthError) :
        IllegalStateException(authError.message)

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

    private fun describeAuthException(context: Context, throwable: Throwable): GoogleDriveAuthError {
        val rawMessage = throwable.localizedMessage.orEmpty()
        val isUnregisteredMsg = rawMessage.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true)

        if (throwable is ApiException) {
            when (throwable.statusCode) {
                CommonStatusCodes.CANCELED -> {
                    return GoogleDriveAuthError(
                        "Google Drive sign-in was cancelled. You can use 'Save via Drive File Picker' instead."
                    )
                }
                CommonStatusCodes.NETWORK_ERROR -> {
                    return GoogleDriveAuthError(
                        "Network error while connecting to Google Drive. Check your internet connection."
                    )
                }
                CommonStatusCodes.DEVELOPER_ERROR, CommonStatusCodes.INTERNAL_ERROR -> {
                    if (throwable.statusCode == CommonStatusCodes.DEVELOPER_ERROR || isUnregisteredMsg) {
                        val sha1 = getSigningSha1(context)
                        return GoogleDriveAuthError(
                            message = "In-app Google Drive OAuth needs this APK's SHA-1 registered under Cloud Console Android Clients & Drive API enabled.",
                            isConsoleRegistrationIssue = true,
                            appSha1 = sha1,
                            packageName = context.packageName
                        )
                    }
                }
            }
        }

        if (isUnregisteredMsg) {
            val sha1 = getSigningSha1(context)
            return GoogleDriveAuthError(
                message = "In-app Google Drive OAuth needs this APK's SHA-1 registered under Cloud Console Android Clients & Drive API enabled.",
                isConsoleRegistrationIssue = true,
                appSha1 = sha1,
                packageName = context.packageName
            )
        }

        return GoogleDriveAuthError(
            rawMessage.ifBlank { "Failed to connect to Google Drive." }
        )
    }
}
