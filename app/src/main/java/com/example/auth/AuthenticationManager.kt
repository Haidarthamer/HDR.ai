package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object AuthenticationManager {
    private const val TAG = "AuthManager"

    private val auth: FirebaseAuth?
        get() = try {
            Firebase.auth
        } catch (e: Throwable) {
            null
        }

    private val _currentUserFlow = MutableStateFlow<com.google.firebase.auth.FirebaseUser?>(null)
    val currentUserFlow: StateFlow<com.google.firebase.auth.FirebaseUser?> = _currentUserFlow.asStateFlow()

    private val _isGuestMode = MutableStateFlow(false)
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    fun setGuestMode(enabled: Boolean) {
        _isGuestMode.value = enabled
    }

    private fun getWebClientId(context: Context): String? = try {
        val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resourceId == 0) null else context.getString(resourceId)
    } catch (e: Exception) {
        Log.w(TAG, "Unable to read default_web_client_id: ${e.message}")
        null
    }

    init {
        try {
            auth?.addAuthStateListener { firebaseAuth ->
                _currentUserFlow.value = firebaseAuth.currentUser
            }
            _currentUserFlow.value = auth?.currentUser
        } catch (e: Throwable) {
            Log.d(TAG, "Firebase Auth not ready or in test environment")
        }
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSuccess: () -> Unit = {},
        onUnauthenticated: () -> Unit = {}
    ) {
        val authInstance = auth
        if (authInstance?.currentUser != null) {
            onSuccess()
            return
        }

        val clientId = getWebClientId(context)
        if (clientId.isNullOrBlank()) {
            Log.w(TAG, "Missing default_web_client_id; configure google-services.json to enable Google sign-in")
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    authInstance?.signInWithCredential(authCredential)?.await()
                    onSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Silent auto-sign-in inactive: ${e.message}")
                onUnauthenticated()
            }
        }
    }

    /**
     * Firebase Auth and Google Credential Manager require configuration generated from
     * app/google-services.json. Return a user-facing explanation instead of masking the
     * missing configuration as an unexplained FirebaseAuth initialization failure.
     */
    private fun getGoogleSignInConfigurationError(context: Context): String? {
        val defaultAppInitialized = try {
            FirebaseApp.getInstance()
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Default Firebase app is not configured", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unable to check Firebase configuration", e)
            false
        }

        if (!defaultAppInitialized) {
            return "Google Sign-In is not configured in this APK. The Firebase project config is missing. Continue as Guest for now; the app needs app/google-services.json and a new build."
        }

        if (getWebClientId(context).isNullOrBlank()) {
            return "Google Sign-In setup is incomplete: the Web Client ID is missing. Add the Firebase config for package com.aistudio.nexusai.qkzvpx and rebuild."
        }

        return null
    }

    fun signInWithGoogle(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        getGoogleSignInConfigurationError(context)?.let { configurationError ->
            onError(configurationError)
            return
        }

        val authInstance = auth
        if (authInstance == null) {
            onError("Firebase Authentication couldn't start. Check the Firebase app configuration and rebuild.")
            return
        }

        val clientId = getWebClientId(context)
        if (clientId.isNullOrBlank()) {
            onError("Google Sign-In setup is incomplete: Web Client ID missing from app/google-services.json.")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context as Activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    authInstance.signInWithCredential(authCredential).await()
                    onSuccess()
                } else {
                    onError("Unrecognized credential type")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
                onCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onError(e.localizedMessage ?: "Sign in failed")
            }
        }
    }

    /**
     * Email/password authentication uses Firebase Auth so passwords are never stored locally
     * by NexusAI. This requires a configured Firebase project with Email/Password enabled.
     */
    fun signInWithEmailPassword(
        email: String,
        password: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val authInstance = auth
        if (authInstance == null) {
            onError("Account login is not configured yet. Firebase setup is required. You can continue as Guest for now.")
            return
        }
        scope.launch {
            try {
                authInstance.signInWithEmailAndPassword(email.trim(), password).await()
                _currentUserFlow.value = authInstance.currentUser
                _isGuestMode.value = false
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Email/password sign-in failed", e)
                onError(friendlyEmailAuthError(e, creatingAccount = false))
            }
        }
    }

    fun createAccountWithEmailPassword(
        email: String,
        password: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val authInstance = auth
        if (authInstance == null) {
            onError("Account creation is not configured yet. Firebase setup is required. You can continue as Guest for now.")
            return
        }
        scope.launch {
            try {
                authInstance.createUserWithEmailAndPassword(email.trim(), password).await()
                _currentUserFlow.value = authInstance.currentUser
                _isGuestMode.value = false
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Email/password account creation failed", e)
                onError(friendlyEmailAuthError(e, creatingAccount = true))
            }
        }
    }

    private fun friendlyEmailAuthError(error: Exception, creatingAccount: Boolean): String {
        val code = (error as? com.google.firebase.auth.FirebaseAuthException)?.errorCode.orEmpty().lowercase()
        val message = error.localizedMessage.orEmpty().lowercase()
        return when {
            "email-already-in-use" in code || "email address is already in use" in message ->
                "An account with this email already exists. Choose Log in instead."
            "weak-password" in code || "password is invalid" in message ->
                "Choose a stronger password with at least 6 characters."
            "invalid-email" in code ->
                "Enter a valid email address."
            "user-not-found" in code || "wrong-password" in code || "invalid-credential" in code ->
                "Email or password is incorrect. If you don't have an account, choose Create an account."
            "network" in message ->
                "Network error. Check your internet connection and try again."
            else -> if (creatingAccount) {
                "Couldn't create your account. Check Firebase Email/Password sign-in is enabled, then try again."
            } else {
                "Couldn't sign in. Check Firebase configuration and your account details, then try again."
            }
        }
    }

    fun signOut(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onComplete: () -> Unit = {}
    ) {
        _isGuestMode.value = false
        try {
            auth?.signOut()
        } catch (e: Throwable) {
            Log.w(TAG, "signOut error", e)
        }
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing credential state", e)
            } finally {
                onComplete()
            }
        }
    }
}
