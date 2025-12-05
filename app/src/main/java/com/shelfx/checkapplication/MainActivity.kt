package com.shelfx.checkapplication
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.shelfx.checkapplication.utils.FaceVerifier
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var viewModelFactory: UserImagesViewModelFactory
    private var isViewModelReady by mutableStateOf(false)

    // Face processing components
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceAlign: FaceAlign
    private lateinit var embedder: EdgeFaceEmbedder
    private lateinit var preprocess: Preprocess
    private var embeddingPipeline: EmbeddingPipeline? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Asynchronously initialize everything needed for the ViewModel
        initializeDependencies()

        setContent {
            CheckapplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isViewModelReady) {
                        AppNavigator(viewModelFactory)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Initializing Face Recognition Engine...")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initializeDependencies() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                faceDetector = FaceDetector()
                faceAlign = FaceAlign()
                embedder = EdgeFaceEmbedder(this@MainActivity)
                preprocess = Preprocess(faceDetector, faceAlign)
                embeddingPipeline = EmbeddingPipeline(preprocess, embedder)
                Log.d("MainActivity", "Face processing components initialized successfully.")


                val pipeline = embeddingPipeline
                if (pipeline == null) {
                    throw IllegalStateException("Embedding pipeline could not be initialized.")
                }

                val faceVerifier = FaceVerifier(pipeline)
                val db = Room.databaseBuilder(
                    applicationContext,
                    AppDatabase::class.java, "user_images_db"
                ).fallbackToDestructiveMigration().build()


                val repository = UserImagesRepository(
                    db.userImagesDao(), pipeline,
                    faceVerifier
                )
                viewModelFactory = UserImagesViewModelFactory(repository)

                withContext(Dispatchers.Main) {
                    isViewModelReady = true
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Fatal initialization error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Initialization Failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::faceDetector.isInitialized) faceDetector.close()
            if (::embedder.isInitialized) embedder.close()
            Log.d("MainActivity", "Face processing components cleaned up.")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during onDestroy cleanup: ${e.message}")
        }
    }
}

// Screen sealed class for navigation
sealed class Screen {
    object Welcome : Screen()
    object RegistrationNameEntry : Screen()
    data class PerformRegistration(val userName: String) : Screen()
    object LoginNameEntry : Screen()
    data class PerformLogin(val userName: String) : Screen()
    data class Dashboard(val userName: String) : Screen()
    data class UpdateImages(val userName: String) : Screen()
}

