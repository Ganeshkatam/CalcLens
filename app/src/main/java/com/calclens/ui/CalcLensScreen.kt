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
import kotlinx.coroutines.delay

@Composable
fun CalcLensScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraManager = remember { CameraManager(context) }
    val tracker = remember { SpatialTracker() }
    val transformer = remember { CoordinateTransformer(viewportWidth = 1080f, viewportHeight = 1920f) }

    var torchEnabled by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var activeMode by remember { mutableStateOf("LIVE_CAMERA") }

    var activeLayouts by remember { mutableStateOf<List<BadgeLayout>>(emptyList()) }
    var globalStatus by remember { mutableStateOf("SEARCHING") }
    var lastOcrLatency by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.shutdown()
        }
    }

    // Function to process vision candidates into tracked layout entities
    val processCandidates = remember {
        { candidates: List<VisionCandidate> ->
            val start = System.currentTimeMillis()
            val entities = tracker.updateWithVisionCandidates(candidates)
            lastOcrLatency = System.currentTimeMillis() - start

            val layouts = entities
                .filter { it.status != TrackingStatus.LOST || (System.currentTimeMillis() - it.lastSeen < 400L) }
                .map { entity ->
                    val exprRect = transformer.toScreenRect(entity.smoothedBox)
                    val badgeRect = transformer.computeBadgePlacement(exprRect)
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
                entities.any { it.status == TrackingStatus.DISPLAYING } -> "DISPLAYING"
                entities.any { it.status == TrackingStatus.TRACKING } -> "TRACKING"
                entities.any { it.status == TrackingStatus.DETECTED } -> "DETECTED"
                else -> "SEARCHING"
            }
        }
    }

    val analyzer = remember {
        TextRecognitionAnalyzer { candidates ->
            if (activeMode == "LIVE_CAMERA") {
                processCandidates(candidates)
            }
        }
    }

    // Interactive Demo Simulation Loop for on-device scenario validation
    LaunchedEffect(activeMode) {
        if (activeMode == "DEMO_SINGLE") {
            tracker.clear()
            while (true) {
                processCandidates(
                    listOf(
                        VisionCandidate(
                            id = "demo-1",
                            rawText = "27 × 14",
                            normalizedText = "27 * 14",
                            boundingBox = RectF(0.25f, 0.40f, 0.75f, 0.47f),
                            confidence = 0.96f
                        )
                    )
                )
                delay(250)
            }
        } else if (activeMode == "DEMO_MOTION") {
            var step = 0f
            while (true) {
                step += 0.08f
                val dx = kotlin.math.sin(step) * 0.12f
                val dy = kotlin.math.cos(step) * 0.06f

                processCandidates(
                    listOf(
                        VisionCandidate(
                            id = "demo-motion",
                            rawText = "27 × 14",
                            normalizedText = "27 * 14",
                            boundingBox = RectF(0.25f + dx, 0.40f + dy, 0.75f + dx, 0.47f + dy),
                            confidence = 0.96f
                        )
                    )
                )
                delay(100)
            }
        } else if (activeMode == "DEMO_MULTI") {
            tracker.clear()
            while (true) {
                processCandidates(
                    listOf(
                        VisionCandidate(
                            id = "demo-m1",
                            rawText = "12 + 8",
                            normalizedText = "12 + 8",
                            boundingBox = RectF(0.12f, 0.30f, 0.45f, 0.36f),
                            confidence = 0.95f
                        ),
                        VisionCandidate(
                            id = "demo-m2",
                            rawText = "7 × 9",
                            normalizedText = "7 * 9",
                            boundingBox = RectF(0.55f, 0.30f, 0.88f, 0.36f),
                            confidence = 0.94f
                        ),
                        VisionCandidate(
                            id = "demo-m3",
                            rawText = "100 ÷ 4",
                            normalizedText = "100 / 4",
                            boundingBox = RectF(0.28f, 0.55f, 0.72f, 0.61f),
                            confidence = 0.93f
                        )
                    )
                )
                delay(250)
            }
        } else if (activeMode == "DEMO_ZERO") {
            tracker.clear()
            while (true) {
                processCandidates(
                    listOf(
                        VisionCandidate(
                            id = "demo-zero",
                            rawText = "100 ÷ 0",
                            normalizedText = "100 / 0",
                            boundingBox = RectF(0.25f, 0.42f, 0.75f, 0.49f),
                            confidence = 0.97f
                        )
                    )
                )
                delay(250)
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
                    cameraManager.startCamera(lifecycleOwner, this, analyzer)
                }
            }
        )

        // Layer 2: Augmented Mathematical Overlays
        for (layout in activeLayouts) {
            val leftDp = with(density) { layout.badgeRect.left.toDp() }
            val topDp = with(density) { layout.badgeRect.top.toDp() }
            val widthDp = with(density) { layout.badgeRect.width().toDp() }
            val heightDp = with(density) { layout.badgeRect.height().toDp() }

            val exprLeftDp = with(density) { layout.expressionRect.left.toDp() }
            val exprTopDp = with(density) { layout.expressionRect.top.toDp() }
            val exprWidthDp = with(density) { layout.expressionRect.width().toDp() }
            val exprHeightDp = with(density) { layout.expressionRect.height().toDp() }

            // Dashed reticle around recognized physical text
            Box(
                modifier = Modifier
                    .offset(x = exprLeftDp, y = exprTopDp)
                    .size(width = exprWidthDp, height = exprHeightDp)
                    .border(1.dp, Color(0x6660A5FA), RoundedCornerShape(6.dp))
            )

            // Anchored Answer Badge
            val borderColor = if (layout.errorMessage != null) AccentRed else AccentGreen
            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(width = widthDp, height = heightDp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FrostedGlass)
                    .border(1.5.dp, borderColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (layout.result != null) {
                    Text(
                        text = layout.result,
                        color = Color.White,
                        fontSize = 20.sp,
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

        // Layer 3: Top Control Bar
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
                val dotColor = when (globalStatus) {
                    "DISPLAYING", "TRACKING" -> AccentGreen
                    "DETECTED" -> AccentBlue
                    else -> Color(0xFFEAB308)
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = globalStatus,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    onClick = { showStats = !showStats },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showStats) AccentBlue else FrostedGlass
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = "Stats", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }

        // Diagnostic Stats Drawer
        if (showStats) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FrostedGlass)
                    .border(1.dp, PillBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(text = "CALCLENS DIAGNOSTICS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Mode: $activeMode", fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    Text(text = "Active Tracks: ${activeLayouts.size}", fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    Text(text = "Processing Latency: ${lastOcrLatency} ms", fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Layer 4: Interactive Mode Bar (Bottom)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(FrostedGlass)
                .border(1.dp, PillBorder, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val modes = listOf(
                "LIVE_CAMERA" to "Live OCR",
                "DEMO_SINGLE" to "27 × 14",
                "DEMO_MOTION" to "Track Motion",
                "DEMO_MULTI" to "Multi",
                "DEMO_ZERO" to "100 ÷ 0"
            )

            for ((modeKey, label) in modes) {
                val isSelected = activeMode == modeKey
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AccentBlue else Color.Transparent)
                        .clickable { activeMode = modeKey }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
