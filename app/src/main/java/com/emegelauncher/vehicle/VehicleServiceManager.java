/*
 * Emegelauncher - Custom Launcher for MG Marvel R
 * Copyright (C) 2026 Emegelauncher Contributors
 *
 * Licensed under the Apache License, Version 2.0 with the
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

    // Radio (Android native RadioManager)
    private volatile boolean mRadioBound = false;

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
        bindRadioService();
        bindMediaServices();
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
            svc -> { mAdapterGeneral = svc; Log.d(TAG, "AdapterGeneral acquired"); registerNavListener(); });
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

    // ==================== Radio (Android native RadioManager HAL via reflection) ====================

    private Object mRadioTuner; // RadioTuner obtained via reflection

    private void bindRadioService() {
        try {
            // Get RadioManager via reflection (hidden API on API 28)
            Object rm = mContext.getSystemService("broadcastradio");
            if (rm == null) {
                Log.w(TAG, "RadioManager: service not available");
                FileLogger.getInstance(mContext).w(TAG, "RadioManager: service not available");
                return;
            }
            Log.d(TAG, "RadioManager obtained: " + rm.getClass().getName());
            FileLogger.getInstance(mContext).i(TAG, "RadioManager obtained: " + rm.getClass().getName());

            // listModules
            java.util.List modules = new java.util.ArrayList();
            java.lang.reflect.Method listModules = rm.getClass().getMethod("listModules", java.util.List.class);
            int status = (int) listModules.invoke(rm, modules);
            Log.d(TAG, "RadioManager: " + modules.size() + " modules, status=" + status);

            if (!modules.isEmpty()) {
                Object module = modules.get(0);
                java.lang.reflect.Method getId = module.getClass().getMethod("getId");
                java.lang.reflect.Method getBands = module.getClass().getMethod("getBands");
                int moduleId = (int) getId.invoke(module);
                Object[] bands = (Object[]) getBands.invoke(module);
                Log.d(TAG, "Radio module id=" + moduleId + " bands=" + bands.length);

                // Find FM band
                Object fmBand = null;
                for (Object band : bands) {
                    java.lang.reflect.Method getType = band.getClass().getMethod("getType");
                    int type = (int) getType.invoke(band);
                    Log.d(TAG, "Radio band type=" + type);
                    if (type == 1) { fmBand = band; break; } // BAND_FM = 1
                }
                if (fmBand == null && bands.length > 0) fmBand = bands[0];

                if (fmBand != null) {
                    // openTuner — try different overloads
                    // openTuner(int moduleId, BandConfig config, boolean withAudio, Callback cb, Handler h)
                    for (java.lang.reflect.Method m : rm.getClass().getMethods()) {
                        if (m.getName().equals("openTuner")) {
                            Log.d(TAG, "RadioManager.openTuner params: " + java.util.Arrays.toString(m.getParameterTypes()));
                            try {
                                // Try without audio control (false) so we don't interfere with radio app
                                mRadioTuner = m.invoke(rm, moduleId, fmBand, false, null, null);
                                if (mRadioTuner != null) {
                                    mRadioBound = true;
                                    Log.d(TAG, "RadioTuner opened (no audio): " + mRadioTuner.getClass().getName());
                                    break;
                                }
                            } catch (Exception e2) {
                                Log.d(TAG, "RadioTuner open attempt failed: " + e2.getMessage());
                            }
                        }
                    }
                    if (mRadioTuner == null) Log.w(TAG, "RadioTuner: failed to open any tuner");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "RadioManager init failed: " + e.getMessage(), e);
        }
    }

    /** Radio: scan to next station */
    public void radioNext(int radioType) {
        if (mRadioTuner == null) { Log.d(TAG, "radioNext: no tuner"); return; }
        try {
            // RadioTuner.scan(int direction, boolean skipSubChannel) — DIRECTION_UP=0
            java.lang.reflect.Method scan = mRadioTuner.getClass().getMethod("scan", int.class, boolean.class);
            int result = (int) scan.invoke(mRadioTuner, 0, true); // 0 = DIRECTION_UP
            Log.d(TAG, "radioNext scan result=" + result);
        } catch (Exception e) { Log.e(TAG, "radioNext failed", e); }
    }

    /** Radio: scan to previous station */
    public void radioPrevious(int radioType) {
        if (mRadioTuner == null) { Log.d(TAG, "radioPrevious: no tuner"); return; }
        try {
            java.lang.reflect.Method scan = mRadioTuner.getClass().getMethod("scan", int.class, boolean.class);
            int result = (int) scan.invoke(mRadioTuner, 1, true); // 1 = DIRECTION_DOWN
            Log.d(TAG, "radioPrevious scan result=" + result);
        } catch (Exception e) { Log.e(TAG, "radioPrevious failed", e); }
    }

    /** Radio: get current frequency in kHz (0 if unavailable) */
    public int radioGetFrequency() {
        if (mRadioTuner == null) return 0;
        try {
            java.lang.reflect.Method getProgramInfo = mRadioTuner.getClass().getMethod("getProgramInformation");
            Object info = getProgramInfo.invoke(mRadioTuner);
            if (info != null) {
                java.lang.reflect.Method getSelector = info.getClass().getMethod("getSelector");
                Object sel = getSelector.invoke(info);
                if (sel != null) {
                    java.lang.reflect.Method getPrimaryId = sel.getClass().getMethod("getPrimaryId");
                    Object primaryId = getPrimaryId.invoke(sel);
                    if (primaryId != null) {
                        java.lang.reflect.Method getValue = primaryId.getClass().getMethod("getValue");
                        return (int) (long) getValue.invoke(primaryId);
                    }
                }
            }
        } catch (Exception e) { Log.d(TAG, "radioGetFrequency: " + e.getMessage()); }
        return 0;
    }

    /** Radio: get signal strength (0-200 range, 0 if unavailable) */
    public int radioGetSignalStrength() {
        if (mRadioTuner == null) return 0;
        try {
            java.lang.reflect.Method getProgramInfo = mRadioTuner.getClass().getMethod("getProgramInformation");
            Object info = getProgramInfo.invoke(mRadioTuner);
            if (info != null) {
                // ProgramInfo.getSignalStrength() — available on API 28+
                java.lang.reflect.Method getSignal = info.getClass().getMethod("getSignalStrength");
                return (int) getSignal.invoke(info);
            }
        } catch (Exception e) { Log.d(TAG, "radioGetSignalStrength: " + e.getMessage()); }
        return 0;
    }

    /** Radio: start playback (open tuner if needed) */
    public void radioPlay() {
        if (mRadioTuner == null) {
            Log.d(TAG, "radioPlay: no tuner, trying to open");
            bindRadioService();
        }
        Log.d(TAG, "radioPlay: tuner=" + (mRadioTuner != null));
    }

    /** Check if radio tuner is available */
    public boolean isRadioBound() { return mRadioBound; }

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
            if (result instanceof String || result instanceof Number || result instanceof Boolean) {
                return String.valueOf(result);
            }
            // Bean object — extract all fields via reflection
            return beanToString(result);
        } catch (Exception e) {
            Log.d(TAG, methodName + " failed: " + e.getMessage());
            return "N/A";
        }
    }

    /** Extract all fields from a bean object via reflection (recursive for nested beans) */
    private String beanToString(Object bean) {
        return beanToString(bean, 0);
    }

    private String beanToString(Object bean, int depth) {
        if (depth > 3) return String.valueOf(bean); // prevent infinite recursion
        try {
            StringBuilder sb = new StringBuilder();
            java.lang.reflect.Field[] fields = bean.getClass().getDeclaredFields();
            for (java.lang.reflect.Field f : fields) {
                f.setAccessible(true);
                Object val = f.get(bean);
                if (val != null) {
                    // Skip static fields (like CREATOR in Parcelable beans)
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (sb.length() > 0) sb.append("\n");
                    String prefix = depth > 0 ? "  " : "";
                    // Check if val is a primitive/wrapper/string or a nested bean
                    if (val instanceof String || val instanceof Number || val instanceof Boolean || val instanceof Character) {
                        sb.append(prefix).append(f.getName()).append(": ").append(val);
                    } else if (val.getClass().isArray()) {
                        // Handle arrays (byte[], Byte[], int[], etc.)
                        sb.append(prefix).append(f.getName()).append(": ");
                        int len = java.lang.reflect.Array.getLength(val);
                        sb.append("[");
                        for (int i = 0; i < len; i++) {
                            if (i > 0) sb.append(", ");
                            Object elem = java.lang.reflect.Array.get(val, i);
                            if (elem instanceof Number || elem instanceof Boolean || elem instanceof String) {
                                sb.append(elem);
                            } else if (elem != null) {
                                sb.append(beanToString(elem, depth + 1));
                            } else {
                                sb.append("null");
                            }
                            if (i > 50) { sb.append("... (").append(len).append(" total)"); break; }
                        }
                        sb.append("]");
                    } else if (val instanceof java.util.Collection) {
                        // Handle List, Set, etc.
                        java.util.Collection<?> col = (java.util.Collection<?>) val;
                        sb.append(prefix).append(f.getName()).append(": [");
                        int i = 0;
                        for (Object elem : col) {
                            if (i > 0) sb.append(", ");
                            if (elem instanceof Number || elem instanceof Boolean || elem instanceof String) {
                                sb.append(elem);
                            } else if (elem != null) {
                                sb.append(beanToString(elem, depth + 1));
                            } else {
                                sb.append("null");
                            }
                            if (i++ > 50) { sb.append("... (").append(col.size()).append(" total)"); break; }
                        }
                        sb.append("]");
                    } else if (val.getClass().getName().startsWith("java.")) {
                        sb.append(prefix).append(f.getName()).append(": ").append(val);
                    } else {
                        // Nested bean object — recurse
                        String nested = beanToString(val, depth + 1);
                        sb.append(prefix).append(f.getName()).append(":\n").append(nested);
                    }
                }
            }
            return sb.length() > 0 ? sb.toString() : String.valueOf(bean);
        } catch (Exception e) {
            return String.valueOf(bean);
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

    // ==================== Binder.transact (DriveHub pattern) ====================

    /**
     * Get the IBinder from a SAIC sub-service proxy via asBinder() reflection.
     * AIDL proxies implement IInterface which has asBinder().
     */
    private IBinder getServiceBinder(Object service) {
        if (service == null) return null;
        try {
            Method m = service.getClass().getMethod("asBinder");
            return (IBinder) m.invoke(service);
        } catch (Exception e) {
            Log.d(TAG, "asBinder failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Call Binder.transact() on IVehicleSettingService with an int value.
     * This is the same approach DriveHub uses for regen level, one-pedal, drive mode.
     * @param txCode Transaction code (e.g. 0xA1 for regen level)
     * @param value  The int value to set
     * @return true if transact succeeded
     */
    public boolean transactSettingInt(int txCode, int value) {
        IBinder binder = getServiceBinder(mSettingService);
        if (binder == null) {
            Log.e(TAG, "transactSettingInt: mSettingService binder is null");
            return false;
        }
        android.os.Parcel data = android.os.Parcel.obtain();
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
            // Write the AIDL interface descriptor (required by Stub.onTransact)
            try {
                // Get the DESCRIPTOR from the Stub class
                String descriptor = null;
                java.lang.reflect.Field[] fields = mSettingService.getClass().getDeclaredFields();
                // Proxy classes don't have DESCRIPTOR directly — check parent Stub
                Class<?> stubClass = mSettingService.getClass().getEnclosingClass();
                if (stubClass != null) {
                    try {
                        java.lang.reflect.Field df = stubClass.getDeclaredField("DESCRIPTOR");
                        df.setAccessible(true);
                        descriptor = (String) df.get(null);
                    } catch (Exception ignored) {}
                }
                if (descriptor == null) {
                    // Fallback: AIDL descriptors follow package.InterfaceName pattern
                    descriptor = "com.saicmotor.telematics.tsgp.otaadapter.IVehicleSettingService";
                }
                data.writeInterfaceToken(descriptor);
            } catch (Exception e) {
                Log.d(TAG, "writeInterfaceToken fallback: " + e.getMessage());
                data.writeInterfaceToken("com.saicmotor.telematics.tsgp.otaadapter.IVehicleSettingService");
            }
            data.writeInt(value);
            boolean ok = binder.transact(txCode, data, reply, 0);
            reply.readException();
            Log.d(TAG, "transactSettingInt(0x" + Integer.toHexString(txCode) + ", " + value + ") = " + ok);
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "transactSettingInt failed: " + e.getMessage());
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /** Check if the setting service binder is available for transact operations */
    public boolean hasSettingBinder() {
        return getServiceBinder(mSettingService) != null;
    }

    /**
     * Enumerate all TRANSACTION_* constants from an AIDL Stub class.
     */
    public java.util.LinkedHashMap<String, Integer> enumerateSettingTransactionCodes() {
        return enumerateTransactionCodes(mSettingService, "IVehicleSettingService");
    }

    /** Enumerate TX codes for any service */
    public java.util.LinkedHashMap<String, Integer> enumerateTransactionCodes(Object service, String ifaceName) {
        java.util.LinkedHashMap<String, Integer> result = new java.util.LinkedHashMap<>();
        if (service == null) return result;
        try {
            Class<?> stubClass = service.getClass().getEnclosingClass();
            if (stubClass == null) {
                String[] stubs = new String[STUB_PREFIXES.length];
                for (int i = 0; i < STUB_PREFIXES.length; i++)
                    stubs[i] = STUB_PREFIXES[i] + ifaceName + "$Stub";
                stubClass = findVsClass(stubs);
            }
            if (stubClass == null) return result;
            java.lang.reflect.Field[] fields = stubClass.getDeclaredFields();
            for (java.lang.reflect.Field f : fields) {
                if (f.getName().startsWith("TRANSACTION_") && f.getType() == int.class) {
                    f.setAccessible(true);
                    int code = f.getInt(null);
                    result.put(f.getName().substring("TRANSACTION_".length()), code);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "enumerateTransactionCodes failed: " + e.getMessage());
        }
        return result;
    }

    /** Enumerate TX codes for ALL connected services */
    public java.util.LinkedHashMap<String, Integer> enumerateAllTransactionCodes() {
        java.util.LinkedHashMap<String, Integer> all = new java.util.LinkedHashMap<>();
        Object[][] services = {
            {mSettingService, "IVehicleSettingService"},
            {mChargingService, "IVehicleChargingService"},
            {mControlService, "IVehicleControlService"},
            {mConditionService, "IVehicleConditionService"},
            {mAirConditionService, "IAirConditionService"},
        };
        for (Object[] s : services) {
            if (s[0] == null) continue;
            java.util.LinkedHashMap<String, Integer> codes = enumerateTransactionCodes(s[0], (String) s[1]);
            for (java.util.Map.Entry<String, Integer> e : codes.entrySet()) {
                all.put(s[1] + "." + e.getKey(), e.getValue());
            }
        }
        return all;
    }

    /**
     * Call Binder.transact() on any named service.
     */
    public boolean transactServiceInt(String serviceName, int txCode, int value) {
        Object service = null;
        switch (serviceName) {
            case "setting": service = mSettingService; break;
            case "charging": service = mChargingService; break;
            case "control": service = mControlService; break;
            case "condition": service = mConditionService; break;
            case "aircondition": service = mAirConditionService; break;
        }
        if (service == null) { Log.e(TAG, "transactServiceInt: " + serviceName + " is null"); return false; }
        IBinder binder = getServiceBinder(service);
        if (binder == null) { Log.e(TAG, "transactServiceInt: binder null for " + serviceName); return false; }

        android.os.Parcel data = android.os.Parcel.obtain();
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
            // Get DESCRIPTOR from Stub class
            String descriptor = null;
            Class<?> stubClass = service.getClass().getEnclosingClass();
            if (stubClass != null) {
                try {
                    java.lang.reflect.Field df = stubClass.getDeclaredField("DESCRIPTOR");
                    df.setAccessible(true);
                    descriptor = (String) df.get(null);
                } catch (Exception ignored) {}
            }
            if (descriptor == null) descriptor = "unknown";

            data.writeInterfaceToken(descriptor);
            data.writeInt(value);
            boolean ok = binder.transact(txCode, data, reply, 0);
            reply.readException();
            Log.d(TAG, "transactServiceInt(" + serviceName + ", 0x" + Integer.toHexString(txCode) + ", " + value + ") = " + ok);
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "transactServiceInt failed: " + e.getMessage());
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    // ==================== Layer 7: SAIC Media Services ====================
    // IRadioAppService (FM/AM/DAB) & IPlayStatusBinderInterface (USB/BT/Online)
    // See MEDIA_REFERENCE.md for full API documentation.

    // getCurrentPlayer() return values (confirmed from decompiled MediaPlayControlManager)
    public static final int MEDIA_SOURCE_USB = 2;
    public static final int MEDIA_SOURCE_ONLINE = 3;
    public static final int MEDIA_SOURCE_BT = 4;
    public static final int MEDIA_SOURCE_CP = 5;   // CarPlay music
    public static final int MEDIA_SOURCE_AA = 6;   // Android Auto music
    // Radio type constants (RadioBean.mRadioType values — confirmed from car logs)
    public static final int RADIO_TYPE_AM = 1;
    public static final int RADIO_TYPE_FM = 2;
    public static final int RADIO_TYPE_DAB = 3;  // Used by tune() and next()
    public static final int RADIO_TYPE_DAB_ACTUAL = 4; // Actual value from RadioBean on Marvel R

    // Active audio source tracking (our unified source IDs)
    public static final int SOURCE_NONE = 0;
    public static final int SOURCE_FM = 1;
    public static final int SOURCE_AM = 2;
    public static final int SOURCE_DAB = 3;
    public static final int SOURCE_USB = 4;
    public static final int SOURCE_BT = 5;
    public static final int SOURCE_ONLINE = 6;
    public static final int SOURCE_CARPLAY = 7;
    public static final int SOURCE_AA = 8;

    // RemoteUI (CarPlay/Android Auto) — device type constants from Allgo
    public static final int REMOTE_DEVICE_CARPLAY = 1;
    public static final int REMOTE_DEVICE_AA = 2;

    private volatile Object mMediaService;       // IPlayStatusBinderInterface proxy
    private volatile Object mRadioService;        // IRadioAppService proxy
    private volatile Object mMediaPlayControlMgr; // MediaPlayControlManager SDK instance (if available)
    private volatile boolean mMediaBound = false;
    private volatile boolean mRadioSaicBound = false;
    private volatile boolean mMusicSdkConnected = false;
    private volatile int mActiveSource = SOURCE_NONE;

    // Cached media state (updated via callbacks or polling)
    private volatile String mMediaTitle = null;
    private volatile String mMediaArtist = null;
    private volatile String mMediaAlbum = null;
    private volatile android.graphics.Bitmap mMediaCoverArt = null;
    private volatile boolean mMediaPlaying = false;
    private volatile long mMediaPosition = 0;
    private volatile long mMediaDuration = 0;
    private volatile int mMediaPlayerType = 0;

    // Cached radio state (updated via polling)
    private volatile String mRadioStationName = null;
    private volatile int mRadioFreqKhz = 0;
    private volatile int mRadioType = 0;
    private volatile boolean mRadioPlaying = false;
    private volatile String mRadioDabService = null;
    private volatile android.graphics.Bitmap mRadioDabSlideshow = null;
    private volatile int mRadioLoggedOnce = 0;

    // Play state tracking
    private volatile boolean mManualPaused = false;
    private volatile long mSourceLockUntil = 0;
    private int mPrevRadioType = 0;

    // RemoteUI (CarPlay / Android Auto)
    private volatile Object mRemoteUIService;     // IRemoteUIService proxy
    private volatile boolean mRemoteUIBound = false;
    private volatile int mConnectedDeviceType = 0; // 0=none, 1=CarPlay, 2=AA
    private volatile Object mConnectedRemoteDevice = null;
    private Runnable mOnProjectionConnected;       // Callback for auto-launch

    public void bindMediaServices() {
        addMediaClassLoaders();
        bindMediaPlayControl();
        bindSaicRadioService();
        bindRemoteUIService();
        initMediaPlayControlManager();
    }

    /**
     * Bind to IPlayStatusBinderInterface via MediaService.
     * CRITICAL: SAIC BaseManager uses BOTH setAction() AND setClassName() on the same Intent.
     * Using only one or the other causes onServiceConnected to never fire.
     */
    private void bindMediaPlayControl() {
        String pkg = "com.saicmotor.service.media";
        String cls = "com.saicmotor.service.media.MediaService";
        String[] stubs = { "com.saicmotor.sdk.media.IPlayStatusBinderInterface$Stub" };

        // Bind with each action — each sub-service uses a different action but same class
        String[] actions = {
            "com.saicmotor.service.media.PLAY_STATUS_ACTION",   // MediaPlayControlManager
            "com.saicmotor.service.media.MUSIC_PLAYER_ACTION",  // UsbMusicManager
            "com.saicmotor.service.media.BT_MUSIC_ACTION",      // BtMusicManager
            "com.saicmotor.service.media.ONLINE_MUSIC_ACTION",  // OnlineMusicManager
        };
        for (String action : actions) {
            try {
                Intent intent = new Intent();
                intent.setAction(action);
                intent.setClassName(pkg, cls);  // BOTH action AND className required
                mContext.bindService(intent, new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        FileLogger.getInstance(mContext).i(TAG, "Media connected: " + action);
                        Object svc = asInterface(stubs, service);
                        if (svc != null && mMediaService == null) {
                            mMediaService = svc;
                            mMediaBound = true;
                            FileLogger.getInstance(mContext).i(TAG, "IPlayStatusBinder acquired");
                            logMediaMethods(svc);
                        } else if (svc == null) {
                            FileLogger.getInstance(mContext).w(TAG, "asInterface null for " + action);
                        }
                    }
                    @Override public void onServiceDisconnected(ComponentName name) {
                        mMediaService = null; mMediaBound = false;
                    }
                }, Context.BIND_AUTO_CREATE);
            } catch (Exception e) {
                Log.d(TAG, "Media bind " + action + ": " + e.getMessage());
            }
        }
    }

    /**
     * Bind to IRadioAppService via radio service.
     * Uses action + package (confirmed from decompiled RadioOptionManager.connectService).
     */
    /**
     * Try to instantiate MediaPlayControlManager from the SAIC SDK via DexClassLoader.
     * This is how the original launcher gets music metadata — via IAudioStatusListener callbacks.
     * If this works, we get real-time title/artist/cover/state for ALL music sources (USB/BT/Online).
     * If it fails, we fall back to Android MediaSession (works for BT only).
     */
    private void initMediaPlayControlManager() {
        new Thread(() -> {
            try {
                // Find the classes from DexClassLoader
                Class<?> mpcmClass = findVsClass(new String[]{
                    "com.saicmotor.sdk.media.mananger.MediaPlayControlManager"
                });
                Class<?> listenerClass = findVsClass(new String[]{
                    "com.saicmotor.sdk.media.contractinterface.IMediaServiceListener"
                });
                Class<?> audioListenerClass = findVsClass(new String[]{
                    "com.saicmotor.sdk.media.mananger.MediaPlayControlManager$IAudioStatusListener"
                });

                if (mpcmClass == null || listenerClass == null) {
                    FileLogger.getInstance(mContext).w(TAG, "MusicSDK: MediaPlayControlManager or IMediaServiceListener class not found");
                    return;
                }

                FileLogger.getInstance(mContext).i(TAG, "MusicSDK: classes found, creating service listener proxy");

                // Create IMediaServiceListener proxy
                Object serviceListener = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class[]{listenerClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("onServiceConnected".equals(name) && args != null && args.length > 0) {
                            Object manager = args[0];
                            FileLogger.getInstance(mContext).i(TAG, "MusicSDK: onServiceConnected, manager=" + manager.getClass().getName());
                            mMediaPlayControlMgr = manager;
                            mMusicSdkConnected = true;
                            // Register IAudioStatusListener
                            registerAudioStatusListener(manager, audioListenerClass);
                        } else if ("onServiceDisconnected".equals(name)) {
                            FileLogger.getInstance(mContext).i(TAG, "MusicSDK: onServiceDisconnected");
                            mMusicSdkConnected = false;
                        }
                        return null;
                    }
                );

                // Call MediaPlayControlManager.getInstance(context, listener)
                Method getInstance = mpcmClass.getMethod("getInstance",
                    android.content.Context.class, listenerClass);
                Object mgr = getInstance.invoke(null, mContext, serviceListener);
                FileLogger.getInstance(mContext).i(TAG, "MusicSDK: getInstance returned " + (mgr != null ? mgr.getClass().getName() : "null"));

            } catch (Exception e) {
                FileLogger.getInstance(mContext).w(TAG, "MusicSDK: init failed: " + e.getMessage());
            }
        }).start();
    }

    /** Register IAudioStatusListener on the MediaPlayControlManager for real-time music callbacks */
    private void registerAudioStatusListener(Object manager, Class<?> audioListenerClass) {
        if (audioListenerClass == null) {
            FileLogger.getInstance(mContext).w(TAG, "MusicSDK: IAudioStatusListener class not found");
            return;
        }
        try {
            Object audioListener = java.lang.reflect.Proxy.newProxyInstance(
                audioListenerClass.getClassLoader(),
                new Class[]{audioListenerClass},
                (proxy, method, args) -> {
                    String name = method.getName();
                    switch (name) {
                        case "onPlayStateChanged":
                            if (args != null && args.length > 0 && args[0] instanceof Number) {
                                int state = ((Number) args[0]).intValue();
                                mMediaPlaying = (state == 3); // 3 = playing
                                FileLogger.getInstance(mContext).d(TAG, "MusicSDK CB onPlayStateChanged: " + state);
                            }
                            break;
                        case "onPlayerChanged":
                            if (args != null && args.length > 0 && args[0] instanceof Number) {
                                int resId = ((Number) args[0]).intValue();
                                mMediaPlayerType = resId;
                                FileLogger.getInstance(mContext).i(TAG, "MusicSDK CB onPlayerChanged: " + resId
                                    + " (2=USB 3=Online 4=BT 5=CP 6=AA)");
                            }
                            break;
                        case "onPlayMusicInfoChanged":
                            if (args != null && args.length > 0 && args[0] != null) {
                                Object bean = args[0];
                                String title = readBeanField(bean, "mAudioName");
                                String artist = readBeanField(bean, "mAudioArtistName");
                                String album = readBeanField(bean, "mAudioAlbumName");
                                android.graphics.Bitmap cover = getBeanBitmapField(bean, "mCpAlbumArt");
                                if (cover == null) cover = getBeanBitmapField(bean, "mAlbumArt");
                                if (title != null && !title.isEmpty()) mMediaTitle = title;
                                if (artist != null && !artist.isEmpty()) mMediaArtist = artist;
                                if (album != null && !album.isEmpty()) mMediaAlbum = album;
                                if (cover != null) mMediaCoverArt = cover;
                                FileLogger.getInstance(mContext).d(TAG, "MusicSDK CB onPlayMusicInfoChanged: "
                                    + title + " / " + artist + " cover=" + (cover != null));
                            }
                            break;
                        case "onProgressChanged":
                            if (args != null && args.length >= 2) {
                                if (args[0] instanceof Number) mMediaDuration = ((Number) args[0]).longValue();
                                if (args[1] instanceof Number) mMediaPosition = ((Number) args[1]).longValue();
                            }
                            break;
                    }
                    return null;
                }
            );

            Method addListener = manager.getClass().getMethod("addAudioStatusListener", audioListenerClass);
            addListener.invoke(manager, audioListener);
            FileLogger.getInstance(mContext).i(TAG, "MusicSDK: IAudioStatusListener registered");
        } catch (Exception e) {
            FileLogger.getInstance(mContext).w(TAG, "MusicSDK: registerAudioStatusListener failed: " + e.getMessage());
        }
    }

    /** Check if music SDK callbacks are active (determines whether to use SDK data or MediaSession fallback) */
    public boolean isMusicSdkConnected() { return mMusicSdkConnected; }

    private void bindSaicRadioService() {
        String[] stubs = { "com.saicmotor.sdk.radio.IRadioAppService$Stub" };
        try {
            Intent intent = new Intent();
            intent.setAction("com.saicmotor.service.radio.radioservice");
            intent.setPackage("com.saicmotor.service.radio");
            mContext.bindService(intent, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    FileLogger.getInstance(mContext).i(TAG, "Radio SAIC connected");
                    Object svc = asInterface(stubs, service);
                    if (svc != null) {
                        mRadioService = svc;
                        mRadioSaicBound = true;
                        FileLogger.getInstance(mContext).i(TAG, "IRadioAppService acquired");
                        logMediaMethods(svc);
                    }
                }
                @Override public void onServiceDisconnected(ComponentName name) {
                    mRadioService = null; mRadioSaicBound = false;
                }
            }, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.d(TAG, "Radio bind: " + e.getMessage());
        }
    }

    /**
     * Bind to Allgo RemoteUIService for CarPlay/Android Auto detection and control.
     * This is how the original SAIC launcher detects phone connections and launches projection.
     */
    private void bindRemoteUIService() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.allgo.rui", "com.allgo.rui.RemoteUIService"));
            mContext.bindService(intent, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    FileLogger.getInstance(mContext).i(TAG, "RemoteUIService connected");
                    String[] stubs = { "com.allgo.rui.IRemoteUIService$Stub" };
                    Object svc = asInterface(stubs, service);
                    if (svc != null) {
                        mRemoteUIService = svc;
                        mRemoteUIBound = true;
                        FileLogger.getInstance(mContext).i(TAG, "IRemoteUIService acquired");
                        logMediaMethods(svc);
                        registerRemoteUICallbacks(svc);
                    }
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mRemoteUIService = null;
                    mRemoteUIBound = false;
                    mConnectedDeviceType = 0;
                    mConnectedRemoteDevice = null;
                }
            }, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.d(TAG, "RemoteUI bind: " + e.getMessage());
            FileLogger.getInstance(mContext).d(TAG, "RemoteUI bind failed: " + e.getMessage());
        }
    }

    /**
     * Register IRemoteDeviceCallback with RemoteUIService to receive device connection events.
     * Uses dynamic proxy since the callback interface is in the Allgo APK classloader.
     */
    private void registerRemoteUICallbacks(Object service) {
        try {
            // Find the callback interface class (prefer interface over Stub)
            Class<?> deviceCbClass = findVsClass(new String[]{
                "com.allgo.rui.IRemoteDeviceCallback"
            });
            if (deviceCbClass == null) {
                deviceCbClass = findVsClass(new String[]{
                    "com.allgo.rui.IRemoteDeviceCallback$Stub"
                });
            }
            Class<?> sessionCbClass = findVsClass(new String[]{
                "com.allgo.rui.IRemoteSessionCallback",
                "com.allgo.rui.IRemoteSessionCallback$Stub"
            });
            Class<?> errorCbClass = findVsClass(new String[]{
                "com.allgo.rui.IErrorCallback",
                "com.allgo.rui.IErrorCallback$Stub"
            });

            if (deviceCbClass == null) {
                FileLogger.getInstance(mContext).w(TAG, "IRemoteDeviceCallback class not found");
                // Fallback: try to get active device without callbacks
                pollRemoteUIDevice(service);
                return;
            }

            // Create dynamic proxy for IRemoteDeviceCallback
            Object deviceCallback = java.lang.reflect.Proxy.newProxyInstance(
                deviceCbClass.getClassLoader(),
                new Class[]{deviceCbClass},
                (proxy, method, args) -> {
                    String mName = method.getName();
                    FileLogger.getInstance(mContext).i(TAG, "RemoteUI callback: " + mName);
                    if ("onDeviceConnected".equals(mName) && args != null && args.length > 0) {
                        handleDeviceConnected(args[0]);
                    } else if ("onDeviceDisconnected".equals(mName) && args != null && args.length > 0) {
                        handleDeviceDisconnected(args[0]);
                    } else if ("onDeviceUpdated".equals(mName) && args != null && args.length > 0) {
                        handleDeviceConnected(args[0]);
                    } else if ("onDeviceNotCapable".equals(mName)) {
                        String reason = (args != null && args.length > 0) ? String.valueOf(args[0]) : "unknown";
                        FileLogger.getInstance(mContext).w(TAG, "Device not capable: " + reason);
                        mConnectedDeviceType = 0;
                        mConnectedRemoteDevice = null;
                    } else if ("asBinder".equals(mName)) {
                        return null;
                    }
                    return null;
                }
            );

            // Try register(IRemoteDeviceCallback, IRemoteSessionCallback, IErrorCallback)
            boolean registered = false;
            // Try with all 3 callback types if available
            if (sessionCbClass != null && errorCbClass != null) {
                try {
                    Method register = service.getClass().getMethod("register",
                        deviceCbClass, sessionCbClass, errorCbClass);
                    register.invoke(service, deviceCallback, null, null);
                    registered = true;
                } catch (Exception ignored) {}
            }
            // Try finding register method by iterating all methods
            if (!registered) {
                for (Method m : service.getClass().getMethods()) {
                    if ("register".equals(m.getName()) && m.getParameterTypes().length >= 1) {
                        try {
                            Class<?>[] params = m.getParameterTypes();
                            Object[] invokeArgs = new Object[params.length];
                            invokeArgs[0] = deviceCallback;
                            // Fill remaining args with null
                            m.invoke(service, invokeArgs);
                            registered = true;
                            break;
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (registered) {
                FileLogger.getInstance(mContext).i(TAG, "RemoteUI callbacks registered");
            } else {
                FileLogger.getInstance(mContext).d(TAG, "RemoteUI register failed, polling fallback");
                pollRemoteUIDevice(service);
            }
        } catch (Exception e) {
            FileLogger.getInstance(mContext).d(TAG, "RemoteUI callbacks setup failed: " + e.getMessage());
            pollRemoteUIDevice(service);
        }
    }

    /** Poll RemoteUIService for active device — covers cases where callback missed the event */
    private void pollRemoteUIDevice(Object service) {
        try {
            Method getActive = service.getClass().getMethod("getActiveRemoteDevice");
            Object device = getActive.invoke(service);
            if (device != null) {
                int type = readBeanInt(device, "type");
                if (type > 0 && type != mConnectedDeviceType) {
                    FileLogger.getInstance(mContext).i(TAG, "Polled active device: type=" + type);
                    handleDeviceConnected(device);
                }
            } else if (mConnectedDeviceType > 0) {
                // Device was connected but now isn't
                FileLogger.getInstance(mContext).d(TAG, "Polled: no active device (was " + mConnectedDeviceType + ")");
                mConnectedDeviceType = 0;
                mConnectedRemoteDevice = null;
            }
        } catch (Exception e) {
            FileLogger.getInstance(mContext).d(TAG, "getActiveRemoteDevice: " + e.getMessage());
        }
    }

    private void handleDeviceConnected(Object remoteDevice) {
        if (remoteDevice == null) return;
        try {
            // Read device type field
            int type = readBeanInt(remoteDevice, "type");
            String btMac = readBeanField(remoteDevice, "btMacAddress");
            FileLogger.getInstance(mContext).i(TAG, "Device connected: type=" + type
                + (type == 1 ? " (CarPlay)" : type == 2 ? " (AndroidAuto)" : "")
                + " bt=" + btMac);

            mConnectedDeviceType = type;
            mConnectedRemoteDevice = remoteDevice;

            // Auto-launch projection (same as SAIC launcher)
            if (type == REMOTE_DEVICE_CARPLAY || type == REMOTE_DEVICE_AA) {
                launchProjection(type);
                if (mOnProjectionConnected != null) {
                    mHandler.post(mOnProjectionConnected);
                }
            }
        } catch (Exception e) {
            FileLogger.getInstance(mContext).w(TAG, "handleDeviceConnected failed: " + e.getMessage());
        }
    }

    private void handleDeviceDisconnected(Object remoteDevice) {
        FileLogger.getInstance(mContext).i(TAG, "Device disconnected");
        mConnectedDeviceType = 0;
        mConnectedRemoteDevice = null;
    }

    // ==================== CarPlay/AA public API ====================

    /** Launch projection screen for connected device. Called automatically on connect. */
    public void launchProjection(int deviceType) {
        if (mRemoteUIService != null) {
            try {
                // launchApp(int appId, int deviceType) — appId 0 = Home
                Method launch = mRemoteUIService.getClass().getMethod("launchApp", int.class, int.class);
                launch.invoke(mRemoteUIService, 0, deviceType);
                FileLogger.getInstance(mContext).i(TAG, "launchApp(0, " + deviceType + ") OK");
                return;
            } catch (Exception e) {
                FileLogger.getInstance(mContext).d(TAG, "launchApp failed: " + e.getMessage());
            }
        }
        // Fallback: try launching the Allgo activities directly
        if (deviceType == REMOTE_DEVICE_CARPLAY) {
            launchActivity("com.allgo.carplay.service", "com.allgo.carplay.service.CarPlayActivity");
        } else if (deviceType == REMOTE_DEVICE_AA) {
            launchActivity("com.allgo.app.androidauto", "com.allgo.app.androidauto.ProjectionActivity");
        }
    }

    /** Resume projection after switching to another car app */
    public void resumeProjection() {
        if (mConnectedDeviceType == 0) return;
        if (mRemoteUIService != null && mConnectedRemoteDevice != null) {
            try {
                Method resume = mRemoteUIService.getClass().getMethod("resumeRemoteSession",
                    mConnectedRemoteDevice.getClass());
                resume.invoke(mRemoteUIService, mConnectedRemoteDevice);
                FileLogger.getInstance(mContext).d(TAG, "resumeRemoteSession OK");
                return;
            } catch (Exception e) {
                // Try without specific class
                try {
                    for (Method m : mRemoteUIService.getClass().getMethods()) {
                        if ("resumeRemoteSession".equals(m.getName())) {
                            m.invoke(mRemoteUIService, mConnectedRemoteDevice);
                            return;
                        }
                    }
                } catch (Exception ignored) {}
                FileLogger.getInstance(mContext).d(TAG, "resumeRemoteSession: " + e.getMessage());
            }
        }
        // Fallback
        launchProjection(mConnectedDeviceType);
    }

    /** Launch specific app on connected device (0=Home, 1=Music, 2=Maps, 3=Phone) */
    public void launchProjectionApp(int appId) {
        if (mRemoteUIService != null && mConnectedDeviceType > 0) {
            try {
                Method launch = mRemoteUIService.getClass().getMethod("launchApp", int.class, int.class);
                launch.invoke(mRemoteUIService, appId, mConnectedDeviceType);
                return;
            } catch (Exception ignored) {}
        }
        resumeProjection();
    }

    public boolean isProjectionConnected() { return mConnectedDeviceType > 0; }
    public boolean isCarPlayConnected() { return mConnectedDeviceType == REMOTE_DEVICE_CARPLAY; }
    public boolean isAndroidAutoConnected() { return mConnectedDeviceType == REMOTE_DEVICE_AA; }
    public int getConnectedDeviceType() { return mConnectedDeviceType; }

    /** Set callback for when a projection device connects (for UI updates) */
    public void setOnProjectionConnected(Runnable callback) {
        mOnProjectionConnected = callback;
    }

    // ==================== Navigation (IGeneralNotificationListener) ====================

    // Navigation state — updated via IGeneralNotificationListener callbacks (push-based)
    private volatile boolean mNavIsNavigating = false;
    private volatile int mNavIconIndex = -1;      // Turn icon index (0-39)
    private volatile int mNavDistanceM = 0;       // Distance to next turn (meters)
    private volatile String mNavDirection = null;  // Turn direction text
    private volatile String mNavRoadName = null;   // Current road name
    private volatile int mNavSpeedLimit = 0;       // Speed limit (km/h)
    private volatile int mNavRemainingDistM = 0;   // Total remaining distance (m)
    private volatile int mNavRemainingTimeSec = 0; // Total remaining time (sec)
    private volatile boolean mNavHasHome = false;
    private volatile boolean mNavHasOffice = false;
    private volatile int mNavDistanceUnit = 1;     // 1=metric, 2=imperial

    /**
     * Register IGeneralNotificationListener with AdapterService using a raw Binder
     * that implements onTransact() with the exact AIDL transaction codes.
     * This replicates how the original SAIC launcher's NaviViewModel$1 Stub works.
     * Transaction codes from decompiled IGeneralNotificationListener$Stub.
     */
    public void registerNavListener() {
        if (mAdapterGeneral == null) return;

        // AIDL transaction codes (from IGeneralNotificationListener$Stub.smali)
        final int TX_GUIDE_STATUS = 1;
        final int TX_GUIDE_INFOS = 2;
        final int TX_ROAD_INFO = 3;
        final int TX_SPEED_LIMIT = 4;
        final int TX_MAP_REQUEST_PROJECTION = 5;
        final int TX_DISPLAY_MODE = 6;
        final int TX_CUR_TIMEZONE = 7;
        final int TX_HOME_ADDRESS = 8;
        final int TX_OFFICE_ADDRESS = 9;
        final int TX_MD5_CHECK = 10;
        final int TX_NETWORK_AVAILABLE = 11;
        final int TX_REMAINING_TIMES = 12;
        final int TX_REMAINING_DISTANCE = 13;
        final int TX_REMAINING_RED_LIGHT = 14;
        final int TX_NAV_APP_ACTIVATED = 15;
        final int TX_DISTANCE_UNIT = 16;

        final String DESCRIPTOR = "com.saicmotor.adapterservice.IGeneralNotificationListener";

        // Create a raw Binder that handles the AIDL callbacks via onTransact
        android.os.Binder navBinder = new android.os.Binder() {
            @Override
            protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
                    throws android.os.RemoteException {
                data.enforceInterface(DESCRIPTOR);
                switch (code) {
                    case TX_GUIDE_STATUS: {
                        int status = data.readInt();
                        mNavIsNavigating = status != 0;
                        FileLogger.getInstance(mContext).d(TAG, "Nav CB guideStatus: " + status);
                        break;
                    }
                    case TX_GUIDE_INFOS: {
                        int icon = data.readInt();
                        int distance = data.readInt();
                        String direction = data.readString();
                        mNavIconIndex = icon;
                        mNavDistanceM = distance;
                        mNavDirection = direction;
                        mNavIsNavigating = true;
                        FileLogger.getInstance(mContext).d(TAG, "Nav CB guideInfos: icon=" + icon
                            + " dist=" + distance + " dir=" + direction);
                        break;
                    }
                    case TX_ROAD_INFO: {
                        String road = data.readString();
                        mNavRoadName = road;
                        FileLogger.getInstance(mContext).d(TAG, "Nav CB roadInfo: " + road);
                        break;
                    }
                    case TX_SPEED_LIMIT: {
                        int limit = data.readInt();
                        mNavSpeedLimit = limit;
                        FileLogger.getInstance(mContext).d(TAG, "Nav CB speedLimit: " + limit);
                        break;
                    }
                    case TX_HOME_ADDRESS: {
                        mNavHasHome = data.readInt() != 0;
                        break;
                    }
                    case TX_OFFICE_ADDRESS: {
                        mNavHasOffice = data.readInt() != 0;
                        break;
                    }
                    case TX_REMAINING_DISTANCE: {
                        mNavRemainingDistM = data.readInt();
                        break;
                    }
                    case TX_REMAINING_TIMES: {
                        mNavRemainingTimeSec = data.readInt();
                        break;
                    }
                    case TX_DISTANCE_UNIT: {
                        mNavDistanceUnit = data.readInt();
                        break;
                    }
                    // Other callbacks — log but don't process
                    default:
                        FileLogger.getInstance(mContext).d(TAG, "Nav CB code=" + code);
                        return super.onTransact(code, data, reply, flags);
                }
                if (reply != null) reply.writeNoException();
                return true;
            }
        };
        // Attach the AIDL interface descriptor so the service recognizes this as a valid listener
        navBinder.attachInterface(null, DESCRIPTOR);

        // Register with IGeneralService via raw Binder.transact()
        // We can't use Method.invoke() because our navBinder doesn't implement IGeneralNotificationListener.
        // Instead, call registerNotificationListener at the binder transport level directly.
        try {
            // Get the IBinder of mAdapterGeneral (the IGeneralService proxy)
            IBinder serviceBinder = null;
            try {
                Method asBinder = mAdapterGeneral.getClass().getMethod("asBinder");
                serviceBinder = (IBinder) asBinder.invoke(mAdapterGeneral);
            } catch (Exception e) {
                FileLogger.getInstance(mContext).d(TAG, "Nav asBinder: " + e.getMessage());
            }

            if (serviceBinder != null) {
                // Find the transaction code for registerNotificationListener
                // From IGeneralService$Stub, look for TRANSACTION_registerNotificationListener
                int regTxCode = -1;
                // Try to find via the Stub class
                for (Method m : mAdapterGeneral.getClass().getMethods()) {
                    if ("registerNotificationListener".equals(m.getName())) {
                        // Found the method — now find its TX code from the Stub's fields
                        Class<?> stubClass = mAdapterGeneral.getClass();
                        // The proxy's enclosing Stub class
                        if (stubClass.getSimpleName().contains("Proxy")) {
                            stubClass = stubClass.getEnclosingClass();
                        }
                        if (stubClass != null) {
                            try {
                                java.lang.reflect.Field txField = stubClass.getDeclaredField("TRANSACTION_registerNotificationListener");
                                txField.setAccessible(true);
                                regTxCode = txField.getInt(null);
                                FileLogger.getInstance(mContext).d(TAG, "Nav registerNotificationListener TX code: " + regTxCode);
                            } catch (Exception e) {
                                FileLogger.getInstance(mContext).d(TAG, "Nav TX code lookup: " + e.getMessage());
                            }
                        }
                        break;
                    }
                }

                // If we couldn't find the TX code from fields, enumerate all TX codes
                if (regTxCode < 0) {
                    try {
                        Class<?> stubClass = mAdapterGeneral.getClass().getEnclosingClass();
                        if (stubClass == null) stubClass = mAdapterGeneral.getClass();
                        for (java.lang.reflect.Field f : stubClass.getDeclaredFields()) {
                            if (f.getName().startsWith("TRANSACTION_")) {
                                f.setAccessible(true);
                                int code = f.getInt(null);
                                FileLogger.getInstance(mContext).d(TAG, "Nav TX: " + f.getName() + "=" + code);
                                if (f.getName().contains("registerNotification")) {
                                    regTxCode = code;
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLogger.getInstance(mContext).d(TAG, "Nav TX enum: " + e.getMessage());
                    }
                }

                // Hardcoded fallback — confirmed from decompiled IGeneralService$Stub
                if (regTxCode < 0) {
                    regTxCode = 1; // TRANSACTION_registerNotificationListener = 0x1
                    FileLogger.getInstance(mContext).d(TAG, "Nav TX using hardcoded fallback: 1");
                }

                if (regTxCode > 0) {
                    // Build the Parcel to call registerNotificationListener(IBinder listener)
                    // The AIDL method writes: interface token + listener.asBinder()
                    String serviceDescriptor = null;
                    try {
                        serviceDescriptor = serviceBinder.getInterfaceDescriptor();
                    } catch (Exception ignored) {}
                    if (serviceDescriptor == null) serviceDescriptor = "com.saicmotor.adapterservice.IGeneralService";

                    android.os.Parcel data = android.os.Parcel.obtain();
                    android.os.Parcel reply = android.os.Parcel.obtain();
                    try {
                        data.writeInterfaceToken(serviceDescriptor);
                        data.writeStrongBinder(navBinder);
                        boolean ok = serviceBinder.transact(regTxCode, data, reply, 0);
                        reply.readException();
                        FileLogger.getInstance(mContext).i(TAG, "Nav listener registered via transact(TX="
                            + regTxCode + ") ok=" + ok);
                    } catch (Exception e) {
                        FileLogger.getInstance(mContext).d(TAG, "Nav transact register: " + e.getMessage());
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }
                } else {
                    FileLogger.getInstance(mContext).w(TAG, "Nav: registerNotificationListener TX code not found");
                }
            } else {
                FileLogger.getInstance(mContext).w(TAG, "Nav: service binder is null");
            }
        } catch (Exception e) {
            FileLogger.getInstance(mContext).d(TAG, "Nav register: " + e.getMessage());
        }

        // Query initial state
        try {
            String homeStr = callServiceMethod(mAdapterGeneral, "getIsSetHomeAddress");
            if (homeStr != null) mNavHasHome = "true".equalsIgnoreCase(homeStr);
            String officeStr = callServiceMethod(mAdapterGeneral, "getIsSetOfficeAddress");
            if (officeStr != null) mNavHasOffice = "true".equalsIgnoreCase(officeStr);
        } catch (Exception ignored) {}
    }

    /** Poll navigation state from IGeneralService. Call from polling loop. */
    private int mNavPollCount = 0;
    private boolean mNavPrevNavigating = false;
    private boolean mNavMethodsLogged = false;

    public void pollNavState() {
        if (mAdapterGeneral == null) {
            if (mNavPollCount++ % 30 == 0) FileLogger.getInstance(mContext).d(TAG, "pollNav: adapterGeneral=null");
            return;
        }

        // Log all available methods once for debugging
        if (!mNavMethodsLogged) {
            mNavMethodsLogged = true;
            try {
                StringBuilder sb = new StringBuilder("AdapterGeneral nav methods: ");
                for (Method m : mAdapterGeneral.getClass().getMethods()) {
                    String name = m.getName();
                    if (name.contains("Nav") || name.contains("nav") || name.contains("Road")
                        || name.contains("road") || name.contains("Guide") || name.contains("guide")
                        || name.contains("Speed") || name.contains("speed") || name.contains("Remain")
                        || name.contains("remain") || name.contains("Distance") || name.contains("distance")
                        || name.contains("Map") || name.contains("map") || name.contains("Time")
                        || name.contains("Lane") || name.contains("Red")) {
                        sb.append(m.getReturnType().getSimpleName()).append(" ").append(name).append("() ");
                    }
                }
                FileLogger.getInstance(mContext).i(TAG, sb.toString());
            } catch (Exception ignored) {}
        }

        try {
            String navigating = callServiceMethod(mAdapterGeneral, "isMapNavigating");
            boolean wasNav = mNavIsNavigating;
            mNavIsNavigating = "true".equalsIgnoreCase(navigating) || "1".equals(navigating);

            // Log state transitions
            if (mNavIsNavigating != mNavPrevNavigating) {
                FileLogger.getInstance(mContext).i(TAG, "Nav state: " + mNavPrevNavigating + " → " + mNavIsNavigating
                    + " (raw=" + navigating + ")");
                mNavPrevNavigating = mNavIsNavigating;
            }

            if (mNavIsNavigating) {
                String road = callServiceMethod(mAdapterGeneral, "getRoadName");
                if (road != null && !road.equals("N/A") && !road.isEmpty()) mNavRoadName = road;

                String sl = callServiceMethod(mAdapterGeneral, "getSpeedLimitValue");
                if (sl != null && !sl.equals("N/A") && !sl.equals("0")) {
                    try { mNavSpeedLimit = (int) Float.parseFloat(sl); } catch (Exception ignored) {}
                }

                String dist = callServiceMethod(mAdapterGeneral, "getRemainingDistance");
                if (dist != null && !dist.equals("N/A") && !dist.equals("0")) {
                    try { mNavRemainingDistM = (int) Float.parseFloat(dist); } catch (Exception ignored) {}
                }

                String time = callServiceMethod(mAdapterGeneral, "getRemainingTimes");
                if (time != null && !time.equals("N/A") && !time.equals("0")) {
                    try { mNavRemainingTimeSec = (int) Float.parseFloat(time); } catch (Exception ignored) {}
                }

                String guide = callServiceMethod(mAdapterGeneral, "getGuideStatus");
                if (guide != null && !guide.equals("N/A")) mNavDirection = guide;

                // Log nav data every 5 cycles while navigating
                if (mNavPollCount++ % 5 == 0) {
                    FileLogger.getInstance(mContext).d(TAG, "Nav data: road=" + mNavRoadName
                        + " dist=" + mNavRemainingDistM + "m time=" + mNavRemainingTimeSec + "s"
                        + " speed=" + mNavSpeedLimit + " guide=" + mNavDirection
                        + " icon=" + mNavIconIndex + " distToTurn=" + mNavDistanceM + "m"
                        + " raw=[nav=" + navigating + " road=" + road + " dist=" + dist
                        + " time=" + time + " sl=" + sl + " guide=" + guide + "]");
                }
            } else {
                // Log periodically when not navigating too
                if (mNavPollCount++ % 15 == 0) {
                    FileLogger.getInstance(mContext).d(TAG, "Nav: not navigating (raw=" + navigating + ")");
                }
            }
        } catch (Exception e) {
            FileLogger.getInstance(mContext).d(TAG, "pollNavState error: " + e.getMessage());
        }
    }

    // Navigation getters
    public boolean isNavigating() { return mNavIsNavigating; }
    public int getNavIconIndex() { return mNavIconIndex; }
    public String getNavRoadName() { return mNavRoadName; }
    public int getNavSpeedLimit() { return mNavSpeedLimit; }
    public boolean hasHomeAddress() { return mNavHasHome; }
    public boolean hasOfficeAddress() { return mNavHasOffice; }

    /** Format distance to turn — same logic as SAIC NaviUtils */
    public String getNavDistanceStr() {
        if (mNavDistanceM <= 0) return "";
        if (mNavDistanceUnit == 2) {
            // Imperial: convert meters to feet/miles
            float feet = mNavDistanceM * 3.28084f;
            if (feet > 5280) return String.format("%.1f mi", feet / 5280f);
            return String.format("%d ft", (int) feet);
        }
        // Metric
        if (mNavDistanceM >= 1000) return String.format("%.1f km", mNavDistanceM / 1000f);
        return mNavDistanceM + " m";
    }

    /** Format remaining trip distance + time */
    public String getNavRemainingStr() {
        StringBuilder sb = new StringBuilder();
        if (mNavRemainingDistM > 0) {
            if (mNavRemainingDistM >= 1000) sb.append(String.format("%.1f km", mNavRemainingDistM / 1000f));
            else sb.append(mNavRemainingDistM).append(" m");
        }
        if (mNavRemainingTimeSec > 0) {
            if (sb.length() > 0) sb.append(" \u2014 ");
            if (mNavRemainingTimeSec > 3600) {
                sb.append(mNavRemainingTimeSec / 3600).append("h ")
                  .append((mNavRemainingTimeSec % 3600) / 60).append(" min");
            } else {
                sb.append(mNavRemainingTimeSec / 60).append(" min");
            }
        }
        return sb.toString();
    }

    /** Get turn direction arrow character based on icon index */
    public String getNavTurnArrow() {
        switch (mNavIconIndex) {
            case 0: return "\u2B06";  // ⬆ straight
            case 1: return "\u2197";  // ↗ slight right
            case 2: return "\u27A1";  // ➡ right
            case 3: return "\u2198";  // ↘ sharp right
            case 4: return "\u21B6";  // ↶ U-turn right
            case 5: return "\u2196";  // ↖ slight left
            case 6: return "\u2B05";  // ⬅ left
            case 7: return "\u2199";  // ↙ sharp left
            case 8: return "\u21B7";  // ↷ U-turn left
            default: return "\u25CF"; // ● dot for unknown
        }
    }

    /** Stop active navigation via IGeneralService */
    public void stopNavigation() {
        if (mAdapterGeneral == null) return;
        callServiceVoid(mAdapterGeneral, "stopNav");
        mNavIsNavigating = false;
        FileLogger.getInstance(mContext).d(TAG, "stopNav called");
    }

    /** Add media/radio APK packages to DexClassLoaders */
    private void addMediaClassLoaders() {
        String[] mediaPackages = {
            "com.saicmotor.service.media",
            "com.saicmotor.hmi.music",
            "com.saicmotor.service.radio",
            "com.saicmotor.hmi.radio",
            "com.saicmotor.adapterservice",  // IGeneralNotificationListener for nav callbacks
            "com.allgo.rui",
            "com.allgo.carplay.service",
            "com.allgo.app.androidauto",
        };
        PackageManager pm = mContext.getPackageManager();
        java.util.List<ClassLoader> loaders = buildVsClassLoaders();
        for (String pkg : mediaPackages) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if (ai.sourceDir != null) {
                    // Check if already added
                    boolean alreadyAdded = false;
                    for (ClassLoader cl : loaders) {
                        if (cl.toString().contains(pkg.replace('.', '_'))) { alreadyAdded = true; break; }
                    }
                    if (!alreadyAdded) {
                        java.io.File optDir = new java.io.File(mContext.getCodeCacheDir(), "dexopt_" + pkg.replace('.', '_'));
                        optDir.mkdirs();
                        DexClassLoader dcl = new DexClassLoader(
                            ai.sourceDir, optDir.getAbsolutePath(), null, mContext.getClassLoader());
                        loaders.add(loaders.size() - 1, dcl); // Insert before app classloader
                        Log.d(TAG, "DexClassLoader added for media: " + pkg + " → " + ai.sourceDir);
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(TAG, "Media package not found: " + pkg);
            } catch (Exception e) {
                Log.d(TAG, "DexClassLoader failed for media " + pkg + ": " + e.getMessage());
            }
        }
    }

    /** Log all methods of a service for debugging */
    private void logMediaMethods(Object svc) {
        try {
            Method[] methods = svc.getClass().getMethods();
            StringBuilder sb = new StringBuilder("Methods on " + svc.getClass().getName() + ":\n");
            for (Method m : methods) {
                if (m.getDeclaringClass() == Object.class) continue;
                sb.append("  ").append(m.getReturnType().getSimpleName()).append(" ")
                  .append(m.getName()).append("(");
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(params[i].getSimpleName());
                }
                sb.append(")\n");
            }
            Log.d(TAG, sb.toString());
            FileLogger.getInstance(mContext).d(TAG, sb.toString());
        } catch (Exception e) {
            Log.d(TAG, "logMediaMethods failed: " + e.getMessage());
        }
    }

    // ==================== Media polling ====================

    /** Poll current media info from SAIC services. Call from polling loop. */
    public void pollMediaState() {
        pollRadioState();
        pollMusicState();
        pollNavState();
        // Periodic RemoteUI device check (covers AA device connected before launcher start)
        if (mRemoteUIService != null && mRadioLoggedOnce % 5 == 0) {
            pollRemoteUIDevice(mRemoteUIService);
        }
        detectActiveSource();
    }

    private void pollRadioState() {
        if (mRadioService == null) return;
        try {
            Method m = mRadioService.getClass().getMethod("getCurrentRadioInfo");
            Object bean = m.invoke(mRadioService);
            if (bean == null) return;

            // Full field dump on first read and on type changes
            mRadioLoggedOnce++;
            if (mRadioLoggedOnce == 1) {
                try {
                    java.lang.reflect.Field[] fields = bean.getClass().getDeclaredFields();
                    StringBuilder sb = new StringBuilder("RadioBean ALL fields:\n");
                    for (java.lang.reflect.Field f : fields) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        Object val = f.get(bean);
                        sb.append("  ").append(f.getName()).append(" (").append(f.getType().getSimpleName()).append(") = ");
                        if (val == null) sb.append("null");
                        else if (val instanceof char[]) sb.append("\"").append(new String((char[]) val)).append("\"");
                        else if (val instanceof byte[]) sb.append("byte[").append(((byte[]) val).length).append("]");
                        else if (val instanceof android.graphics.Bitmap) {
                            android.graphics.Bitmap bmp = (android.graphics.Bitmap) val;
                            sb.append("Bitmap ").append(bmp.getWidth()).append("x").append(bmp.getHeight());
                        } else if (val instanceof android.graphics.Bitmap[]) {
                            android.graphics.Bitmap[] arr = (android.graphics.Bitmap[]) val;
                            sb.append("Bitmap[").append(arr.length).append("]");
                            for (int i = 0; i < arr.length; i++) {
                                if (arr[i] != null) sb.append(" #").append(i).append("=").append(arr[i].getWidth()).append("x").append(arr[i].getHeight());
                            }
                        } else if (val instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>) val;
                            sb.append("List[").append(list.size()).append("]");
                            if (!list.isEmpty()) {
                                Object first = list.get(0);
                                if (first instanceof android.graphics.Bitmap) {
                                    android.graphics.Bitmap bmp = (android.graphics.Bitmap) first;
                                    sb.append(" Bitmap ").append(bmp.getWidth()).append("x").append(bmp.getHeight());
                                } else if (first != null) {
                                    sb.append(" ").append(first.getClass().getSimpleName());
                                }
                            }
                        } else {
                            sb.append(val);
                        }
                        sb.append("\n");
                    }
                    FileLogger.getInstance(mContext).i(TAG, sb.toString());
                } catch (Exception e) {
                    FileLogger.getInstance(mContext).d(TAG, "RadioBean dump failed: " + e.getMessage());
                }
            }

            // Read fields — confirmed names from car log dump
            mRadioStationName = readBeanField(bean, "mRadioName");
            int prevType = mRadioType;
            String typeStr = readBeanField(bean, "mRadioType");
            if (typeStr != null) {
                try { mRadioType = (int) Float.parseFloat(typeStr); } catch (Exception ignored) {}
            }
            String freqStr = readBeanField(bean, "mFrequencyKhz");
            if (freqStr != null) {
                try { mRadioFreqKhz = (int) Float.parseFloat(freqStr); } catch (Exception ignored) {}
            }
            // mRadioState: 0=off, 2=playing (confirmed from car). mRadioEnable also relevant.
            String stateStr = readBeanField(bean, "mRadioState");
            String enableStr = readBeanField(bean, "mRadioEnable");
            if (stateStr != null) {
                try {
                    int st = (int) Float.parseFloat(stateStr);
                    // mRadioState: 0=off, 2=playing (confirmed from car). mRadioEnable also relevant.
                    mRadioPlaying = (st >= 1) && "true".equalsIgnoreCase(enableStr);
                } catch (Exception ignored) {}
            }
            // DAB fields
            mRadioDabService = readBeanField(bean, "mServiceName");
            String ensembleName = readBeanField(bean, "mEnsembleName");
            mRadioDabSlideshow = getBeanBitmapField(bean, "mDabSlideShow");

            // Log on type change (steering wheel band switch or tune result)
            if (mRadioType != prevType) {
                FileLogger.getInstance(mContext).i(TAG, "Radio type changed: " + prevType + " → " + mRadioType
                    + " name=" + mRadioStationName + " dabSvc=" + mRadioDabService
                    + " ensemble=" + ensembleName + " freq=" + mRadioFreqKhz
                    + " slideshow=" + (mRadioDabSlideshow != null ? mRadioDabSlideshow.getWidth() + "x" + mRadioDabSlideshow.getHeight() : "null"));
            }

            // Periodic log (every 10 cycles)
            if (mRadioLoggedOnce % 10 == 0) {
                FileLogger.getInstance(mContext).d(TAG, "Radio poll: type=" + mRadioType
                    + " freq=" + mRadioFreqKhz + " name=" + mRadioStationName
                    + " dabSvc=" + mRadioDabService + " playing=" + mRadioPlaying
                    + " state=" + readBeanField(bean, "mRadioState")
                    + " enable=" + readBeanField(bean, "mRadioEnable")
                    + " slideshow=" + (mRadioDabSlideshow != null) + " src=" + mActiveSource);
            }
        } catch (Exception e) {
            Log.d(TAG, "pollRadioState: " + e.getMessage());
        }

        // Fallback frequency from Android HAL
        if (mRadioFreqKhz <= 0) {
            int halFreq = radioGetFrequency();
            if (halFreq > 0) {
                mRadioFreqKhz = halFreq;
                if (mRadioType <= 0) mRadioType = halFreq > 50000 ? RADIO_TYPE_FM : RADIO_TYPE_AM;
            }
        }
    }

    private int mPrevMediaPlayer = 0;

    private void pollMusicState() {
        // SAIC IPlayStatusBinderInterface — getCurrentPlayer + metadata
        if (mMediaService != null) {
            try {
                Method gcp = mMediaService.getClass().getMethod("getCurrentPlayer");
                Object r = gcp.invoke(mMediaService);
                if (r instanceof Number) {
                    int p = ((Number) r).intValue();
                    if (p > 0) {
                        if (p != mPrevMediaPlayer) {
                            FileLogger.getInstance(mContext).i(TAG, "getCurrentPlayer: " + mPrevMediaPlayer
                                + " → " + p + " (0=none 2=USB 3=Online 4=BT 5=CP 6=AA)");
                            mPrevMediaPlayer = p;
                        }
                        mMediaPlayerType = p;
                        mMediaPlaying = true;
                    } else if (mPrevMediaPlayer > 0) {
                        FileLogger.getInstance(mContext).d(TAG, "getCurrentPlayer: stopped (was " + mPrevMediaPlayer + ")");
                        mPrevMediaPlayer = 0;
                        mMediaPlaying = false;
                    }
                }
            } catch (Exception e) {
                // Only log once
                if (mRadioLoggedOnce % 60 == 1) {
                    FileLogger.getInstance(mContext).d(TAG, "getCurrentPlayer failed: " + e.getMessage());
                }
            }
        }

        // Android MediaSession fallback (reliable for BT audio)
        try {
            android.media.session.MediaSessionManager msm =
                (android.media.session.MediaSessionManager) mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
            java.util.List<android.media.session.MediaController> controllers = msm.getActiveSessions(null);
            if (controllers != null && !controllers.isEmpty()) {
                android.media.session.MediaController mc = controllers.get(0);
                android.media.session.PlaybackState state = mc.getPlaybackState();
                boolean playing = state != null && state.getState() == android.media.session.PlaybackState.STATE_PLAYING;
                String pkg = mc.getPackageName();
                boolean isBt = pkg != null && (pkg.contains("bluetooth") || pkg.contains("bt"));

                if (playing) {
                    if (isBt) { mMediaPlayerType = MEDIA_SOURCE_BT; mMediaPlaying = true; }
                    android.media.MediaMetadata meta = mc.getMetadata();
                    if (meta != null && (mMediaTitle == null || mMediaTitle.isEmpty())) {
                        String t = meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
                        String a = meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
                        if (t != null && !t.isEmpty()) mMediaTitle = t;
                        if (a != null && !a.isEmpty()) mMediaArtist = a;
                        if (mMediaCoverArt == null) {
                            mMediaCoverArt = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
                            if (mMediaCoverArt == null)
                                mMediaCoverArt = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
                        }
                    }
                    if (state != null && mMediaPosition <= 0) mMediaPosition = state.getPosition();
                }
            }
        } catch (Exception ignored) {}
    }

    private int mDetectLogCount = 0;

    private void detectActiveSource() {
        long lockRemain = mSourceLockUntil - System.currentTimeMillis();
        if (lockRemain > 0) {
            if (mDetectLogCount++ % 10 == 0) {
                FileLogger.getInstance(mContext).d(TAG, "detectSource: LOCKED for " + lockRemain + "ms, src=" + mActiveSource);
            }
            return;
        }

        int prevSource = mActiveSource;

        // Music playing takes priority (BT/USB/Online/CP/AA)
        if (mMediaPlaying && mMediaPlayerType > 0) {
            int src = mActiveSource;
            switch (mMediaPlayerType) {
                case MEDIA_SOURCE_USB: src = SOURCE_USB; break;
                case MEDIA_SOURCE_ONLINE: src = SOURCE_ONLINE; break;
                case MEDIA_SOURCE_BT: src = SOURCE_BT; break;
                case MEDIA_SOURCE_CP: src = SOURCE_CARPLAY; break;
                case MEDIA_SOURCE_AA: src = SOURCE_AA; break;
            }
            if (src != mActiveSource) {
                FileLogger.getInstance(mContext).i(TAG, "Source AUTO (media): " + mActiveSource + " → " + src
                    + " playerType=" + mMediaPlayerType + " mediaPlaying=" + mMediaPlaying);
                mActiveSource = src;
            }
            return;
        }

        // Radio band from RadioBean — only when radio is actually playing
        if (mRadioPlaying && mRadioType > 0) {
            int src;
            switch (mRadioType) {
                case RADIO_TYPE_AM: src = SOURCE_AM; break;
                case RADIO_TYPE_FM: src = SOURCE_FM; break;
                case RADIO_TYPE_DAB:
                case RADIO_TYPE_DAB_ACTUAL:
                default: src = SOURCE_DAB; break;
            }
            if (src != mActiveSource) {
                FileLogger.getInstance(mContext).i(TAG, "Source AUTO (radio): " + mActiveSource + " → " + src
                    + " radioType=" + mRadioType + " radioPlaying=" + mRadioPlaying);
            }
            mActiveSource = src;
        }

        // Periodic full state dump every 10 cycles
        if (mDetectLogCount++ % 10 == 0) {
            FileLogger.getInstance(mContext).d(TAG, "detectSource: src=" + mActiveSource
                + " radioType=" + mRadioType + " radioPlaying=" + mRadioPlaying
                + " mediaPlayer=" + mMediaPlayerType + " mediaPlaying=" + mMediaPlaying
                + " lockExpired=" + (lockRemain <= 0));
        }
    }

    // ==================== Media transport controls ====================

    public void switchSource(int source) {
        FileLogger.getInstance(mContext).i(TAG, "switchSource: " + source);
        mManualPaused = false;
        mActiveSource = source;
        mSourceLockUntil = System.currentTimeMillis() + 5000;
        switch (source) {
            case SOURCE_FM: switchToRadio(RADIO_TYPE_FM); break;
            case SOURCE_AM: switchToRadio(RADIO_TYPE_AM); break;
            case SOURCE_DAB: switchToRadio(RADIO_TYPE_DAB); break;
            case SOURCE_USB: switchToMusic(MEDIA_SOURCE_USB); break;
            case SOURCE_BT: switchToMusic(MEDIA_SOURCE_BT); break;
            case SOURCE_ONLINE: switchToMusic(MEDIA_SOURCE_ONLINE); break;
        }
    }

    /**
     * Switch radio band using IRadioAppService.tune(int radioType, int frequencyKhz).
     * Pattern from decompiled RadioOptionManager: srcPlayRadio() → getLastModeRadio/getRadioList → tune()
     */
    private void switchToRadio(int radioType) {
        FileLogger.getInstance(mContext).d(TAG, "switchToRadio: " + radioType + " cur=" + mRadioType);
        if (mRadioService == null) {
            launchActivity("com.saicmotor.hmi.radio", "com.saicmotor.hmi.radio.app.RadioHomeActivity");
            return;
        }

        // Same band: just resume
        if (mRadioType == radioType) {
            callServiceMethod(mRadioService, "srcPlayRadio");
            return;
        }

        // 1. Ensure radio hardware is active (like RadioOptionManager.play does)
        try {
            mRadioService.getClass().getMethod("srcPlayRadio").invoke(mRadioService);
        } catch (Exception ignored) {}

        // 2. For DAB: use tuneDab() with station from getRadioList
        // Note: Marvel R uses radioType=4 for DAB in RadioBean, but the API may accept 3 or 4
        if (radioType == RADIO_TYPE_DAB) {
            boolean dabOk = false;
            // Try both type 4 (actual car value) and type 3 (standard DAB)
            for (int dabType : new int[]{RADIO_TYPE_DAB_ACTUAL, RADIO_TYPE_DAB}) {
                if (dabOk) break;
                try {
                    Method gl = mRadioService.getClass().getMethod("getRadioList", int.class);
                    Object list = gl.invoke(mRadioService, dabType);
                    int listSize = (list instanceof java.util.List) ? ((java.util.List) list).size() : 0;
                    FileLogger.getInstance(mContext).d(TAG, "DAB getRadioList(" + dabType + ") size=" + listSize);
                    if (listSize > 0) {
                        Object st = ((java.util.List) list).get(0);
                        int eid = readBeanInt(st, "mEnsembleId");
                        long sid = readBeanLong(st, "mServiceId");
                        int fidx = readBeanInt(st, "mFrequencyIndex");
                        int ssid = readBeanInt(st, "mSecondaryServiceId");
                        int freq = readBeanInt(st, "mFrequencyKhz");
                        String sName = readBeanField(st, "mServiceName");
                        FileLogger.getInstance(mContext).d(TAG, "DAB station: eid=" + eid + " sid=" + sid
                            + " fidx=" + fidx + " ssid=" + ssid + " freq=" + freq + " name=" + sName);
                        if (eid > 0 || sid > 0) {
                            Method td = mRadioService.getClass().getMethod("tuneDab", int.class, long.class, int.class, int.class);
                            td.invoke(mRadioService, eid, sid, fidx, ssid);
                            FileLogger.getInstance(mContext).d(TAG, "tuneDab(" + eid + "," + sid + "," + fidx + "," + ssid + ") OK");
                            dabOk = true;
                        }
                    }
                } catch (Exception e) {
                    FileLogger.getInstance(mContext).d(TAG, "DAB list/tune(" + dabType + "): " + e.getMessage());
                }
            }
            // Fallback: try tune(4, freq) then tune(3, freq)
            if (!dabOk) {
                for (int dabType : new int[]{RADIO_TYPE_DAB_ACTUAL, RADIO_TYPE_DAB}) {
                    try {
                        Method tune = mRadioService.getClass().getMethod("tune", int.class, int.class);
                        tune.invoke(mRadioService, dabType, 174928);
                        FileLogger.getInstance(mContext).d(TAG, "DAB tune(" + dabType + ",174928) OK");
                        dabOk = true;
                        break;
                    } catch (Exception e) {
                        FileLogger.getInstance(mContext).d(TAG, "DAB tune(" + dabType + "): " + e.getMessage());
                    }
                }
            }
            // Last resort: next(4) then next(3)
            if (!dabOk) {
                for (int dabType : new int[]{RADIO_TYPE_DAB_ACTUAL, RADIO_TYPE_DAB}) {
                    try {
                        Method next = mRadioService.getClass().getMethod("next", int.class);
                        next.invoke(mRadioService, dabType);
                        FileLogger.getInstance(mContext).d(TAG, "DAB next(" + dabType + ") OK");
                        break;
                    } catch (Exception ignored) {}
                }
            }
            return;
        }

        // 3. For FM/AM: use tune(int radioType, int frequencyKhz)
        int freq = 0;
        // Try getRadioList for a known frequency
        try {
            Method gl = mRadioService.getClass().getMethod("getRadioList", int.class);
            Object list = gl.invoke(mRadioService, radioType);
            if (list instanceof java.util.List && !((java.util.List) list).isEmpty()) {
                freq = readBeanInt(((java.util.List) list).get(0), "mFrequencyKhz");
            }
        } catch (Exception ignored) {}
        // Default frequencies
        if (freq <= 0) freq = radioType == RADIO_TYPE_FM ? 87500 : radioType == RADIO_TYPE_AM ? 531 : 174928;

        try {
            Method tune = mRadioService.getClass().getMethod("tune", int.class, int.class);
            tune.invoke(mRadioService, radioType, freq);
            FileLogger.getInstance(mContext).d(TAG, "tune(" + radioType + "," + freq + ") OK");
            return;
        } catch (Exception e) {
            FileLogger.getInstance(mContext).d(TAG, "tune: " + e.getMessage());
        }

        // 4. Fallback: next(int) also switches band
        try {
            Method next = mRadioService.getClass().getMethod("next", int.class);
            next.invoke(mRadioService, radioType);
            FileLogger.getInstance(mContext).d(TAG, "next(" + radioType + ") OK");
        } catch (Exception e) {
            FileLogger.getInstance(mContext).d(TAG, "next: " + e.getMessage());
        }
    }

    private void switchToMusic(int playerType) {
        FileLogger.getInstance(mContext).d(TAG, "switchToMusic: " + playerType + " bound=" + mMediaBound);
        // Try SAIC IPlayStatusBinderInterface
        if (mMediaService != null) {
            try {
                // setCurrentPlayer switches the active source
                Method scp = mMediaService.getClass().getMethod("setCurrentPlayer", int.class);
                scp.invoke(mMediaService, playerType);
                FileLogger.getInstance(mContext).d(TAG, "setCurrentPlayer(" + playerType + ") OK");
                return;
            } catch (Exception e) {
                FileLogger.getInstance(mContext).d(TAG, "setCurrentPlayer: " + e.getMessage());
            }
        }
        // Fallback: BT via media key, others via app launch
        if (playerType == MEDIA_SOURCE_BT) {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY);
        } else {
            launchActivity("com.saicmotor.hmi.music", "com.saicmotor.hmi.music.ui.activity.MusicActivity");
        }
    }

    public void mediaPlay() {
        FileLogger.getInstance(mContext).d(TAG, "mediaPlay: src=" + mActiveSource);
        if (isRadioSource(mActiveSource)) {
            if (mRadioService != null) callServiceMethod(mRadioService, "srcPlayRadio");
        } else if (mMediaService != null) {
            int id = mActiveSource == SOURCE_USB ? 2 : mActiveSource == SOURCE_ONLINE ? 3 : 4;
            try {
                mMediaService.getClass().getMethod("play", int.class).invoke(mMediaService, id);
            } catch (Exception e) { callServiceVoid(mMediaService, "resume"); }
        } else {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY);
        }
    }

    public void mediaPause() {
        FileLogger.getInstance(mContext).d(TAG, "mediaPause: src=" + mActiveSource);
        if (isRadioSource(mActiveSource)) {
            if (mRadioService != null) callServiceVoid(mRadioService, "srcPauseRadio");
        } else if (mMediaService != null) {
            callServiceVoid(mMediaService, "pause");
        } else {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE);
        }
    }

    public void mediaNext() {
        if (isRadioSource(mActiveSource)) {
            int type = mRadioType > 0 ? mRadioType : RADIO_TYPE_FM;
            if (mRadioService != null) {
                try { mRadioService.getClass().getMethod("next", int.class).invoke(mRadioService, type); return; }
                catch (Exception ignored) {}
            }
            radioNext(type);
        } else if (mMediaService != null) {
            callServiceVoid(mMediaService, "next");
        } else {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
        }
    }

    public void mediaPrevious() {
        if (isRadioSource(mActiveSource)) {
            int type = mRadioType > 0 ? mRadioType : RADIO_TYPE_FM;
            if (mRadioService != null) {
                try { mRadioService.getClass().getMethod("previous", int.class).invoke(mRadioService, type); return; }
                catch (Exception ignored) {}
            }
            radioPrevious(type);
        } else if (mMediaService != null) {
            callServiceVoid(mMediaService, "prev");
        } else {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
    }

    public void mediaPlayPause() {
        FileLogger.getInstance(mContext).d(TAG, "playPause: paused=" + mManualPaused + " src=" + mActiveSource);
        if (mManualPaused) { mediaPlay(); mManualPaused = false; }
        else { mediaPause(); mManualPaused = true; }
    }

    private boolean isRadioSource(int source) {
        return source == SOURCE_FM || source == SOURCE_AM || source == SOURCE_DAB;
    }

    // ==================== Radio-specific controls (clone of RadioViewModel) ====================

    // Track radio play state manually (mRadioPlaying from bean is unreliable — enable always false)
    private volatile boolean mRadioManualPlaying = true; // Assume playing on start

    /** Radio play/pause — toggles srcPlayRadio / srcPauseRadio on IRadioAppService */
    public void radioPlayPause() {
        if (mRadioService == null) { FileLogger.getInstance(mContext).d(TAG, "radioPlayPause: no service"); return; }
        mRadioManualPlaying = !mRadioManualPlaying;
        if (mRadioManualPlaying) {
            try {
                Method m = mRadioService.getClass().getMethod("srcPlayRadio");
                boolean ok = (boolean) m.invoke(mRadioService);
                FileLogger.getInstance(mContext).d(TAG, "radioPlayPause → srcPlayRadio: " + ok);
            } catch (Exception e) {
                FileLogger.getInstance(mContext).d(TAG, "srcPlayRadio: " + e.getMessage());
            }
        } else {
            callServiceVoid(mRadioService, "srcPauseRadio");
            FileLogger.getInstance(mContext).d(TAG, "radioPlayPause → srcPauseRadio");
        }
    }

    /** Radio next — calls next() on IRadioAppService with current radio type */
    public void radioNext() {
        if (mRadioService == null) { FileLogger.getInstance(mContext).d(TAG, "radioNext: no service"); return; }
        int type = mRadioType > 0 ? mRadioType : RADIO_TYPE_FM;
        try {
            Method m = mRadioService.getClass().getMethod("next", int.class);
            boolean ok = (boolean) m.invoke(mRadioService, type);
            FileLogger.getInstance(mContext).d(TAG, "radioNext(" + type + "): " + ok);
        } catch (Exception e) {
            // Fallback to Android HAL
            radioNext(type);
            FileLogger.getInstance(mContext).d(TAG, "radioNext HAL fallback: " + e.getMessage());
        }
    }

    /** Radio previous — calls previous() on IRadioAppService with current radio type */
    public void radioPrev() {
        if (mRadioService == null) { FileLogger.getInstance(mContext).d(TAG, "radioPrev: no service"); return; }
        int type = mRadioType > 0 ? mRadioType : RADIO_TYPE_FM;
        try {
            Method m = mRadioService.getClass().getMethod("previous", int.class);
            boolean ok = (boolean) m.invoke(mRadioService, type);
            FileLogger.getInstance(mContext).d(TAG, "radioPrev(" + type + "): " + ok);
        } catch (Exception e) {
            radioPrevious(type);
            FileLogger.getInstance(mContext).d(TAG, "radioPrev HAL fallback: " + e.getMessage());
        }
    }

    // ==================== Music-specific controls (clone of MusicViewModel) ====================

    /** Music play/pause — uses MediaSession key event (proper toggle) */
    public void musicPlayPause() {
        // MediaKey PLAY_PAUSE is a proper toggle — the active MediaSession handles it correctly
        // This works for BT, USB, Online, CarPlay, AA — any source with an active MediaSession
        sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        FileLogger.getInstance(mContext).d(TAG, "musicPlayPause → MediaKey PLAY_PAUSE");
    }

    /** Music next — uses MediaPlayControlManager SDK or MediaSession key event */
    public void musicNext() {
        if (mMediaPlayControlMgr != null) {
            try {
                mMediaPlayControlMgr.getClass().getMethod("next").invoke(mMediaPlayControlMgr);
                FileLogger.getInstance(mContext).d(TAG, "musicNext → SDK next()");
                return;
            } catch (Exception e) {
                FileLogger.getInstance(mContext).d(TAG, "musicNext SDK: " + e.getMessage());
            }
        }
        sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
        FileLogger.getInstance(mContext).d(TAG, "musicNext → MediaKey NEXT");
    }

    /** Music previous — uses MediaPlayControlManager SDK or MediaSession key event */
    public void musicPrev() {
        if (mMediaPlayControlMgr != null) {
            try {
                mMediaPlayControlMgr.getClass().getMethod("prev").invoke(mMediaPlayControlMgr);
                FileLogger.getInstance(mContext).d(TAG, "musicPrev → SDK prev()");
                return;
            } catch (Exception e) {
                FileLogger.getInstance(mContext).d(TAG, "musicPrev SDK: " + e.getMessage());
            }
        }
        sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        FileLogger.getInstance(mContext).d(TAG, "musicPrev → MediaKey PREVIOUS");
    }

    public void sendMediaKeyEvent(int keyCode) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode));
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode));
        } catch (Exception e) { Log.d(TAG, "mediaKey: " + e.getMessage()); }
    }

    private void callServiceVoid(Object svc, String name) {
        if (svc == null) return;
        try { svc.getClass().getMethod(name).invoke(svc); }
        catch (Exception e) { Log.d(TAG, name + ": " + e.getMessage()); }
    }

    private void launchActivity(String pkg, String cls) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(pkg, cls));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(i);
        } catch (Exception e) { Log.d(TAG, "launch: " + e.getMessage()); }
    }

    // ==================== Media state getters ====================

    public int getActiveSource() { return mActiveSource; }
    public boolean isMediaBound() { return mMediaBound; }
    public boolean isRadioSaicBound() { return mRadioSaicBound; }
    public boolean isCurrentlyPlaying() { return !mManualPaused; }

    // Independent radio getters (for radio card — always return radio data)
    public String getRadioTitle() {
        if (mRadioStationName != null && !mRadioStationName.isEmpty()) return mRadioStationName;
        if (mRadioDabService != null && !mRadioDabService.isEmpty()) return mRadioDabService;
        return getRadioFrequencyLabel();
    }

    public String getRadioSubtitle() {
        if (mRadioStationName != null && !mRadioStationName.isEmpty()) return getRadioFrequencyLabel();
        return mRadioDabService;
    }

    public String getMediaTitle() {
        if (isRadioSource(mActiveSource)) {
            if (mRadioStationName != null && !mRadioStationName.isEmpty()) return mRadioStationName;
            // DAB: use service name when station name is empty
            if (mRadioDabService != null && !mRadioDabService.isEmpty()) return mRadioDabService;
            return getRadioFrequencyLabel();
        }
        return mMediaTitle;
    }

    public String getMediaSubtitle() {
        if (isRadioSource(mActiveSource)) {
            if (mRadioStationName != null && !mRadioStationName.isEmpty()) return getRadioFrequencyLabel();
            return mRadioDabService;
        }
        return mMediaArtist;
    }

    public android.graphics.Bitmap getMediaCover() {
        if (mActiveSource == SOURCE_DAB && mRadioDabSlideshow != null) return mRadioDabSlideshow;
        if (isRadioSource(mActiveSource)) return null;
        return mMediaCoverArt; // Also works for CP/AA — MediaSession provides cover art
    }

    public String getMediaArtist() { return mMediaArtist; }
    public boolean isMediaPlaying() { return mMediaPlaying; }
    public boolean isRadioManualPlaying() { return mRadioManualPlaying; }
    public long getMediaPositionMs() { return mMediaPosition; }
    public long getMediaDurationMs() { return isRadioSource(mActiveSource) ? 0 : mMediaDuration; }

    private String getRadioFrequencyLabel() {
        if (mRadioFreqKhz <= 0) {
            int f = radioGetFrequency();
            if (f > 0) mRadioFreqKhz = f;
        }
        if (mRadioFreqKhz > 50000) return String.format("%.1f MHz", mRadioFreqKhz / 1000f);
        if (mRadioFreqKhz > 0) return String.format("%d kHz", mRadioFreqKhz);
        return null;
    }

    // ==================== Bean field helpers (media) ====================

    private String readBeanField(Object bean, String fieldName) {
        if (bean == null) return null;
        try {
            java.lang.reflect.Field f = bean.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(bean);
            if (v == null) return null;
            // Handle char[] fields (e.g., mServiceName, mEnsembleName in RadioBean)
            if (v instanceof char[]) return new String((char[]) v);
            if (v instanceof byte[]) return new String((byte[]) v, "UTF-8").trim();
            return String.valueOf(v);
        } catch (Exception e) { return null; }
    }

    private int readBeanInt(Object bean, String fieldName) {
        String s = readBeanField(bean, fieldName);
        if (s == null) return 0;
        try { return (int) Float.parseFloat(s); } catch (Exception e) { return 0; }
    }

    private long readBeanLong(Object bean, String fieldName) {
        String s = readBeanField(bean, fieldName);
        if (s == null) return 0;
        try { return (long) Float.parseFloat(s); } catch (Exception e) { return 0; }
    }

    private android.graphics.Bitmap getBeanBitmapField(Object bean, String fieldName) {
        if (bean == null) return null;
        try {
            java.lang.reflect.Field f = bean.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(bean);
            if (v instanceof android.graphics.Bitmap) return (android.graphics.Bitmap) v;
            // Handle Bitmap arrays (e.g., mDabSlideShow is Bitmap[])
            if (v instanceof android.graphics.Bitmap[]) {
                android.graphics.Bitmap[] arr = (android.graphics.Bitmap[]) v;
                if (arr.length > 0 && arr[0] != null) return arr[0];
            }
            // Handle List<Bitmap>
            if (v instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) v;
                if (!list.isEmpty() && list.get(0) instanceof android.graphics.Bitmap) {
                    return (android.graphics.Bitmap) list.get(0);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public android.media.session.MediaController getActiveMediaSession(Context ctx) {
        try {
            android.media.session.MediaSessionManager msm =
                (android.media.session.MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            java.util.List<android.media.session.MediaController> c = msm.getActiveSessions(null);
            if (c != null && !c.isEmpty()) return c.get(0);
        } catch (Exception ignored) {}
        return null;
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