@Composable
fun AppNavigator(viewModelFactory: UserImagesViewModelFactory) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Welcome) }
    val viewModel: UserImagesViewModel = viewModel(factory = viewModelFactory)

    when (val screen = currentScreen) {
        is Screen.Welcome -> WelcomeScreen(
            viewModel = viewModel,
            onNavigateToLogin = { currentScreen = Screen.LoginNameEntry },
            onNavigateToRegister = { currentScreen = Screen.RegistrationNameEntry }
        )

        is Screen.RegistrationNameEntry -> RegistrationNameEntryScreen(
            onNameEntered = { userName ->
                currentScreen = Screen.PerformRegistration(userName)
            },
            onBackToWelcome = { currentScreen = Screen.Welcome }
        )

        is Screen.PerformRegistration -> PermissionWrapper {
            CameraCaptureScreen(
                viewModel = viewModel,
                userName = screen.userName,
                screenTitle = "New User Registration",
                onSuccess = { currentScreen = Screen.Welcome },
                onCancel = { currentScreen = Screen.RegistrationNameEntry }
            )
        }

        is Screen.LoginNameEntry -> LoginNameEntryScreen(
            onNameEntered = { userName ->
                currentScreen = Screen.PerformLogin(userName)
            },
            onBackToWelcome = { currentScreen = Screen.Welcome }
        )

        is Screen.PerformLogin -> PermissionWrapper {
            LoginVerificationScreen(
                viewModel = viewModel,
                userName = screen.userName,
                onLoginSuccess = { userName ->
                    currentScreen = Screen.Dashboard(userName)
                },
                onBackToNameEntry = { currentScreen = Screen.LoginNameEntry }
            )
        }

        is Screen.Dashboard -> DashboardScreen(
            userName = screen.userName,
            onUpdateImages = { currentScreen = Screen.UpdateImages(screen.userName) },
            onLogout = { currentScreen = Screen.Welcome }
        )

        is Screen.UpdateImages -> PermissionWrapper {
            CameraCaptureScreen(
                viewModel = viewModel,
                userName = screen.userName,
                screenTitle = "Update Your Images",
                onSuccess = { currentScreen = Screen.Dashboard(screen.userName) },
                onCancel = { currentScreen = Screen.Dashboard(screen.userName) }
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    viewModel: UserImagesViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(true) }
    LaunchedEffect(key1 = true) {
        scope.launch {

//            Log.d("Start","start")
//            try {
//                val del = viewModel.DeletemyUsers()
//
//            }
//            catch (e:Exception){
//                Log.d("Deleted","${e}")
//
//            }

            // Check if users exist in database
            val hasUsers = viewModel.hasAnyUsers()

            val hasUser = viewModel.loadUserImages("ahmad")

            Log.d("Has User","users ${hasUser}")

            // Small delay for better UX (optional - shows the welcome screen briefly)
            delay(1000)

            // Auto-redirect based on user existence
            if (hasUsers) {
                onNavigateToLogin()
            } else {
                onNavigateToRegister()
            }

            isChecking = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // App Logo or Title
            Text(
                text = "Face Recognition",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Secure Login System",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            if (isChecking) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Initializing...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RegistrationNameEntryScreen(
    onNameEntered: (String) -> Unit,
    onBackToWelcome: () -> Unit
) {
    var userName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "New User Registration",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            "Please enter a username to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = userName,
            onValueChange = {
                userName = it
                nameError = null
            },
            label = { Text("Username") },
            singleLine = true,
            isError = nameError != null,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        if (nameError != null) {
            Text(
                text = nameError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                when {
                    userName.isBlank() -> nameError = "Username cannot be empty"
                    userName.length < 3 -> nameError = "Username must be at least 3 characters"
                    else -> onNameEntered(userName)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Continue to Camera")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBackToWelcome,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Back to Welcome")
        }
    }
}

@Composable
fun LoginNameEntryScreen(
    onNameEntered: (String) -> Unit,
    onBackToWelcome: () -> Unit
) {
    var userName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Login",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            "Enter your username to proceed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = userName,
            onValueChange = {
                userName = it
                nameError = null
            },
            label = { Text("Username") },
            singleLine = true,
            isError = nameError != null,
            modifier = Modifier.fillMaxWidth()
        )

        if (nameError != null) {
            Text(
                text = nameError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (userName.isBlank()) {
                    nameError = "Please enter your username"
                } else {
                    onNameEntered(userName)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Continue to Verification")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBackToWelcome,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Back to Welcome")
        }
    }
}

@Composable
fun LoginVerificationScreen(
    viewModel: UserImagesViewModel,
    userName: String,
    onLoginSuccess: (String) -> Unit,
    onBackToNameEntry: () -> Unit
) {
    var statusText by remember { mutableStateOf("Position your face in the frame and tap verify") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val imageCapture = remember { ImageCapture.Builder().build() }
    val preview = remember { Preview.Builder().build() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                PreviewView(ctx).also { preview.setSurfaceProvider(it.surfaceProvider) }
            },
            update = {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                }, ContextCompat.getMainExecutor(context))
            }
        )

        // Login Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Face Verification", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(8.dp))

            Text(
                text = "User: $userName",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                statusText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Verify Button
            Button(
                onClick = {
                    isLoading = true
                    statusText = "Verifying your face..."
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                scope.launch {
                                    val bitmap = image.toBitmap()
                                    image.close()

                                    if (bitmap == null) {
                                        isLoading = false
                                        statusText = "Failed to read image from camera."
                                        return@launch
                                    }

                                    val isVerified = viewModel.verifyUser(bitmap, userName)
                                    isLoading = false

                                    if (isVerified) {
                                        Toast.makeText(
                                            context,
                                            "Welcome back, $userName!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onLoginSuccess(userName)
                                    } else {
                                        statusText = "Verification failed. Please try again."
                                    }
                                }
                            }

                            override fun onError(exc: ImageCaptureException) {
                                isLoading = false
                                statusText = "Capture failed: ${exc.message}"
                            }
                        }
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Verify My Face")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Back Button
            OutlinedButton(
                onClick = onBackToNameEntry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Back")
            }
        }
    }
}

// Convert ImageProxy (YUV_420_888) to Bitmap for immediate processing
private fun ImageProxy.toBitmap(): android.graphics.Bitmap? {
    return try {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to convert image: ${e.message}", e)
        null
    }
}

@Composable
fun DashboardScreen(
    userName: String,
    onUpdateImages: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Welcome!",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            userName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "✓ Login Successful",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You have been verified successfully",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = onUpdateImages,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Update My Images")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Logout")
        }
    }
}

