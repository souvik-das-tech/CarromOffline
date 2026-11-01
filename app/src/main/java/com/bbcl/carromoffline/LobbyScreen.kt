package com.bbcl.carromoffline

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BlueBrush = Brush.verticalGradient(listOf(Color(0xFF7DBBFF), Color(0xFF1F7FF0), Color(0xFF0B48A6)))

/** Create a room or join one on a nearby phone, see who is in, and (as host) start the game. */
@Composable
fun LobbyScreen(model: GameModel) {
    val context = LocalContext.current
    val gate = rememberBluetoothGate { model.netMessage = it }
    val scanner = remember { BluetoothScanner(context) }
    var browsing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { scanner.stop() } }
    val visible = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val makeVisible = {
        runCatching {
            visible.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300))
        }
    }
    BackHandler {
        if (browsing) {
            scanner.stop()
            browsing = false
        } else {
            model.closeLobby()
        }
    }

    GameBackground {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") {
                        scanner.stop()
                        model.closeLobby()
                    }
                    Spacer(Modifier.weight(1f))
                }
                BluetoothBadge(active = model.role != Role.LOCAL || browsing)
                Spacer(Modifier.height(10.dp))
                GoldText("PLAY TOGETHER", 30, spacing = 2f)
                Text(
                    "Each player on their own phone, over Bluetooth.\nNo internet or Wi‑Fi needed. Stay within a few metres.",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(18.dp))

                model.netMessage?.let { Problem(it) }

                when {
                    model.role == Role.HOST -> HostCard(model, onMakeVisible = { makeVisible() })
                    model.role == Role.GUEST -> GuestCard(model)
                    browsing -> Browser(model, scanner) { device ->
                        scanner.stop()
                        browsing = false
                        model.joinRoom(device)
                    }
                    else -> {
                        NameCard(model)
                        Spacer(Modifier.height(18.dp))
                        GlossyButton("CREATE ROOM", GoldBrush, {
                            gate {
                                model.hostRoom()
                                makeVisible()
                            }
                        }, Modifier.fillMaxWidth(), icon = Icons.Default.Bluetooth, textColor = Ink, height = 64.dp)
                        Spacer(Modifier.height(12.dp))
                        GlossyButton("JOIN ROOM", BlueBrush, {
                            gate {
                                model.netMessage = null
                                browsing = true
                                scanner.start()
                            }
                        }, Modifier.fillMaxWidth(), icon = Icons.Default.BluetoothSearching, height = 64.dp)
                        Spacer(Modifier.height(18.dp))
                        Steps()
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).background(GlassFill, CircleShape).border(1.dp, GlassLine, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White) }
}

/** A Bluetooth symbol with rings spreading out while a room is open or a search is running. */
@Composable
private fun BluetoothBadge(active: Boolean) {
    val wave by rememberInfiniteTransition(label = "wave").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "wave",
    )
    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Canvas(Modifier.fillMaxSize()) {
                for (k in 0..1) {
                    val t = (wave + k * 0.5f) % 1f
                    drawCircle(Color(0xFF7DBBFF).copy(alpha = 0.6f * (1 - t)), size.minDimension / 2 * (0.5f + 0.5f * t), style = Stroke(3.dp.toPx()))
                }
            }
        }
        Box(
            Modifier.size(64.dp).background(BlueBrush, CircleShape).border(3.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Bluetooth, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
    }
}

@Composable
private fun Problem(text: String) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(Color(0xFFE53935).copy(alpha = 0.18f), shape)
            .border(1.dp, Color(0xFFFF8A80).copy(alpha = 0.6f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFFF8A80))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun NameCard(model: GameModel) {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionLabel("YOUR NAME")
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = model.myName,
            onValueChange = { model.myName = it.take(14) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Column {
                    Box {
                        if (model.myName.isEmpty()) Text("Type your name", color = Color.White.copy(alpha = 0.4f), fontSize = 20.sp)
                        inner()
                    }
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.3f)))
                }
            },
        )
    }
}

