package com.calclens.ui

import android.graphics.RectF
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.calclens.camera.CameraManager
import com.calclens.overlay.BadgeLayout
import com.calclens.overlay.CollisionAvoidance
import com.calclens.overlay.CoordinateTransformer
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

    var torchEnabled by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    val currentPaused by rememberUpdatedState(isPaused)
    var showAboutDialog by remember { mutableStateOf(false) }

    var activeLayouts by remember { mutableStateOf<List<BadgeLayout>>(emptyList()) }
    var globalStatus by remember { mutableStateOf("SEARCHING") }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.shutdown()
        }
    }

    // Function to process vision candidates into tracked layout entities
    val processCandidates = remember {
        { candidates: List<VisionCandidate> ->
            if (!currentPaused) {
                val entities = tracker.updateWithVisionCandidates(candidates)

                val layouts = entities
                    .filter { it.status != TrackingStatus.LOST || (System.currentTimeMillis() - it.lastSeen < 400L) }
                    .map { entity ->
                        val exprRect = transformer.toScreenRect(entity.smoothedBox)
                        val textLength = (entity.result ?: entity.errorMessage ?: "..").length
                        val badgeRect = transformer.computeBadgePlacement(
                            expressionRect = exprRect,
                            textLength = textLength
                        )
                        BadgeLayout(
                            id = entity.id,
                            badgeRect = badgeRect,
                            expressionRect = exprRect,
                            result = entity.result,
                            errorMessage = entity.errorMessage,
                            status = entity.status,
                            rawText = entity.rawText
                        )
                    }

                activeLayouts = CollisionAvoidance.resolveCollisions(layouts)
                globalStatus = when {
                    entities.any { it.status == TrackingStatus.DISPLAYING } -> "SOLVED"
                    entities.any { it.status == TrackingStatus.TRACKING } -> "TRACKING"
                    entities.any { it.status == TrackingStatus.DETECTED } -> "RECOGNIZING"
                    else -> "SEARCHING"
                }
            }
        }
    }

    val analyzer = remember {
        TextRecognitionAnalyzer { candidates, imgW, imgH ->
            if (!currentPaused) {
                transformer.sensorWidth = imgW.toFloat()
                transformer.sensorHeight = imgH.toFloat()
                processCandidates(candidates)
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

        // Layer 2: Augmented Mathematical Overlays
        for (layout in activeLayouts) {
            val leftDp = with(density) { layout.badgeRect.left.toDp() }
            val topDp = with(density) { layout.badgeRect.top.toDp() }
            val widthDp = with(density) { layout.badgeRect.width.toDp() }
            val heightDp = with(density) { layout.badgeRect.height.toDp() }

            val exprLeftDp = with(density) { layout.expressionRect.left.toDp() }
            val exprTopDp = with(density) { layout.expressionRect.top.toDp() }
            val exprWidthDp = with(density) { layout.expressionRect.width.toDp() }
            val exprHeightDp = with(density) { layout.expressionRect.height.toDp() }

            // Reticle around recognized physical text
            Box(
                modifier = Modifier
                    .offset(x = exprLeftDp, y = exprTopDp)
                    .size(width = exprWidthDp, height = exprHeightDp)
                    .border(1.5.dp, Color(0x7760A5FA), RoundedCornerShape(6.dp))
            )

            // Anchored Answer Badge
            val borderColor = if (layout.errorMessage != null) AccentRed else AccentGreen
            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(width = widthDp, height = heightDp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FrostedGlass)
                    .border(1.5.dp, borderColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (layout.result != null) {
                    Text(
                        text = layout.result,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (layout.errorMessage != null) {
                    Text(
                        text = layout.errorMessage,
                        color = Color(0xFFFCA5A5),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "...",
                        color = TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

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
                    else -> Color(0xFFEAB308)
                }
                val statusLabel = when {
                    isPaused -> "Paused"
                    globalStatus == "SOLVED" || globalStatus == "TRACKING" -> "Live"
                    globalStatus == "RECOGNIZING" -> "Reading..."
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
