package com.example.blelib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.unity3d.player.UnityPlayer;

import java.util.UUID;

public class BLE {
    private final static String TAG = BLE.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int MY_PERMISSION_RESPONSE = 2;

    private static final String PERIPHERAL_LOCAL_NAME = "MKR WiFi 1010";
    private static final UUID PERIPHERAL_SERVICE_UUID = UUID.fromString("BF9CB85F-620C-4A67-BDD2-1A64213F74CA");
    private static final UUID PERIPHERAL_CHARACTERISTIC_UUID = UUID.fromString("5F83E23F-BCA1-42B3-B6F2-EA82BE46A93D");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private String mGameObjName;
    private String mCallBackName;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mCharacteristic;
    private Handler mHandler;

    // Stops scanning after 30 seconds.
    private static final long SCAN_PERIOD = 30000;

    public BLE(String gameObjName, String callBackName) {

        this.mGameObjName = gameObjName;
        this.mCallBackName = callBackName;

        mHandler = new Handler();

        if (!UnityPlayer.currentActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(UnityPlayer.currentActivity, "BLEをサポートしていません", Toast.LENGTH_SHORT).show();
            UnityPlayer.currentActivity.finish();
            return;
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) UnityPlayer.currentActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(UnityPlayer.currentActivity, "Bluetoothをサポートしていません", Toast.LENGTH_SHORT).show();
            UnityPlayer.currentActivity.finish();
            return;
        }

        onActive();
    }

    public void onActive() {
        Log.d(TAG, "onActive");
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            UnityPlayer.currentActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        scanLeDevice(true);
    }

    public void onPause() {
        Log.d(TAG, "onPause");
        scanLeDevice(false);

        if(mCharacteristic != null){
            mBluetoothGatt.setCharacteristicNotification(
                    mCharacteristic,
                    false
            );
        }

        if(mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    Log.d(TAG, device.getName());
                    if(PERIPHERAL_LOCAL_NAME.equals(device.getName())){
                        scanLeDevice(false);
                        connect(device);
                    }
                }
            };

    private boolean connect(BluetoothDevice device) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return mBluetoothGatt.connect();
        }

        mBluetoothGatt = device.connectGatt(UnityPlayer.currentActivity, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        return true;
    }


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {

                scanLeDevice(false);
                Log.i(TAG, "Connected to GATT server.");
                gatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
            } else{
                Log.i(TAG, "onConnectionStateChange:" + newState);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mCharacteristic = gatt.getService(PERIPHERAL_SERVICE_UUID).
                        getCharacteristic(PERIPHERAL_CHARACTERISTIC_UUID);

                gatt.setCharacteristicNotification(
                        mCharacteristic,
                        true
                );
                BluetoothGattDescriptor descriptor = mCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(descriptor);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            UnityPlayer.UnitySendMessage(mGameObjName, mCallBackName, new String(characteristic.getValue()));
        }
    };

}