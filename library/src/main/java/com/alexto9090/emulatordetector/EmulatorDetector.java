package com.alexto9090.emulatordetector;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static android.content.Context.ACTIVITY_SERVICE;

/**
 * EmulatorDetector - Detects if the app is running on an Android emulator.
 * 
 * @author Yusuke Arakawa (Original)
 * @author Updated 2024-2025 with modern emulator detection
 * 
 * Supports detection of:
 * - Android Studio Emulator (AVD)
 * - Genymotion
 * - BlueStacks (all versions)
 * - NoxPlayer
 * - LDPlayer (all versions including LDPlayer 9)
 * - MuMu Player / MuMu Player X
 * - MEmu Play
 * - Andy
 * - Phoenix OS
 * - Droid4X
 * - Waydroid
 * - Google Play Games for PC
 * - And many more...
 */
public class EmulatorDetector {

    // ============ QEMU / Generic Emulator Indicators ============
    private static final String[] QEMU_DRIVERS = {"goldfish", "ranchu"};

    // ============ Emulator-Specific Files ============
    private static final String[] GENY_FILES = {
            "/dev/socket/genyd",
            "/dev/socket/baseband_genyd"
    };

    private static final String[] PIPES = {
            "/dev/socket/qemud",
            "/dev/qemu_pipe"
    };

    private static final String[] X86_FILES = {
            "ueventd.android_x86.rc",
            "x86.prop",
            "ueventd.ttVM_x86.rc",
            "init.ttVM_x86.rc",
            "fstab.ttVM_x86",
            "fstab.vbox86",
            "init.vbox86.rc",
            "ueventd.vbox86.rc"
    };

    private static final String[] ANDY_FILES = {
            "fstab.andy",
            "ueventd.andy.rc"
    };

    private static final String[] NOX_FILES = {
            "fstab.nox",
            "init.nox.rc",
            "ueventd.nox.rc",
            "/data/dalvik-cache/nox"
    };

    private static final String[] LDPLAYER_FILES = {
            "fstab.ldinit",
            "init.ldinit.rc",
            "/data/data/com.ldmnq.launcher3",
            "/data/dalvik-cache/ld"
    };

    private static final String[] MUMU_FILES = {
            "fstab.nemu",
            "init.nemu.rc",
            "/data/data/com.mumu.launcher"
    };

    private static final String[] MEMU_FILES = {
            "fstab.memu",
            "init.memu.rc",
            "/data/data/com.microvirt.launcher"
    };

    private static final String[] PHOENIX_FILES = {
            "fstab.phoenix",
            "init.phoenix.rc"
    };

    private static final String[] WAYDROID_FILES = {
            "/dev/.lxc",
            "/system/etc/init/waydroid"
    };

    // ============ Emulator Package Prefixes ============
    private static final String[] EMULATOR_PACKAGE_PREFIXES = {
            // BlueStacks
            "com.bluestacks.",
            "com.bluestacks2.",
            "com.bluestacks4.",
            "com.bluestacks5.",
            // NoxPlayer
            "com.bignox.",
            "com.nox.mopen.app",
            "com.vphone.",
            // LDPlayer
            "com.ldmnq.",
            "com.ludashi.",
            "com.changzhi.",
            // MuMu Player
            "com.mumu.",
            "com.netease.mumu.",
            // MEmu
            "com.microvirt.",
            // iTools / Haima
            "cn.itools.",
            "me.haima.",
            // Various
            "com.kop.",
            "com.kaopu.",
            // Genymotion
            "com.genymotion.",
            // Phoenix OS
            "com.topjohnwu.",
            // Google Play Games for PC
            "com.google.android.games.",
            "com.google.android.gms.games.playgames",
            // Waydroid
            "waydroid."
    };

    // ============ Known Emulator Properties ============
    private static final String[] KNOWN_PRODUCT_NAMES = {
            "sdk", "sdk_x86", "sdk_x86_64", "sdk_gphone_x86",
            "sdk_gphone_x86_64", "sdk_gphone_arm64", "sdk_google_phone_x86",
            "sdk_google_phone_x86_64", "sdk_google_phone_arm64",
            "vbox86p", "emulator", "simulator", "google_sdk",
            // LDPlayer
            "ldplayer", "ldinit",
            // MuMu
            "nemu", "mumu",
            // MEmu
            "memu", "microvirt",
            // Nox
            "nox",
            // Phoenix
            "phoenix"
    };

    private static final String[] KNOWN_DEVICE_NAMES = {
            "generic", "generic_arm64", "generic_x86", "generic_x86_64",
            "vbox86p", "Emulator", "goldfish", "ranchu",
            // LDPlayer
            "ldinit",
            // MuMu
            "nemu",
            // MEmu
            "memu"
    };

