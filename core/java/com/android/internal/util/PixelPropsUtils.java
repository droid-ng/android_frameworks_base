/*
 * Copyright (C) 2020 The Pixel Experience Project
 *               2021-2023 crDroid Android Project
 *               2023 droid-ng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.app.ActivityThread;
import android.app.Application;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PixelPropsUtils {

    /* *** UTILITIES *** */

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final String DEVICE = "ro.product.device";
    private static final boolean DEBUG = false;
    private static volatile boolean sIsGms = false;
    private static volatile boolean sIsFinsky = false;

    // Codenames for currently supported Pixels by Google
    private static final Set<String> pixelCodenames = Set.of(
            "cheetah",
            "panther",
            "bluejay",
            "oriole",
            "raven",
            "barbet",
            "redfin",
            "bramble",
            "sunfish",
            "coral",
            "flame"
    );

    private static boolean spoofGms() {
        return SystemProperties.getInt("ro.pixelprops_gms", 0) == 1;
    }

    private static Boolean spoofGooglePhotos(String packageName) {
        return packageName != null && packageName.equals("com.google.android.apps.photos") ?
                SystemProperties.getBoolean("persist.sys.pixelprops.gphotos", false) : null;
    }

    private static Boolean spoofNetflix(String packageName) {
        return packageName != null && packageName.equals("com.netflix.mediaclient") ?
                SystemProperties.getBoolean("persist.sys.pixelprops.netflix", false) : null;
    }

    private static void setField(Class<?> clz, String key, Object value) {
        try {
            if (DEBUG) Log.d(TAG, "Defining prop " + key + " to " + value.toString());
            Field field = clz.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setPropValue(String key, Object value) {
        setField(Build.class, key, value);
    }

    private static void setVersionField(String key, Integer value) {
        setField(Build.VERSION.class, key, value);
    }

    private static boolean setAllProps(Set<String> packages, Map<String, String> props, String packageName) {
        if (packages != null && !packages.contains(packageName))
            return false;
        List<String> keep = propsToKeep.getOrDefault(packageName, new ArrayList<>());
        if (DEBUG) Log.d(TAG, "Defining props for: " + packageName);
        for (Map.Entry<String, String> prop : props.entrySet()) {
            String key = prop.getKey();
            if (keep.contains(key)) {
                if (DEBUG) Log.d(TAG, "Not defining " + key + " prop for: " + packageName);
                continue;
            }
            String value = prop.getValue();
            setPropValue(key, value);
        }
        return true;
    }

    /* *** PROP SPOOFING *** */

    private static final Map<String, String> propsToChangePixel5 = new HashMap<>();
    private static final Map<String, String> propsToChangePixel7Pro = new HashMap<>();
    private static final Map<String, String> propsToChangePixelXL = new HashMap<>();
    private static final Map<String, String> propsToChangeROG1 = new HashMap<>();
    private static final Map<String, String> propsToChangeROG3 = new HashMap<>();
    private static final Map<String, String> propsToChangeXP5 = new HashMap<>();
    private static final Map<String, String> propsToChangeOP8P = new HashMap<>();
    private static final Map<String, String> propsToChangeOP9R = new HashMap<>();
    private static final Map<String, String> propsToChange11T = new HashMap<>();
    private static final Map<String, String> propsToChangeF4 = new HashMap<>();
    private static final Map<String, List<String>> propsToKeep = new HashMap<>();

    // Packages to Spoof as Pixel 7 Pro (WILL NOT BE APPLIED ON PIXEL DEVICES)
    private static final Set<String> packagesToChangePixel7Pro = new HashSet<>(Set.of(
            "com.google.android.apps.wallpaper",
            "com.google.android.apps.privacy.wildlife",
            "com.google.android.inputmethod.latin"
    ));

    // Packages to Spoof as Pixel XL (WILL NOT BE APPLIED ON PIXEL DEVICES)
    private static final Set<String> packagesToChangePixelXL = new HashSet<>(Set.of(
            "com.samsung.accessory",
            "com.samsung.accessory.fridaymgr",
            "com.samsung.accessory.berrymgr",
            "com.samsung.accessory.neobeanmgr",
            "com.samsung.android.app.watchmanager",
            "com.samsung.android.geargplugin",
            "com.samsung.android.gearnplugin",
            "com.samsung.android.modenplugin",
            "com.samsung.android.neatplugin",
            "com.samsung.android.waterplugin"
    ));

    // Packages to Spoof as Pixel 5 (WILL NOT BE APPLIED ON PIXEL DEVICES)
    private static final Set<String> extraPackagesToChange = new HashSet<>(Set.of(
            "com.android.chrome",
            "com.breel.wallpapers20",
            "com.nhs.online.nhsonline"
    ));

    // Packages to Keep with original device
    private static final Set<String> packagesToKeep = new HashSet<>(Set.of(
            "com.google.android.gms",
            "com.google.android.GoogleCamera",
            "com.google.android.GoogleCamera.Cameight",
            "com.google.android.GoogleCamera.Go",
            "com.google.android.GoogleCamera.Urnyx",
            "com.google.android.GoogleCameraAsp",
            "com.google.android.GoogleCameraCVM",
            "com.google.android.GoogleCameraEng",
            "com.google.android.GoogleCameraEng2",
            "com.google.android.GoogleCameraGood",
            "com.google.android.MTCL83",
            "com.google.android.UltraCVM",
            "com.google.android.apps.cameralite",
            "com.google.android.dialer",
            "com.google.android.euicc",
            "com.google.ar.core",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.kids",
            "com.google.android.apps.youtube.music",
            "com.google.android.apps.recorder",
            "com.google.android.apps.wearables.maestro.companion"
    ));

    // Packages to Spoof as ROG Phone 1
    private static final Set<String> packagesToChangeROG1 = Set.of(
            "com.madfingergames.legends"
    );

    // Packages to Spoof as ROG Phone 3
    private static final Set<String> packagesToChangeROG3 = Set.of(
            "com.pearlabyss.blackdesertm",
            "com.pearlabyss.blackdesertm.gl"
    );

    // Packages to Spoof as Xperia 5
    private static final Set<String> packagesToChangeXP5 = Set.of(
            "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.tencent.tmgp.kr.codm",
            "com.vng.codmvn"
    );

    // Packages to Spoof as OnePlus 8 Pro
    private static final Set<String> packagesToChangeOP8P = Set.of(
            "com.netease.lztgglobal",
            "com.pubg.imobile",
            "com.pubg.krmobile",
            "com.rekoo.pubgm",
            "com.riotgames.league.wildrift",
            "com.riotgames.league.wildrifttw",
            "com.riotgames.league.wildriftvn",
            "com.tencent.ig",
            "com.tencent.tmgp.pubgmhd",
            "com.vng.pubgmobile"
    );

    // Packages to Spoof as OnePlus 9R
    private static final Set<String> packagesToChangeOP9R = Set.of(
            "com.epicgames.fortnite",
            "com.epicgames.portal"
    );

    // Packages to Spoof as Mi 11T
    private static final Set<String> packagesToChange11T = Set.of(
            "com.ea.gp.apexlegendsmobilefps",
            "com.levelinfinite.hotta.gp",
            "com.mobile.legends",
            "com.supercell.clashofclans",
            "com.tencent.tmgp.sgame",
            "com.vng.mlbbvn"
    );

    // Packages to Spoof as POCO F4
    private static final Set<String> packagesToChangeF4 = Set.of(
            "com.dts.freefiremax",
            "com.dts.freefireth"
    );

    public static void setProps(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        boolean done = false;
        final boolean isPixelDevice = pixelCodenames.contains(SystemProperties.get(DEVICE));
        propsToKeep.put("com.google.android.settings.intelligence", Collections.singletonList("FINGERPRINT"));
        propsToChangePixel7Pro.put("BRAND", "google");
        propsToChangePixel7Pro.put("MANUFACTURER", "Google");
        propsToChangePixel7Pro.put("DEVICE", "cheetah");
        propsToChangePixel7Pro.put("PRODUCT", "cheetah");
        propsToChangePixel7Pro.put("MODEL", "Pixel 7 Pro");
        propsToChangePixel7Pro.put("FINGERPRINT", "google/cheetah/cheetah:13/TQ2A.230305.008.C1/9619669:user/release-keys");
        propsToChangePixel5.put("BRAND", "google");
        propsToChangePixel5.put("MANUFACTURER", "Google");
        propsToChangePixel5.put("DEVICE", "redfin");
        propsToChangePixel5.put("PRODUCT", "redfin");
        propsToChangePixel5.put("MODEL", "Pixel 5");
        propsToChangePixel5.put("FINGERPRINT", "google/redfin/redfin:13/TQ2A.230305.008.C1/9619669:user/release-keys");
        propsToChangePixelXL.put("BRAND", "google");
        propsToChangePixelXL.put("MANUFACTURER", "Google");
        propsToChangePixelXL.put("DEVICE", "marlin");
        propsToChangePixelXL.put("PRODUCT", "marlin");
        propsToChangePixelXL.put("MODEL", "Pixel XL");
        propsToChangePixelXL.put("FINGERPRINT", "google/marlin/marlin:7.1.2/NJH47F/4146041:user/release-keys");
        propsToChangeROG1.put("MODEL", "ASUS_Z01QD");
        propsToChangeROG1.put("MANUFACTURER", "asus");
        propsToChangeROG3.put("MODEL", "ASUS_I003D");
        propsToChangeROG3.put("MANUFACTURER", "asus");
        propsToChangeXP5.put("MODEL", "SO-52A");
        propsToChangeXP5.put("MANUFACTURER", "Sony");
        propsToChangeOP8P.put("MODEL", "IN2020");
        propsToChangeOP8P.put("MANUFACTURER", "OnePlus");
        propsToChangeOP9R.put("MODEL", "LE2101");
        propsToChangeOP9R.put("MANUFACTURER", "OnePlus");
        propsToChange11T.put("MODEL", "21081111RG");
        propsToChange11T.put("MANUFACTURER", "Xiaomi");
        propsToChangeF4.put("MODEL", "22021211RG");
        propsToChangeF4.put("MANUFACTURER", "Xiaomi");

        if (isPixelDevice) {
            // Do not spoof GApps on Pixel devices
            packagesToChangePixel7Pro.clear();
            packagesToChangePixelXL.clear();
            extraPackagesToChange.clear();
        }
        Boolean doSpoofNetflix = spoofNetflix(packageName);
        if (doSpoofNetflix != null && doSpoofNetflix) { // we are in netflix app and spoof is on
            if (DEBUG) Log.d(TAG, "Netflix spoofing enabled by system prop");
            extraPackagesToChange.add(packageName);
        }
        Boolean doSpoofGphotos = spoofGooglePhotos(packageName);
        if (doSpoofGphotos != null) { // we are in photos app
            if (doSpoofGphotos) {
                packagesToChangePixelXL.add(packageName);
            } else if (isPixelDevice) {
                packagesToKeep.add(packageName);
            }
        }

        if (packageName.equals("com.android.vending")) {
            sIsFinsky = true;
        }
        if (packageName.equals("com.google.android.gms")) {
            final String processName = Application.getProcessName();
            if (processName != null && processName.equals("com.google.android.gms.unstable")) {
                sIsGms = true;
                setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.N_MR1);
            }
            if (sIsGms || spoofGms()) {
                packagesToKeep.remove(packageName);
                packagesToChangePixelXL.add(packageName);
            }
        }
        // Set proper indexing fingerprint
        if (packageName.equals("com.google.android.settings.intelligence")) {
            setPropValue("FINGERPRINT", Build.VERSION.INCREMENTAL);
        }

        if ((packageName.startsWith("com.google.") || extraPackagesToChange.contains(packageName))
                    && !packagesToKeep.contains(packageName)) {
            // Google spoofing
            done = setAllProps(packagesToChangePixel7Pro, propsToChangePixel7Pro, packageName)
                || setAllProps(packagesToChangePixelXL, propsToChangePixelXL, packageName)
                || setAllProps(extraPackagesToChange, propsToChangePixel5, packageName)
                || isPixelDevice // only spoof packages to Pixel5 on non-pixel devices
                || setAllProps(null, propsToChangePixel5, packageName)
            ;
        } else {
            // Game spoofing
            done = !SystemProperties.getBoolean("persist.sys.pixelprops.games", false)
                || setAllProps(packagesToChangeROG1, propsToChangeROG1, packageName)
                || setAllProps(packagesToChangeROG3, propsToChangeROG3, packageName)
                || setAllProps(packagesToChangeXP5, propsToChangeXP5, packageName)
                || setAllProps(packagesToChangeOP8P, propsToChangeOP8P, packageName)
                || setAllProps(packagesToChangeOP9R, propsToChangeOP9R, packageName)
                || setAllProps(packagesToChange11T, propsToChange11T, packageName)
                || setAllProps(packagesToChangeF4, propsToChangeF4, packageName)
            ;
        }
        if (DEBUG) Log.d(TAG, "Done setting props for: " + packageName + " status: " + done);
    }

    /* *** KEYSTORE SPOOFING *** */

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet or Play Integrity
        if (isCallerSafetyNet() || sIsFinsky) {
            if (DEBUG) Log.d(TAG, "Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }

    /* *** FEATURE SPOOFING *** */

    private static final Set<String> featuresPixel = Set.of(
            "com.google.android.apps.photos.PIXEL_2019_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2019_MIDYEAR_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2018_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2017_PRELOAD",
            "com.google.android.feature.PIXEL_2022_EXPERIENCE",
            "com.google.android.feature.PIXEL_2022_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2021_EXPERIENCE",
            "com.google.android.feature.PIXEL_2021_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2020_EXPERIENCE",
            "com.google.android.feature.PIXEL_2020_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2019_EXPERIENCE",
            "com.google.android.feature.PIXEL_2019_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2018_EXPERIENCE",
            "com.google.android.feature.PIXEL_2017_EXPERIENCE",
            "com.google.android.feature.PIXEL_EXPERIENCE",
            "com.google.android.feature.GOOGLE_BUILD",
            "com.google.android.feature.GOOGLE_EXPERIENCE"
    );

    private static final Set<String> featuresNexus = Set.of(
            "com.google.android.apps.photos.NEXUS_PRELOAD",
            "com.google.android.apps.photos.nexus_preload"
    );

    public static Boolean getFeature(String name) {
        Boolean gPhotos = spoofGooglePhotos(ActivityThread.currentPackageName());
        if (gPhotos != null && gPhotos) {
            if (featuresPixel.contains(name)) return false;
            if (featuresNexus.contains(name)) return true;
        }
        if (featuresPixel.contains(name)) return true;
        return null;
    }
}