@Composable
private fun Steps() {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionLabel("HOW IT WORKS")
        Spacer(Modifier.height(10.dp))
        listOf(
            "1" to "One person taps CREATE ROOM.",
            "2" to "Everyone else taps JOIN ROOM and picks that phone's name from the list.",
            "3" to "The host taps START. Empty seats are played by the computer.",
        ).forEach { (n, text) ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).background(GoldBrush, CircleShape), contentAlignment = Alignment.Center) {
                    Text(n, color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun HostCard(model: GameModel, onMakeVisible: () -> Unit) {
    val context = LocalContext.current
    val name = remember { phoneName(context) }
    GlassCard(Modifier.fillMaxWidth()) {
        SectionLabel("ROOM OPEN")
        Spacer(Modifier.height(8.dp))
        Text("Friends tap JOIN ROOM and choose:", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PhoneAndroid, null, tint = Gold, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(name, color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier.clickable(onClick = onMakeVisible).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Visibility, null, tint = Color(0xFF7DBBFF), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Not showing up for them? Tap to make this phone visible", color = Color(0xFF7DBBFF), fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("GAME")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((n, label) in listOf(2 to "1 vs 1", 4 to "2 vs 2")) {
                val ok = n >= model.members.size
                val selected = model.roomCount == n
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .alpha(if (ok) 1f else 0.35f)
                        .background(if (selected) GoldBrush else SolidColor(Color.Black.copy(alpha = 0.25f)), shape)
                        .border(1.dp, if (selected) Color.White.copy(alpha = 0.7f) else GlassLine, shape)
                        .clickable(enabled = ok) { model.changeRoomCount(n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) Ink else Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SeatList(model)
    }
    Spacer(Modifier.height(18.dp))
    val ready = model.members.size >= 2
    GlossyButton(
        if (ready) "START" else "WAITING FOR FRIENDS…",
        if (ready) GreenBrush else SolidColor(Color.White.copy(alpha = 0.12f)),
        { if (ready) model.startRoom() },
        Modifier.fillMaxWidth(),
        icon = if (ready) Icons.Default.PlayArrow else null,
        height = 64.dp,
    )
}

/** Players in turn order with their coin colour: who plays, or the computer if nobody does. */
@Composable
private fun SeatList(model: GameModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until model.roomCount) {
            val m = model.members.getOrNull(i)
            val me = i == model.myIndex
            val kind = if (i % 2 == 0) Kind.WHITE else Kind.BLACK
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = if (kind == Kind.WHITE) 0.15f else 0.3f), shape)
                    .border(if (me) 2.dp else 1.dp, if (me) Teal else GlassLine, shape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoinIcon(kind, Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            m == null -> "Computer"
                            me -> "${m.name} (You)"
                            else -> m.name
                        },
                        color = if (m == null) Color.White.copy(alpha = 0.55f) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        (if (kind == Kind.WHITE) "White" else "Black") + if (model.roomCount == 4) " team" else "",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                    )
                }
                if (m == null) {
                    Icon(Icons.Default.SmartToy, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
                } else {
                    Text(if (m.peer == 0) "HOST" else "JOINED", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
        if (model.roomCount == 4) {
            Text("Partners sit opposite each other.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun GuestCard(model: GameModel) {
    GlassCard(Modifier.fillMaxWidth()) {
        val connecting = model.connecting
        if (connecting != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Gold, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Connecting to", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    Text(connecting, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else if (model.members.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Gold, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Text("Joining the room…", color = Color.White, fontSize = 16.sp)
            }
        } else {
            SectionLabel("YOU'RE IN ${model.members.first().name.uppercase()}'S ROOM")
            Spacer(Modifier.height(10.dp))
            SeatList(model)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Gold, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Waiting for the host to start…", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
        }
    }
}

/** Nearby phones to join. Paired phones show up at once; others appear as the search finds them. */
@Composable
private fun Browser(model: GameModel, scanner: BluetoothScanner, onPick: (BluetoothDevice) -> Unit) {
    NameCard(model)
    Spacer(Modifier.height(14.dp))
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("NEARBY PHONES")
            Spacer(Modifier.weight(1f))
            if (scanner.scanning) {
                CircularProgressIndicator(color = Gold, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Row(Modifier.clickable { scanner.start() }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, null, tint = Gold, modifier = Modifier.size(18.dp))
                    Text(" Search again", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (scanner.devices.isEmpty()) {
            Text(
                if (scanner.scanning) "Looking for phones…" else "No phones found. Make sure the host tapped CREATE ROOM, then search again.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (device in scanner.devices.sortedBy { it.label().contains(":") }) {
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.25f), shape)
                        .border(1.dp, GlassLine, shape)
                        .clickable { onPick(device) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PhoneAndroid, null, tint = Color(0xFF7DBBFF))
                    Spacer(Modifier.width(10.dp))
                    Text(device.label(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("JOIN", color = Gold, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Pick the name shown on the host's screen.",
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 12.sp,
    )
}
