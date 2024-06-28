package com.firebase.unlam.applibros.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.firebase.unlam.applibros.R
import com.firebase.unlam.applibros.databinding.FragmentDashboardBinding
import com.firebase.unlam.applibros.ui.adapters.CategoriesAdapter
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import java.text.Normalizer

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var categoriesAdapter: CategoriesAdapter
    private lateinit var analytics: FirebaseAnalytics
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var allCategories: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = FirebaseFirestore.getInstance()
        analytics = FirebaseAnalytics.getInstance(requireContext())
        remoteConfig = FirebaseRemoteConfig.getInstance()

        categoriesAdapter = CategoriesAdapter { category ->
            logCategoryVisitEvent(category)
            val action = DashboardFragmentDirections.actionDashboardFragmentToBooksFragment(category)
            findNavController().navigate(action)
        }

        binding.categoriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = categoriesAdapter
        }

        fetchCategories()
        applyRemoteConfigSettings()

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCategories(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun fetchCategories() {
        firestore.collection("categories")
            .get()
            .addOnSuccessListener { documents ->
                allCategories = documents.map { it.getString("name") ?: "" }
                categoriesAdapter.updateCategories(allCategories)
            }
            .addOnFailureListener { e ->

            }
    }

    private fun filterCategories(query: String) {
        val normalizedQuery = normalizeString(query)
        val filteredCategories = allCategories.filter {
            normalizeString(it).contains(normalizedQuery, ignoreCase = true)
        }
        categoriesAdapter.updateCategories(filteredCategories)
    }

    private fun normalizeString(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
    }

    private fun logCategoryVisitEvent(category: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_NAME, category)
        }
        analytics.logEvent("category_visited", bundle)
    }

    private fun applyRemoteConfigSettings() {
        val welcomeMessage = remoteConfig.getString("welcome_message")
        val welcomeMessageColor = remoteConfig.getString("welcome_message_color")

        val welcomeTextView = view?.findViewById<TextView>(R.id.welcomeMessageTextView)
        welcomeTextView?.text = welcomeMessage

        // Establecer el color del mensaje de bienvenida a verde si no se encuentra en Remote Config
        val defaultColor = "#009624" // Verde
        try {
            welcomeTextView?.setTextColor(Color.parseColor(welcomeMessageColor.ifEmpty { defaultColor }))
        } catch (e: IllegalArgumentException) {
            // Manejar el error de análisis de color si es necesario
            welcomeTextView?.setTextColor(Color.parseColor(defaultColor)) // color por defecto en caso de error
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}