package uk.co.rodrunners.raffles.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uk.co.rodrunners.raffles.core.Collections

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(
        val uid: String,
        val emailVerified: Boolean,
        val isAdmin: Boolean,
        val email: String? = null,
    ) : AuthState
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    val currentUid: String? get() = auth.currentUser?.uid
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits on every sign-in, sign-out and token refresh. */
    fun authState(): Flow<AuthState> = callbackFlow {
        trySend(AuthState.Loading)
        val listener = FirebaseAuth.AuthStateListener { fa ->
            val user = fa.currentUser
            if (user == null) {
                trySend(AuthState.SignedOut)
            } else {
                user.getIdToken(false).addOnSuccessListener { token ->
                    trySend(
                        AuthState.SignedIn(
                            uid = user.uid,
                            emailVerified = user.isEmailVerified,
                            isAdmin = token.claims["admin"] == true,
                            email = user.email,
                        )
                    )
                }.addOnFailureListener {
                    trySend(AuthState.SignedIn(user.uid, user.isEmailVerified, false, user.email))
                }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    /**
     * Registration writes a one-shot signup document; a Firestore trigger builds
     * the real profile server-side, so the client never sets fields like
     * `suspended` or `referralCode` itself.
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        ageConfirmed: Boolean,
        marketingOptIn: Boolean,
        referredBy: String?,
    ) {
        require(ageConfirmed) { "You must confirm you're 18 or over to register." }
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Registration failed. Try again.")
        user.updateProfile(userProfileChangeRequest { this.displayName = displayName }).await()
        db.collection(Collections.USER_SIGNUPS).document(user.uid).set(
            mapOf(
                "email" to email.trim(),
                "displayName" to displayName.trim(),
                "ageConfirmed" to true,
                "marketingOptIn" to marketingOptIn,
                "referredBy" to referredBy?.trim()?.uppercase(),
            )
        ).await()
        user.sendEmailVerification().await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun resendVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    suspend fun reloadUser(): Boolean {
        auth.currentUser?.reload()?.await()
        return auth.currentUser?.isEmailVerified == true
    }

    /** Required before changing password, email or deleting the account. */
    suspend fun reauthenticate(password: String) {
        val user = auth.currentUser ?: error("You're not logged in.")
        val email = user.email ?: error("This account has no email address.")
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).await()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        reauthenticate(currentPassword)
        auth.currentUser?.updatePassword(newPassword)?.await()
    }

    fun signOut() = auth.signOut()
}
