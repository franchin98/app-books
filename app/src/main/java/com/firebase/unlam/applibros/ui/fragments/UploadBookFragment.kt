package com.firebase.unlam.applibros.ui.fragments

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.firebase.unlam.applibros.databinding.FragmentUploadBookBinding
import com.firebase.unlam.applibros.models.Book
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class UploadBookFragment : Fragment() {

    private var _binding: FragmentUploadBookBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var coverPickerLauncher: ActivityResultLauncher<Intent>
    private var fileUri: Uri? = null
    private var coverUri: Uri? = null
    private var fileName: String? = null
    private var coverName: String? = null
    private lateinit var progressDialog: ProgressDialog
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentUploadBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        firebaseAnalytics = FirebaseAnalytics.getInstance(requireContext())

        setupCategorySpinner()
        binding.selectFileButton.setOnClickListener {
            openFilePicker()
        }

        binding.selectCoverButton.setOnClickListener {
            openCoverPicker()
        }

        binding.uploadBookButton.setOnClickListener {
            validateAndUploadBook()
        }

        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                fileUri = result.data?.data
                fileUri?.let { uri ->
                    fileName = getFileName(uri)
                    binding.selectedFileTextView.text = fileName
                }
            }
        }

        coverPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                coverUri = result.data?.data
                coverUri?.let { uri ->
                    coverName = getFileName(uri)
                    binding.selectedCoverTextView.text = coverName
                }
            }
        }
    }

    private fun setupCategorySpinner() {
        firestore.collection("categories").get()
            .addOnSuccessListener { documents ->
                val categories = mutableListOf("Seleccione categoría")
                categories.addAll(documents.map { it.getString("name") ?: "" })
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.categorySpinner.adapter = adapter
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al cargar categorías", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        filePickerLauncher.launch(intent)
    }

    private fun openCoverPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        coverPickerLauncher.launch(intent)
    }

    private fun getFileName(uri: Uri): String {
        var name = ""
        val cursor = context?.contentResolver?.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && it.moveToFirst()) {
                name = it.getString(nameIndex)
            }
        }
        return name
    }

    private fun validateAndUploadBook() {
        val title = binding.titleEditText.text.toString().trim()
        val description = binding.descriptionEditText.text.toString().trim()
        val selectedCategory = binding.categorySpinner.selectedItem.toString()

        if (title.isEmpty() || description.isEmpty() || fileUri == null || coverUri == null || selectedCategory == "Seleccione categoría") {
            Toast.makeText(context, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            binding.uploadBookButton.isEnabled = true
            return
        }

        binding.uploadBookButton.isEnabled = false
        showProgressDialog()
        uploadBook(title, description, selectedCategory)
    }

    private fun uploadBook(title: String, description: String, category: String) {
        val bookId = UUID.randomUUID().toString()  // Generar un ID único para el libro
        val filePath = "books/$bookId/$fileName"
        val coverPath = "covers/$bookId/$coverName"
        val fileRef = storage.reference.child(filePath)
        val coverRef = storage.reference.child(coverPath)

        fileUri?.let { uri ->
            fileRef.putFile(uri)
                .addOnSuccessListener {
                    fileRef.downloadUrl.addOnSuccessListener { fileDownloadUrl ->
                        coverUri?.let { coverUri ->
                            coverRef.putFile(coverUri)
                                .addOnSuccessListener {
                                    coverRef.downloadUrl.addOnSuccessListener { coverDownloadUrl ->
                                        saveBookToDatabase(bookId, title, description, fileDownloadUrl.toString(), coverDownloadUrl.toString(), category)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("UploadBookFragment", "Error al subir la portada", e)
                                    Toast.makeText(context, "Error al subir la portada", Toast.LENGTH_SHORT).show()
                                    binding.uploadBookButton.isEnabled = true
                                    hideProgressDialog()
                                }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("UploadBookFragment", "Error al subir el archivo", e)
                    Toast.makeText(context, "Error al subir el archivo", Toast.LENGTH_SHORT).show()
                    binding.uploadBookButton.isEnabled = true
                    hideProgressDialog()
                }
        }
    }

    private fun saveBookToDatabase(id: String, title: String, description: String, fileUrl: String, coverUrl: String, category: String) {
        val book = Book(
            id = id,
            title = title,
            description = description,
            fileUrl = fileUrl,
            coverUrl = coverUrl,
            category = category,
            uploadedBy = auth.currentUser?.uid
        )

        firestore.collection("books").document(id).set(book)  // Usar el ID generado como el ID del documento
            .addOnSuccessListener {
                Toast.makeText(context, "Libro subido exitosamente", Toast.LENGTH_LONG).show()
                binding.titleEditText.text.clear()
                binding.descriptionEditText.text.clear()
                binding.selectedFileTextView.text = ""
                binding.selectedCoverTextView.text = ""
                fileUri = null
                coverUri = null

                // Log del evento upload
                firebaseAnalytics.logEvent("upload_book") {
                    param("book_name", title)
                    param("book_id", id)
                }
            }
            .addOnFailureListener { e ->
                Log.e("UploadBookFragment", "Error al guardar el libro en la base de datos", e)
                Toast.makeText(context, "Error al guardar el libro en la base de datos", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                binding.uploadBookButton.isEnabled = true
                hideProgressDialog()
            }
    }

    private fun showProgressDialog() {
        progressDialog = ProgressDialog(requireContext())
        progressDialog.setMessage("Subiendo libro...")
        progressDialog.setCancelable(false)
        progressDialog.show()
    }

    private fun hideProgressDialog() {
        progressDialog.dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}