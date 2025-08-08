package com.example.myapplication

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ConstructorActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_constructor)

        // Настройка системных баров
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        
        // Настройка WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // Дополнительные настройки
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
        }

        // JavaScript интерфейс для взаимодействия с Android
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        // Обработка ошибок и установка масштаба
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e("ConstructorActivity", "WebView error: ${error?.description}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i("ConstructorActivity", "WebView loaded successfully")
                
                // Устанавливаем оптимальный масштаб после загрузки страницы
                webView.postDelayed({
                    val screenWidth = resources.displayMetrics.widthPixels
                    val optimalScale = 0.5f // Идеальный масштаб, найденный пользователем
                    
                    Log.i("ConstructorActivity", "Setting optimal scale: $optimalScale for screen width: $screenWidth")
                    
                    // Применяем масштаб через JavaScript
                    val jsCode = """
                        javascript:(function() {
                            const viewport = document.querySelector('meta[name="viewport"]');
                            const newContent = 'width=device-width, initial-scale=$optimalScale, maximum-scale=0.5, minimum-scale=0.5, user-scalable=yes';
                            viewport.setAttribute('content', newContent);
                            console.log('Constructor: Android applied scale: $optimalScale');
                        })()
                    """.trimIndent()
                    
                    webView.loadUrl(jsCode)
                }, 1000)
            }
        }

        // Загружаем HTML файл из assets
        webView.loadUrl("file:///android_asset/constructor.html")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * JavaScript интерфейс для взаимодействия с Android
     */
    inner class WebAppInterface(private val context: Context) {
        


        @JavascriptInterface
        fun saveImage(base64Data: String, filename: String) {
            Log.i("ConstructorActivity", "saveImage called with filename: $filename")
            try {
                Log.d("ConstructorActivity", "Base64 data length: ${base64Data.length}")
                
                val imageData = Base64.decode(base64Data, Base64.DEFAULT)
                Log.d("ConstructorActivity", "Decoded image data size: ${imageData.size} bytes")
                
                val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                if (bitmap == null) {
                    Log.e("ConstructorActivity", "Failed to decode bitmap from base64 data")
                    runOnUiThread {
                        Toast.makeText(context, "Ошибка: не удалось декодировать изображение", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                
                Log.d("ConstructorActivity", "Bitmap decoded successfully: ${bitmap.width}x${bitmap.height}")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Для Android 10+ используем MediaStore
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    
                    Log.d("ConstructorActivity", "Using MediaStore for Android 10+")
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            outputStream.flush()
                        }
                        runOnUiThread {
                            Toast.makeText(context, "PNG сохранен в Загрузки: $filename", Toast.LENGTH_LONG).show()
                        }
                        Log.i("ConstructorActivity", "Image saved successfully using MediaStore: $filename")
                    } else {
                        Log.e("ConstructorActivity", "Failed to create URI for image")
                        runOnUiThread {
                            Toast.makeText(context, "Ошибка: не удалось создать файл", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Для старых версий Android
                    Log.d("ConstructorActivity", "Using legacy file system for Android < 10")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, filename)
                    
                    FileOutputStream(file).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        fos.flush()
                    }
                    
                    runOnUiThread {
                        Toast.makeText(context, "Изображение сохранено: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                    Log.i("ConstructorActivity", "Image saved successfully: ${file.absolutePath}")
                }
                
                // Освобождаем память
                bitmap.recycle()
                
            } catch (e: Exception) {
                Log.e("ConstructorActivity", "Error saving image", e)
                runOnUiThread {
                    Toast.makeText(context, "Ошибка сохранения PNG: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        @JavascriptInterface
        fun log(message: String) {
            Log.d("ConstructorActivity", "JS Log: $message")
        }
    }
} 