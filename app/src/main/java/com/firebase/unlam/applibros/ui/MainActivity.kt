package com.firebase.unlam.applibros.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.firebase.unlam.applibros.R
import com.firebase.unlam.applibros.databinding.ActivityMainBinding
import com.firebase.unlam.applibros.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var navController: NavController
    private lateinit var remoteConfig: FirebaseRemoteConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        remoteConfig = FirebaseRemoteConfig.getInstance()

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        setupActionBarWithNavController(navController)
        binding.bottomNavigationView.setupWithNavController(navController)

        // Chequear rol y setear visibilidad del botón de carga de libros
        checkUserRole(navController)

        // Chequear si el perfil del usuario está completo
        checkUserProfileCompletion()

        // Aplicar el color de fondo según las preferencias del usuario
        getUserPreference { preference ->
            applyRemoteConfigBackgroundColor(preference)
        }
    }

    private fun checkUserRole(navController: NavController) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val role = document.getString("role")
                        if (role == "admin") {
                            binding.uploadButton.visibility = View.VISIBLE
                            binding.uploadButton.setOnClickListener {
                                navController.navigate(R.id.uploadBookFragment)
                            }
                        } else {
                            binding.uploadButton.visibility = View.GONE
                        }
                    }
                }
                .addOnFailureListener {
                    binding.uploadButton.visibility = View.GONE
                }
        }
    }

    private fun checkUserProfileCompletion() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("name")
                        val birthDate = document.getString("birthDate")
                        val gender = document.getString("gender")
                        if (name.isNullOrEmpty() || birthDate.isNullOrEmpty() || gender.isNullOrEmpty()) {
                            navController.navigate(R.id.profileFragment)
                        }
                    }
                }
                .addOnFailureListener {

                }
        }
    }

    private fun applyRemoteConfigBackgroundColor(preference: List<String>) {
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val backgroundColor = when {
                        preference.size == 1 && preference.contains("Terror") -> remoteConfig.getString("window_background_color_terror")
                        preference.size == 1 && preference.contains("Romance") -> remoteConfig.getString("window_background_color_romance")
                        else -> remoteConfig.getString("window_background_color_default")
                    }
                    if (backgroundColor.isNotEmpty()) {
                        try {
                            window.decorView.setBackgroundColor(Color.parseColor(backgroundColor))
                        } catch (e: IllegalArgumentException) {
                            // Manejar color no válido
                        }
                    }
                }
            }
    }

    private fun getUserPreference(callback: (List<String>) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val user = document.toObject(User::class.java)
                    val preferences = user?.preferences ?: listOf()
                    callback(preferences)
                } else {
                    callback(listOf())
                }
            }
            .addOnFailureListener {
                callback(listOf())
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.logout_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                auth.signOut()
                val intent = Intent(this, RegisterActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        // Dejar la aplicación en segundo plano si se presiona el botón de retroceso en lugar de cerrar sesión
        moveTaskToBack(true)
    }
}