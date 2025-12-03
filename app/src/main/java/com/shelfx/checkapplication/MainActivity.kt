package com.shelfx.checkapplication
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.shelfx.checkapplication.data.database.AppDatabase
import com.shelfx.checkapplication.data.entity.ImageType
import com.shelfx.checkapplication.data.repository.UserImagesRepository
import com.shelfx.checkapplication.ml.EdgeFaceEmbedder
import com.shelfx.checkapplication.ui.theme.CheckapplicationTheme
import com.shelfx.checkapplication.utils.*
import com.shelfx.checkapplication.viewmodel.UserImagesViewModel
import com.shelfx.checkapplication.viewmodel.UserImagesViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: UserImagesViewModel

    // Face processing components
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceAlign: FaceAlign
    private lateinit var embedder: EdgeFaceEmbedder
    private lateinit var preprocess: Preprocess

    // make pipeline nullable to avoid hard crash
    private var embeddingPipeline: EmbeddingPipeline? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "user_images_db"
        ).build()

        // 1️⃣ Initialize face processing FIRST
        initializeFaceProcessing()

        val pipeline = embeddingPipeline

        Log.d("Pipeline", pipeline.toString())

        if (pipeline == null) {
            // If pipeline failed, don't proceed – avoid crash
            Toast.makeText(
                this,
                "Failed to initialize face recognition. Closing screen.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        // 2️⃣ Then create repository with NON-null pipeline
        val repository = UserImagesRepository(
            db.userImagesDao(),
            pipeline
        )

        // 3️⃣ ViewModel with ONLY repository
        viewModel = ViewModelProvider(
            this,
            UserImagesViewModelFactory(repository)
        )[UserImagesViewModel::class.java]

        setContent {
            CheckapplicationTheme {
                MainScreen(viewModel)
            }
        }
    }

    private fun initializeFaceProcessing() {
        try {
            faceDetector = FaceDetector()
            faceAlign = FaceAlign()
            // You can use `this` or `applicationContext` – both are fine here
            embedder = EdgeFaceEmbedder(this)
            preprocess = Preprocess(faceDetector, faceAlign)
            embeddingPipeline = EmbeddingPipeline(preprocess, embedder)

            Log.d("MainActivity", "Face processing components initialized successfully")
        } catch (e: Exception) {null
            Log.e("MainActivity", "Error initializing face processing: ${e.message}", e)
            embeddingPipeline = null
            Toast.makeText(
                this,
                "Error initializing face recognition: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::faceDetector.isInitialized) faceDetector.close()
            if (::embedder.isInitialized) embedder.close()
            Log.d("MainActivity", "Face processing components cleaned up")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error cleaning up: ${e.message}")
        }
    }
}

@Composable
fun MainScreen(viewModel: UserImagesViewModel) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                Toast.makeText(
                    context,
                    "Camera permission denied.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )

    LaunchedEffect(true) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraScreen(viewModel)
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Camera permission is required to continue.")
        }
    }
}

