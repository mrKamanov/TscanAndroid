package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class ScanActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        // Полноэкранный режим (immersive mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        // val btnMenu = findViewById<ImageButton>(R.id.btn_menu) // удалено
        // val btnSettingsGear = findViewById<ImageButton>(R.id.btn_settings_gear) // удалено
        val btnStartCamera = findViewById<Button>(R.id.btn_drawer_start_camera)
        val btnStopCamera = findViewById<Button>(R.id.btn_drawer_stop_camera)
        val btnHideVideo = findViewById<Button>(R.id.btn_drawer_hide_video)
        // val btnCloseCameraSettings = findViewById<ImageButton>(R.id.btn_close_camera_settings) // удалено
        val btnCameraSettings = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_camera_settings)
        val btnCloseCameraSettings = findViewById<ImageButton>(R.id.btn_close_camera_settings)

        // btnMenu.setOnClickListener {
        //     drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        // }
        // btnSettingsGear.setOnClickListener {
        //     drawerLayout.openDrawer(androidx.core.view.GravityCompat.END)
        // }
        // btnCloseCameraSettings.setOnClickListener {
        //     drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
        // }
        btnStartCamera.setOnClickListener {
            // TODO: Запуск камеры
        }
        btnStopCamera.setOnClickListener {
            // TODO: Остановка камеры
        }
        btnHideVideo.setOnClickListener {
            // TODO: Скрыть видео
        }
        // Слайдеры настроек камеры
        val seekBrightness = findViewById<SeekBar?>(R.id.seek_brightness)
        val valueBrightness = findViewById<TextView?>(R.id.value_brightness)
        seekBrightness?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Яркость: от -100 до +100
                valueBrightness?.text = (progress - 100).toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val seekContrast = findViewById<SeekBar?>(R.id.seek_contrast)
        val valueContrast = findViewById<TextView?>(R.id.value_contrast)
        seekContrast?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Контраст: 0..200
                valueContrast?.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val seekSaturation = findViewById<SeekBar?>(R.id.seek_saturation)
        val valueSaturation = findViewById<TextView?>(R.id.value_saturation)
        seekSaturation?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Насыщенность: 0..200
                valueSaturation?.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val seekSharpness = findViewById<SeekBar?>(R.id.seek_sharpness)
        val valueSharpness = findViewById<TextView?>(R.id.value_sharpness)
        seekSharpness?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Резкость: 0..100
                valueSharpness?.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // --- Кнопки управления под видеопотоком ---
        val btnStopFrame = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_stop_frame)
        val btnAddToReport = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_to_report)
        val btnToggleGrid = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_toggle_grid)
        val btnUpdateAnswers = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_update_answers)
        val btnOverlayMode = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_overlay_mode)
        // btnCameraSettings с типом ImageButton удалён, используем MaterialButton выше

        // Состояния для кнопок-свитчей
        var isGridVisible = true
        var isOverlayMode = false
        var isFramePaused = false
        btnAddToReport.isEnabled = false

        btnStopFrame.setOnClickListener {
            // TODO: Логика создания стоп-кадра
            isFramePaused = !isFramePaused
            btnAddToReport.isEnabled = isFramePaused
            // Можно визуально подсветить кнопку, если стоп-кадр активен
            btnStopFrame.alpha = if (isFramePaused) 0.6f else 1.0f
        }

        btnAddToReport.setOnClickListener {
            // TODO: Логика добавления результата в отчёт
        }

        btnToggleGrid.setOnClickListener {
            isGridVisible = !isGridVisible
            btnToggleGrid.setIconResource(
                if (isGridVisible) R.drawable.ic_grid_on else R.drawable.ic_grid_off
            )
            // TODO: Показать/скрыть сетку на экране
        }

        btnUpdateAnswers.setOnClickListener {
            // TODO: Логика обновления ответов
        }

        btnOverlayMode.setOnClickListener {
            isOverlayMode = !isOverlayMode
            btnOverlayMode.setIconResource(
                if (isOverlayMode) R.drawable.ic_toggle_on else R.drawable.ic_layers
            )
            // TODO: Переключить режим наложения
        }

        btnCameraSettings.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.END)
        }
        btnCloseCameraSettings.setOnClickListener {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
        }

        // --- Кнопки навигации из бокового меню ---
        val btnNavHome = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_nav_home)
        val btnNavReports = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_nav_reports)

        btnNavHome.setOnClickListener {
            // Переход на главный экран
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }
        btnNavReports.setOnClickListener {
            // Переход на экран отчётов (если нет ReportsActivity — TODO)
            try {
                val intent = android.content.Intent(this, ReportsActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Экран отчётов не реализован", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
} 