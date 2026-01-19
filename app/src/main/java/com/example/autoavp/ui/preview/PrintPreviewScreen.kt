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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.autoavp.R
import com.example.autoavp.data.local.entities.InstanceOfficeEntity
import com.example.autoavp.data.local.entities.MailItemEntity
import com.example.autoavp.ui.print.AvpPdfGenerator
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
                            generator.printSession(items, it, orientation)
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
    ) {
        innerPadding ->
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
                
                AvpVisualPreview(item = items.first(), office = office!!)
            }
        }
    }
}

@Composable
fun AvpVisualPreview(item: MailItemEntity, office: InstanceOfficeEntity) {
    // Le format r\u00e9el est 210x99. On scale pour l\u0027affichage \u00e9cran.
    val scale = 1.5f // Ajustement visuel pour le t\u00e9l\u00e9phone
    val widthDp = 210.dp * scale
    val heightDp = 99.dp * scale

    Box(
        modifier = Modifier
            .size(widthDp, heightDp)
            .background(Color.White)
    ) {
        // Image de fond (Template)
        Image(
            painter = painterResource(id = R.drawable.avp_template),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // Overlay Texte avec Canvas pour matcher le PDF
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // Ratio entre les points PDF (595x281) et les pixels du Canvas
            val ratioX = canvasWidth / 595f
            val ratioY = canvasHeight / 281f

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    isAntiAlias = true
                }

                // 1. Tracking
                paint.textSize = 13f * ratioY
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                drawText(
                    item.trackingNumber ?: "",
                    PrintUtils.mmToPoints(22f) * ratioX,
                    PrintUtils.mmToPoints(52f) * ratioY,
                    paint
                )

                // 2. Adresse
                paint.textSize = 11f * ratioY
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                var addrY = PrintUtils.mmToPoints(66f) * ratioY
                val lineHeight = (paint.descent() - paint.ascent()) * 0.9f
                
                (item.recipientAddress ?: "").split("\n").take(4).forEach { line ->
                    if (line.isNotBlank()) {
                        drawText(line.trim(), PrintUtils.mmToPoints(22f) * ratioX, addrY, paint)
                        addrY += lineHeight
                    }
                }

                // 3. Bureau d\u0027Instance (Rectangle + Texte)
                val instX = PrintUtils.mmToPoints(122f) * ratioX
                val instY = PrintUtils.mmToPoints(55f) * ratioY
                val instW = PrintUtils.mmToPoints(68f) * ratioX
                val instH = PrintUtils.mmToPoints(32f) * ratioY

                val bgPaint = android.graphics.Paint().apply {
                    color = try { android.graphics.Color.parseColor(office.colorHex) } catch (e: Exception) { android.graphics.Color.parseColor("#FFCE00") }
                    style = android.graphics.Paint.Style.FILL
                }
                drawRect(instX, instY - 10f*ratioY, instX + instW, instY + instH - 10f*ratioY, bgPaint)

                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                paint.textSize = 9f * ratioY
                var currY = instY + 2f*ratioY
                drawText(office.name, instX + 5f*ratioX, currY, paint)

                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                paint.textSize = 7.5f * ratioY
                office.address.split("\n").take(3).forEach { line ->
                    currY += lineHeight * 0.7f
                    drawText(line.trim(), instX + 5f*ratioX, currY, paint)
                }
                currY += lineHeight * 0.8f
                drawText(office.openingHours, instX + 5f*ratioX, currY, paint)
            }
        }
    }
}