    private static final String[] KNOWN_HARDWARE_NAMES = {
            "goldfish", "ranchu", "vbox86", "nox", "ttVM_x86",
            // LDPlayer
            "ldinit",
            // MuMu
            "nemu"
    };

    private static final String[] KNOWN_MANUFACTURER_NAMES = {
            "Genymotion", "unknown", "iToolsAVM"
    };

    // ============ Suspicious System Properties ============
    private static final String[] SUSPICIOUS_PROPS = {
            "init.svc.qemud",
            "init.svc.qemu-props",
            "ro.kernel.android.qemud",
            "ro.kernel.qemu",
            "ro.kernel.qemu.gles",
            "ro.bootimage.build.date.utc",
            "ro.hardware.virtual",
            "ro.product.cpu.abilist",
            "ro.product.first_api_level",
            "ro.build.flavor"
    };

    // ============ Main Detection Method ============
    /**
     * Detects if app is currently running on emulator, or real device.
     *
     * @param context Application context
     * @return true for emulator, false for real devices
     */
    public static boolean isEmulator(Context context) {
        return checkBuildProperties()
                || checkAdvancedFiles()
                || checkPackages(context)
                || checkSensors(context)
                || checkTelephony(context)
                || checkSystemProperties();
    }

    /**
     * Returns a detailed detection result with confidence score and reasons.
     *
     * @param context Application context
     * @return DetectionResult with isEmulator flag, confidence score (0-100), and reasons
     */
    public static DetectionResult getDetailedResult(Context context) {
        List<String> reasons = new ArrayList<>();
        int score = 0;

        // Build properties check (high weight)
        BuildCheckResult buildResult = checkBuildPropertiesDetailed();
        score += buildResult.score;
        reasons.addAll(buildResult.reasons);

        // Advanced file check
        FileCheckResult fileResult = checkAdvancedFilesDetailed();
        score += fileResult.score;
        reasons.addAll(fileResult.reasons);

        // Package check
        PackageCheckResult packageResult = checkPackagesDetailed(context);
        score += packageResult.score;
        reasons.addAll(packageResult.reasons);

        // Sensor check
        SensorCheckResult sensorResult = checkSensorsDetailed(context);
        score += sensorResult.score;
        reasons.addAll(sensorResult.reasons);

        // Telephony check
        TelephonyCheckResult telephonyResult = checkTelephonyDetailed(context);
        score += telephonyResult.score;
        reasons.addAll(telephonyResult.reasons);

        // System properties check
        PropertyCheckResult propResult = checkSystemPropertiesDetailed();
        score += propResult.score;
        reasons.addAll(propResult.reasons);

        // Cap score at 100
        int finalScore = Math.min(score, 100);
        boolean isEmulator = finalScore >= 30; // Threshold for detection

        return new DetectionResult(isEmulator, finalScore, reasons);
    }

    // ============ Build Properties Check ============
    private static boolean checkBuildProperties() {
        return checkBuildPropertiesDetailed().score >= 15;
    }

