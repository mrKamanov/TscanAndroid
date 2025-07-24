package com.example.myapplication

data class MenuItem(
    val title: String,
    val description: String,
    val iconRes: Int,
    val onClick: () -> Unit
) 