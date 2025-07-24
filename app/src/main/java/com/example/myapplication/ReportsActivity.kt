package com.example.myapplication

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView

class ReportsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout_reports)
        val btnMenu = findViewById<ImageButton>(R.id.btn_menu_reports)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
        // TODO: Настроить адаптер для списка отчётов
        val recyclerView = findViewById<RecyclerView>(R.id.reportsRecyclerView)
        // recyclerView.adapter = ...
    }
} 