package com.calclens.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.calclens.camera.CameraManager
import com.calclens.overlay.CoordinateTransformer
import com.calclens.overlay.StickyOverlayView
import com.calclens.tracking.ImuMotionPredictor
import com.calclens.tracking.SpatialTracker
import com.calclens.tracking.TrackingStatus
import com.calclens.ui.theme.*
import com.calclens.vision.TextRecognitionAnalyzer
import com.calclens.vision.VisionCandidate

@Composable
fun CalcLensScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraManager = remember { CameraManager(context) }
    val tracker = remember { SpatialTracker() }
    val transformer = remember { CoordinateTransformer(viewportWidth = 1080f, viewportHeight = 1920f) }
    val imuPredictor = remember { ImuMotionPredictor(context) }

    var torchEnabled by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    val currentPaused by rememberUpdatedState(isPaused)
    var showAboutDialog by remember { mutableStateOf(false) }

    var globalStatus by remember { mutableStateOf("SEARCHING") }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.shutdown()
            imuPredictor.stop()
        }
    }

    val analyzer = remember {
        TextRecognitionAnalyzer { candidates, imgW, imgH, hasTooFarText ->
            if (!currentPaused) {
                transformer.sensorWidth = imgW.toFloat()
                transformer.sensorHeight = imgH.toFloat()
                val entities = tracker.updateWithVisionCandidates(candidates)
                globalStatus = when {
                    entities.any { it.status == TrackingStatus.DISPLAYING } -> "SOLVED"
                    entities.any { it.status == TrackingStatus.TRACKING } -> "TRACKING"
                    entities.any { it.status == TrackingStatus.DETECTED } -> "RECOGNIZING"
                    hasTooFarText -> "TOO_FAR"
                    else -> "SEARCHING"
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        LaunchedEffect(widthPx, heightPx) {
            transformer.viewportWidth = widthPx
            transformer.viewportHeight = heightPx
        }

        // Layer 1: Native CameraX Viewfinder
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    cameraManager.startCamera(lifecycleOwner, this, analyzer)
                }
            }
        )

        // Layer 2: Hardware-Accelerated 120Hz Sticky Mathematical Overlay
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                StickyOverlayView(ctx, tracker, imuPredictor, transformer)
            }
        )

        // Layer 3: Top Control Bar (Clean Minimal Production UI)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Pill
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(FrostedGlass)
                    .border(1.dp, PillBorder, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotColor = when {
                    isPaused -> Color(0xFF94A3B8)
                    globalStatus == "SOLVED" || globalStatus == "TRACKING" -> AccentGreen
                    globalStatus == "RECOGNIZING" -> AccentBlue
                    globalStatus == "TOO_FAR" -> Color(0xFFF59E0B)
                    else -> Color(0xFFEAB308)
                }
                val statusLabel = when {
                    isPaused -> "Paused"
                    globalStatus == "SOLVED" || globalStatus == "TRACKING" -> "Live"
                    globalStatus == "RECOGNIZING" -> "Reading..."
                    globalStatus == "TOO_FAR" -> "Move closer"
                    else -> "Point at math"
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusLabel,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Controls: Pause, Torch, Info
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { isPaused = !isPaused },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) AccentBlue else FrostedGlass
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isPaused) "Resume" else "Pause",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = {
                        torchEnabled = !torchEnabled
                        cameraManager.toggleTorch(torchEnabled)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (torchEnabled) AccentBlue else FrostedGlass
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = "Torch", fontSize = 12.sp, color = TextPrimary)
                }

                Button(
                    onClick = { showAboutDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = FrostedGlass),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "About", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }

        // About / Info Modal
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                containerColor = FrostedGlass,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                title = {
                    Text(text = "CalcLens", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(text = "Continuous, camera-first spatial arithmetic solver.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "• Point at any printed horizontal equation or vertical column arithmetic.")
                        Text(text = "• 100% on-device vision and computation.")
                        Text(text = "• Zero image pixels ever leave your device.")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showAboutDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text(text = "Done", color = Color.White)
                    }
                }
            )
        }
    }
}
