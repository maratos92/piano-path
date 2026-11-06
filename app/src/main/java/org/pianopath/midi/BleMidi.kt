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
data class UsbPiano(val id: Int,val port: Int,val name: String,val portName: String)
data class MidiStatus(val status: String = "Not connected", val connected: Boolean = false, val scanning: Boolean = false,
    val devices: List<PianoDevice> = emptyList(), val held: Map<Int,Int> = emptyMap(), val sounding: Set<Int> = emptySet(),
    val usbDevices: List<UsbPiano> = emptyList(), val lastNote: Int? = null, val lastVelocity: Int = 0, val lastChannel: Int = 0,
    val sustain: Boolean = false, val events: List<String> = emptyList(),val canSend: Boolean=false)

/** Each connection owns its running status; queued events and flushes retain that connection. */
internal class SessionMidiReceiver(private val isCurrent: ()->Boolean,private val post: (()->Unit)->Unit,
    private val emit: (MidiEvent)->Unit,private val reset: ()->Unit): MidiReceiver() {
    private val lock=Any()
    @Volatile private var closed=false
    @Volatile private var epoch=0L
    private fun current()=!closed && isCurrent()
    private val parser=MidiParser {event->
        val captured=epoch
        post {if(current() && epoch==captured)emit(event)}
    }
    override fun onSend(data: ByteArray,offset: Int,count: Int,timestamp: Long) {
        synchronized(lock) {
            if(current())parser.feed(data,offset,count,if(timestamp>0)timestamp else System.nanoTime())
        }
    }
    override fun onFlush() {
        synchronized(lock) {
            if(!current())return
            val captured=++epoch
            parser.reset()
            post {if(current() && epoch==captured)reset()}
        }
    }
    fun close() {synchronized(lock) {closed=true;epoch++;parser.reset()}}
}

