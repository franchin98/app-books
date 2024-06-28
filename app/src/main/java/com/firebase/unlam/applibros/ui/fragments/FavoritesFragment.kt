package com.firebase.unlam.applibros.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.firebase.unlam.applibros.databinding.FragmentFavoritesBinding
import com.firebase.unlam.applibros.models.Book
import com.google.firebase.firestore.FirebaseFirestore

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = FirebaseFirestore.getInstance()

        fetchTopRatedBooks()
        setupCrashButtons()
    }

    private fun fetchTopRatedBooks() {
        firestore.collection("books")
            .orderBy("rating", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(3)
            .get()
            .addOnSuccessListener { result ->
                val books = result.toObjects(Book::class.java)
                if (books.isNotEmpty()) {
                    bindTopBook(books[0])
                    if (books.size > 1) bindSecondBook(books[1])
                    if (books.size > 2) bindThirdBook(books[2])
                }
            }
            .addOnFailureListener { e ->
                // Handle the error
            }
    }

    private fun bindTopBook(book: Book) {
        binding.topBookTitle.text = book.title
        binding.topBookRatingBar.rating = book.rating.toFloat()
        binding.topBookRatingBar.setIsIndicator(true)
        Glide.with(this)
            .load(book.coverUrl)
            .into(binding.topBookCover)
    }

    private fun bindSecondBook(book: Book) {
        binding.secondBookTitle.text = book.title
        binding.secondBookRatingBar.rating = book.rating.toFloat()
        binding.secondBookRatingBar.setIsIndicator(true)
        Glide.with(this)
            .load(book.coverUrl)
            .into(binding.secondBookCover)
    }

    private fun bindThirdBook(book: Book) {
        binding.thirdBookTitle.text = book.title
        binding.thirdBookRatingBar.rating = book.rating.toFloat()
        binding.thirdBookRatingBar.setIsIndicator(true)
        Glide.with(this)
            .load(book.coverUrl)
            .into(binding.thirdBookCover)
    }

    private fun setupCrashButtons() {
        binding.illegalArgumentExceptionButton.setOnClickListener {
            simulateIllegalArgumentException()
        }

        binding.arithmeticExceptionButton.setOnClickListener {
            simulateArithmeticException()
        }
    }

    private fun simulateIllegalArgumentException() {
        val invalidAge = getAge(-5)
        println(invalidAge)
    }

    private fun getAge(age: Int): Int {
        require(age >= 0) { "Age cannot be negative" }
        return age
    }

    private fun simulateArithmeticException() {
        val result = divide(10, 0)
        println(result)
    }

    private fun divide(a: Int, b: Int): Int {
        require(b != 0) { "Divisor cannot be zero" }
        return a / b
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}