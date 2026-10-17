package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.AuthenticationManager
import kotlinx.coroutines.launch

@Composable
fun RealAuthScreen(
    onAuthSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var manualFlowOpen by remember { mutableStateOf(false) }
    var manualMode by remember { mutableStateOf("CHOICE") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Automatic silent auto-sign in check on display
    LaunchedEffect(Unit) {
        AuthenticationManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onSuccess = onAuthSuccess,
            onUnauthenticated = {}
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp)
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = "Nexus AI",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Welcome to NexusAI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Extensible AI Assistant with real cloud plugins, secure Firestore storage, and multi-step tool routing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (manualFlowOpen) {
                Text(
                    text = "Log in manually",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                if (manualMode == "CHOICE") {
                    Text(
                        text = "Already have an account, or creating a new one?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { manualMode = "LOGIN"; errorMessage = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !isLoading
                    ) { Text("Log in") }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { manualMode = "CREATE"; errorMessage = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !isLoading
                    ) { Text("Create an account") }
                } else {
                    Text(
                        text = if (manualMode == "LOGIN") "Log in to your account" else "Create your NexusAI account",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (at least 6 characters)") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            errorMessage = null
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                                errorMessage = "Enter a valid email address."
                            } else if (password.length < 6) {
                                errorMessage = "Password must be at least 6 characters."
                            } else {
                                isLoading = true
                                val success = {
                                    isLoading = false
                                    onAuthSuccess()
                                }
                                val failure: (String) -> Unit = { err ->
                                    isLoading = false
                                    errorMessage = err
                                }
                                if (manualMode == "LOGIN") {
                                    AuthenticationManager.signInWithEmailPassword(email, password, scope, success, failure)
                                } else {
                                    AuthenticationManager.createAccountWithEmailPassword(email, password, scope, success, failure)
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (manualMode == "LOGIN") "Log in" else "Create account")
                    }
                    TextButton(
                        onClick = {
                            manualMode = if (manualMode == "LOGIN") "CREATE" else "LOGIN"
                            errorMessage = null
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (manualMode == "LOGIN") "New here? Create an account" else "Already have an account? Log in")
                    }
                }
                TextButton(
                    onClick = {
                        manualFlowOpen = false
                        manualMode = "CHOICE"
                        errorMessage = null
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Back to sign-in options") }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!manualFlowOpen) {
                OutlinedButton(
                    onClick = {
                        manualFlowOpen = true
                        manualMode = "CHOICE"
                        errorMessage = null
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("manual_signin_button")
                ) {
                    Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Log in manually", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Real Google Sign-In Button via Jetpack CredentialManager
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    AuthenticationManager.signInWithGoogle(
                        context = context,
                        credentialManager = credentialManager,
                        scope = scope,
                        onSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onError = { err ->
                            isLoading = false
                            errorMessage = err
                        },
                        onCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("google_signin_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Authenticating...")
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Apple Sign-In OAuth guidance
            OutlinedButton(
                onClick = {
                    errorMessage = "Apple OAuth requires configured APPLE_CLIENT_ID / Service ID in Firebase Auth console."
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("apple_signin_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Continue with Apple",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Guest Mode Button
            OutlinedButton(
                onClick = onContinueAsGuest,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("guest_mode_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PersonOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Continue as Guest",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Guest Mode lets you chat directly with AI models and test tools. Sign in with Google anytime to enable multi-device cloud synchronization.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Secured with Firebase Auth & Cloud Firestore",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