class BleMidi(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(MidiManager::class.java)
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val mutable = MutableStateFlow(MidiStatus())
    val state = mutable.asStateFlow()
    private val keyboard = KeyboardState()
    private var device: MidiDevice? = null
    private var port: MidiOutputPort? = null
    private var referencePort: MidiInputPort? = null
    private val referenceNotes=linkedMapOf<Int,Int>()
    private var target: BluetoothDevice? = null
    private var usbTarget: String? = null
    private var usbPort = 0
    private var reconnect = true
    private var attempt = 0
    @Volatile private var generation = 0
    @Volatile private var openEpoch = 0
    var onEvent: ((MidiEvent) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
    var onInputReset: (()->Unit)? = null
    @Volatile private var inputReceiver: SessionMidiReceiver?=null
    private fun attachInput(output: MidiOutputPort,token: Int,epoch: Int): Boolean {
        lateinit var receiver: SessionMidiReceiver
        receiver=SessionMidiReceiver(
            isCurrent={token==generation && epoch==openEpoch && inputReceiver===receiver},
            post={handler.post(it)},emit={if(device!=null)deliver(it)},
            reset={clearKeys();onInputReset?.invoke()})
        inputReceiver=receiver
        return try {output.connect(receiver);true} catch(_: Exception) {
            inputReceiver=null;receiver.close();false
        }
    }
    private val callback = object: MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) { refreshUsb(); retryUsb() }
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            refreshUsb()
            if(device?.info?.id == info.id) {
                generation++; release(); clearKeys(); onDisconnect?.invoke()
                mutable.value = mutable.value.copy(connected=false,status="Connection lost · retrying")
                if(usbTarget!=null)mutable.value=mutable.value.copy(status="USB disconnected · reconnect cable to resume") else scheduleReconnect()
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
    init { manager?.registerDeviceCallback(callback,handler); refreshUsb() }
    @Suppress("DEPRECATION")
    private fun usbInfos() = manager?.devices?.filter {it.type==MidiDeviceInfo.TYPE_USB} ?: emptyList()
    private fun usbName(info: MidiDeviceInfo) = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
        ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: "USB MIDI piano"
    private fun fingerprint(info: MidiDeviceInfo) = listOf(info.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER),
        info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT),usbName(info)).joinToString("|")
    fun refreshUsb() {
        mutable.value=mutable.value.copy(usbDevices=usbInfos().flatMap {info -> info.ports.filter{it.type==MidiDeviceInfo.PortInfo.TYPE_OUTPUT}.map{
            UsbPiano(info.id,it.portNumber,usbName(info),it.name.ifBlank{"MIDI input ${it.portNumber+1}"})
        }})
    }
    private fun retryUsb() {
        if(!reconnect || device!=null || usbTarget==null)return
        val candidates=usbInfos().filter{fingerprint(it)==usbTarget}
        if(candidates.size==1)openUsb(candidates.single(),usbPort,generation)
        else if(candidates.size>1)mutable.value=mutable.value.copy(status="Multiple matching USB devices · select the input again")
    }
    fun connectUsb(id: Int,portNumber: Int) {
        val info=usbInfos().firstOrNull{it.id==id} ?: run{refreshUsb();return}
        reconnect=true;generation++;openEpoch++;stopScan();release();clearKeys();onDisconnect?.invoke()
        target=null;usbTarget=fingerprint(info);usbPort=portNumber
        openUsb(info,portNumber,generation)
    }
    private fun openUsb(info: MidiDeviceInfo,portNumber: Int,token: Int) {
        val epoch=++openEpoch
        mutable.value=mutable.value.copy(connected=false,status="Opening USB MIDI…")
        handler.postDelayed({if(token==generation && epoch==openEpoch && device==null){openEpoch++;mutable.value=mutable.value.copy(status="USB open timed out · tap Refresh and reconnect")}},10000)
        try {
            manager?.openDevice(info,{opened ->
                if(token!=generation || epoch!=openEpoch || !reconnect){opened?.close();return@openDevice}
                if(opened==null){mutable.value=mutable.value.copy(status="USB input busy or unavailable · reconnect or select again");return@openDevice}
                device=opened;port=opened.openOutputPort(portNumber)
                if(port==null){release();mutable.value=mutable.value.copy(status="USB MIDI port unavailable");return@openDevice}
                if(!attachInput(requireNotNull(port),token,epoch)) {
                    release();mutable.value=mutable.value.copy(status="USB MIDI receiver unavailable");return@openDevice
                }
                openReference(opened)
                mutable.value=mutable.value.copy(connected=true,status="${usbName(info)} · USB connected")
            },handler)
        }catch(e: Exception){release();mutable.value=mutable.value.copy(connected=false,status="USB connection error: ${e.message}")}
    }
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
            reconnect = true; usbTarget=null; attempt = 0; generation++
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
                if(!attachInput(requireNotNull(port),token,epoch)) {
                    release();mutable.value=mutable.value.copy(status="MIDI receiver unavailable · retrying");scheduleReconnect();return@openBluetoothDevice
                }
                attempt = 0; stopScan()
                openReference(opened)
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
        reconnect=false; generation++; target=null; usbTarget=null; stopScan(); release(); clearKeys(); onDisconnect?.invoke()
        mutable.value = mutable.value.copy(connected=false,status="Not connected")
    }
    /** Android names directions from the device's perspective: TYPE_INPUT receives app output. */
    private fun openReference(opened: MidiDevice) {
        stopReference();closeReferencePort()
        val input=opened.info.ports.firstOrNull{it.type==MidiDeviceInfo.PortInfo.TYPE_INPUT}
        referencePort=try {input?.let{opened.openInputPort(it.portNumber)}} catch(_: Exception) {null}
        mutable.value=mutable.value.copy(canSend=referencePort!=null)
    }
    private fun closeReferencePort() {
        try {referencePort?.close()} catch(_: Exception) {}
        referencePort=null
        mutable.value=mutable.value.copy(canSend=false)
    }
    /** Reference output is not input: it never calls deliver, simulate, capture, or scoring. */
    fun sendReference(pitch: Int,velocity: Int,down: Boolean): Boolean {
        if(pitch !in 0..127 || velocity !in 0..127 || (down && velocity==0))return false
        val output=referencePort ?: return false
        if(!down && (referenceNotes[pitch] ?: 0)==0)return true
        return try {
            output.send(byteArrayOf((if(down)0x90 else 0x80).toByte(),pitch.toByte(),velocity.toByte()),0,3)
            if(down)referenceNotes[pitch]=(referenceNotes[pitch] ?: 0)+1
            else {
                val remaining=(referenceNotes[pitch] ?: 1)-1
                if(remaining==0)referenceNotes.remove(pitch) else referenceNotes[pitch]=remaining
            }
            true
        } catch(_: Exception) {
            stopReference();closeReferencePort();false
        }
    }
    /** Release exactly the reference strikes we sent, without panicking the player's channels. */
    fun stopReference() {
        val notes=referenceNotes.toMap();referenceNotes.clear()
        val output=referencePort ?: return
        var failed=false
        notes.forEach{(pitch,count)->repeat(count) {
            try {output.send(byteArrayOf(0x80.toByte(),pitch.toByte(),0),0,3)} catch(_: Exception) {failed=true}
        }}
        if(failed)closeReferencePort()
    }
    private fun release() {
        val previous=inputReceiver;inputReceiver=null;previous?.close()
        stopReference();closeReferencePort()
        try {if(previous!=null)port?.disconnect(previous)} catch(_: Exception) {}
        try {port?.close()} catch(_: Exception) {}
        try {device?.close()} catch(_: Exception) {}
        port=null;device=null
    }
    fun clearKeys() { keyboard.reset(); mutable.value = mutable.value.copy(held=emptyMap(),sounding=emptySet(),sustain=false) }
    fun simulate(e: MidiEvent) { deliver(e) }
    private fun deliver(e: MidiEvent) {
        keyboard.accept(e)
        val message = when(e) { is MidiEvent.On -> "ON  ${noteName(e.pitch)}  velocity ${e.velocity}"; is MidiEvent.Off -> "OFF ${noteName(e.pitch)}"; is MidiEvent.Control -> "CC ${e.controller} = ${e.value}" }
        mutable.value = mutable.value.copy(lastNote=if(e is MidiEvent.On)e.pitch else mutable.value.lastNote,lastVelocity=if(e is MidiEvent.On)e.velocity else mutable.value.lastVelocity,lastChannel=if(e is MidiEvent.On)e.channel else mutable.value.lastChannel,held=keyboard.held.entries.groupBy { it.key.second }.mapValues { it.value.maxOf { v -> v.value } },
            sounding=keyboard.sounding.map { it.second }.toSet(),sustain=keyboard.sustain.isNotEmpty(),events=(listOf("${e.timestamp/1_000_000} ms · ch ${e.channel+1} · $message")+mutable.value.events).take(120))
        onEvent?.invoke(e)
    }
    fun close() { disconnect(); manager?.unregisterDeviceCallback(callback); handler.removeCallbacksAndMessages(null) }
}
