package com.btcallbridge.client

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.btcallbridge.core.BTConstants

class MainActivity : AppCompatActivity() {

    private val permissions = mutableListOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.FOREGROUND_SERVICE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
        }
    }.toTypedArray()

    private lateinit var deviceListView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val deviceList = mutableListOf<BluetoothDevice>()

    private val uuidReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_UUID == intent.action) {
                loadPairedDevices()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        deviceListView = findViewById(R.id.deviceList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        deviceListView.adapter = adapter

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            if (checkPermissions()) {
                loadPairedDevices()
            } else {
                requestPermissions()
            }
        }

        deviceListView.setOnItemClickListener { _, _, position, _ ->
            if (position < deviceList.size) {
                val device = deviceList[position]
                saveHostMac(device.address)
                startBridgeService(device.address)
                requestBatteryOptimizationExemption()
                Toast.makeText(this, "Connecting to Host", Toast.LENGTH_SHORT).show()
            }
        }

        if (checkPermissions()) {
            loadPairedDevices()
        } else {
            requestPermissions()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter(BluetoothDevice.ACTION_UUID)
        ContextCompat.registerReceiver(this, uuidReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(uuidReceiver)
    }

    private fun checkPermissions(): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, permissions, 100)
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            return
        }

        if (!btAdapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_LONG).show()
            return
        }

        val pairedDevices = btAdapter.bondedDevices
        
        deviceList.clear()
        adapter.clear()
        
        if (pairedDevices.isNotEmpty()) {
            var foundAny = false
            for (device in pairedDevices) {
                val uuids = device.uuids
                if (uuids == null) {
                    device.fetchUuidsWithSdp()
                }
                
                val isHost = uuids?.any { it.uuid == BTConstants.SIGNAL_UUID } ?: false
                
                if (isHost) {
                    deviceList.add(device)
                    adapter.add("BTCallBridge Host (${device.name})\n${device.address}")
                    foundAny = true
                }
            }
            if (!foundAny) {
                adapter.add("Searching for Host Apps among paired devices...")
            }
        } else {
            adapter.add("No paired devices found. Pair with the Host device first.")
        }
        adapter.notifyDataSetChanged()
    }

    private fun saveHostMac(mac: String) {
        val prefs = getSharedPreferences("bt_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("host_mac", mac).apply()
    }

    private fun startBridgeService(mac: String) {
        val intent = Intent(this, ClientService::class.java).apply {
            putExtra("host_mac", mac)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }
}
