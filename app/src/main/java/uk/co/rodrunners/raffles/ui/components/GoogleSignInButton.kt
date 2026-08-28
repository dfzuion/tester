package uk.co.rodrunners.raffles.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.rodrunners.raffles.R
import uk.co.rodrunners.raffles.ui.theme.Dimens
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

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

    GoogleButton(
        text = text,
        onClick = {
            if (busy) return@OutlineButton
            busy = true
            scope.launch {
                try {
                    // google-services.json only carries a web client id once the
                    // Google provider is enabled in the Firebase console, so the
                    // resource may genuinely not exist. Look it up by name rather
                    // than by R reference, and fail with something a human can act on.
                    val resId = context.resources.getIdentifier(
                        "default_web_client_id", "string", context.packageName,
                    )
                    if (resId == 0) {
                        onError("Google sign-in isn't set up for this build yet.")
                        busy = false
                        return@launch
                    }
                    val option = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(context.getString(resId))
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

/**
 * Google's sign-in button, to Google's own spec: their four-colour mark on a
 * white surface, Roboto-weight label, mark left of centred text. The app's own
 * khaki-and-bronze styling deliberately stops at this button - a recoloured
 * Google mark is both against their brand terms and a thing people have learnt
 * to distrust on a login screen.
 */
@Composable
private fun GoogleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color(0xFFE3E3E3),
            disabledContentColor = Color(0xFF9A9A9A),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_google),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
        }
    }
}
