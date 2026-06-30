package hu.szabonorbert.mokusors.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val uid: String, val email: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState

    private var authListener: FirebaseAuth.AuthStateListener? = null

    init {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _authState.value = if (user != null) {
                AuthState.LoggedIn(user.uid, user.email ?: "")
            } else {
                AuthState.LoggedOut
            }
        }
        auth.addAuthStateListener(listener)
        authListener = listener
    }

    fun login(email: String, password: String) {
        _authState.value = AuthState.Loading
        auth.signInWithEmailAndPassword(email, password)
            .addOnFailureListener { e ->
                val msg = when {
                    e.message?.contains("network", ignoreCase = true) == true ||
                    e.message?.contains("timeout", ignoreCase = true) == true ||
                    e.message?.contains("unreachable", ignoreCase = true) == true ->
                        "Hálózati hiba. Ellenőrizd az internetkapcsolatot és próbáld újra."
                    e.message?.contains("password", ignoreCase = true) == true ||
                    e.message?.contains("credential", ignoreCase = true) == true ||
                    e.message?.contains("identifier", ignoreCase = true) == true ->
                        "Hibás email cím vagy jelszó."
                    e.message?.contains("user", ignoreCase = true) == true &&
                    e.message?.contains("found", ignoreCase = true) == true ->
                        "Nem található ilyen felhasználó."
                    e.message?.contains("disabled", ignoreCase = true) == true ->
                        "Ez a fiók le van tiltva."
                    else -> "Bejelentkezési hiba. Próbáld újra."
                }
                _authState.value = AuthState.Error(msg)
            }
    }

    fun logout() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("pushTokens").document(uid).delete()
        }
        auth.signOut()
    }

    override fun onCleared() {
        super.onCleared()
        authListener?.let { auth.removeAuthStateListener(it) }
    }
}
