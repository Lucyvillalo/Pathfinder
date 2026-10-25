package com.example.pathfinder

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** Completes the real Google Credential Manager -> Firebase Authentication flow. */
suspend fun firebaseGoogleSignIn(activity: Activity, auth: FirebaseAuth): FirebaseUser {
    val resourceId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
    check(resourceId != 0) { "Falta google-services.json o el cliente OAuth web de Firebase." }
    val serverClientId = activity.getString(resourceId)
    check(serverClientId.isNotBlank()) { "Firebase no generó el cliente OAuth web." }
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(serverClientId)
        .setFilterByAuthorizedAccounts(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val response = CredentialManager.create(activity).getCredential(activity, request)
    val credential = response.credential
    check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "Google no devolvió una credencial de identidad compatible."
    }
    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
    return auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user
        ?: error("Firebase no devolvió una cuenta autenticada.")
}

@Composable
fun FirebaseAccountPage(
    configured: Boolean,
    user: FirebaseUser?,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Volver") }
        }
        Spacer(Modifier.height(28.dp))
        Text("Cuenta Pathfinder", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(10.dp))
        Text("Tu progreso local funciona sin cuenta. Conecta Google si quieres identificarte con Firebase.", color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(24.dp))
        if (user != null) {
            Text("Sesión iniciada", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(user.displayName ?: user.email ?: "Cuenta Google", color = MaterialTheme.colorScheme.onBackground)
            user.email?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onSignOut, enabled = !busy) { Text("Cerrar sesión") }
        } else {
            Button(onClick = onGoogleSignIn, enabled = configured && !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(15.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Continuar con Google")
            }
            if (!configured) {
                Spacer(Modifier.height(14.dp))
                Text("Esta app aún no está registrada en el proyecto Firebase. El modo local permanece disponible.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
    }
}

fun authErrorMessage(throwable: Throwable): String = when {
    throwable is GetCredentialException -> "No se pudo completar la selección de cuenta Google. Puedes intentarlo de nuevo o seguir en modo local."
    throwable.message?.contains("CONFIGURATION_NOT_FOUND", true) == true -> "La configuración de Firebase todavía no está lista para esta app."
    else -> "No fue posible iniciar sesión con Firebase. ${throwable.localizedMessage ?: "Inténtalo más tarde."}"
}
