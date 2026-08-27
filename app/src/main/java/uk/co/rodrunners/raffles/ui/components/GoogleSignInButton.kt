package uk.co.rodrunners.raffles.ui.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.R

/**
 * "Continue with Google" using Credential Manager. The web client id comes from
 * google-services.json, so this only works once the Google provider is enabled
 * in the Firebase console and the signing SHA-1 has been registered there.
 */
@Composable
fun GoogleSignInButton(
    onIdToken: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    text: String = "Continue with Google",
    enabled: Boolean = true,
) {
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { CredentialManager.create(context) }
    var busy by remember { mutableStateOf(false) }

    OutlineButton(
        text = text,
        onClick = {
            if (busy) return@OutlineButton
            busy = true
            scope.launch {
                try {
                    val option = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(context.getString(R.string.default_web_client_id))
                        .setAutoSelectEnabled(false)
                        .build()
                    val response = manager.getCredential(
                        context = context,
                        request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
                    )
                    val credential = response.credential
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        onIdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                    } else {
                        onError("That Google account could not be used. Try another.")
                    }
                } catch (_: GetCredentialCancellationException) {
                    // The user backed out - not an error worth showing.
                } catch (e: Exception) {
                    onError(e.message ?: "Google sign-in is unavailable right now.")
                } finally {
                    busy = false
                }
            }
        },
        modifier = modifier,
        enabled = enabled && !busy,
    )
}
