package com.bbcl.carromoffline

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/*
 * Plays between phones over classic Bluetooth only (no Wi-Fi, no internet). One phone hosts a
 * room and the others connect straight to it; every message goes through the host. Insecure
 * RFCOMM sockets are used so the phones don't need to be paired first.
 */

/** Something that happened on the link. The host is always peer 0; the host numbers its guests from 1. */
sealed interface LinkEvent {
    data class Joined(val peer: Int) : LinkEvent
    data class Message(val peer: Int, val data: JSONObject) : LinkEvent
    data class Left(val peer: Int) : LinkEvent
}

fun bluetoothAdapter(context: Context): BluetoothAdapter? =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

/** Runtime permissions Bluetooth play needs on this Android version. */
fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        // Before Android 12, finding nearby devices counts as using location.
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

fun hasBluetoothPermissions(context: Context) =
    bluetoothPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/** This phone's Bluetooth name, which the others look for in their list. */
@SuppressLint("MissingPermission")
fun phoneName(context: Context): String =
    runCatching { bluetoothAdapter(context)?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: Build.MODEL

@SuppressLint("MissingPermission")
fun BluetoothDevice.label(): String = runCatching { name }.getOrNull()?.takeIf { it.isNotBlank() } ?: address

/**
 * Asks for whatever is missing (permissions, then switching Bluetooth on) and runs the action
 * once everything is ready. [onProblem] gets a message if the person says no.
 */
@Composable
fun rememberBluetoothGate(onProblem: (String) -> Unit): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val action = pending
        pending = null
        if (bluetoothAdapter(context)?.isEnabled == true) action?.invoke() else onProblem("Bluetooth is off. Turn it on to play together.")
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            if (bluetoothAdapter(context)?.isEnabled == true) {
                pending?.invoke()
                pending = null
            } else {
                enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } else {
            pending = null
            onProblem("Allow \"Nearby devices\" so the phones can find each other.")
        }
    }
    return { action ->
        val adapter = bluetoothAdapter(context)
        when {
            adapter == null -> onProblem("This phone has no Bluetooth.")
            !hasBluetoothPermissions(context) -> {
                pending = action
                permissions.launch(bluetoothPermissions())
            }
            !adapter.isEnabled -> {
                pending = action
                enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            else -> action()
        }
    }
}

/** Finds nearby phones: paired ones straight away, then whatever Bluetooth discovery turns up. */
class BluetoothScanner(private val context: Context) {
    val devices = mutableStateListOf<BluetoothDevice>()
    var scanning by mutableStateOf(false)
        private set
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && devices.none { it.address == device.address }) devices += device
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> scanning = true
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val adapter = bluetoothAdapter(context) ?: return
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        devices.clear()
        runCatching { adapter.bondedDevices }.getOrNull()?.let { devices += it }
        runCatching {
            adapter.cancelDiscovery()
            scanning = adapter.startDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching { bluetoothAdapter(context)?.cancelDiscovery() }
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
        scanning = false
    }
}

/**
 * The connections between phones. Each message is a JSON object sent as a 4-byte length and
 * its UTF-8 bytes. Reading and writing happen on background threads; [events] reports what
 * arrives.
 */
class BluetoothLink(private val adapter: BluetoothAdapter, private val uuid: UUID, private val scope: CoroutineScope) {
    val events = MutableSharedFlow<LinkEvent>(extraBufferCapacity = 512)
    private val peers = ConcurrentHashMap<Int, Peer>()
    private var server: BluetoothServerSocket? = null
    private var nextId = 1

    private class Peer(val socket: BluetoothSocket) {
        val out = Channel<ByteArray>(Channel.UNLIMITED)
    }

    val connected get() = peers.size

    /** Starts accepting guests. */
    @SuppressLint("MissingPermission")
    fun host(serviceName: String) {
        val socket = adapter.listenUsingInsecureRfcommWithServiceRecord(serviceName, uuid)
        server = socket
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val guest = runCatching { socket.accept() }.getOrNull() ?: break
                attach(nextId++, guest)
            }
        }
    }

    fun stopAccepting() {
        runCatching { server?.close() }
        server = null
    }

    /** Connects to a host. Throws if the phone isn't hosting a room or is out of range. */
    @SuppressLint("MissingPermission")
    suspend fun join(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        runCatching { adapter.cancelDiscovery() }
        val socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
        try {
            socket.connect()
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
        attach(0, socket)
    }

    private fun attach(id: Int, socket: BluetoothSocket) {
        val peer = Peer(socket)
        peers[id] = peer
        scope.launch(Dispatchers.IO) {
            runCatching {
                val out = DataOutputStream(BufferedOutputStream(socket.outputStream))
                for (bytes in peer.out) {
                    out.writeInt(bytes.size)
                    out.write(bytes)
                    out.flush()
                }
            }
            drop(id)
        }
        scope.launch(Dispatchers.IO) {
            events.emit(LinkEvent.Joined(id))
            runCatching {
                val input = DataInputStream(BufferedInputStream(socket.inputStream))
                while (isActive) {
                    val size = input.readInt()
                    if (size !in 0..MAX_MESSAGE) break
                    val bytes = ByteArray(size)
                    input.readFully(bytes)
                    events.emit(LinkEvent.Message(id, JSONObject(String(bytes, Charsets.UTF_8))))
                }
            }
            drop(id)
        }
    }

    fun send(peer: Int, message: JSONObject) {
        peers[peer]?.out?.trySend(message.toString().toByteArray(Charsets.UTF_8))
    }

    fun broadcast(message: JSONObject, except: Int = -1) {
        val bytes = message.toString().toByteArray(Charsets.UTF_8)
        for ((id, peer) in peers) if (id != except) peer.out.trySend(bytes)
    }

    fun drop(id: Int) {
        val peer = peers.remove(id) ?: return
        peer.out.close()
        runCatching { peer.socket.close() }
        events.tryEmit(LinkEvent.Left(id))
    }

    fun close() {
        stopAccepting()
        peers.keys.toList().forEach(::drop)
    }

    companion object {
        private const val MAX_MESSAGE = 1 shl 20
    }
}
