package com.firebase.unlam.applibros.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Book(
    var id: String = "",
    val title: String = "",
    val description: String = "",
    val fileUrl: String = "",
    val coverUrl: String = "",
    val category: String = "",
    val uploadedBy: String? = null,
    var viewCount: Int = 0,
    var rating: Double = 0.0
) : Parcelable