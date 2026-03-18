/*
 * Emegelauncher - Custom Launcher for MG Marvel R
 * Copyright (C) 2026 Emegelauncher Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 with the
 * Commons Clause License Condition v1.0 (see LICENSE files).
 *
 * You may NOT sell this software. Donations are welcome.
 */

package com.emegelauncher.vehicle;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import dalvik.system.DexClassLoader;

import java.lang.reflect.Method;

/**
 * All-reflection vehicle service manager.
 * Layer 1: Android Car API (CarPropertyManager, CarBMSManager, CarHvacManager)
 * Layer 2a: SAIC VehicleService via bindService (IHubService → sub-services)
 * Layer 2b: SAIC VehicleService via ServiceManager.getService (direct binder)
 */
public class VehicleServiceManager {
    private static final String TAG = "VehicleServiceMgr";

    private static final String SAIC_SERVICE_PKG = "com.saicmotor.service.vehicle";
    private static final String SAIC_SERVICE_CLS = "com.saicmotor.service.vehicle.VehicleService";

    private static final String[] HUB_STUB_CLASSES = {
        "com.saicmotor.sdk.vehiclesettings.IHubService$Stub",
        "com.saicvehicleservice.IHubService$Stub",
        "com.saicvehicleservice.sdk.IHubService$Stub",
        "com.saicmotor.service.vehicle.IHubService$Stub",
    };

    // All known sub-service keys and their stub class names
    private static final String[][] SUB_SERVICES = {
        // {field_index, key1, key2, stubClass1, stubClass2}
        // We'll use a simpler structure and handle it in code
    };

    private static final String[] SETTING_KEYS = {"vehiclesetting", "vehicle_settings"};
    private static final String[] CONDITION_KEYS = {"vehiclecondition", "vehicle_condition"};
    private static final String[] CHARGING_KEYS = {"vehiclecharging", "vehicle_charging"};
    private static final String[] AIRCONDITION_KEYS = {"aircondition", "air_condition"};
    private static final String[] CONTROL_KEYS = {"vehiclecontrol", "vehicle_control"};

    private static final String[] STUB_PREFIXES = {
        "com.saicmotor.sdk.vehiclesettings.",
        "com.saicvehicleservice.sdk.vehiclesettings.",
        "com.saicvehicleservice.sdk.",
    };

    private static VehicleServiceManager sInstance;
    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Layer 1: Car API (set from binder thread, read from main thread)
    private volatile Object mCar;
    private volatile Object mCarPropertyManager;
    private volatile Object mCarHvacManager;
    private volatile Object mBmsMgr;

    // Layer 2: SAIC sub-services (set from binder thread, read from main thread)
    private volatile Object mHubService;
    private volatile Object mSettingService;
    private volatile Object mConditionService;
    private volatile Object mChargingService;
    private volatile Object mAirConditionService;
    private volatile Object mControlService;

    private volatile boolean mCarConnected = false;
    private volatile boolean mSaicBound = false;

