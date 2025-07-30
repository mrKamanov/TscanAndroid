package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import android.content.Intent
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.myapplication.ml.OMRModelManager
import android.util.Log

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Современный способ скрыть системные бары (2025)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val menuRecyclerView = findViewById<RecyclerView>(R.id.menuRecyclerView)
        menuRecyclerView.layoutManager = GridLayoutManager(this, 1)

        val items = listOf(
            MenuItem(
                title = getString(R.string.menu_scan),
                description = getString(R.string.menu_scan_desc),
                iconRes = R.drawable.ic_document_scanner,
                onClick = {
                    val intent = Intent(this, ScanActivity::class.java)
                    startActivity(intent)
                }
            ),
            MenuItem(
                title = getString(R.string.menu_batch),
                description = getString(R.string.menu_batch_desc),
                iconRes = R.drawable.ic_batch_prediction,
                onClick = {
                    val intent = Intent(this, BatchActivity::class.java)
                    startActivity(intent)
                }
            ),
            MenuItem(
                title = getString(R.string.menu_reports),
                description = getString(R.string.menu_reports_desc),
                iconRes = R.drawable.ic_description,
                onClick = {
                    val intent = Intent(this, ReportsActivity::class.java)
                    startActivity(intent)
                }
            ),
            MenuItem(
                title = getString(R.string.menu_instructions),
                description = getString(R.string.menu_instructions_desc),
                iconRes = R.drawable.ic_help_outline,
                onClick = {
                    val intent = Intent(this, InstructionsActivity::class.java)
                    startActivity(intent)
                }
            ),
            MenuItem(
                title = getString(R.string.menu_constructor),
                description = getString(R.string.menu_constructor_desc),
                iconRes = R.drawable.ic_build,
                onClick = {
                    val intent = Intent(this, ConstructorActivity::class.java)
                    startActivity(intent)
                }
            ),
            MenuItem(
                title = getString(R.string.menu_multiple),
                description = getString(R.string.menu_multiple_desc),
                iconRes = R.drawable.ic_layers,
                onClick = {
                    val intent = Intent(this, MultipleActivity::class.java)
                    startActivity(intent)
                }
            )
        )

        menuRecyclerView.adapter = MenuAdapter(items)
        
        // Тестируем загрузку модели
        testModelLoading()
    }
    
    /**
     * Тестирует загрузку и работу модели
     */
    private fun testModelLoading() {
        // Запускаем тестирование в фоновом потоке
        Thread {
            try {
                Log.i("MainActivity", "🧪 Начинаем тестирование модели...")
                val modelManager = OMRModelManager(this)
                
                // Ждем инициализации
                Thread.sleep(2000)
                
                if (modelManager.isModelReady()) {
                    Log.i("MainActivity", "✅ Модель загружена успешно")
                    Log.i("MainActivity", "Информация о модели:\n${modelManager.getModelInfo()}")
                    
                                                // Показываем информацию пользователю
                            runOnUiThread {
                                val format = modelManager.getCurrentFormat()
                                val message = when (format) {
                                    OMRModelManager.ModelFormat.ONNX -> "Модель загружена: ONNX (оптимизированная)"
                                    OMRModelManager.ModelFormat.TFLITE -> "Модель загружена: TFLite (оптимизированная)"
                                    OMRModelManager.ModelFormat.PYTORCH -> "Модель загружена: PyTorch (fallback)"
                                }
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                } else {
                    Log.e("MainActivity", "❌ Модель не загружена")
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка загрузки модели", Toast.LENGTH_LONG).show()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Ошибка тестирования модели: ${e.message}")
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
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