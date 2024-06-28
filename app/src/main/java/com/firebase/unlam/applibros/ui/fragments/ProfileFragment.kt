package com.firebase.unlam.applibros.ui.fragments

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.firebase.unlam.applibros.R
import com.firebase.unlam.applibros.databinding.FragmentProfileBinding
import com.firebase.unlam.applibros.models.User
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseMessaging: FirebaseMessaging
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var analytics: FirebaseAnalytics
    private var user: User? = null
    private var selectedPreferences = mutableListOf<String>()
    private var allCategories = listOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        firebaseMessaging = FirebaseMessaging.getInstance()
        remoteConfig = FirebaseRemoteConfig.getInstance()
        analytics = FirebaseAnalytics.getInstance(requireContext())

        setupGenderSpinner()
        fetchCategories()

        binding.birthDateEditText.apply {
            isFocusable = false
            isClickable = true
            setOnClickListener {
                showDatePicker()
            }
        }

        binding.selectPreferencesButton.setOnClickListener {
            showPreferencesDialog()
        }

        binding.updateProfileButton.setOnClickListener {
            updateProfile()
        }

        loadUserProfile()
    }

    private fun setupGenderSpinner() {
        val genderOptions = listOf("Selecciona tu género", "Hombre", "Mujer")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, genderOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.genderSpinner.adapter = adapter
    }

    private fun fetchCategories() {
        firestore.collection("categories").get()
            .addOnSuccessListener { documents ->
                allCategories = documents.map { it.getString("name") ?: "" }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al cargar categorías", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showPreferencesDialog() {
        val checkedItems = BooleanArray(allCategories.size) { index ->
            selectedPreferences.contains(allCategories[index])
        }

        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Selecciona tus preferencias")
            .setMultiChoiceItems(allCategories.toTypedArray(), checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedPreferences.add(allCategories[which])
                } else {
                    selectedPreferences.remove(allCategories[which])
                }
            }
            .setPositiveButton("Aceptar") { dialog, _ ->
                binding.preferencesTextView.text = selectedPreferences.joinToString(", ")
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }

        builder.create().show()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val date = "${selectedYear}-${String.format("%02d", selectedMonth + 1)}-${String.format("%02d", selectedDay)}"
            binding.birthDateEditText.setText(date)
        }, year, month, day)

        datePickerDialog.show()
    }

    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    user = document.toObject(User::class.java)
                    user?.let { populateUserProfile(it) }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al cargar el perfil", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateUserProfile(user: User) {
        binding.nameEditText.setText(user.name)
        binding.birthDateEditText.setText(user.birthDate)
        val genderPosition = if (user.gender == "Hombre") 1 else 2
        binding.genderSpinner.setSelection(genderPosition)
        selectedPreferences = user.preferences.toMutableList()
        binding.preferencesTextView.text = selectedPreferences.joinToString(", ")
        applyThemeBasedOnPreferences()
        setUserProperties(user)
    }

    private fun updateProfile() {
        val userId = auth.currentUser?.uid ?: return
        val name = binding.nameEditText.text.toString().trim()
        val birthDate = binding.birthDateEditText.text.toString().trim()
        val gender = binding.genderSpinner.selectedItem.toString()

        if (!isValidDateFormat(birthDate)) {
            Toast.makeText(context, "Fecha de nacimiento inválida. Usa el formato YYYY-MM-DD.", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedUser = user?.copy(
            name = name,
            birthDate = birthDate,
            gender = gender,
            preferences = selectedPreferences
        )

        updatedUser?.let { userToUpdate ->
            // Desuscribirse de todos los temas actuales
            unsubscribeFromAllTopics(userToUpdate.email)

            firestore.collection("users").document(userId).set(userToUpdate)
                .addOnSuccessListener {
                    updateCategorySubscriptions(userToUpdate)
                    setUserProperties(userToUpdate) // Actualizar las propiedades de usuario aquí
                    Toast.makeText(context, "Perfil actualizado y suscripto a las categorías", Toast.LENGTH_SHORT).show()
                    applyThemeBasedOnPreferences()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error al actualizar el perfil", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun unsubscribeFromAllTopics(email: String) {
        firestore.collection("categories").get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val categoryName = document.getString("name") ?: continue
                    val normalizedCategory = categoryName.normalizeCategoryName()

                    firebaseMessaging.unsubscribeFromTopic(normalizedCategory)
                        .addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                Toast.makeText(context, "Error al desuscribirse de $categoryName", Toast.LENGTH_SHORT).show()
                            }
                        }

                    firestore.collection("categories").document(document.id)
                        .update("users", FieldValue.arrayRemove(email))
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al obtener las categorías anteriores", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateCategorySubscriptions(user: User) {
        val email = user.email

        user.preferences.forEach { category ->
            val normalizedCategory = category.normalizeCategoryName()

            firebaseMessaging.subscribeToTopic(normalizedCategory)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Asegúrate de que el contexto esté disponible antes de mostrar el Toast
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Suscrito a $category", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Error al suscribirse a $category", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

            firestore.collection("categories")
                .whereEqualTo("name", category)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        firestore.collection("categories").add(
                            mapOf("name" to category, "users" to listOf(email))
                        )
                    } else {
                        for (document in documents) {
                            firestore.collection("categories").document(document.id)
                                .update("users", FieldValue.arrayUnion(email))
                        }
                    }
                }
                .addOnFailureListener {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Error al actualizar la categoría $category", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun setUserProperties(user: User) {
        analytics.setUserProperty("preferencias", user.preferences.joinToString(","))
        analytics.setUserProperty("genero", user.gender)
        if (user.birthDate.isNotEmpty()) {
            val age = calculateAge(user.birthDate)
            analytics.setUserProperty("edad", age.toString())
        }
    }

    private fun calculateAge(birthDate: String): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dob = sdf.parse(birthDate)
        val today = Calendar.getInstance()
        val birthDateCalendar = Calendar.getInstance().apply { time = dob }
        var age = today.get(Calendar.YEAR) - birthDateCalendar.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birthDateCalendar.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        return age
    }

    private fun isValidDateFormat(date: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.isLenient = false
            sdf.parse(date)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun String.normalizeCategoryName(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace("[\\p{InCombiningDiacriticalMarks}]".toRegex(), "")
            .replace(" ", "_")
            .lowercase(Locale.getDefault())
    }

    private fun applyThemeBasedOnPreferences() {
        val theme = when {
            selectedPreferences.contains("Terror") && selectedPreferences.size == 1 -> R.style.Theme_AppLibros_Terror
            selectedPreferences.contains("Romance") && selectedPreferences.size == 1 -> R.style.Theme_AppLibros_Romance
            else -> R.style.Theme_AppLibros
        }
        requireActivity().setTheme(theme)
    }
}