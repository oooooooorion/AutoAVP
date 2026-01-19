package com.example.autoavp.ui.scan

import android.Manifest
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.autoavp.domain.model.ScannedData
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScanEntryPoint {
    fun getAnalyzer(): AutoAvpAnalyzer
}

@Composable
fun ScanScreen(
    viewModel: ScanViewModel = hiltViewModel(),
    onFinishScan: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scanState by viewModel.scanState.collectAsState()
    val scannedCount by viewModel.scannedCount.collectAsState()
    val liveTracking by viewModel.liveTracking.collectAsState()
    val liveOcr by viewModel.liveOcr.collectAsState()
    val isManualMode by viewModel.isManualMode.collectAsState()
    
    val analyzer = remember {
        EntryPointAccessors.fromApplication(context, ScanEntryPoint::class.java).getAnalyzer()
    }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var isFlashEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    
    // Zoom state
    var zoomRatio by remember { mutableStateOf(1f) }
    
    // Pour le Tap-to-focus
    var previewViewForFocus by remember { mutableStateOf<PreviewView?>(null) }

    // Pour l'effet de flash visuel
    var showFlashOverlay by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (showFlashOverlay) 0.8f else 0f,
        animationSpec = tween(durationMillis = 100),
        finishedListener = { showFlashOverlay = false },
        label = "FlashAlpha"
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.CAMERA) }

    LaunchedEffect(scanState) {
        if (scanState is ScanUiState.Finished) {
            onFinishScan()
        }
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    previewViewForFocus = previewView
                    
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0

                        val preview = Preview.Builder()
                            .setTargetRotation(rotation)
                            .build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                        // Résolution 1080p pour une précision maximale sur les petits codes
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1920, 1080),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                                )
                            ).build()

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setTargetRotation(rotation)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val capture = ImageCapture.Builder()
                            .setTargetRotation(rotation)
                            .setResolutionSelector(resolutionSelector) // On veut aussi la HD pour la capture
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture

                        // Configuration des callbacks de l'analyseur
                        analyzer.onDetectionUpdate = { tracking: String?, ocr: String? ->
                            viewModel.onLiveDetection(tracking, ocr)
                        }
                        
                        analyzer.onResult = { data: ScannedData, isManualTrigger: Boolean -> 
                            // Si le mode manuel est activé, on ignore les déclenchements automatiques
                            if (viewModel.isManualMode.value && !isManualTrigger) {
                                // Do nothing
                            } else {
                                if (isManualTrigger) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.onDataScanned(data, isManualTrigger)
                            }
                        }
                        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)

                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner, 
                                CameraSelector.DEFAULT_BACK_CAMERA, 
                                preview, 
                                imageAnalysis,
                                capture
                            )
                            cameraControl = camera.cameraControl
                        } catch (e: Exception) { Log.e("ScanScreen", "Binding failed", e) }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val factory = previewViewForFocus?.let { 
                                SurfaceOrientedMeteringPointFactory(it.width.toFloat(), it.height.toFloat()) 
                            }
                            val point = factory?.createPoint(offset.x, offset.y)
                            if (point != null && cameraControl != null) {
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                cameraControl?.startFocusAndMetering(action)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomRatio = (zoomRatio * zoom).coerceIn(1f, 10f)
                            cameraControl?.setZoomRatio(zoomRatio)
                        }
                    }
            )

            // Effet Flash visuel
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))

            // HUD de détection en temps réel (Haut Centre)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .fillMaxWidth(0.8f)
                    .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = liveTracking ?: "Code: non détecté",
                    color = if (liveTracking != null) Color.Green else Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (liveOcr.isNullOrBlank()) "Texte: recherche..." else liveOcr!!,
                    color = if (!liveOcr.isNullOrBlank()) Color.Yellow else Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // 1. Guide de cadrage (Format bloc adresse)
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Ciblez le bloc SmartData / Adresse",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(2.0f) // Ratio plus compact pour le bloc
                            .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)))
                    )
                }
            }

            // Bouton Flash (Haut Gauche)
            IconButton(
                onClick = { 
                    isFlashEnabled = !isFlashEnabled
                    cameraControl?.enableTorch(isFlashEnabled)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (isFlashEnabled) Color.Yellow else Color.White
                )
            }

            // Switch Mode Manuel (Haut Droite, à côté des paramètres)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 70.dp) // Décalé par rapport au bouton settings
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manuel",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Switch(
                    checked = isManualMode,
                    onCheckedChange = { viewModel.toggleManualMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray
                    ),
                    modifier = Modifier.size(width = 36.dp, height = 20.dp)
                )
            }

            // 2. Bouton de Capture Manuelle (Prise de PHOTO réelle)
            IconButton(
                onClick = { 
                    imageCapture?.let { capture ->
                        showFlashOverlay = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        
                        capture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                                        analyzer.processHighQualityImage(inputImage) { scannedData ->
                                            if (scannedData != null) {
                                                viewModel.onDataScanned(scannedData, true)
                                            }
                                            imageProxy.close()
                                        }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                    Log.e("ScanScreen", "Capture failed", exception)
                                }
                            }
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
                    .border(BorderStroke(4.dp, Color.White), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capturer",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Paramètres",
                    tint = Color.White
                )
            }

            if (scanState is ScanUiState.Success) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).size(100.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Green,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
            
            if (scanState is ScanUiState.Duplicate) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).size(140.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Yellow,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("Déjà scanné", color = Color.White)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "$scannedCount",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.size(16.dp))

                // Bouton Terminer
                ExtendedFloatingActionButton(
                    onClick = onFinishScan,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    text = { Text("Terminer la pile") }
                )
            }
        }
    }
}