    private static BuildCheckResult checkBuildPropertiesDetailed() {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        String product = Build.PRODUCT != null ? Build.PRODUCT.toLowerCase(Locale.US) : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toLowerCase(Locale.US) : "";
        String hardware = Build.HARDWARE != null ? Build.HARDWARE.toLowerCase(Locale.US) : "";
        String model = Build.MODEL != null ? Build.MODEL.toLowerCase(Locale.US) : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.US) : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.US) : "";
        String fingerprint = Build.FINGERPRINT != null ? Build.FINGERPRINT.toLowerCase(Locale.US) : "";
        String board = Build.BOARD != null ? Build.BOARD.toLowerCase(Locale.US) : "";
        String bootloader = Build.BOOTLOADER != null ? Build.BOOTLOADER.toLowerCase(Locale.US) : "";
        String host = Build.HOST != null ? Build.HOST.toLowerCase(Locale.US) : "";
        String serial = getSerial().toLowerCase(Locale.US);

        // Product checks
        for (String known : KNOWN_PRODUCT_NAMES) {
            if (product.contains(known.toLowerCase(Locale.US))) {
                score += 20;
                reasons.add("Product contains: " + known);
                break;
            }
        }

        // Device checks
        for (String known : KNOWN_DEVICE_NAMES) {
            if (device.contains(known.toLowerCase(Locale.US))) {
                score += 15;
                reasons.add("Device contains: " + known);
                break;
            }
        }

        // Hardware checks
        for (String known : KNOWN_HARDWARE_NAMES) {
            if (hardware.contains(known.toLowerCase(Locale.US))) {
                score += 20;
                reasons.add("Hardware contains: " + known);
                break;
            }
        }

        // Manufacturer checks
        for (String known : KNOWN_MANUFACTURER_NAMES) {
            if (manufacturer.equalsIgnoreCase(known)) {
                score += 15;
                reasons.add("Manufacturer is: " + known);
                break;
            }
        }

        // Generic fingerprint
        if (fingerprint.startsWith("generic") || fingerprint.contains("test-keys")) {
            score += 15;
            reasons.add("Generic fingerprint detected");
        }

        // Model checks
        if (model.contains("sdk") || model.contains("emulator") || model.contains("android sdk")) {
            score += 15;
            reasons.add("Model indicates SDK/Emulator");
        }

        // Brand checks  
        if (brand.equals("generic") || brand.startsWith("generic_")) {
            score += 10;
            reasons.add("Generic brand detected");
        }

        // Board/Bootloader checks for specific emulators
        if (board.contains("nox") || bootloader.contains("nox") || 
            board.contains("memu") || board.contains("ldplayer")) {
            score += 20;
            reasons.add("Board/Bootloader indicates emulator");
        }

        // Host checks
        if (host.contains("buildstation") || host.contains("build") && host.contains("droid")) {
            score += 10;
            reasons.add("Host indicates build environment");
        }

        // Serial checks
        if (serial.equals("unknown") || serial.contains("nox") || serial.contains("android")) {
            score += 5;
            reasons.add("Suspicious serial number");
        }

        return new BuildCheckResult(score, reasons);
    }

    // ============ Advanced File Check ============
    private static boolean checkAdvancedFiles() {
        return checkAdvancedFilesDetailed().score >= 20;
    }

    private static FileCheckResult checkAdvancedFilesDetailed() {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // Check all emulator file sets
        if (checkFilesExist(GENY_FILES)) {
            score += 30;
            reasons.add("Genymotion files detected");
        }
        if (checkFilesExist(ANDY_FILES)) {
            score += 30;
            reasons.add("Andy files detected");
        }
        if (checkFilesExist(NOX_FILES)) {
            score += 30;
            reasons.add("NoxPlayer files detected");
        }
        if (checkFilesExist(LDPLAYER_FILES)) {
            score += 30;
            reasons.add("LDPlayer files detected");
        }
        if (checkFilesExist(MUMU_FILES)) {
            score += 30;
            reasons.add("MuMu Player files detected");
        }
        if (checkFilesExist(MEMU_FILES)) {
            score += 30;
            reasons.add("MEmu files detected");
        }
        if (checkFilesExist(PHOENIX_FILES)) {
            score += 30;
            reasons.add("Phoenix OS files detected");
        }
        if (checkFilesExist(WAYDROID_FILES)) {
            score += 30;
            reasons.add("Waydroid files detected");
        }
        if (checkFilesExist(PIPES)) {
            score += 25;
            reasons.add("QEMU pipes detected");
        }
        if (checkFilesExist(X86_FILES)) {
            score += 20;
            reasons.add("x86 emulator files detected");
        }

        // Check QEMU drivers
        if (checkQEmuDrivers()) {
            score += 25;
            reasons.add("QEMU drivers detected in /proc");
        }

        return new FileCheckResult(score, reasons);
    }

    private static boolean checkFilesExist(String[] targets) {
        for (String path : targets) {
            File file = new File(path);
            if (file.exists()) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkQEmuDrivers() {
        File[] driverFiles = {new File("/proc/tty/drivers"), new File("/proc/cpuinfo")};
        for (File driverFile : driverFiles) {
            if (driverFile.exists() && driverFile.canRead()) {
                try {
                    byte[] data = new byte[2048];
                    InputStream is = new FileInputStream(driverFile);
                    int bytesRead = is.read(data);
                    is.close();

                    if (bytesRead > 0) {
                        String content = new String(data, 0, bytesRead);
                        for (String driver : QEMU_DRIVERS) {
                            if (content.contains(driver)) {
                                return true;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    // ============ Package Check ============
    private static boolean checkPackages(Context context) {
        return checkPackagesDetailed(context).score >= 20;
    }

    @SuppressLint("QueryPermissionsNeeded")
    private static PackageCheckResult checkPackagesDetailed(Context context) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        try {
            PackageManager packageManager = context.getPackageManager();

            // Check launcher activities
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);

            for (ResolveInfo info : activities) {
                String packageName = info.activityInfo.packageName;
                for (String prefix : EMULATOR_PACKAGE_PREFIXES) {
                    if (packageName.startsWith(prefix)) {
                        score += 30;
                        reasons.add("Launcher app: " + packageName);
                        break;
                    }
                }
            }

            // Check installed packages
            List<ApplicationInfo> packages = packageManager.getInstalledApplications(0);
            Set<String> foundPackages = new HashSet<>();

            for (ApplicationInfo packageInfo : packages) {
                String packageName = packageInfo.packageName;
                for (String prefix : EMULATOR_PACKAGE_PREFIXES) {
                    if (packageName.startsWith(prefix) && !foundPackages.contains(packageName)) {
                        foundPackages.add(packageName);
                        score += 25;
                        reasons.add("Emulator package: " + packageName);
                    }
                }

                // Special check for Genymotion launcher
                if (packageName.equals("com.google.android.launcher.layouts.genymotion")) {
                    score += 30;
                    reasons.add("Genymotion launcher detected");
                }
            }

            // Check running services (deprecated but still works on older devices)
            try {
                ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
                if (manager != null) {
                    @SuppressWarnings("deprecation")
                    List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(50);
                    for (ActivityManager.RunningServiceInfo serviceInfo : services) {
                        String serviceName = serviceInfo.service.getClassName();
                        for (String prefix : EMULATOR_PACKAGE_PREFIXES) {
                            if (serviceName.startsWith(prefix)) {
                                score += 20;
                                reasons.add("Running service: " + serviceName);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            // Package manager might throw exceptions on some custom ROMs
        }

        return new PackageCheckResult(score, reasons);
    }

    // ============ Sensor Check ============
    private static boolean checkSensors(Context context) {
        return checkSensorsDetailed(context).score >= 15;
    }

    private static SensorCheckResult checkSensorsDetailed(Context context) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        try {
            SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager == null) {
                score += 10;
                reasons.add("SensorManager unavailable");
                return new SensorCheckResult(score, reasons);
            }

            List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

            // Real devices typically have multiple sensors
            if (sensors.isEmpty()) {
                score += 25;
                reasons.add("No sensors available");
            } else if (sensors.size() < 3) {
                score += 15;
                reasons.add("Very few sensors: " + sensors.size());
            }

            // Check for specific sensors that emulators often lack
            int[] criticalSensors = {
                    Sensor.TYPE_ACCELEROMETER,
                    Sensor.TYPE_GYROSCOPE,
                    Sensor.TYPE_MAGNETIC_FIELD
            };

            int missingSensors = 0;
            for (int sensorType : criticalSensors) {
                if (sensorManager.getDefaultSensor(sensorType) == null) {
                    missingSensors++;
                }
            }

            if (missingSensors >= 2) {
                score += 15;
                reasons.add("Missing " + missingSensors + " critical sensors");
            }

            // Check sensor names for emulator indicators
            for (Sensor sensor : sensors) {
                String name = sensor.getName().toLowerCase(Locale.US);
                String vendor = sensor.getVendor().toLowerCase(Locale.US);
                if (name.contains("goldfish") || name.contains("ranchu") ||
                    vendor.contains("goldfish") || vendor.contains("ranchu") ||
                    vendor.contains("the android open source project")) {
                    score += 20;
                    reasons.add("Emulator sensor: " + sensor.getName());
                    break;
                }
            }

        } catch (Exception ignored) {
        }

        return new SensorCheckResult(score, reasons);
    }

    // ============ Telephony Check ============
    private static boolean checkTelephony(Context context) {
        return checkTelephonyDetailed(context).score >= 15;
    }

    @SuppressLint({"MissingPermission", "HardwareIds"})
    private static TelephonyCheckResult checkTelephonyDetailed(Context context) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                score += 10;
                reasons.add("TelephonyManager unavailable");
                return new TelephonyCheckResult(score, reasons);
            }

            // Check network operator
            String networkOperator = tm.getNetworkOperatorName();
            if (networkOperator != null) {
                String lower = networkOperator.toLowerCase(Locale.US);
                if (lower.contains("android") || lower.isEmpty()) {
                    score += 10;
                    reasons.add("Suspicious network operator: " + networkOperator);
                }
            }

            // Check SIM state
            int simState = tm.getSimState();
            if (simState == TelephonyManager.SIM_STATE_ABSENT) {
                // Not definitive, but combined with other factors
                score += 5;
                reasons.add("No SIM card");
            }

            // Check phone type
            int phoneType = tm.getPhoneType();
            if (phoneType == TelephonyManager.PHONE_TYPE_NONE) {
                score += 10;
                reasons.add("Phone type is NONE");
            }

            // Try to get line number (may require permission)
            try {
                String line1 = tm.getLine1Number();
                if (line1 != null && (
                        line1.equals("15555215554") || 
                        line1.equals("15555215556") ||
                        line1.startsWith("155552")
                )) {
                    score += 25;
                    reasons.add("Emulator phone number: " + line1);
                }
            } catch (SecurityException ignored) {
            }

        } catch (Exception ignored) {
        }

        return new TelephonyCheckResult(score, reasons);
    }

    // ============ System Properties Check ============
    private static boolean checkSystemProperties() {
        return checkSystemPropertiesDetailed().score >= 15;
    }

    private static PropertyCheckResult checkSystemPropertiesDetailed() {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // Check for QEMU-related properties
        String qemu = getSystemProperty("ro.kernel.qemu");
        if ("1".equals(qemu)) {
            score += 25;
            reasons.add("ro.kernel.qemu is 1");
        }

        // Check bootloader
        String bootloader = getSystemProperty("ro.bootloader");
        if ("unknown".equals(bootloader)) {
            score += 10;
            reasons.add("Bootloader is unknown");
        }

        // Check for virtual hardware
        String hardware = getSystemProperty("ro.hardware");
        if (hardware != null) {
            String lower = hardware.toLowerCase(Locale.US);
            if (lower.contains("goldfish") || lower.contains("ranchu") || 
                lower.contains("vbox") || lower.contains("nox")) {
                score += 20;
                reasons.add("Virtual hardware: " + hardware);
            }
        }

        // Check for emulator flavors
        String flavor = getSystemProperty("ro.build.flavor");
        if (flavor != null && flavor.contains("sdk")) {
            score += 15;
            reasons.add("SDK build flavor: " + flavor);
        }

        // Check secure properties
        String secure = getSystemProperty("ro.secure");
        String debuggable = getSystemProperty("ro.debuggable");
        if ("0".equals(secure) && "1".equals(debuggable)) {
            score += 5;
            reasons.add("Device is debuggable and not secure");
        }

        return new PropertyCheckResult(score, reasons);
    }

    // ============ Helper Methods ============
    @SuppressLint("HardwareIds")
    private static String getSerial() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Requires READ_PHONE_STATE permission on Android 10+
                return Build.getSerial();
            } else {
                return Build.SERIAL != null ? Build.SERIAL : "unknown";
            }
        } catch (SecurityException e) {
            return "unknown";
        }
    }

    private static String getSystemProperty(String name) {
        try {
            @SuppressLint("PrivateApi")
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            return (String) systemProperties.getMethod("get", String.class).invoke(null, name);
        } catch (Exception e) {
            // Try using command line
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"getprop", name});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String value = reader.readLine();
                reader.close();
                return value;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // ============ Result Classes ============
    public static class DetectionResult {
        public final boolean isEmulator;
        public final int confidenceScore;
        public final List<String> reasons;

        DetectionResult(boolean isEmulator, int confidenceScore, List<String> reasons) {
            this.isEmulator = isEmulator;
            this.confidenceScore = confidenceScore;
            this.reasons = reasons;
        }

        @Override
        public String toString() {
            return "DetectionResult{" +
                    "isEmulator=" + isEmulator +
                    ", confidenceScore=" + confidenceScore +
                    ", reasons=" + reasons +
                    '}';
        }
    }

    private static class BuildCheckResult {
        final int score;
        final List<String> reasons;

        BuildCheckResult(int score, List<String> reasons) {
            this.score = score;
            this.reasons = reasons;
        }
    }

    private static class FileCheckResult {
        final int score;
        final List<String> reasons;

        FileCheckResult(int score, List<String> reasons) {
            this.score = score;
            this.reasons = reasons;
        }
    }

    private static class PackageCheckResult {
        final int score;
        final List<String> reasons;

        PackageCheckResult(int score, List<String> reasons) {
            this.score = score;
            this.reasons = reasons;
        }
    }

    private static class SensorCheckResult {
        final int score;
        final List<String> reasons;

        SensorCheckResult(int score, List<String> reasons) {
            this.score = score;
            this.reasons = reasons;
        }
    }

    private static class TelephonyCheckResult {
        final int score;
        final List<String> reasons;

        TelephonyCheckResult(int score, List<String> reasons) {
            this.score = score;
            this.reasons = reasons;
        }
    }

    private static class PropertyCheckResult {
        final int score;
        final List<String> reasons;

        PropertyCheckResult(int score, List<String> reasons) {
            this.score = score;
            this.reasons = reasons;
        }
    }
}
