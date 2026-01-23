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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
    onOpenSettings: () -> Unit = {},
    onScanResult: (ScannedData) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scanState by viewModel.scanState.collectAsState()
    val scannedCount by viewModel.scannedCount.collectAsState()
    val liveTracking by viewModel.liveTracking.collectAsState()
    val detectedBlocks by viewModel.detectedBlocks.collectAsState()
    val sourceImageSize by viewModel.sourceImageSize.collectAsState()
    val accumulationStatus by viewModel.accumulationStatus.collectAsState()
    
    val analyzer = remember {
        EntryPointAccessors.fromApplication(context, ScanEntryPoint::class.java).getAnalyzer()
    }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var isFlashEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    
    // Zoom state
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    
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
        if (scanState is ScanUiState.Success) {
            onScanResult((scanState as ScanUiState.Success).data)
        }
        if (scanState is ScanUiState.Finished) {
            onFinishScan()
        }
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewViewForFocus = this
                        
                        addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                            analyzer.updateViewport(right - left, bottom - top)
                        }
                        
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            
                            val preview = Preview.Builder()
                                .build().also {
                                    it.setSurfaceProvider(this.surfaceProvider)
                                }

                            // Résolution 1080p
                            val resolutionSelector = ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1920, 1080),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                                    )
                                ).build()

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setResolutionSelector(resolutionSelector)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            val capture = ImageCapture.Builder()
                                .setResolutionSelector(resolutionSelector)
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            imageCapture = capture

                            // Configuration des callbacks de l'analyseur
                            analyzer.onDetectionUpdate = { tracking, ocr, blocks, w, h ->
                                viewModel.onLiveDetection(tracking, ocr, blocks, w, h)
                            }
                            
                            analyzer.onResult = { data, isManualTrigger -> 
                                if (isManualTrigger) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val point = previewViewForFocus?.let { 
                                SurfaceOrientedMeteringPointFactory(it.width.toFloat(), it.height.toFloat()) 
                                    .createPoint(offset.x, offset.y)
                            }
                            if (point != null && cameraControl != null) {
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                cameraControl?.startFocusAndMetering(action)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val currentZoom = zoomRatio
                            val newZoom = (currentZoom * zoom).coerceIn(1f, 10f)
                            zoomRatio = newZoom
                            cameraControl?.setZoomRatio(newZoom)
                        }
                    }
            )

            // Visualisation des blocs détectés
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val screenW = size.width
                val screenH = size.height
                val (imgW, imgH) = sourceImageSize
                
                if (imgW > 0 && imgH > 0 && detectedBlocks.isNotEmpty()) {
                    // Calcul de la transformation FILL_CENTER (Center Crop)
                    // On détermine l'échelle pour remplir l'écran
                    val scale = maxOf(screenW / imgW, screenH / imgH)
                    
                    // Dimensions de l'image mise à l'échelle
                    val scaledW = imgW * scale
                    val scaledH = imgH * scale
                    
                    // Offsets pour centrer l'image
                    val offsetX = (screenW - scaledW) / 2f
                    val offsetY = (screenH - scaledH) / 2f

                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    val color = Color.Green.copy(alpha = 0.8f)

                    detectedBlocks.forEach { rect ->
                        // Transformation des coordonnées normalisées (0..1) vers l'écran
                        // 1. Dénormalisation vers taille image source
                        // 2. Application de l'échelle
                        // 3. Application de l'offset
                        
                        val left = rect.left * imgW * scale + offsetX
                        val top = rect.top * imgH * scale + offsetY
                        val right = rect.right * imgW * scale + offsetX
                        val bottom = rect.bottom * imgH * scale + offsetY

                        drawRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            style = stroke
                        )
                    }
                }
            }

            // Effet Flash visuel
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))

            // HUD de diagnostic en temps réel (Haut Centre)
            StatusHud(
                status = accumulationStatus,
                liveTracking = liveTracking,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            )

            // 1. Guide de cadrage (Format bloc adresse)
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Ciblez le bloc destinataire",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f) // Largeur augmentée à 85%
                            .aspectRatio(1.6f)   // Plus haut (ratio 1.6 au lieu de 2.0)
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
                                        // Sauvegarde de l'image manuelle
                                        var imagePath: String? = null
                                        try {
                                            val bitmap = imageProxy.toBitmap()
                                            val matrix = android.graphics.Matrix()
                                            matrix.postRotate(rotation.toFloat())
                                            val finalBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                            
                                            val file = java.io.File(context.filesDir, "manual_${System.currentTimeMillis()}.jpg")
                                            val out = java.io.FileOutputStream(file)
                                            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                                            out.flush()
                                            out.close()
                                            imagePath = file.absolutePath
                                        } catch (e: Exception) {
                                            Log.e("ScanScreen", "Failed to save manual image", e)
                                        }

                                        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                                        analyzer.processHighQualityImage(inputImage, imagePath) { scannedData ->
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

@Composable
fun StatusHud(
    status: ScanViewModel.AccumulationStatus,
    liveTracking: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(0.9f),
        color = Color.Black.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Ligne Suivi / Tracking
            HudDataRow(
                label = "N° Suivi",
                value = status.tracking ?: liveTracking,
                isOk = status.tracking != null
            )

            if (status.isSmartData) {
                HudDataRow(
                    label = "Clé OCR",
                    value = status.ocrKey,
                    isOk = status.ocrKey != null
                )
            }

            HudDataRow(
                label = "Adresse",
                value = status.address?.replace("\n", " "),
                isOk = status.address != null,
                maxLines = 3
            )
        }
    }
}

@Composable
fun HudDataRow(label: String, value: String?, isOk: Boolean, maxLines: Int = 1) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label :",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value ?: "En attente...",
            style = MaterialTheme.typography.bodySmall,
            color = if (isOk) Color.Green else Color.Yellow.copy(alpha = 0.8f),
            fontWeight = if (isOk) FontWeight.Bold else FontWeight.Normal,
            maxLines = maxLines,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isOk) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.Green,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
