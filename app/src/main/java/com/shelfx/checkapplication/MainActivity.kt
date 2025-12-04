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
import androidx.compose.ui.graphics.Color // Add this import
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    private lateinit var viewModelFactory: UserImagesViewModelFactory
    // Face processing components
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceAlign: FaceAlign
    private lateinit var embedder: EdgeFaceEmbedder
    private lateinit var preprocess: Preprocess
    private var embeddingPipeline: EmbeddingPipeline? = null

//#-------------------------------------------------------------------------------


// #-----------------------------------------------------------------------------------------------


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "user_images_db"
        ).fallbackToDestructiveMigration().build()

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
            Log.e("Preprocess",preprocess.toString())
            embeddingPipeline = EmbeddingPipeline(preprocess, embedder)

            Log.e("MainActivity", "Face processing components initialized successfully")
        } catch (e: Exception) {
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

fun processAndSaveImages(
    context: Context,
    viewModel: UserImagesViewModel,
    capturedImages: Map<ImageType, String>,
    userName: String,
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
//                val userName = "John"

                Log.d("ProcessImages", "Using userName: $userName")

//                // Save all images with face processing
                // Save all images and AWAIT the result
                val wasSuccessful = viewModel.saveUserEmbeddings(
                    context = context,
                    userName = userName,
                    frontBitmap = frontBitmap,
                    leftBitmap = leftBitmap,
                    rightBitmap = rightBitmap,
                )

                // Now, check the result before reporting back to the UI
                if (wasSuccessful) {
                    Log.d("ProcessImages", "Save operation completed successfully.")
                    withContext(Dispatchers.Main) {
                        onComplete(true, "User '$userName' saved successfully!")
                    }
                } else {
                    Log.e("ProcessImages", "Save operation failed. Check ViewModel logs.")
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Failed to save user data to the database.")
                    }
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

@Composable
fun CameraScreen(viewModel: UserImagesViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. STATE TO CONTROL THE UI FLOW
    var hasName by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) } // For showing error text

    // Camera-related state variables remain the same
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


//    #----------------------------------------------------------------------------------------------
    LaunchedEffect(capturedImages.size, currentViewToCapture) {
        if (capturedImages.size == 3 && currentViewToCapture == null && !isProcessing) {
            isProcessing = true

            if (userName.isBlank()) {
                Toast.makeText(context, "Please enter your name first", Toast.LENGTH_SHORT).show()
                return@LaunchedEffect
            }

            Log.e("CameraScreen--", "All images captured")

            processAndSaveImages(
                context = context,
                viewModel = viewModel,
                capturedImages = capturedImages,
                userName = userName,
                onComplete = { success, message ->// This lambda will be called when processing is finished
                    isProcessing = false
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()

                    if (success) {
                        // Reset the screen for a new capture session
                        capturedImages = emptyMap()
                        currentViewToCapture = ImageType.FRONT
                    } else {
                        // Optionally, allow the user to try saving again
                        // Or reset the state
                        capturedImages = emptyMap()
                        currentViewToCapture = ImageType.FRONT
                    }
                }
            )

        }
    }

////    #----------------------------------------------------------------------------------------------
//    val viewLabel = when (currentViewToCapture) {
//        ImageType.FRONT -> "Step 1/3: Capture FRONT view"
//        ImageType.LEFT -> "Step 2/3: Capture LEFT side"
//        ImageType.RIGHT -> "Step 3/3: Capture RIGHT side"
//        null -> "✓ All 3 images captured successfully!"
//    }
//
//    val instruction = when (currentViewToCapture) {
//        ImageType.FRONT -> "Face the camera directly"
//        ImageType.LEFT -> "Turn your head to the LEFT"
//        ImageType.RIGHT -> "Turn your head to the RIGHT"
//        null -> "Processing and saving to database..."
//    }


    Box(modifier = Modifier.fillMaxSize()) {
        // 2. CONDITIONAL UI: SHOW CAMERA OR NAME ENTRY
        if (hasName) {
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
                                                Log.e(
                                                    "CameraScreen",
                                                    "Photo capture failed: ${exc.message}",
                                                    exc
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "Capture failed: ${exc.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                isCapturing = false
                                            }

                                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                                val path = photoFile.absolutePath
                                                Log.d(
                                                    "CameraScreen",
                                                    "Photo capture succeeded: $path"
                                                )

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
            else {
                // --- UI PART 2: NAME ENTRY SCREEN ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Step 1: Enter Your Name",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
//                    OutlinedTextField(
//                        value = userName,
//                        onValueChange = {
//                            userName = it
//                            if (it.isNotBlank()) nameError = null
//                        },
//                        label = { Text("Your full name") },
//                        singleLine = true,
//                        isError = nameError != null,
//                        modifier = Modifier.fillMaxWidth()
//
//                    )

                    OutlinedTextField(
                        value = userName,
                        onValueChange = {
                            userName = it
                            if (it.isNotBlank()) nameError = null
                        },
                        label = { Text("Your full name") },
                        singleLine = true,
                        isError = nameError != null,
                        modifier = Modifier.fillMaxWidth(),
                        // Add this 'colors' parameter to customize the text color
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,   // Color when the field is focused
                            unfocusedTextColor = Color.Black // Color when the field is not focused
                        )
                    )

                    if (nameError != null) {
                        Text(
                            text = nameError!!,
//                            color = MaterialTheme.colorScheme.error,
                            color = Color.Black,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (userName.isNotBlank()) {
                                hasName = true // This switches the UI to the camera
                            } else {
                                nameError = "Name cannot be empty"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text("Continue to Camera")
                    }
                }
            }
        }
    }


