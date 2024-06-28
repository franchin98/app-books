package com.firebase.unlam.applibros.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "user", // "user" o "admin"
    val birthDate: String = "", // Almacenar fecha de nacimiento en formato YYYY-MM-DD
    val gender: String = "",
    val preferences: List<String> = emptyList()
) : Parcelable