package com.firebase.unlam.applibros.ui.fragments

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.firebase.unlam.applibros.databinding.FragmentBooksBinding
import com.firebase.unlam.applibros.models.Book
import com.firebase.unlam.applibros.ui.adapters.BookAdapter
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class BooksFragment : Fragment() {

    private var _binding: FragmentBooksBinding? = null
    private val binding get() = _binding!!
    private lateinit var booksAdapter: BookAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var progressDialog: ProgressDialog
    private lateinit var analytics: FirebaseAnalytics
    private var startTime: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBooksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = FirebaseFirestore.getInstance()
        analytics = FirebaseAnalytics.getInstance(requireContext())
        booksAdapter = BookAdapter(emptyList(), this::onViewClick, this::onRatingChange)

        binding.booksRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = booksAdapter
        }

        fetchBooks()
    }

    private fun fetchBooks() {
        showProgressBar()
        val category = arguments?.getString("category")
        firestore.collection("books")
            .whereEqualTo("category", category)
            .get()
            .addOnSuccessListener { result ->
                handleSuccess(result)
                hideProgressBar()
            }
            .addOnFailureListener { exception ->
                Log.e("BooksFragment", "Error fetching books", exception)
                hideProgressBar()
            }
    }

    private fun handleSuccess(result: QuerySnapshot) {
        val books = result.toObjects(Book::class.java)
        fetchUserRatings(books)
    }

    private fun fetchUserRatings(books: List<Book>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let {
            firestore.collection("user_ratings")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener { documents ->
                    val userRatings = mutableMapOf<String, Double>()
                    for (document in documents) {
                        val bookId = document.getString("bookId") ?: ""
                        val rating = document.getDouble("rating") ?: 0.0
                        userRatings[bookId] = rating
                    }
                    booksAdapter.updateBooks(books, userRatings)
                }
                .addOnFailureListener { exception ->
                    Log.e("BooksFragment", "Error fetching user ratings", exception)
                }
        } ?: booksAdapter.updateBooks(books, emptyMap())
    }

    private fun onViewClick(book: Book) {
        incrementViewCount(book)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(book.fileUrl))
        startActivity(intent)
        logViewEvent(book)  // Registrar evento de vista del libro
    }

    private fun incrementViewCount(book: Book) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let {
            firestore.collection("books").document(book.id).get()
                .addOnSuccessListener { document ->
                    val viewCount = document.getLong("viewCount") ?: 0
                    val userViews = document.get("userViews") as? Map<String, Boolean> ?: emptyMap()
                    if (!userViews.containsKey(it)) {
                        val newViewCount = viewCount + 1
                        val newUserViews = userViews.toMutableMap().apply { put(it, true) }
                        firestore.collection("books").document(book.id).update(
                            "viewCount", newViewCount,
                            "userViews", newUserViews
                        )
                    }
                }
        }
    }

    private fun onRatingChange(book: Book, newRating: Float) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let {
            firestore.collection("user_ratings").document("${book.id}_$it").set(
                mapOf("bookId" to book.id, "userId" to it, "rating" to newRating)
            ).addOnSuccessListener {
                updateAverageRating(book)
                logRatingUpdatedEvent(book, newRating)  // Registrar evento de actualización de calificación
                Toast.makeText(context, "Puntuación actualizada", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                Toast.makeText(context, "Error al actualizar la puntuación", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateAverageRating(book: Book) {
        firestore.collection("user_ratings")
            .whereEqualTo("bookId", book.id)
            .get()
            .addOnSuccessListener { documents ->
                var totalRating = 0.0
                for (document in documents) {
                    totalRating += document.getDouble("rating") ?: 0.0
                }
                val newAverageRating = if (documents.size() > 0) totalRating / documents.size() else 0.0
                firestore.collection("books").document(book.id)
                    .update("rating", newAverageRating)
            }
    }

    private fun logViewEvent(book: Book) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, book.id)
            putString(FirebaseAnalytics.Param.ITEM_NAME, book.title)
        }
        analytics.logEvent("view_book", bundle)
    }

    private fun logRatingUpdatedEvent(book: Book, newRating: Float) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, book.id)
            putString(FirebaseAnalytics.Param.ITEM_NAME, book.title)
            putFloat("rating", newRating)
        }
        analytics.logEvent("rating_updated", bundle)
    }

    private fun logTimeSpentEvent(category: String, timeSpent: Long) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_NAME, category)
            putLong("time_spent", timeSpent)
        }
        analytics.logEvent("time_spent_in_category", bundle)
    }

    private fun showProgressBar() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        binding.progressBar.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        startTime = SystemClock.elapsedRealtime()
    }

    override fun onPause() {
        super.onPause()
        val endTime = SystemClock.elapsedRealtime()
        val timeSpent = endTime - startTime
        val category = arguments?.getString("category") ?: "Unknown"
        logTimeSpentEvent(category, timeSpent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}