@Composable
fun PermissionWrapper(content: @Composable () -> Unit) {
    var hasCameraPermission by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                Toast.makeText(
                    context,
                    "Camera permission is required for face recognition.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )

    LaunchedEffect(true) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            hasCameraPermission = true
        }
    }

    if (hasCameraPermission) {
        content()
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    "Camera Permission Required",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    "This app needs camera access to capture your face for recognition.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
fun CameraCaptureScreen(
    viewModel: UserImagesViewModel,
    userName: String,
    screenTitle: String,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
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

    LaunchedEffect(capturedImages.size) {
        if (capturedImages.size == 3 && !isProcessing) {
            isProcessing = true
            Log.d("CameraCaptureScreen", "All images captured for user: $userName")

            processAndSaveImages(
                context = context,
                viewModel = viewModel,
                capturedImages = capturedImages,
                userName = userName,
                onComplete = { success, message ->
                    isProcessing = false
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()

                    if (success) {
                        onSuccess()
                    } else {
                        capturedImages = emptyMap()
                        currentViewToCapture = ImageType.FRONT
                    }
                }
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Camera Preview
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                PreviewView(ctx).also { preview.setSurfaceProvider(it.surfaceProvider) }
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
                    } catch (e: Exception) {
                        Log.e("CameraCaptureScreen", "Camera binding error: ${e.message}", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        val viewLabel = when (currentViewToCapture) {
            ImageType.FRONT -> "Step 1/3: Capture FRONT view"
            ImageType.LEFT -> "Step 2/3: Capture LEFT side"
            ImageType.RIGHT -> "Step 3/3: Capture RIGHT side"
            null -> "✓ All 3 images captured!"
        }

        val instruction = when (currentViewToCapture) {
            ImageType.FRONT -> "Face the camera directly"
            ImageType.LEFT -> "Turn your head to the LEFT"
            ImageType.RIGHT -> "Turn your head to the RIGHT"
            else -> "Processing and saving..."
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = screenTitle,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "User: $userName",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = viewLabel,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text(
                    text = "Processing with face recognition...",
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
                                context.cacheDir,
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
                                            "CameraCapture",
                                            "Photo capture failed: ${exc.message}",
                                            exc
                                        )
                                        isCapturing = false
                                    }

                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        val path = photoFile.absolutePath
                                        Log.d("CameraCapture", "Photo captured: $path")

                                        capturedImages = capturedImages + (type to path)
                                        currentViewToCapture = when (type) {
                                            ImageType.FRONT -> ImageType.LEFT
                                            ImageType.LEFT -> ImageType.RIGHT
                                            ImageType.RIGHT -> null
                                        }
                                        isCapturing = false
                                    }
                                }
                            )
                        }
                    },
                    enabled = !isCapturing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = if (isCapturing) "Capturing..."
                        else "Capture ${currentViewToCapture?.name} View",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Cancel")
                }
            }
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

            withContext(Dispatchers.IO) {
                Log.d("ProcessImages", "Starting face detection and embedding generation...")

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
                Log.d("ProcessImages", "Using userName: $userName")

                val wasSuccessful = viewModel.saveUserEmbeddings(
                    context = context,
                    userName = userName,
                    frontBitmap = frontBitmap,
                    leftBitmap = leftBitmap,
                    rightBitmap = rightBitmap,
                )

                if (wasSuccessful) {
                    Log.d("ProcessImages", "Save operation completed successfully.")
                    withContext(Dispatchers.Main) {
                        onComplete(true, "User '$userName' saved successfully!")
                    }
                } else {
                    Log.e("ProcessImages", "Save operation failed.")
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Failed to save user data. Please try again.")
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