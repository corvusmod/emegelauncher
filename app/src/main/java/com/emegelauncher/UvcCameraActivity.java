/*
 * Emegelauncher - Custom Launcher for MG Marvel R
 * Copyright (C) 2026 Emegelauncher Contributors
 *
 * Licensed under the Apache License, Version 2.0 with the
 * Commons Clause License Condition v1.0 (see LICENSE files).
 *
 * You may NOT sell this software. Donations are welcome.
 */

package com.emegelauncher;

import android.app.Activity;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jiangdg.ausbc.MultiCameraClient;
import com.jiangdg.ausbc.callback.ICameraStateCallBack;
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack;
import com.jiangdg.ausbc.camera.bean.CameraRequest;
import com.jiangdg.ausbc.widget.AspectRatioTextureView;
import com.serenegiant.usb.USBMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UVC Camera test page.
 * Shows a list of connected USB cameras. User taps one to open preview.
 * Uses AndroidUSBCamera library (jiangdongguo) for user-space UVC support.
 * Heavy debug logging with TAG "UvcCamera" for troubleshooting.
 */
public class UvcCameraActivity extends Activity {
    private static final String TAG = "UvcCamera";

    private MultiCameraClient mClient;
    private MultiCameraClient.Camera mActiveCamera;
    private AspectRatioTextureView mPreview;
    private FrameLayout mPreviewContainer;
    private LinearLayout mCameraList;
    private TextView mStatusText;
    private final List<UsbDevice> mDevices = new ArrayList<>();
    private int cBg, cCard, cText, cTextSec;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "=== UvcCameraActivity onCreate ===");
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        resolveColors();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setPadding(20, 8, 20, 8);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(0, 4, 0, 8);
        TextView title = new TextView(this);
        title.setText("USB Camera (UVC)");
        title.setTextSize(22);
        title.setTextColor(cText);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(title);
        TextView back = new TextView(this);
        back.setText(getString(R.string.back));
        back.setTextSize(13);
        back.setTextColor(ThemeHelper.accentBlue(this));
        back.setPadding(20, 12, 20, 12);
        back.setOnClickListener(v -> finish());
        header.addView(back);
        root.addView(header);

        // Preview area (hidden until camera is opened)
        mPreviewContainer = new FrameLayout(this);
        mPreviewContainer.setBackgroundColor(0xFF000000);
        mPreview = new AspectRatioTextureView(this);
        mPreviewContainer.addView(mPreview, new FrameLayout.LayoutParams(-1, -1));
        // Use 4:3 aspect ratio based on screen width (matches 640x480 camera resolution)
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int previewHeight = (int) (dm.widthPixels * 3.0 / 4.0);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, previewHeight);
        previewLp.setMargins(0, 4, 0, 4);
        mPreviewContainer.setVisibility(View.GONE);
        root.addView(mPreviewContainer, previewLp);

        // Close camera button (hidden until camera is opened)
        TextView closeBtn = new TextView(this);
        closeBtn.setTag("close_btn");
        closeBtn.setText("Close Camera");
        closeBtn.setTextSize(15);
        closeBtn.setTextColor(ThemeHelper.accentRed(this));
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setBackgroundColor(cCard);
        closeBtn.setPadding(16, 12, 16, 12);
        closeBtn.setVisibility(View.GONE);
        closeBtn.setOnClickListener(v -> {
            Log.d(TAG, "Close button pressed");
            closeActiveCamera();
        });
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, -2);
        closeLp.setMargins(0, 4, 0, 8);
        root.addView(closeBtn, closeLp);

        // Status
        mStatusText = new TextView(this);
        mStatusText.setText("Scanning for USB cameras...");
        mStatusText.setTextSize(14);
        mStatusText.setTextColor(cTextSec);
        mStatusText.setGravity(Gravity.CENTER);
        mStatusText.setPadding(0, 12, 0, 12);
        root.addView(mStatusText);

        // Camera list
        ScrollView scroll = new ScrollView(this);
        mCameraList = new LinearLayout(this);
        mCameraList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mCameraList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);

        // Enumerate all USB devices first (for debug)
        enumerateAllUsbDevices();

        // Then init UVC monitor
        initUsbMonitor();
    }

    /** Log ALL USB devices connected to the system, regardless of class */
    private void enumerateAllUsbDevices() {
        Log.i(TAG, "=== Enumerating ALL USB devices ===");
        try {
            UsbManager usbMgr = (UsbManager) getSystemService(USB_SERVICE);
            if (usbMgr == null) {
                Log.e(TAG, "UsbManager is NULL — USB Host not available?");
                return;
            }
            HashMap<String, UsbDevice> deviceList = usbMgr.getDeviceList();
            Log.i(TAG, "Total USB devices found: " + deviceList.size());

            if (deviceList.isEmpty()) {
                Log.w(TAG, "No USB devices connected at all");
                return;
            }

            for (Map.Entry<String, UsbDevice> entry : deviceList.entrySet()) {
                UsbDevice dev = entry.getValue();
                Log.i(TAG, "--- USB Device ---");
                Log.i(TAG, "  Name: " + entry.getKey());
                Log.i(TAG, "  Product: " + dev.getProductName());
                Log.i(TAG, "  Manufacturer: " + dev.getManufacturerName());
                Log.i(TAG, "  VID: " + String.format("0x%04X", dev.getVendorId())
                    + " PID: " + String.format("0x%04X", dev.getProductId()));
                Log.i(TAG, "  Device ID: " + dev.getDeviceId());
                Log.i(TAG, "  Device Name: " + dev.getDeviceName());
                Log.i(TAG, "  Device Class: " + dev.getDeviceClass()
                    + " SubClass: " + dev.getDeviceSubclass()
                    + " Protocol: " + dev.getDeviceProtocol());
                Log.i(TAG, "  Interface count: " + dev.getInterfaceCount());
                Log.i(TAG, "  Has permission: " + usbMgr.hasPermission(dev));

                boolean isVideo = false;
                for (int i = 0; i < dev.getInterfaceCount(); i++) {
                    UsbInterface iface = dev.getInterface(i);
                    Log.i(TAG, "  Interface " + i + ": class=" + iface.getInterfaceClass()
                        + " subclass=" + iface.getInterfaceSubclass()
                        + " protocol=" + iface.getInterfaceProtocol()
                        + " endpoints=" + iface.getEndpointCount()
                        + " name=" + iface.getName());
                    if (iface.getInterfaceClass() == 14) { // USB_CLASS_VIDEO = 0x0E
                        isVideo = true;
                        Log.i(TAG, "  >>> USB_CLASS_VIDEO detected on interface " + i);
                    }
                    if (iface.getInterfaceClass() == 1) { // USB_CLASS_AUDIO
                        Log.i(TAG, "  >>> USB_CLASS_AUDIO detected on interface " + i);
                    }
                    if (iface.getInterfaceClass() == 255) { // VENDOR_SPECIFIC
                        Log.i(TAG, "  >>> VENDOR_SPECIFIC class on interface " + i);
                    }
                }
                if (isVideo) {
                    Log.i(TAG, "  ==> This is a UVC camera device");
                    if (!mDevices.contains(dev)) mDevices.add(dev);
                } else {
                    Log.d(TAG, "  (not a video device)");
                }
            }
            Log.i(TAG, "=== UVC devices found: " + mDevices.size() + " ===");
        } catch (Exception e) {
            Log.e(TAG, "enumerateAllUsbDevices failed", e);
        }
    }

    private void initUsbMonitor() {
        Log.i(TAG, "Initializing MultiCameraClient USB monitor...");
        try {
            mClient = new MultiCameraClient(this, new IDeviceConnectCallBack() {
                @Override
                public void onAttachDev(UsbDevice device) {
                    Log.i(TAG, ">>> onAttachDev: " + device.getDeviceName()
                        + " product=" + device.getProductName()
                        + " VID=" + String.format("0x%04X", device.getVendorId())
                        + " PID=" + String.format("0x%04X", device.getProductId())
                        + " interfaces=" + device.getInterfaceCount());
                    runOnUiThread(() -> {
                        if (!mDevices.contains(device)) {
                            mDevices.add(device);
                            Log.d(TAG, "Added to device list. Total: " + mDevices.size());
                            rebuildCameraList();
                        } else {
                            Log.d(TAG, "Device already in list, skipping");
                        }
                    });
                }

                @Override
                public void onDetachDec(UsbDevice device) {
                    Log.i(TAG, ">>> onDetachDec: " + device.getDeviceName());
                    runOnUiThread(() -> {
                        mDevices.remove(device);
                        Log.d(TAG, "Removed from device list. Total: " + mDevices.size());
                        rebuildCameraList();
                        if (mActiveCamera != null) {
                            Log.d(TAG, "Active camera detached, closing...");
                            closeActiveCamera();
                        }
                    });
                }

                @Override
                public void onConnectDev(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
                    Log.i(TAG, ">>> onConnectDev: " + device.getDeviceName()
                        + " ctrlBlock=" + ctrlBlock);
                    runOnUiThread(() -> {
                        Log.d(TAG, "Creating MultiCameraClient.Camera for " + device.getDeviceName());
                        try {
                            mActiveCamera = new MultiCameraClient.Camera(UvcCameraActivity.this, device);
                            Log.d(TAG, "Camera object created, setting USB control block...");
                            mActiveCamera.setUsbControlBlock(ctrlBlock);
                            Log.d(TAG, "Setting camera state callback...");
                            mActiveCamera.setCameraStateCallBack(new ICameraStateCallBack() {
                                @Override
                                public void onCameraState(MultiCameraClient.Camera cam, ICameraStateCallBack.State state, String msg) {
                                    Log.i(TAG, ">>> onCameraState: " + state + " msg=" + msg);
                                    runOnUiThread(() -> handleCameraState(state, msg));
                                }
                            });
                            Log.d(TAG, "Building CameraRequest 640x480...");
                            CameraRequest request = new CameraRequest.Builder()
                                .setPreviewWidth(640)
                                .setPreviewHeight(480)
                                .create();
                            Log.d(TAG, "Opening camera with preview surface...");
                            mActiveCamera.openCamera(mPreview, request);
                            Log.d(TAG, "openCamera() called successfully");
                            mPreviewContainer.setVisibility(View.VISIBLE);
                            View closeBtn = getWindow().getDecorView().findViewWithTag("close_btn");
                            if (closeBtn != null) closeBtn.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open camera after connect", e);
                            mStatusText.setText("Error opening camera: " + e.getMessage());
                            Toast.makeText(UvcCameraActivity.this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onDisConnectDec(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
                    Log.i(TAG, ">>> onDisConnectDec: " + device.getDeviceName());
                    runOnUiThread(() -> closeActiveCamera());
                }

                @Override
                public void onCancelDev(UsbDevice device) {
                    Log.w(TAG, ">>> onCancelDev: Permission denied for " + device.getDeviceName());
                    runOnUiThread(() -> {
                        mStatusText.setText("Permission denied for camera");
                        Toast.makeText(UvcCameraActivity.this, "USB permission denied", Toast.LENGTH_SHORT).show();
                    });
                }
            });

            Log.d(TAG, "Registering USB monitor...");
            mClient.register();
            Log.i(TAG, "USB monitor registered successfully");
            mStatusText.setText("USB monitor active.\nConnect a UVC camera or tap one below.");
            rebuildCameraList();

        } catch (Exception e) {
            Log.e(TAG, "USB monitor init FAILED", e);
            mStatusText.setText("USB monitor error: " + e.getMessage());
        }
    }

    private void rebuildCameraList() {
        Log.d(TAG, "Rebuilding camera list, devices: " + mDevices.size());
        mCameraList.removeAllViews();
        if (mDevices.isEmpty()) {
            mStatusText.setText("No USB cameras detected.\nConnect a UVC camera to the USB port.");
            return;
        }
        mStatusText.setText(mDevices.size() + " camera(s) found. Tap to open:");

        for (int i = 0; i < mDevices.size(); i++) {
            UsbDevice device = mDevices.get(i);
            String name = device.getProductName();
            if (name == null || name.isEmpty()) name = device.getDeviceName();
            Log.d(TAG, "  List item " + i + ": " + name
                + " VID=" + String.format("0x%04X", device.getVendorId())
                + " PID=" + String.format("0x%04X", device.getProductId()));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundColor(cCard);
            row.setPadding(20, 16, 20, 16);
            row.setElevation(4f);

            TextView nameText = new TextView(this);
            nameText.setText((i + 1) + ". " + name);
            nameText.setTextSize(16);
            nameText.setTextColor(cText);
            row.addView(nameText);

            StringBuilder info = new StringBuilder();
            info.append("VID: ").append(String.format("0x%04X", device.getVendorId()));
            info.append("  PID: ").append(String.format("0x%04X", device.getProductId()));
            info.append("\nInterfaces: ").append(device.getInterfaceCount());
            for (int j = 0; j < device.getInterfaceCount(); j++) {
                UsbInterface iface = device.getInterface(j);
                info.append("\n  [").append(j).append("] class=").append(iface.getInterfaceClass())
                    .append(" sub=").append(iface.getInterfaceSubclass())
                    .append(" ep=").append(iface.getEndpointCount());
            }

            TextView infoText = new TextView(this);
            infoText.setText(info.toString());
            infoText.setTextSize(11);
            infoText.setTextColor(cTextSec);
            row.addView(infoText);

            TextView openBtn = new TextView(this);
            openBtn.setText("Open Preview");
            openBtn.setTextSize(14);
            openBtn.setTextColor(ThemeHelper.accentGreen(this));
            openBtn.setPadding(0, 8, 0, 0);
            row.addView(openBtn);

            final UsbDevice dev = device;
            row.setOnClickListener(v -> openCamera(dev));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 4, 0, 4);
            mCameraList.addView(row, lp);
        }
    }

    private void openCamera(UsbDevice device) {
        Log.i(TAG, "=== openCamera requested for: " + device.getDeviceName()
            + " product=" + device.getProductName()
            + " VID=" + String.format("0x%04X", device.getVendorId())
            + " PID=" + String.format("0x%04X", device.getProductId()));

        if (mActiveCamera != null) {
            Log.d(TAG, "Closing previous active camera first...");
            closeActiveCamera();
        }

        mStatusText.setText("Requesting USB permission for\n" + device.getDeviceName() + "...");
        mStatusText.setTextColor(cTextSec);

        Log.d(TAG, "Calling mClient.requestPermission()...");
        try {
            boolean result = mClient.requestPermission(device);
            Log.d(TAG, "requestPermission returned: " + result);
        } catch (Exception e) {
            Log.e(TAG, "requestPermission FAILED", e);
            mStatusText.setText("Permission request failed: " + e.getMessage());
        }
    }

    private void closeActiveCamera() {
        Log.i(TAG, "=== closeActiveCamera ===");
        if (mActiveCamera != null) {
            try {
                Log.d(TAG, "Calling mActiveCamera.closeCamera()...");
                mActiveCamera.closeCamera();
                Log.d(TAG, "Camera closed successfully");
            } catch (Exception e) {
                Log.e(TAG, "closeCamera failed", e);
            }
            mActiveCamera = null;
        }
        mPreviewContainer.setVisibility(View.GONE);
        View closeBtn = getWindow().getDecorView().findViewWithTag("close_btn");
        if (closeBtn != null) closeBtn.setVisibility(View.GONE);
        mStatusText.setText("Camera closed. Select another or connect a new one.");
        mStatusText.setTextColor(cTextSec);
    }

    private void handleCameraState(ICameraStateCallBack.State state, String msg) {
        Log.i(TAG, "handleCameraState: " + state + " msg=" + msg);
        switch (state) {
            case OPENED:
                Log.i(TAG, "Camera OPENED — preview should be active");
                mStatusText.setText("Preview active" + (msg != null ? ": " + msg : ""));
                mStatusText.setTextColor(ThemeHelper.accentGreen(this));
                break;
            case CLOSED:
                Log.i(TAG, "Camera CLOSED");
                mStatusText.setText("Camera closed");
                mStatusText.setTextColor(cTextSec);
                mPreviewContainer.setVisibility(View.GONE);
                View closeBtn = getWindow().getDecorView().findViewWithTag("close_btn");
                if (closeBtn != null) closeBtn.setVisibility(View.GONE);
                break;
            case ERROR:
                Log.e(TAG, "Camera ERROR: " + msg);
                mStatusText.setText("Error: " + (msg != null ? msg : "unknown"));
                mStatusText.setTextColor(ThemeHelper.accentRed(this));
                Toast.makeText(this, "Camera error: " + msg, Toast.LENGTH_LONG).show();
                break;
        }
    }

    private void resolveColors() {
        cBg = ThemeHelper.resolveColor(this, R.attr.colorBgPrimary);
        cCard = ThemeHelper.resolveColor(this, R.attr.colorBgCard);
        cText = ThemeHelper.resolveColor(this, R.attr.colorTextPrimary);
        cTextSec = ThemeHelper.resolveColor(this, R.attr.colorTextSecondary);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "=== UvcCameraActivity onDestroy ===");
        super.onDestroy();
        try {
            if (mActiveCamera != null) {
                Log.d(TAG, "Closing active camera in onDestroy");
                mActiveCamera.closeCamera();
            }
        } catch (Exception e) {
            Log.e(TAG, "closeCamera in onDestroy failed", e);
        }
        try {
            if (mClient != null) {
                Log.d(TAG, "Unregistering and destroying USB monitor");
                mClient.unRegister();
                mClient.destroy();
            }
        } catch (Exception e) {
            Log.e(TAG, "USB monitor cleanup failed", e);
        }
        Log.i(TAG, "=== UvcCameraActivity destroyed ===");
    }
}
