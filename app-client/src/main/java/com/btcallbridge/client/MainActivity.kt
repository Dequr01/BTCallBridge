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

class MainActivity : AppCompatActivity() {

    private val permissions = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.FOREGROUND_SERVICE,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
        } else {
            Manifest.permission.FOREGROUND_SERVICE
        }
    )

    private lateinit var deviceListView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val deviceList = mutableListOf<BluetoothDevice>()

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
            val device = deviceList[position]
            saveHostMac(device.address)
            startBridgeService(device.address)
            requestBatteryOptimizationExemption()
            Toast.makeText(this, "Connecting to ${device.name}", Toast.LENGTH_SHORT).show()
        }

        if (checkPermissions()) {
            loadPairedDevices()
        } else {
            requestPermissions()
        }
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

        deviceList.clear()
        adapter.clear()
        
        val pairedDevices = btAdapter.bondedDevices
        if (pairedDevices.isNotEmpty()) {
            for (device in pairedDevices) {
                deviceList.add(device)
                adapter.add("${device.name}
${device.address}")
            }
        } else {
            adapter.add("No paired devices found")
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
