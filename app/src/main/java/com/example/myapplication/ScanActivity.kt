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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import android.util.Log
import org.opencv.core.Core
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f

class ScanActivity : AppCompatActivity() {
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraBound = false
    private lateinit var previewView: PreviewView
    private lateinit var resultOverlay: ImageView
    private lateinit var btnStartCamera: Button
    private lateinit var btnStopCamera: Button
    private lateinit var btnHideVideo: Button
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                android.widget.Toast.makeText(this, "Требуется разрешение на камеру", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    private var overlayMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        // Инициализация OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("ScanActivity", "Ошибка инициализации OpenCV")
        }

        // Полноэкранный режим (immersive mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        previewView = findViewById(R.id.camera_preview)
        resultOverlay = findViewById(R.id.result_overlay)
        btnStartCamera = findViewById(R.id.btn_drawer_start_camera)
        btnStopCamera = findViewById(R.id.btn_drawer_stop_camera)
        btnHideVideo = findViewById(R.id.btn_drawer_hide_video)
        val btnCameraSettings = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_camera_settings)
        val btnCloseCameraSettings = findViewById<ImageButton>(R.id.btn_close_camera_settings)

        btnStartCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        btnStopCamera.setOnClickListener {
            stopCamera()
        }
        btnHideVideo.setOnClickListener {
            if (previewView.visibility == View.VISIBLE) {
                previewView.visibility = View.INVISIBLE
                resultOverlay.visibility = View.INVISIBLE
                btnHideVideo.text = "Показать видео"
            } else {
                previewView.visibility = View.VISIBLE
                resultOverlay.visibility = View.VISIBLE
                btnHideVideo.text = "Скрыть видео"
            }
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

        // Состояния для кнопок-свитчей
        var isGridVisible = true
        var isFramePaused = false
        btnAddToReport.isEnabled = false

        btnStopFrame.setOnClickListener {
            // TODO: Обработка остановки кадра
            Log.d("ScanActivity", "Остановка кадра")
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
            overlayMode = !overlayMode
            btnOverlayMode.setIconResource(
                if (overlayMode) R.drawable.ic_toggle_on else R.drawable.ic_layers
            )
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

        // CameraX provider init (но не запуск)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCamera() {
        if (cameraProvider == null || cameraBound) return
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this), { imageProxy ->
            try {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees.toFloat()
                val bitmap = imageProxyToBitmap(imageProxy)
                val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees)
                // Обработка с помощью нативной OpenCV
                val processedBitmap = processFrameWithOpenCV(rotatedBitmap, previewView.width, previewView.height)
                runOnUiThread {
                    resultOverlay.setImageBitmap(processedBitmap)
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "Ошибка обработки кадра: ${e.message}", e)
            } finally {
                imageProxy.close()
            }
        })
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider?.unbindAll()
        cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
        cameraBound = true
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraBound = false
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return src
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun processFrameWithOpenCV(inputBitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val inputMat = Mat()
        Utils.bitmapToMat(inputBitmap, inputMat)
        
        // Получаем размеры входного изображения
        val inputWidth = inputMat.cols()
        val inputHeight = inputMat.rows()
        
        // 1. Поиск контура бланка (наибольший прямоугольник)
        val gray = Mat()
        Imgproc.cvtColor(inputMat, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edged = Mat()
        Imgproc.Canny(gray, edged, 75.0, 200.0)
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        var maxArea = 0.0
        var pageContour: MatOfPoint? = null
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > 1000) {
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true), true)
                if (approx.total() == 4L && area > maxArea) {
                    maxArea = area
                    pageContour = MatOfPoint(*approx.toArray())
                }
            }
        }
        
        if (pageContour != null) {
            val pts = pageContour.toArray()
            val sortedPts = sortPoints(pts)
            
            // Используем размеры входного изображения для warp
            val dstSize = minOf(inputWidth, inputHeight)
            val srcMat = MatOfPoint2f(*sortedPts)
            val dstMat = MatOfPoint2f(
                org.opencv.core.Point(0.0, 0.0),
                org.opencv.core.Point(dstSize - 1.0, 0.0),
                org.opencv.core.Point(dstSize - 1.0, dstSize - 1.0),
                org.opencv.core.Point(0.0, dstSize - 1.0)
            )
            val perspectiveTransform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
            val warp = Mat()
            Imgproc.warpPerspective(inputMat, warp, perspectiveTransform, Size(dstSize.toDouble(), dstSize.toDouble()))

            // Здесь можно рисовать сетку, маркеры и т.д. на warp
            // ...

            if (overlayMode) {
                // В режиме overlay показываем исходный кадр с выделенным контуром бланка
                val contourMat = inputMat.clone()
                Imgproc.drawContours(contourMat, listOf(pageContour), 0, org.opencv.core.Scalar(0.0, 255.0, 0.0), 3)
                contourMat.copyTo(inputMat)
                contourMat.release()
            } else {
                // Показываем только warp-бланк, но масштабируем до размеров входного изображения
                val resizedWarp = Mat()
                Imgproc.resize(warp, resizedWarp, Size(inputWidth.toDouble(), inputHeight.toDouble()))
                resizedWarp.copyTo(inputMat)
                resizedWarp.release()
            }
            warp.release()
            srcMat.release()
            dstMat.release()
            perspectiveTransform.release()
        }
        
        gray.release()
        edged.release()
        hierarchy.release()
        
        // Масштабируем результат под размеры UI
        val finalMat = Mat()
        Imgproc.resize(inputMat, finalMat, Size(targetWidth.toDouble(), targetHeight.toDouble()))
        
        // Конвертируем обратно в Bitmap с целевыми размерами
        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(finalMat, outputBitmap)
        inputMat.release()
        finalMat.release()
        return outputBitmap
    }

    private fun sortPoints(pts: Array<org.opencv.core.Point>): Array<org.opencv.core.Point> {
        // Сортировка точек: [top-left, top-right, bottom-right, bottom-left]
        val sorted = pts.sortedWith(compareBy({ it.y + it.x }, { it.y - it.x }))
        val result = Array(4) { org.opencv.core.Point() }
        result[0] = sorted[0] // top-left
        result[2] = sorted[3] // bottom-right
        val remain = sorted.subList(1, 3)
        if (remain[0].x > remain[1].x) {
            result[1] = remain[0] // top-right
            result[3] = remain[1] // bottom-left
        } else {
            result[1] = remain[1]
            result[3] = remain[0]
        }
        return result
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