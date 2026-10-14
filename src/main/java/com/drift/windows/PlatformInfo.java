package com.drift.windows;

/** Detects the host OS so unsupported features can be reported explicitly instead of failing obscurely. */
public final class PlatformInfo {
    private PlatformInfo() {}

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    public static String osDescription() {
        return System.getProperty("os.name", "?") + " " + System.getProperty("os.version", "?")
                + " (" + System.getProperty("os.arch", "?") + ")";
    }

    /** Windows 11 reports itself as "Windows 10" to older JVMs; build 22000+ identifies Windows 11. */
    public static String windowsEdition() {
        if (!isWindows()) return "not Windows";
        try {
            String[] v = System.getProperty("os.version", "0.0").split("\\.");
            int major = Integer.parseInt(v[0]);
            long build = com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(
                    com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE,
                    "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "CurrentBuildNumber").isBlank() ? 0
                    : Long.parseLong(com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(
                    com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE,
                    "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "CurrentBuildNumber"));
            if (major >= 10) return build >= 22000 ? "Windows 11 (build " + build + ")" : "Windows 10 (build " + build + ")";
            return "Unsupported Windows version " + System.getProperty("os.version");
        } catch (RuntimeException | LinkageError e) {
            return "Windows (version could not be determined)";
        }
    }
}
