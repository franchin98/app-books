package com.firebase.unlam.applibros.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RatingBar
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.firebase.unlam.applibros.databinding.ItemBookBinding
import com.firebase.unlam.applibros.models.Book

class BookAdapter(
    private var books: List<Book>,
    private val onViewClick: (Book) -> Unit,
    private val onRatingChange: (Book, Float) -> Unit,
    private val isIndicator: Boolean = false
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    private var userRatings = mutableMapOf<String, Double>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding, onViewClick, onRatingChange, isIndicator)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        holder.bind(book, userRatings[book.id]?.toFloat() ?: 0f)
    }

    override fun getItemCount(): Int = books.size

    fun updateBooks(newBooks: List<Book>, newUserRatings: Map<String, Double>) {
        books = newBooks
        userRatings = newUserRatings.toMutableMap()
        notifyDataSetChanged()
    }

    class BookViewHolder(
        val binding: ItemBookBinding,
        private val onViewClick: (Book) -> Unit,
        private val onRatingChange: (Book, Float) -> Unit,
        private val isIndicator: Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var currentBook: Book

        init {
            binding.bookViewButton.setOnClickListener {
                onViewClick(currentBook)
            }
        }

        fun bind(book: Book, userRating: Float) {
            currentBook = book
            binding.bookTitle.text = book.title
            binding.bookDescription.text = book.description
            binding.viewCount.text = "Visualizaciones: ${book.viewCount}"

            // Desactiva temporalmente el listener de cambios del RatingBar
            binding.ratingBar.onRatingBarChangeListener = null
            binding.ratingBar.rating = userRating
            binding.ratingBar.onRatingBarChangeListener = RatingBar.OnRatingBarChangeListener { _, rating, _ ->
                if (!isIndicator) {
                    onRatingChange(currentBook, rating)
                }
            }
            binding.ratingBar.setIsIndicator(isIndicator)

            Glide.with(binding.bookCover.context)
                .load(book.coverUrl)
                .into(binding.bookCover)
        }
    }
}