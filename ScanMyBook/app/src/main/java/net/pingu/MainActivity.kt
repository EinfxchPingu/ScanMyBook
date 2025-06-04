package net.pingu

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import net.pingu.ui.theme.ScanMyBookTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCameraPermission()
        enableEdgeToEdge()
        setContent {
            ScanMyBookTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {}
            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

val poppinsFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var detectedBarcode by remember { mutableStateOf<Barcode?>(null) }
    var showBarcodeDialog by remember { mutableStateOf(false) }
    var capturedBarcodeData by remember { mutableStateOf<String?>(null) }
    var previewPaused by remember { mutableStateOf(false) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = ContextCompat.getMainExecutor(context)

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val preview = Preview.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            if (previewPaused) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val mediaImage = imageProxy.image ?: run {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            val options = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                                .build()

                            val scanner = BarcodeScanning.getClient(options)
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    detectedBarcode = barcodes.firstOrNull()
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        }
                    }

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, executor)

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )


        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
        ) {
            AnimatedVisibility(
                visible = detectedBarcode != null,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "shine")
                val animatedOffset by infiniteTransition.animateFloat(
                    initialValue = -200f,
                    targetValue = 400f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "shine_offset"
                )

                val animatedBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF00FF00),
                        Color(0xFF00A000),
                        Color(0xFF00FF00)
                    ),
                    start = Offset(animatedOffset - 150f, 0f),
                    end = Offset(animatedOffset + 150f, 0f)
                )

                Text(
                    text = "✓ Barcode erkannt",
                    fontSize = 14.sp,
                    fontFamily = poppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.ui.text.TextStyle(brush = animatedBrush),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }


            Text(
                text = "Foto aufnehmen",
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = poppinsFontFamily,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .border(
                        4.dp,
                        SolidColor(Color.White),
                        CircleShape
                    )

                    .clickable {
                        detectedBarcode?.let {
                            capturedBarcodeData = it.rawValue
                            previewPaused = true
                            showBarcodeDialog = true
                        }
                    }
            )
        }

        Text(
            text = "Made with ❤️ by Stefan",
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = poppinsFontFamily,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    if (showBarcodeDialog && capturedBarcodeData != null) {
        BarcodeResultDialog(
            barcodeData = capturedBarcodeData!!,
            onDismiss = {
                showBarcodeDialog = false
                previewPaused = false
                capturedBarcodeData = null
            }
        )
    }
}


@Composable
fun BarcodeResultDialog(
    barcodeData: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color.White, shape = RoundedCornerShape(16.dp))
                .padding(24.dp)
                .widthIn(min = 260.dp, max = 320.dp)
        ) {
            Text(
                text = "BAR CODE",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = poppinsFontFamily,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = barcodeData,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = poppinsFontFamily,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )

                    var copied by remember { mutableStateOf(false) }
                    val scale = remember { Animatable(1f) }
                    val scope = rememberCoroutineScope()

                    Icon(
                        painter = painterResource(id = R.drawable.content_copy_24),
                        contentDescription = "Copy",
                        tint = Color.DarkGray,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                            }
                            .clickable {
                                scope.launch {
                                    scale.animateTo(
                                        targetValue = 1.3f,
                                        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
                                    )
                                    scale.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
                                    )
                                }
                                val clip = android.content.ClipData.newPlainText("Barcode", barcodeData)
                                clipboardManager.setPrimaryClip(clip)
                                copied = true
                            }
                    )

                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Schließen",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = poppinsFontFamily,
                    color = Color.White
                )

            }
        }
    }
}