    private VehicleServiceManager(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public static synchronized VehicleServiceManager getInstance(Context context) {
        if (sInstance == null) sInstance = new VehicleServiceManager(context);
        return sInstance;
    }

    // Layer 3: EngineerMode service
    private volatile Object mEngSystemSettings;
    private volatile Object mEngSystemHardware;

    // Layer 4: SaicAdapterService (3 separate services)
    private volatile Object mAdapterGeneral;
    private volatile Object mAdapterMap;
    private volatile Object mAdapterVoice;

    // Layer 5: SystemSettingsService (multiple sub-services via intent actions)
    private volatile Object mSysBt;
    private volatile Object mSysGeneral;
    private volatile Object mSysMycar;
    private volatile Object mSysSound;
    private volatile Object mSysHotspot;
    private volatile Object mSysGdpr;
    private volatile Object mSysWifi;
    private volatile Object mSysDataUsage;

    // Layer 6: vehicleService_overseas
    private volatile Object mVehicleOverseas;

    public void bindService() {
        bindCarService();
        bindSaicService();
        bindViaServiceManager();
        bindEngModeService();
        bindAdapterServices();
        bindSystemSettingsServices();
        bindVehicleOverseas();
    }

    // ==================== Layer 1: Android Car API ====================

    private void bindCarService() {
        try {
            Class<?> carClass = Class.forName("android.car.Car");
            try {
                Method m = carClass.getMethod("createCar", Context.class);
                mCar = m.invoke(null, mContext);
                if (mCar != null && isCarConnected()) { tryGetManagers(); return; }
            } catch (Exception ignored) {}
            try {
                Method m = carClass.getMethod("createCar", Context.class, Handler.class);
                mCar = m.invoke(null, mContext, (Handler) null);
                if (mCar != null && isCarConnected()) { tryGetManagers(); return; }
            } catch (Exception ignored) {}
            Method m = carClass.getMethod("createCar", Context.class, ServiceConnection.class);
            mCar = m.invoke(null, mContext, new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName n, IBinder s) { tryGetManagers(); }
                @Override public void onServiceDisconnected(ComponentName n) {
                    mCarConnected = false; mCarPropertyManager = null; mCarHvacManager = null; mBmsMgr = null;
                }
            });
            if (mCar != null) mCar.getClass().getMethod("connect").invoke(mCar);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind Car service", e);
        }
    }

    private boolean isCarConnected() {
        try { return Boolean.TRUE.equals(mCar.getClass().getMethod("isConnected").invoke(mCar)); }
        catch (Exception e) { return false; }
    }

    private void tryGetManagers() {
        if (mCar == null) return;
        try {
            String propSvc = getStaticStringField(mCar.getClass(), "PROPERTY_SERVICE");
            if (propSvc == null) propSvc = "property";
            mCarPropertyManager = callGetCarManager(propSvc);
            if (mCarPropertyManager != null) Log.d(TAG, "CarPropertyManager: " + mCarPropertyManager.getClass().getName());

            // HVAC manager
            String hvacSvc = getStaticStringField(mCar.getClass(), "HVAC_SERVICE");
            if (hvacSvc == null) hvacSvc = "hvac";
            mCarHvacManager = callGetCarManager(hvacSvc);
            if (mCarHvacManager != null) Log.d(TAG, "CarHvacManager: " + mCarHvacManager.getClass().getName());

            mBmsMgr = callGetCarManager("bms");
            if (mBmsMgr != null) Log.d(TAG, "CarBMSManager: " + mBmsMgr.getClass().getName());

            mCarConnected = true;
        } catch (Exception e) { Log.e(TAG, "Error getting car managers", e); }
    }

    private Object callGetCarManager(String name) {
        try { return mCar.getClass().getMethod("getCarManager", String.class).invoke(mCar, name); }
        catch (Exception e) { return null; }
    }

    // ==================== Layer 2a: SAIC VehicleService via bind ====================

    private final ServiceConnection mSaicConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "SAIC VehicleService connected");
            try {
                // Build DexClassLoaders BEFORE trying asInterface
                buildVsClassLoaders();
                mHubService = asInterface(HUB_STUB_CLASSES, service);
                if (mHubService == null) { Log.e(TAG, "IHubService not found"); return; }
                Log.d(TAG, "IHubService: " + mHubService.getClass().getName());

                mSettingService = getSubService(mHubService, SETTING_KEYS, "IVehicleSettingService");
                mConditionService = getSubService(mHubService, CONDITION_KEYS, "IVehicleConditionService");
                mChargingService = getSubService(mHubService, CHARGING_KEYS, "IVehicleChargingService");
                mAirConditionService = getSubService(mHubService, AIRCONDITION_KEYS, "IAirConditionService");
                mControlService = getSubService(mHubService, CONTROL_KEYS, "IVehicleControlService");

                mSaicBound = true;
                Log.d(TAG, "SAIC: setting=" + (mSettingService != null)
                    + " condition=" + (mConditionService != null)
                    + " charging=" + (mChargingService != null)
                    + " aircon=" + (mAirConditionService != null)
                    + " control=" + (mControlService != null));
            } catch (Exception e) { Log.e(TAG, "SAIC init failed", e); }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSaicBound = false;
            mHubService = mSettingService = mConditionService = mChargingService = mAirConditionService = mControlService = null;
        }
    };

    private void bindSaicService() {
        if (mSaicBound) return;
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(SAIC_SERVICE_PKG, SAIC_SERVICE_CLS));
            mContext.bindService(intent, mSaicConn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) { Log.e(TAG, "Failed to bind SAIC service", e); }
    }

    // ==================== Layer 2b: ServiceManager direct ====================

    private void bindViaServiceManager() {
        try {
            buildVsClassLoaders();
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);

            // Try to get hub via ServiceManager (DriveHub Katman2 approach)
            Object binder = getService.invoke(null, "vehiclesetting");
            if (binder instanceof IBinder) {
                Object hub = asInterface(HUB_STUB_CLASSES, (IBinder) binder);
                if (hub != null && mHubService == null) {
                    mHubService = hub;
                    Log.d(TAG, "IHubService via ServiceManager");
                    if (mAirConditionService == null)
                        mAirConditionService = getSubService(hub, AIRCONDITION_KEYS, "IAirConditionService");
                    if (mConditionService == null)
                        mConditionService = getSubService(hub, CONDITION_KEYS, "IVehicleConditionService");
                    if (mSettingService == null)
                        mSettingService = getSubService(hub, SETTING_KEYS, "IVehicleSettingService");
                    if (mChargingService == null)
                        mChargingService = getSubService(hub, CHARGING_KEYS, "IVehicleChargingService");
                    if (mControlService == null)
                        mControlService = getSubService(hub, CONTROL_KEYS, "IVehicleControlService");
                    mSaicBound = true;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "ServiceManager approach failed: " + e.getMessage());
        }
    }

    // ==================== Layer 3: EngineerModeService ====================

    private static final String ENG_SERVICE_PKG = "com.saicmotor.service.engmode";
    private static final String ENG_SERVICE_CLS = "com.saicmotor.service.engmode.EngineeringModeService";
    private static final String[] ENG_HUB_STUBS = {
        "com.saicmotor.sdk.engmode.IEngineeringMode$Stub",
    };

    private final ServiceConnection mEngConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "EngMode service connected");
            try {
                Object engHub = asInterface(ENG_HUB_STUBS, service);
                if (engHub == null) { Log.d(TAG, "IEngineeringMode hub not found"); return; }
                // Get system_setting sub-service
                try {
                    Object binder = engHub.getClass().getMethod("getService", String.class).invoke(engHub, "system_setting");
                    if (binder instanceof IBinder) {
                        String[] stubs = {"com.saicmotor.sdk.engmode.ISystemSettingsManager$Stub"};
                        mEngSystemSettings = asInterface(stubs, (IBinder) binder);
                        if (mEngSystemSettings != null) Log.d(TAG, "EngMode SystemSettings acquired");
                    }
                } catch (Exception e) { Log.d(TAG, "EngMode system_setting failed: " + e.getMessage()); }
                // Get system_hardware sub-service (has TBox, GNSS, BT, WiFi info)
                try {
                    Object hwBinder = engHub.getClass().getMethod("getService", String.class).invoke(engHub, "system_hardware");
                    if (hwBinder instanceof IBinder) {
                        String[] hwStubs = {"com.saicmotor.sdk.engmode.ISystemHardwareManager$Stub"};
                        mEngSystemHardware = asInterface(hwStubs, (IBinder) hwBinder);
                        if (mEngSystemHardware != null) Log.d(TAG, "EngMode SystemHardware acquired");
                    }
                } catch (Exception e) { Log.d(TAG, "EngMode system_hardware failed: " + e.getMessage()); }
            } catch (Exception e) { Log.d(TAG, "EngMode init failed: " + e.getMessage()); }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { mEngSystemSettings = null; mEngSystemHardware = null; }
    };

    private void bindEngModeService() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(ENG_SERVICE_PKG, ENG_SERVICE_CLS));
            mContext.bindService(intent, mEngConn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) { Log.d(TAG, "EngMode bind failed: " + e.getMessage()); }
    }

    // EngMode accessors
    public String get12VBatteryVoltage() { return callServiceMethod(mEngSystemSettings, "getBatteryPower"); }
    public String getEngCarSpeed() { return callServiceMethod(mEngSystemSettings, "getCarSpeed"); }
    public String getEngChargeStatus() { return callServiceMethod(mEngSystemSettings, "getChargeStatus"); }
    public String getEngGearStatus() { return callServiceMethod(mEngSystemSettings, "getGearStatus"); }
    public String getEngParkingBrake() { return callServiceMethod(mEngSystemSettings, "getParkingBrakeStatus"); }
    public String getEngPowerRunType() { return callServiceMethod(mEngSystemSettings, "getPowerRunType"); }
    public String getEngPowerSystemStatus() { return callServiceMethod(mEngSystemSettings, "getPowerSystemStatus"); }
    public String getEngShowRoom() { return callServiceMethod(mEngSystemSettings, "getShowRoom"); }
    public boolean hasEngMode() { return mEngSystemSettings != null; }

    // ==================== Layer 4: SaicAdapterService ====================

    private static final String ADAPTER_PKG = "com.saicmotor.adapterservice";

    private void bindAdapterServices() {
        // Each adapter sub-service is a separate Android Service
        bindByComponent(ADAPTER_PKG, ADAPTER_PKG + ".services.GeneralService",
            new String[]{"com.saicmotor.adapterservice.IGeneralService$Stub"},
            svc -> { mAdapterGeneral = svc; Log.d(TAG, "AdapterGeneral acquired"); });
        bindByComponent(ADAPTER_PKG, ADAPTER_PKG + ".services.MapService",
            new String[]{"com.saicmotor.adapterservice.IMapService$Stub"},
            svc -> { mAdapterMap = svc; Log.d(TAG, "AdapterMap acquired"); });
        bindByComponent(ADAPTER_PKG, ADAPTER_PKG + ".services.VoiceVuiService",
            new String[]{"com.saicmotor.adapterservice.IVoiceVuiService$Stub"},
            svc -> { mAdapterVoice = svc; Log.d(TAG, "AdapterVoice acquired"); });
    }

    // ==================== Layer 5: SystemSettingsService ====================

    private static final String SYSSETTINGS_PKG = "com.saicmotor.service.systemsettings";

    private static final String SYSSETTINGS_CLS = "com.saicmotor.service.systemsettings.SettingsService";

    private void bindSystemSettingsServices() {
        // SystemSettingsService requires BOTH action AND explicit className
        // (discovered from decompiled com.saicmotor.sdk.systemsettings.BaseManager.bindService)
        String[][] sysServices = {
            {"com.saicmotor.service.systemsettings.IBtService",
             "com.saicmotor.sdk.systemsettings.IBtService$Stub"},
            {"com.saicmotor.service.systemsettings.IGeneralService",
             "com.saicmotor.sdk.systemsettings.IGeneralService$Stub"},
            {"com.saicmotor.service.systemsettings.IMyCarService",
             "com.saicmotor.sdk.systemsettings.IMyCarService$Stub"},
            {"com.saicmotor.service.systemsettings.ISmartSoundService",
             "com.saicmotor.sdk.systemsettings.ISmartSoundService$Stub"},
            {"com.saicmotor.service.systemsettings.IHotspotService",
             "com.saicmotor.sdk.systemsettings.IHotspotService$Stub"},
            {"com.saicmotor.service.systemsettings.IGdprService",
             "com.saicmotor.sdk.systemsettings.IGdprService$Stub"},
            {"com.saicmotor.service.systemsettings.IWiFiService",
             "com.saicmotor.sdk.systemsettings.IWiFiService$Stub"},
            {"com.saicmotor.service.systemsettings.IDataUsageService",
             "com.saicmotor.sdk.systemsettings.IDataUsageService$Stub"},
        };
        for (String[] s : sysServices) {
            String action = s[0];
            String stubClass = s[1];
            bindByActionAndClass(SYSSETTINGS_PKG, SYSSETTINGS_CLS, action, new String[]{stubClass}, svc -> {
                if (action.contains("IBtService")) { mSysBt = svc; Log.d(TAG, "SysBt acquired"); }
                else if (action.contains("IGeneralService")) { mSysGeneral = svc; Log.d(TAG, "SysGeneral acquired"); }
                else if (action.contains("IMyCarService")) { mSysMycar = svc; Log.d(TAG, "SysMycar acquired"); }
                else if (action.contains("ISmartSoundService")) { mSysSound = svc; Log.d(TAG, "SysSound acquired"); }
                else if (action.contains("IHotspotService")) { mSysHotspot = svc; Log.d(TAG, "SysHotspot acquired"); }
                else if (action.contains("IGdprService")) { mSysGdpr = svc; Log.d(TAG, "SysGdpr acquired"); }
                else if (action.contains("IWiFiService")) { mSysWifi = svc; Log.d(TAG, "SysWifi acquired"); }
                else if (action.contains("IDataUsageService")) { mSysDataUsage = svc; Log.d(TAG, "SysDataUsage acquired"); }
            });
        }
    }

    // ==================== Layer 6: vehicleService_overseas ====================

    private void bindVehicleOverseas() {
        bindByAction("com.saicvehicleservice", "com.saic.vehicle.VehicleService",
            new String[]{
                "com.saicvehicleservice.IVehicleAidlInterface$Stub",
                "com.saic.vehicle.IVehicleAidlInterface$Stub",
            },
            svc -> { mVehicleOverseas = svc; Log.d(TAG, "VehicleOverseas acquired"); });
    }

    // ==================== Generic bind helpers ====================

    private interface ServiceCallback { void onConnected(Object service); }

    private void bindByComponent(String pkg, String cls, String[] stubs, ServiceCallback cb) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(pkg, cls));
            mContext.bindService(intent, new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName n, IBinder s) {
                    Object svc = asInterface(stubs, s);
                    if (svc != null) cb.onConnected(svc);
                }
                @Override public void onServiceDisconnected(ComponentName n) {}
            }, Context.BIND_AUTO_CREATE);
        } catch (Exception e) { Log.d(TAG, "bindByComponent " + cls + " failed: " + e.getMessage()); }
    }

    private void bindByAction(String pkg, String action, String[] stubs, ServiceCallback cb) {
        try {
            Intent intent = new Intent(action);
            intent.setPackage(pkg);
            mContext.bindService(intent, new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName n, IBinder s) {
                    Object svc = asInterface(stubs, s);
                    if (svc != null) cb.onConnected(svc);
                }
                @Override public void onServiceDisconnected(ComponentName n) {}
            }, Context.BIND_AUTO_CREATE);
        } catch (Exception e) { Log.d(TAG, "bindByAction " + action + " failed: " + e.getMessage()); }
    }

    /** Bind with both action AND explicit className (required by SystemSettingsService) */
    private void bindByActionAndClass(String pkg, String cls, String action, String[] stubs, ServiceCallback cb) {
        try {
            Intent intent = new Intent();
            intent.setAction(action);
            intent.setClassName(pkg, cls);
            mContext.bindService(intent, new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName n, IBinder s) {
                    Object svc = asInterface(stubs, s);
                    if (svc != null) cb.onConnected(svc);
                    else Log.d(TAG, "bindByActionAndClass: asInterface null for " + action);
                }
                @Override public void onServiceDisconnected(ComponentName n) {}
            }, Context.BIND_AUTO_CREATE);
        } catch (Exception e) { Log.d(TAG, "bindByActionAndClass " + action + " failed: " + e.getMessage()); }
    }

    // ==================== Property reading ====================

    private static int getAreaId(int propId) {
        int area = (propId >> 28) & 0xF;
        if (area == 0x5 || area == 0x4) return 0x31;
        if (area == 0x6) return 0x1;
        return 0;
    }

    public String getPropertyValue(int propId) {
        if (!mCarConnected) return "Connecting...";

        if (mCarPropertyManager != null) {
            Object val = getPropertyFromManager(mCarPropertyManager, propId);
            if (val != null) return formatValue(val);
        }
        if (mCarHvacManager != null) {
            Object val = getPropertyFromManager(mCarHvacManager, propId);
            if (val != null) return formatValue(val);
        }
        if (mBmsMgr != null) {
            Object val = callGetGlobalProperty(mBmsMgr, propId);
            if (val != null) return formatValue(val);
        }
        return "N/A";
    }

    private Object getPropertyFromManager(Object mgr, int propId) {
        int areaId = getAreaId(propId);
        int propType = (propId >> 20) & 0xF;

        // STRING type (0x1) or BYTES type (0x7) — use getProperty() → CarPropertyValue
        if (propType == 0x1 || propType == 0x7) {
            Object r = callGetPropertyAsString(mgr, propId, areaId);
            if (r != null) return r;
            if (areaId != 0) { r = callGetPropertyAsString(mgr, propId, 0); if (r != null) return r; }
        }
        // FLOAT type (0x6)
        if (propType == 0x6) {
            Object r = callTypedProperty(mgr, "getFloatProperty", propId, areaId);
            if (r != null) return r;
            if (areaId != 0) { r = callTypedProperty(mgr, "getFloatProperty", propId, 0); if (r != null) return r; }
        }
        // INT32 type (0x4) or BOOLEAN (0x2)
        if (propType == 0x4 || propType == 0x2) {
            Object r = callTypedProperty(mgr, "getIntProperty", propId, areaId);
            if (r != null) return r;
            if (areaId != 0) { r = callTypedProperty(mgr, "getIntProperty", propId, 0); if (r != null) return r; }
        }
        // Fallback: try all
        Object r = callGetPropertyAsString(mgr, propId, 0);
        if (r != null) return r;
        r = callTypedProperty(mgr, "getFloatProperty", propId, 0);
        if (r != null) return r;
        r = callTypedProperty(mgr, "getIntProperty", propId, 0);
        if (r != null) return r;
        return callGetGlobalProperty(mgr, propId);
    }

    /** Read a STRING or BYTES property via getProperty() → CarPropertyValue.getValue() */
    private Object callGetPropertyAsString(Object mgr, int propId, int areaId) {
        // Try getProperty(Class, int, int) → CarPropertyValue
        for (Class<?> valClass : new Class[]{String.class, byte[].class, Object.class}) {
            try {
                Method m = mgr.getClass().getMethod("getProperty", Class.class, int.class, int.class);
                Object cpv = m.invoke(mgr, valClass, propId, areaId);
                if (cpv != null) {
                    Object val = cpv.getClass().getMethod("getValue").invoke(cpv);
                    if (val != null) return convertToString(val);
                }
            } catch (Exception ignored) {}
        }
        // Try getProperty(int, int) → CarPropertyValue
        try {
            Method m = mgr.getClass().getMethod("getProperty", int.class, int.class);
            Object cpv = m.invoke(mgr, propId, areaId);
            if (cpv != null) {
                Object val = cpv.getClass().getMethod("getValue").invoke(cpv);
                if (val != null) return convertToString(val);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Convert property value to readable string — handles byte[], String, and other types */
    private static String convertToString(Object val) {
        if (val instanceof String) return (String) val;
        if (val instanceof byte[]) {
            byte[] bytes = (byte[]) val;
            // Try UTF-8 string first (most SAIC properties are ASCII/UTF-8)
            try {
                String s = new String(bytes, "UTF-8").trim();
                // If it looks like a readable string, return it
                if (!s.isEmpty() && s.chars().allMatch(c -> c >= 0x20 && c < 0x7F)) return s;
            } catch (Exception ignored) {}
            // Fallback: hex representation
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02X", b));
            return sb.toString();
        }
        return val.toString();
    }

    private Object callTypedProperty(Object mgr, String methodName, int propId, int areaId) {
        try {
            Method m = mgr.getClass().getMethod(methodName, int.class, int.class);
            return m.invoke(mgr, propId, areaId);
        } catch (Exception ignored) { return null; }
    }

    private Object callGetGlobalProperty(Object mgr, int propId) {
        try {
            Method m = mgr.getClass().getMethod("getGlobalProperty", Class.class, int.class);
            return m.invoke(mgr, Object.class, propId);
        } catch (Exception ignored) { return null; }
    }

    // ==================== SAIC HVAC data (via IAirConditionService) ====================

    public String getOutsideTemp() { return callServiceMethod(mAirConditionService, "getOutCarTemp"); }
    public String getDriverTemp() { return callServiceMethod(mAirConditionService, "getDrvTemp"); }
    public String getPassengerTemp() { return callServiceMethod(mAirConditionService, "getPsgTemp"); }
    public String getHvacPowerStatus() { return callServiceMethod(mAirConditionService, "getHvacPowerStatus"); }
    public String getAcSwitch() { return callServiceMethod(mAirConditionService, "getAcSwitch"); }
    public String getAutoStatus() { return callServiceMethod(mAirConditionService, "getAutoStatus"); }
    public String getAirVolumeLevel() { return callServiceMethod(mAirConditionService, "getAirVolumeLevel"); }
    public String getLoopMode() { return callServiceMethod(mAirConditionService, "getLoopMode"); }
    public String getPm25Concentration() { return callServiceMethod(mAirConditionService, "getPm25Concentration"); }
    public String getPm25Filter() { return callServiceMethod(mAirConditionService, "getPm25Filter"); }
    public String getAnionStatus() { return callServiceMethod(mAirConditionService, "getAnionStatus"); }
    public String getDrvSeatHeatLevel() { return callServiceMethod(mAirConditionService, "getDrvSeatHeatLevel"); }
    public String getBlowerDirection() { return callServiceMethod(mAirConditionService, "getBlowerDirectionMode"); }

    // ==================== SAIC Condition data ====================

    public String getCarSpeed() { return callServiceMethod(mConditionService, "getCarSpeed"); }
    public String getCarGear() { return callServiceMethod(mConditionService, "getCarGear"); }
    public String getVinNumber() { return callServiceMethod(mConditionService, "getVinNumber"); }
    public String getDistanceUnit() { return callServiceMethod(mConditionService, "getDistanceUnit"); }

    // ==================== SAIC Charging data ====================

    public float getBatteryLevel() { return callServiceFloat(mChargingService, "getCurrentElectricQuantity"); }
    public int getEnduranceMileage() { return (int) callServiceFloat(mChargingService, "getCurrentEnduranceMileage"); }

    // ==================== Bulk diagnostics - read ALL SAIC service data ====================

    /** Returns a map of "ServiceName.methodName" → value for ALL readable getters across all services */
    public java.util.LinkedHashMap<String, String> getAllSaicData() {
        java.util.LinkedHashMap<String, String> data = new java.util.LinkedHashMap<>();

        // IAirConditionService
        String[][] acMethods = {
            {"getOutCarTemp", "Outside Temp (°C)"}, {"getDrvTemp", "Driver Temp Setting"},
            {"getPsgTemp", "Passenger Temp Setting"}, {"getMinTemp", "Min Temp"},
            {"getMaxTemp", "Max Temp"}, {"getHvacPowerStatus", "HVAC Power"},
            {"getAcSwitch", "AC Switch"}, {"getAutoStatus", "Auto Mode"},
            {"getEconStatus", "Econ Mode"}, {"getAirVolumeLevel", "Air Volume Level"},
            {"getMinAirVolume", "Min Air Volume"}, {"getMaxAirVolume", "Max Air Volume"},
            {"getLoopMode", "Loop/Recirc Mode"}, {"getBlowerDirectionMode", "Blower Direction"},
            {"getFrontWindowDefroster", "Front Defroster"}, {"getBackWindowDefroster", "Rear Defroster"},
            {"getDrvSeatHeatLevel", "Driver Seat Heat"}, {"getPsgSeatHeatLevel", "Passenger Seat Heat"},
            {"getDrvSeatWindLevel", "Driver Seat Ventilation"}, {"getPsgSeatWindLevel", "Passenger Seat Ventilation"},
            {"getAnionStatus", "Anion/Ionizer"}, {"getPm25Concentration", "PM2.5 Inside (µg/m³)"},
            {"getPm25Filter", "PM2.5 Filter Status"}, {"getTempDualZoneOn", "Dual Zone"},
            {"getSmartBlowerStatus", "Smart Blower"}, {"getWindOutletCanStatus", "Wind Outlet Status"},
        };
        for (String[] m : acMethods) data.put("AirCondition." + m[0] + " (" + m[1] + ")", callServiceMethod(mAirConditionService, m[0]));

        // IVehicleConditionService
        String[][] condMethods = {
            {"getCarSpeed", "Speed (km/h)"}, {"getCarGear", "Gear (1=D,2=N,3=R,4=P)"},
            {"getVehicleIgnition", "Ignition State"}, {"getEngineState", "Engine/Motor State"},
            {"getVinNumber", "VIN"}, {"getVehicleType", "Vehicle Type"},
            {"getVehicleExteriorColor", "Exterior Color Code"}, {"getDistanceUnit", "Distance Unit"},
            {"getMileageUnit", "Mileage Unit"}, {"getEcallState", "eCall State"},
            {"getMaintenanceStatus", "Maintenance Status"}, {"getNextResetDay", "Next Service (days)"},
            {"getNextResetMileage", "Next Service (km)"}, {"getAcAvlbly", "AC ECU Available"},
            {"getBcmAvlbly", "BCM Available"}, {"getBmsAvlbly", "BMS Available"},
            {"getHcuAvlbly", "HCU Available"}, {"getMsmAvlbly", "MSM Available"},
            {"getPepsAvlbly", "PEPS Available"}, {"getRadarAvlbly", "Radar Available"},
            {"getScsAvlbly", "SCS Available"}, {"getApaAvlbly", "APA Available"},
            {"getFvcmAvlbly", "FVCM Available"}, {"getPlcmAvlbly", "PLCM Available"},
            {"getConfig360", "360 Camera Config"}, {"getEp21CarConfigCode", "Car Config Code"},
            {"getEngModeStackStatus", "Eng Mode Stack"},
        };
        for (String[] m : condMethods) data.put("Condition." + m[0] + " (" + m[1] + ")", callServiceMethod(mConditionService, m[0]));

        // IVehicleSettingService
        String[][] setMethods = {
            {"getAmbtLightGlbOn", "Ambient Light On"}, {"getAmbtLightBrightness", "Ambient Brightness"},
            {"getAmbtLightColor", "Ambient Color"}, {"getAmbtLightDrvMode", "Ambient Drive Mode"},
            {"getAutoEmergencyBraking", "AEB Switch"}, {"getAutoMainBeamControl", "Auto High Beam"},
            {"getBlindSpotDetection", "Blind Spot Detection"}, {"getFcwAlarmMode", "FCW Alarm Mode"},
            {"getFcwSensitivity", "FCW Sensitivity"}, {"getFcwAutoBrakeMode", "FCW Auto Brake"},
            {"getLaneKeepingAsstMode", "Lane Keeping Mode"}, {"getLaneKeepingAsstSen", "Lane Keeping Sensitivity"},
            {"getLaneChangeAsst", "Lane Change Assist"}, {"getTrafficJamAsstOn", "Traffic Jam Assist"},
            {"getDrivingAutoLock", "Auto Lock While Driving"}, {"getStallingAutoUnlock", "Auto Unlock on Stop"},
            {"getKeyUnlockMode", "Key Unlock Mode"}, {"getInductiveDoorHandle", "Inductive Door Handle"},
            {"getInductiveTailgate", "Inductive Tailgate"}, {"getDrowsinessMonitorSysOn", "Drowsiness Monitor"},
            {"getDrowsinessMonitorSysSen", "Drowsiness Sensitivity"}, {"getSpeedAsstMode", "Speed Assist Mode"},
            {"getRearDriveAsstSys", "Rear Drive Assist"}, {"getRearTrafficWarning", "Rear Traffic Warning"},
            {"getHomeLightTime", "Home Light Timer (s)"}, {"getWelcomeLightTime", "Welcome Light Timer (s)"},
            {"getWelcomeSoundOn", "Welcome Sound"}, {"getWelcomeSoundType", "Welcome Sound Type"},
            {"getDefrostLinkage", "Defrost Linkage"}, {"getHvacEconLinkage", "HVAC Econ Linkage"},
            {"getSeatHeatVentLinkage", "Seat Heat/Vent Linkage"}, {"getTowingMode", "Towing Mode"},
            {"getPsgSafetyAirbagOn", "Passenger Airbag"}, {"getPsgSafetyAirbagStatus", "Passenger Airbag Status"},
            {"getElectricTailgatePos", "Tailgate Position"}, {"getSteeringWheelDefine", "Steering Wheel Define"},
            {"getLeftRearviewDowndip", "Left Mirror Dip"}, {"getRightRearviewDowndip", "Right Mirror Dip"},
            {"getOuterRearviewFold", "Mirror Auto Fold"},
        };
        for (String[] m : setMethods) data.put("Setting." + m[0] + " (" + m[1] + ")", callServiceMethod(mSettingService, m[0]));

        // IVehicleControlService
        String[][] ctrlMethods = {
            {"getDoorLock", "Door Lock"}, {"getEspSwitch", "ESP Switch"},
            {"getHdcSwitch", "HDC Switch"}, {"getPdcSwitch", "PDC Switch"},
            {"getSunroofSwitch", "Sunroof"}, {"getSunroofVentilation", "Sunroof Vent"},
            {"getDriveWindow", "Driver Window Pos"}, {"getPassengerWindow", "Passenger Window Pos"},
            {"getLeftRearWindow", "Left Rear Window Pos"}, {"getRightRearWindow", "Right Rear Window Pos"},
            {"getElectricTailgateEnable", "Tailgate Enable"}, {"getElectricTailgateOpenStatus", "Tailgate Open"},
        };
        for (String[] m : ctrlMethods) data.put("Control." + m[0] + " (" + m[1] + ")", callServiceMethod(mControlService, m[0]));

        // IVehicleChargingService
        String[][] chrgMethods = {
            {"getCurrentElectricQuantity", "Battery Level (%)"}, {"getCurrentEnduranceMileage", "Range (km)"},
            {"getChargingStatus", "Charging Status"}, {"getChargingCurrent", "Charging Current Setting"},
            {"getActualChargingCurrent", "Actual Charging Current (A)"}, {"getExpectedCurrent", "Expected Current (A)"},
            {"getChargingStopReason", "Stop Reason"}, {"getChargingControlSwitch", "Charge Control Switch"},
            {"getChargingLockSwitch", "Charge Lock"}, {"getChargingCloseSoc", "Charge Limit SOC"},
            {"getPredictChargingTime", "Predicted Time (min)"}, {"getDrivingBatteryHeat", "Battery Heater"},
            {"getDischrgControlStatus", "V2L Discharge Status"}, {"getDischrgControlSwitch", "V2L Switch"},
            {"getDischrgCloseSoc", "V2L Stop SOC"}, {"getDischrgCloseSocResp", "V2L Stop SOC Response"},
            {"getPredictDischrgTime", "V2L Time Remaining"}, {"getDischrgClosePredictMileage", "V2L Range Impact (km)"},
            {"getReserChrgControl", "Scheduled Charge On/Off"}, {"getReserChrgStartHour", "Sched Start Hour"},
            {"getReserChrgStartMinute", "Sched Start Minute"}, {"getReserChrgStopHour", "Sched Stop Hour"},
            {"getReserChrgStopMinute", "Sched Stop Minute"}, {"getReserChrgAdpPileType", "Sched Charger Type"},
            {"getIsLowLimit", "Low Battery Limit"},
        };
        for (String[] m : chrgMethods) data.put("Charging." + m[0] + " (" + m[1] + ")", callServiceMethod(mChargingService, m[0]));

        // EngineerMode ISystemSettingsManager
        String[][] engMethods = {
            {"getBatteryPower", "12V Battery Voltage (V)"}, {"getCarSpeed", "Speed (EngMode)"},
            {"getChargeStatus", "Charge Status (EngMode)"}, {"getGearStatus", "Gear (EngMode)"},
            {"getParkingBrakeStatus", "Parking Brake (EngMode)"}, {"getPowerRunType", "Power Run Type"},
            {"getPowerSystemStatus", "Power System Status"}, {"getShowRoom", "Showroom Mode"},
        };
        for (String[] m : engMethods) data.put("EngMode." + m[0] + " (" + m[1] + ")", callServiceMethod(mEngSystemSettings, m[0]));

        // SaicAdapterService - IGeneralService
        String[][] adapterGenMethods = {
            {"geCurTimeZone", "Current Timezone"}, {"getCarMode", "Car Mode"},
            {"getDisplayMode", "Display Mode"}, {"getDistanceUnit", "Distance Unit"},
            {"getDrivingPosition", "Driving Position (L/R)"}, {"getGuideStatus", "Nav Guide Status"},
            {"getIsMapHasProjection", "Map Has Projection"}, {"getIsSetHomeAddress", "Home Address Set"},
            {"getIsSetOfficeAddress", "Office Address Set"}, {"getLocationProvider", "Location Provider"},
            {"getMapAppPkgName", "Map App Package"}, {"getMapAppVersion", "Map App Version"},
            {"getMapResPkgVersion", "Map Resource Version"}, {"getNavCountryCode", "Nav Country Code"},
            {"getNetworkIsAvailable", "Network Available"}, {"getRemainingDistance", "Nav Remaining Distance"},
            {"getRemainingRedLightNumber", "Red Lights Remaining"}, {"getRemainingTimes", "Nav Remaining Time"},
            {"getRoadName", "Current Road Name"}, {"getSpeedLimitValue", "Nav Speed Limit"},
            {"getSubjectID", "Subject ID"}, {"getVehicleLaneInfo", "Lane Info"},
            {"isMapNavigating", "Map Is Navigating"}, {"isNavAppHasActivated", "Nav App Activated"},
        };
        for (String[] m : adapterGenMethods) data.put("Adapter.General." + m[0] + " (" + m[1] + ")", callServiceMethod(mAdapterGeneral, m[0]));

        // SaicAdapterService - IMapService
        String[][] adapterMapMethods = {
            {"getBatteryPercentage", "Battery % (Map)"}, {"getCarMode", "Car Mode (Map)"},
            {"getCarType", "Car Type"}, {"getChargingFinishTime", "Charging Finish Time"},
            {"getChargingStatus", "Charging (Map)"}, {"getClusterIsReady", "Cluster Ready"},
            {"getCurTtsLang", "TTS Language"}, {"getDayNightMode", "Day/Night Mode"},
            {"getDrivingPosition", "Driving Position (Map)"}, {"getEnduranceMileage", "Range (Map)"},
            {"getEvPortType", "EV Port Type"}, {"getFuelType", "Fuel Type"},
            {"getGdprMapStatus", "GDPR Map Status"}, {"getIsSupportGetDayNightMode", "Supports Day/Night"},
            {"getLaunchDisplayId", "Launch Display ID"}, {"getLocationProvider", "Location Provider (Map)"},
            {"getLowBatteryStatus", "Low Battery Status"}, {"getLowFuelStatus", "Low Fuel Status"},
            {"getNetworkIsAvailable", "Network (Map)"}, {"getSubjectID", "Subject ID (Map)"},
            {"getTotalMileage", "Total Mileage (Map)"}, {"getUnitTypeFromAvn", "Unit Type From AVN"},
        };
        for (String[] m : adapterMapMethods) data.put("Adapter.Map." + m[0] + " (" + m[1] + ")", callServiceMethod(mAdapterMap, m[0]));

        // SaicAdapterService - IVoiceVuiService
        String[][] adapterVoiceMethods = {
            {"getCurLocationDesc", "Current Location Description"}, {"getNavAppStatus", "Nav App Status"},
            {"getNetworkIsAvailable", "Network (Voice)"}, {"isMapMaxSize", "Map Max Size"},
            {"isMapMinSize", "Map Min Size"}, {"isMapNavigating", "Map Navigating (Voice)"},
            {"isMapOriginalZoom", "Map Original Zoom"}, {"isMapPlanningRoute", "Map Planning Route"},
            {"isMapReCenter", "Map Re-Centered"}, {"isSetHomeAddress", "Home Address Set (Voice)"},
            {"isSetOfficeAddress", "Office Address Set (Voice)"},
        };
        for (String[] m : adapterVoiceMethods) data.put("Adapter.Voice." + m[0] + " (" + m[1] + ")", callServiceMethod(mAdapterVoice, m[0]));

        // SystemSettingsService - IBtService
        String[][] sysBtMethods = {
            {"getAutoPairMode", "BT Auto Pair"}, {"getBluetoothEnabled", "BT Enabled"},
            {"getCarPlayConnected", "CarPlay Connected"}, {"getDiscoverable", "BT Discoverable"},
            {"getLocalDeviceName", "BT Device Name"}, {"getScreenOperable", "Screen Operable"},
        };
        for (String[] m : sysBtMethods) data.put("SysSettings.BT." + m[0] + " (" + m[1] + ")", callServiceMethod(mSysBt, m[0]));

        // SystemSettingsService - IGeneralService
        String[][] sysGenMethods = {
            {"get24Hour", "24h Time Format"}, {"getAutoTimeState", "Auto Time"},
            {"getBrightness", "Screen Brightness"}, {"getBrightnessAutoState", "Auto Brightness"},
            {"getDayNightAutoMode", "Auto Day/Night"}, {"getDimClockEnabled", "Dim Clock"},
            {"getIsNightMode", "Night Mode Active"}, {"getSummerTimeMode", "Summer Time"},
        };
        for (String[] m : sysGenMethods) data.put("SysSettings.General." + m[0] + " (" + m[1] + ")", callServiceMethod(mSysGeneral, m[0]));

        // SystemSettingsService - IMyCarService
        String[][] sysMycarMethods = {
            {"getDeviceName", "Device Name"}, {"getMcuVersion", "MCU Version"},
            {"getMpuVersion", "MPU Version"}, {"getTboxVersion", "TBox Version"},
            {"getVehicleType", "Vehicle Type (SysSvc)"},
        };
        for (String[] m : sysMycarMethods) data.put("SysSettings.MyCar." + m[0] + " (" + m[1] + ")", callServiceMethod(mSysMycar, m[0]));

        // SystemSettingsService - ISmartSoundService
        String[][] sysSoundMethods = {
            {"get3DEffectType", "3D Sound Effect"}, {"getBoseSoundEffect", "Bose Sound Effect"},
            {"getEqualizerBand", "Equalizer Band"}, {"getLoudnessState", "Loudness"},
            {"getMuteState", "Mute State"}, {"getNaviDuckState", "Navi Duck"},
            {"getRearQuietModeState", "Rear Quiet Mode"}, {"getRingtoneState", "Ringtone"},
            {"getSoundFieldBalance", "Sound Balance"}, {"getSoundFieldFader", "Sound Fader"},
            {"getSpeedVolumeControlLevel", "Speed Volume Level"}, {"getSubwooferState", "Subwoofer"},
            {"getSystemBeepState", "System Beep"}, {"getVehicleInfoVolume", "Vehicle Info Volume"},
            {"getVoiceVolume", "Voice Volume"}, {"isVolumeMax", "Volume Max"},
            {"isVolumeMin", "Volume Min"},
        };
        for (String[] m : sysSoundMethods) data.put("SysSettings.Sound." + m[0] + " (" + m[1] + ")", callServiceMethod(mSysSound, m[0]));

        // SystemSettingsService - IHotspotService
        String[][] sysHotspotMethods = {
            {"getApBand", "AP Band (2.4/5GHz)"}, {"getLocalDeviceName", "Hotspot Name"},
            {"getTetheringEnabled", "Tethering Enabled"},
        };
        for (String[] m : sysHotspotMethods) data.put("SysSettings.Hotspot." + m[0] + " (" + m[1] + ")", callServiceMethod(mSysHotspot, m[0]));

        // SystemSettingsService - IGdprService
        String[][] sysGdprMethods = {
            {"getMapEnabled", "GDPR Map Enabled"}, {"getOnlineMusicEnabled", "GDPR Online Music"},
            {"getPrivacyEnabled", "GDPR Privacy Mode"}, {"getVoiceEnabled", "GDPR Voice Enabled"},
        };
        for (String[] m : sysGdprMethods) data.put("SysSettings.GDPR." + m[0] + " (" + m[1] + ")", callServiceMethod(mSysGdpr, m[0]));

        // SystemSettingsService - IWiFiService
        String[][] sysWifiMethods = {
            {"getWifiEnabled", "WiFi Enabled"},
        };
        for (String[] m : sysWifiMethods) data.put("SysSettings.WiFi." + m[0] + " (" + m[1] + ")", callServiceMethod(mSysWifi, m[0]));

        // SystemSettingsService - IDataUsageService
        String[][] sysDataMethods = {
            {"getDataEnabled", "Mobile Data Enabled"}, {"getRemoteControlEnabled", "Remote Control Enabled"},
        };
        for (String[] m : sysDataMethods) data.put("SysSettings.Data." + m[0] + " (" + m[1] + ")", callServiceMethod(mSysDataUsage, m[0]));

        // vehicleService_overseas - IVehicleAidlInterface
        String[][] vsoMethods = {
            {"getAvnId", "AVN ID"}, {"getBaseUrl", "Server Base URL"},
            {"getToken", "Auth Token"}, {"getUserName", "User Name"},
            {"getUserId", "User ID"}, {"getTrueId", "True ID"},
            {"getPhotoPath", "Photo Path"}, {"getSecurityKey", "Security Key"},
            {"isVehicleActivated", "Vehicle Activated"}, {"isVehicleActivating", "Vehicle Activating"},
            {"isNaviActivated", "Navi Activated"}, {"isNaviActivating", "Navi Activating"},
        };
        for (String[] m : vsoMethods) data.put("VehicleOverseas." + m[0] + " (" + m[1] + ")", callServiceMethod(mVehicleOverseas, m[0]));

        return data;
    }

    /** Generic accessor: call any getter on a named sub-service */
    public String callSaicMethod(String serviceName, String methodName) {
        Object svc = null;
        switch (serviceName) {
            case "aircondition": svc = mAirConditionService; break;
            case "condition": svc = mConditionService; break;
            case "setting": svc = mSettingService; break;
            case "control": svc = mControlService; break;
            case "charging": svc = mChargingService; break;
            case "engmode": svc = mEngSystemSettings; break;
            case "enghardware": svc = mEngSystemHardware; break;
            case "adaptergeneral": svc = mAdapterGeneral; break;
            case "adaptermap": svc = mAdapterMap; break;
            case "adaptervoice": svc = mAdapterVoice; break;
            case "sysbt": svc = mSysBt; break;
            case "sysgeneral": svc = mSysGeneral; break;
            case "sysmycar": svc = mSysMycar; break;
            case "syssound": svc = mSysSound; break;
            case "syshotspot": svc = mSysHotspot; break;
            case "sysgdpr": svc = mSysGdpr; break;
            case "syswifi": svc = mSysWifi; break;
            case "sysdata": svc = mSysDataUsage; break;
            case "overseas": svc = mVehicleOverseas; break;
        }
        return callServiceMethod(svc, methodName);
    }

    // ==================== Generic service method caller ====================

    private String callServiceMethod(Object service, String methodName) {
        if (service == null) return "N/A";
        try {
            Method m = service.getClass().getMethod(methodName);
            Object result = m.invoke(service);
            if (result == null) return "N/A";
            if (result instanceof Float) return String.format("%.1f", (Float) result);
            if (result instanceof Double) return String.format("%.1f", (Double) result);
            return String.valueOf(result);
        } catch (Exception e) {
            Log.d(TAG, methodName + " failed: " + e.getMessage());
            return "N/A";
        }
    }

    private float callServiceFloat(Object service, String methodName) {
        if (service == null) return 0f;
        try {
            Method m = service.getClass().getMethod(methodName);
            Object r = m.invoke(service);
            if (r instanceof Number) return ((Number) r).floatValue();
        } catch (Exception e) { Log.d(TAG, methodName + " failed: " + e.getMessage()); }
        return 0f;
    }

    // ==================== Cleanup ====================

    public void unbindService() {
        if (mSaicBound) { try { mContext.unbindService(mSaicConn); } catch (Exception ignored) {} }
        if (mEngSystemSettings != null) { try { mContext.unbindService(mEngConn); } catch (Exception ignored) {} }
        if (mCar != null) { try { mCar.getClass().getMethod("disconnect").invoke(mCar); } catch (Exception ignored) {} }
    }

    public boolean isConnected() { return mCarConnected; }
    public boolean isSaicConnected() { return mSaicBound; }
    public boolean hasAirCondition() { return mAirConditionService != null; }

    // ==================== DexClassLoader approach (DriveHub pattern) ====================

    /** ClassLoaders built from SAIC service APKs on the car's system partition */
    private java.util.List<ClassLoader> mVsClassLoaders;

    /**
     * Build DexClassLoaders from known SAIC service APK packages.
     * This allows us to load AIDL stub classes that live inside those APKs.
     * No SAIC code is bundled in our APK — we load from APKs already installed on the car.
     */
    private java.util.List<ClassLoader> buildVsClassLoaders() {
        if (mVsClassLoaders != null) return mVsClassLoaders;

        java.util.List<ClassLoader> loaders = new java.util.ArrayList<>();
        String[] saicPackages = {
            "com.saicmotor.service.vehicle",
            "com.saicvehicleservice",
            "com.saicmotor.service.engmode",
            "com.saicmotor.adapterservice",
            "com.saicmotor.service.systemsettings",
        };

        PackageManager pm = mContext.getPackageManager();
        for (String pkg : saicPackages) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if (ai.sourceDir != null) {
                    java.io.File optDir = new java.io.File(mContext.getCodeCacheDir(), "dexopt_" + pkg.replace('.', '_'));
                    optDir.mkdirs();
                    DexClassLoader dcl = new DexClassLoader(
                        ai.sourceDir, optDir.getAbsolutePath(), null, mContext.getClassLoader());
                    loaders.add(dcl);
                    Log.d(TAG, "DexClassLoader created for " + pkg + " → " + ai.sourceDir);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(TAG, "Package not found: " + pkg);
            } catch (Exception e) {
                Log.d(TAG, "DexClassLoader failed for " + pkg + ": " + e.getMessage());
            }
        }

        // Fallback: also try the app's own classloader
        loaders.add(mContext.getClassLoader());
        mVsClassLoaders = loaders;
        return loaders;
    }

    /**
     * Find a class by trying multiple names across multiple classloaders.
     * Returns the first successful Class, or null.
     */
    private Class<?> findVsClass(String[] classNames) {
        java.util.List<ClassLoader> loaders = buildVsClassLoaders();
        for (String name : classNames) {
            for (ClassLoader cl : loaders) {
                try {
                    Class<?> c = Class.forName(name, false, cl);
                    if (c != null) {
                        Log.d(TAG, "findVsClass OK: " + name);
                        return c;
                    }
                } catch (ClassNotFoundException ignored) {}
            }
        }
        Log.d(TAG, "findVsClass FAIL: " + java.util.Arrays.toString(classNames));
        return null;
    }

    // ==================== Reflection helpers ====================

    /**
     * Call Stub.asInterface(binder) using DexClassLoaders to find the Stub class.
     */
    private Object asInterface(String[] stubClassNames, IBinder binder) {
        Class<?> stubClass = findVsClass(stubClassNames);
        if (stubClass != null) {
            try {
                Object r = stubClass.getMethod("asInterface", IBinder.class).invoke(null, binder);
                if (r != null) return r;
            } catch (Exception e) {
                Log.d(TAG, "asInterface invoke failed: " + e.getMessage());
            }
        }
        return null;
    }

    private Object getSubService(Object hub, String[] keys, String ifaceName) {
        String[] stubs = new String[STUB_PREFIXES.length];
        for (int i = 0; i < STUB_PREFIXES.length; i++) stubs[i] = STUB_PREFIXES[i] + ifaceName + "$Stub";

        for (String key : keys) {
            try {
                Object binder = hub.getClass().getMethod("getService", String.class).invoke(hub, key);
                if (binder instanceof IBinder) {
                    Object svc = asInterface(stubs, (IBinder) binder);
                    if (svc != null) { Log.d(TAG, "SubService '" + key + "' → " + ifaceName); return svc; }
                }
            } catch (Exception e) { Log.d(TAG, "getSubService('" + key + "'): " + e.getMessage()); }
        }
        return null;
    }

    private static String getStaticStringField(Class<?> clazz, String fieldName) {
        try { return (String) clazz.getField(fieldName).get(null); }
        catch (Exception e) { return null; }
    }

    private static String formatValue(Object val) {
        if (val instanceof Float) return String.format("%.2f", (Float) val);
        if (val instanceof Double) return String.format("%.2f", (Double) val);
        return String.valueOf(val);
    }
}
