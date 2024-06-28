package com.firebase.unlam.applibros.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.firebase.unlam.applibros.R
import com.firebase.unlam.applibros.databinding.ActivityRegisterBinding
import com.firebase.unlam.applibros.models.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var binding: ActivityRegisterBinding
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        configureGoogleSignIn()

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            signInWithEmail(email, password)
        }

        binding.googleLoginButton.setOnClickListener {
            signInWithGoogle()
        }

        binding.registerTextView.setOnClickListener {
            navigateToRegistrationActivity()
        }
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun signInWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    updateUI(auth.currentUser)
                } else {
                    Toast.makeText(this, "Error de inicio de sesión: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.let { firebaseUser ->
                        val firestore = FirebaseFirestore.getInstance()
                        val userRef = firestore.collection("users").document(firebaseUser.uid)
                        userRef.get()
                            .addOnSuccessListener { document ->
                                if (!document.exists()) {
                                    // El usuario no existe, crear uno nuevo
                                    val newUser = User(
                                        id = firebaseUser.uid,
                                        name = firebaseUser.displayName ?: "",
                                        email = firebaseUser.email ?: "",
                                        role = "user",
                                        birthDate = "",
                                        gender = "",
                                        preferences = emptyList()
                                    )
                                    saveUserToFirestore(newUser)
                                } else {
                                    // El usuario ya existe, actualizar solo los campos necesarios
                                    val existingUser = document.toObject(User::class.java)
                                    val updatedUser = existingUser?.copy(
                                        name = firebaseUser.displayName ?: existingUser.name,
                                        email = firebaseUser.email ?: existingUser.email
                                    )
                                    userRef.set(updatedUser!!)
                                        .addOnSuccessListener {
                                            checkAndUpdateProfile(updatedUser)
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(this, "Error al actualizar usuario: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                    }
                } else {
                    Toast.makeText(this, "Error de autenticación con Google: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserToFirestore(user: User) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("users").document(user.id).set(user)
            .addOnSuccessListener {
                checkAndUpdateProfile(user)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar usuario: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkAndUpdateProfile(user: User) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("users").document(user.id).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val userProfile = document.toObject(User::class.java)
                    if (userProfile != null && userProfile.name.isNotEmpty() && userProfile.birthDate.isNotEmpty() && userProfile.gender.isNotEmpty()) {
                        navigateToMainActivity()
                    } else {
                        navigateToProfileFragment()
                    }
                } else {
                    navigateToProfileFragment()
                }
            }
            .addOnFailureListener {
                navigateToProfileFragment()
            }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToProfileFragment() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to_profile", true)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToRegistrationActivity() {
        val intent = Intent(this, RegistrationActivity::class.java)
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Toast.makeText(this, "Error de inicio de sesión con Google: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(user: FirebaseUser?) {
        user?.let {
            navigateToMainActivity()
        }
    }
}