@Composable
fun CameraScreen(viewModel: UserImagesViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentViewToCapture by remember { mutableStateOf<ImageType?>(ImageType.FRONT) }
    var isCapturing by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var capturedImages by remember { mutableStateOf<Map<ImageType, String>>(emptyMap()) }

    val imageCapture: ImageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val preview: Preview = remember { Preview.Builder().build() }

    val viewLabel = when (currentViewToCapture) {
        ImageType.FRONT -> "Step 1/3: Capture FRONT view"
        ImageType.LEFT -> "Step 2/3: Capture LEFT side"
        ImageType.RIGHT -> "Step 3/3: Capture RIGHT side"
        null -> "✓ All 3 images captured successfully!"
    }

    val instruction = when (currentViewToCapture) {
        ImageType.FRONT -> "Face the camera directly"
        ImageType.LEFT -> "Turn your head to the LEFT"
        ImageType.RIGHT -> "Turn your head to the RIGHT"
        null -> "Processing and saving to database..."
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                PreviewView(ctx).also {
                    preview.setSurfaceProvider(it.surfaceProvider)
                }
            },
            update = {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                            .build()

                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                        Log.d("CameraScreen", "Camera bound successfully")
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Camera error: ${e.message}", e)
                        Toast.makeText(
                            context,
                            "Camera error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = viewLabel,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp)
                )
                Text(
                    text = "Processing images with face recognition...",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (currentViewToCapture != null && !isProcessing) {
                Button(
                    onClick = {
                        if (!isCapturing) {
                            isCapturing = true
                            val type = currentViewToCapture!!
                            val photoFile = File(
                                context.getExternalFilesDir(null) ?: context.filesDir,
                                "${System.currentTimeMillis()}_${type.name}.jpg"
                            )
                            val outputOptions =
                                ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onError(exc: ImageCaptureException) {
                                        Log.e("CameraScreen", "Photo capture failed: ${exc.message}", exc)
                                        Toast.makeText(
                                            context,
                                            "Capture failed: ${exc.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        isCapturing = false
                                    }

                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        val path = photoFile.absolutePath
                                        Log.d("CameraScreen", "Photo capture succeeded: $path")

                                        capturedImages = capturedImages + (type to path)

                                        currentViewToCapture = when (type) {
                                            ImageType.FRONT -> ImageType.LEFT
                                            ImageType.LEFT -> ImageType.RIGHT
                                            ImageType.RIGHT -> {
                                                // (optional) trigger processing here
                                                null
                                            }
                                        }

                                        isCapturing = false
                                    }
                                }
                            )
                        }
                    },
                    enabled = !isCapturing && !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = if (isCapturing)
                            "Capturing..."
                        else
                            "Capture ${currentViewToCapture?.name} View",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else if (!isProcessing && currentViewToCapture == null) {
                Button(
                    onClick = {
                        currentViewToCapture = ImageType.FRONT
                        capturedImages = emptyMap()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Capture New Set")
                }
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    currentType: ImageType,
    onSuccess: (String) -> Unit
) {
    val photoFile = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "${System.currentTimeMillis()}_${currentType.name}.jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(
                    context,
                    "Failed to capture: ${exc.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                onSuccess(photoFile.absolutePath)
            }
        }
    )
}

private fun processAndSaveImages(
    context: Context,
    viewModel: UserImagesViewModel,
    capturedImages: Map<ImageType, String>,
    onComplete: (Boolean, String) -> Unit
) {
    // Use coroutine for async processing
    (context as? ComponentActivity)?.lifecycleScope?.launch {
        try {
            val frontPath = capturedImages[ImageType.FRONT]
            val leftPath = capturedImages[ImageType.LEFT]
            val rightPath = capturedImages[ImageType.RIGHT]

            if (frontPath == null || leftPath == null || rightPath == null) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Missing image paths")
                }
                return@launch
            }

            // Process images in background
            withContext(Dispatchers.IO) {
                Log.d("ProcessImages", "Starting face detection and embedding generation...")

                // Load bitmaps
                val frontBitmap = BitmapFactory.decodeFile(frontPath)
                val leftBitmap = BitmapFactory.decodeFile(leftPath)
                val rightBitmap = BitmapFactory.decodeFile(rightPath)

                if (frontBitmap == null || leftBitmap == null || rightBitmap == null) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Failed to load images")
                    }
                    return@withContext
                }

                Log.d("ProcessImages", "Images loaded successfully")
                Log.d("ProcessImages", "Front: ${frontBitmap.width}x${frontBitmap.height}")
                Log.d("ProcessImages", "Left: ${leftBitmap.width}x${leftBitmap.height}")
                Log.d("ProcessImages", "Right: ${rightBitmap.width}x${rightBitmap.height}")

                // Generate user ID
                val userId = "User_${System.currentTimeMillis()}"
//                val userName = "User ${System.currentTimeMillis() % 10000}"
                val userName = "John"

                Log.d("ProcessImages", "Generated userName: $userName")

                // Save all images with face processing
                viewModel.saveUserEmbeddings(
                    context = context,
                    userName = userName,
                    frontBitmap = frontBitmap,
                    leftBitmap = leftBitmap,
                    rightBitmap = rightBitmap,
                )

                Log.d("ProcessImages", "All images processed and saved successfully")

                withContext(Dispatchers.Main) {
                    onComplete(true, "All images processed and saved successfully!")
                }
            }
        } catch (e: Exception) {
            Log.e("ProcessImages", "Error processing images: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onComplete(false, "Error: ${e.message}")
            }
        }
    }
}


