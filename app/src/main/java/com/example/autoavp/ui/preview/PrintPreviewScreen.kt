package com.example.autoavp.ui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.autoavp.R
import com.example.autoavp.data.local.entities.InstanceOfficeEntity
import com.example.autoavp.data.local.entities.MailItemEntity
import com.example.autoavp.ui.print.AvpPdfGenerator
import com.example.autoavp.ui.print.PrintConfig
import com.example.autoavp.ui.print.PrintOrientation
import com.example.autoavp.ui.print.PrintUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintPreviewScreen(
    navController: NavController,
    viewModel: PrintPreviewViewModel = hiltViewModel()
) {
    val items by viewModel.mailItems.collectAsState()
    val office by viewModel.office.collectAsState()
    val calibX by viewModel.calibrationX.collectAsState(initial = 0f)
    val calibY by viewModel.calibrationY.collectAsState(initial = 0f)
    val context = LocalContext.current
    var orientation by remember { mutableStateOf(PrintOrientation.HORIZONTAL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aperçu avant impression") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            BottomAppBar {
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        office?.let {
                            val generator = AvpPdfGenerator(context)
                            generator.printSession(items, it, orientation, calibX, calibY)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    enabled = office != null && items.isNotEmpty()
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Lancer l'impression")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sens d\u0027insertion dans l\u0027imprimante :", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = orientation == PrintOrientation.HORIZONTAL,
                    onClick = { orientation = PrintOrientation.HORIZONTAL }
                )
                Text("Horizontal (Long c\u00f4t\u00e9)")
                Spacer(Modifier.width(16.dp))
                RadioButton(
                    selected = orientation == PrintOrientation.VERTICAL,
                    onClick = { orientation = PrintOrientation.VERTICAL }
                )
                Text("Vertical (Petit c\u00f4t\u00e9)")
            }

            Spacer(Modifier.height(24.dp))
            
            if (items.isNotEmpty() && office != null) {
                Text("Aper\u00e7u du premier avis (sur ${items.size}) :", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                
                AvpVisualPreview(item = items.first(), office = office!!, calibX = calibX, calibY = calibY)
                
                if (calibX != 0f || calibY != 0f) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Calibration active : X=${("%.1f".format(calibX))}mm, Y=${("%.1f".format(calibY))}mm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun AvpVisualPreview(item: MailItemEntity, office: InstanceOfficeEntity, calibX: Float, calibY: Float) {
    val scale = 1.5f
    val widthDp = 210.dp * scale
    val heightDp = 99.dp * scale

    Box(
        modifier = Modifier
            .size(widthDp, heightDp)
            .background(Color.White)
    ) {
        Image(
            painter = painterResource(id = R.drawable.avp_template),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val ratioX = canvasWidth / 595f
            val ratioY = canvasHeight / 281f

            drawContext.canvas.nativeCanvas.apply {
                save()
                translate(PrintUtils.mmToPoints(calibX) * ratioX, PrintUtils.mmToPoints(calibY) * ratioY)

                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    isAntiAlias = true
                }

                // 1. Tracking
                paint.textSize = 13f * ratioY
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                val trackingText = item.trackingNumber ?: ""
                val maxTrackingWidth = PrintUtils.mmToPoints(PrintConfig.TRACKING_BOX_W) * ratioX
                while (paint.measureText(trackingText) > maxTrackingWidth && paint.textSize > 6f * ratioY) {
                    paint.textSize -= 0.5f * ratioY
                }
                drawText(trackingText, PrintUtils.mmToPoints(PrintConfig.TRACKING_BOX_X) * ratioX, PrintUtils.mmToPoints(PrintConfig.TRACKING_BOX_Y + 5.5f) * ratioY, paint)

                // 2. Adresse
                paint.textSize = 11f * ratioY
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                val addrX = PrintUtils.mmToPoints(PrintConfig.ADDR_BOX_X) * ratioX
                val startAddrY = PrintUtils.mmToPoints(PrintConfig.ADDR_BOX_Y + 4.5f) * ratioY
                val addrBoxBottom = PrintUtils.mmToPoints(PrintConfig.ADDR_BOX_Y + PrintConfig.ADDR_BOX_H) * ratioY
                val lineHeight = (paint.descent() - paint.ascent()) * 0.9f 
                val linesAddr = (item.recipientAddress ?: "").split("\n")
                var currentAddrY = startAddrY
                for (line in linesAddr) {
                    if (currentAddrY + paint.descent() > addrBoxBottom) break
                    if (line.isNotBlank()) drawText(line.trim(), addrX, currentAddrY, paint)
                    currentAddrY += lineHeight
                }

                // 3. Bureau d'Instance (Centrage Paragraph)
                val instX = PrintUtils.mmToPoints(PrintConfig.INSTANCE_BOX_X) * ratioX
                val instY = PrintUtils.mmToPoints(PrintConfig.INSTANCE_BOX_Y) * ratioY
                val instW = PrintUtils.mmToPoints(PrintConfig.INSTANCE_BOX_W) * ratioX
                val instH = PrintUtils.mmToPoints(PrintConfig.INSTANCE_BOX_H) * ratioY

                val bgPaint = android.graphics.Paint().apply {
                    color = try { android.graphics.Color.parseColor(office.colorHex) } catch (e: Exception) { android.graphics.Color.parseColor("#FFCE00") }
                    alpha = 100
                    style = android.graphics.Paint.Style.FILL
                }
                drawRect(instX, instY, instX + instW, instY + instH, bgPaint)

                val paragraphLines = mutableListOf<Pair<String, Boolean>>()
                val introText = "Votre objet sera disponible à partir de la date et de l'heure indiquées sur l'avis à l'emplacement suivant"
                paint.textSize = 6.5f * ratioY
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                
                // Wrap simple pour l'aperçu
                fun wrapPreview(text: String, maxWidth: Float): List<String> {
                    val words = text.split(" ")
                    val res = mutableListOf<String>()
                    var curr = ""
                    for (w in words) {
                        val t = if (curr.isEmpty()) w else "$curr $w"
                        if (paint.measureText(t) <= maxWidth) curr = t else { res.add(curr); curr = w }
                    }
                    if (curr.isNotEmpty()) res.add(curr)
                    return res
                }

                paragraphLines.addAll(wrapPreview(introText, instW - 10f * ratioX).map { it to false })
                paragraphLines.add(office.name to true)
                office.address.split("\n").take(2).forEach { paragraphLines.add(it to false) }
                paragraphLines.add(office.openingHours to false)

                val spacing = 8.5f * ratioY
                val totalHeight = paragraphLines.size * spacing
                var currY = instY + (instH - totalHeight) / 2f + 6f * ratioY
                
                paint.textAlign = android.graphics.Paint.Align.CENTER
                val centerX = instX + instW / 2f

                paragraphLines.forEach { (text, isBold) ->
                    paint.typeface = if (isBold) android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) 
                                     else android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                    paint.textSize = (if (isBold) 8.5f else 6.5f) * ratioY
                    drawText(text.trim(), centerX, currY, paint)
                    currY += spacing
                }

                restore()
            }
        }
    }
}
