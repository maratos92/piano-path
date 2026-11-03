package org.pianopath.midi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.*
import android.os.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.pianopath.core.*
import java.util.UUID

data class PianoDevice(val address: String, val name: String)
data class MidiStatus(val status: String = "Not connected", val connected: Boolean = false, val scanning: Boolean = false,
    val devices: List<PianoDevice> = emptyList(), val held: Map<Int,Int> = emptyMap(), val sounding: Set<Int> = emptySet(),
    val sustain: Boolean = false, val events: List<String> = emptyList())

class BleMidi(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(MidiManager::class.java)
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val mutable = MutableStateFlow(MidiStatus())
    val state = mutable.asStateFlow()
    private val keyboard = KeyboardState()
    private var device: MidiDevice? = null
    private var port: MidiOutputPort? = null
    private var target: BluetoothDevice? = null
    private var reconnect = true
    private var attempt = 0
    @Volatile private var generation = 0
    private var openEpoch = 0
    var onEvent: ((MidiEvent) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
    private val parser = MidiParser { event -> val token=generation; handler.post { if(token==generation && device!=null)deliver(event) } }
    private val receiver = object: MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            parser.feed(data,offset,count,if(timestamp > 0) timestamp else System.nanoTime())
        }
        override fun onFlush() { handler.post { clearKeys() } }
    }
    private val callback = object: MidiManager.DeviceCallback() {
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if(device?.info?.id == info.id) {
                generation++; release(); clearKeys(); onDisconnect?.invoke()
                mutable.value = mutable.value.copy(connected=false,status="Connection lost · retrying")
                scheduleReconnect()
            }
        }
    }
    private val scanner = object: ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post {
                try {
                    val address = result.device.address
                    val name = result.scanRecord?.deviceName ?: result.device.name ?: "BLE MIDI piano"
                    val discovered = (mutable.value.devices + PianoDevice(address,name)).distinctBy { it.address }
                    mutable.value = mutable.value.copy(devices=discovered)
                } catch(_: SecurityException) { mutable.value = mutable.value.copy(status="Bluetooth permission required") }
            }
        }
        override fun onScanFailed(errorCode: Int) { mutable.value = mutable.value.copy(scanning=false,status="Scan failed ($errorCode). Toggle Bluetooth and retry.") }
    }
    init { manager?.registerDeviceCallback(callback,handler) }
    fun hasPermissions(): Boolean = requiredPermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    fun requiredPermissions(): Array<String> = if(Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
    @SuppressLint("MissingPermission") // hasPermissions checks version-specific grants; SecurityException is caught.
    fun scan() {
        if(!hasPermissions()) { mutable.value = mutable.value.copy(status="Grant Bluetooth access to discover your piano"); return }
        try {
            if(adapter?.isEnabled != true) { mutable.value = mutable.value.copy(status="Turn on Bluetooth in Android settings"); return }
            stopScan()
            mutable.value = mutable.value.copy(scanning=true,devices=emptyList(),status=if(device != null) "Connected · scanning" else "Looking for BLE MIDI pianos…")
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700"))).build()
            adapter.bluetoothLeScanner?.startScan(listOf(filter),ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanner)
            handler.postDelayed({ stopScan() },15000)
        } catch(e: Exception) { mutable.value = mutable.value.copy(scanning=false,status="Scan unavailable: ${e.message}") }
    }
    @SuppressLint("MissingPermission") // Guarded by hasPermissions; handles revoked grants.
    fun stopScan() {
        try { if(hasPermissions()) adapter?.bluetoothLeScanner?.stopScan(scanner) } catch(_: Exception) { }
        mutable.value = mutable.value.copy(scanning=false)
    }
    fun connect(address: String) {
        try {
            reconnect = true; attempt = 0; generation++
            release(); clearKeys(); onDisconnect?.invoke()
            target = adapter?.getRemoteDevice(address)
            open(generation)
        } catch(e: Exception) { mutable.value = mutable.value.copy(status="Cannot connect: ${e.message}") }
    }
    @SuppressLint("MissingPermission") // Guarded by hasPermissions; revoked grants are caught below.
    private fun open(token: Int) {
        val remote = target ?: return
        if(!hasPermissions()) return
        val epoch=++openEpoch
        handler.postDelayed({
            if(token==generation && epoch==openEpoch && device==null && reconnect) { openEpoch++; mutable.value=mutable.value.copy(status="Connection timed out · retrying"); scheduleReconnect() }
        },20000)
        mutable.value = mutable.value.copy(status="Connecting to piano…",connected=false)
        try {
            if(manager == null) { mutable.value = mutable.value.copy(status="Android MIDI service is unavailable"); return }
            manager.openBluetoothDevice(remote,{ opened ->
                if(token != generation || epoch != openEpoch || !reconnect) { opened?.close(); return@openBluetoothDevice }
                if(opened == null) { mutable.value = mutable.value.copy(status="Piano unavailable · retrying"); scheduleReconnect(); return@openBluetoothDevice }
                device = opened
                val output = opened.info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
                port = output?.let { opened.openOutputPort(it.portNumber) }
                if(port == null) { release(); mutable.value = mutable.value.copy(status="No MIDI output port · retrying"); scheduleReconnect(); return@openBluetoothDevice }
                port?.connect(receiver); attempt = 0; stopScan()
                mutable.value = mutable.value.copy(connected=true,status="${remote.name ?: "Piano"} connected")
            },handler)
        } catch(e: Exception) { mutable.value = mutable.value.copy(connected=false,status="Connection error: ${e.message}"); scheduleReconnect() }
    }
    private fun scheduleReconnect() {
        if(!reconnect || target == null) return
        val token = generation
        val delay = (1000L shl attempt.coerceAtMost(4)).coerceAtMost(15000)
        attempt++
        handler.postDelayed({ if(token == generation && reconnect && device == null) open(token) },delay)
    }
    fun disconnect() {
        reconnect=false; generation++; target=null; stopScan(); release(); clearKeys(); onDisconnect?.invoke()
        mutable.value = mutable.value.copy(connected=false,status="Not connected")
    }
    private fun release() { try { port?.disconnect(receiver); port?.close(); device?.close() } catch(_: Exception) { }; port=null; device=null; parser.reset() }
    fun clearKeys() { keyboard.reset(); mutable.value = mutable.value.copy(held=emptyMap(),sounding=emptySet(),sustain=false) }
    fun simulate(e: MidiEvent) { deliver(e) }
    private fun deliver(e: MidiEvent) {
        keyboard.accept(e)
        val message = when(e) { is MidiEvent.On -> "ON  ${noteName(e.pitch)}  velocity ${e.velocity}"; is MidiEvent.Off -> "OFF ${noteName(e.pitch)}"; is MidiEvent.Control -> "CC ${e.controller} = ${e.value}" }
        mutable.value = mutable.value.copy(held=keyboard.held.entries.groupBy { it.key.second }.mapValues { it.value.maxOf { v -> v.value } },
            sounding=keyboard.sounding.map { it.second }.toSet(),sustain=keyboard.sustain.isNotEmpty(),events=(listOf("${e.timestamp/1_000_000} ms · ch ${e.channel+1} · $message")+mutable.value.events).take(120))
        onEvent?.invoke(e)
    }
    fun close() { disconnect(); manager?.unregisterDeviceCallback(callback); handler.removeCallbacksAndMessages(null) }
}
