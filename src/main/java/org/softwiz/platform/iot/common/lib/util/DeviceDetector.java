package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeviceDetector {

    /**
     * User-Agent를 분석하여 디바이스 타입 반환
     */
    public static DeviceInfo detectDevice(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return DeviceInfo.UNKNOWN;
        }

        userAgent = userAgent.toLowerCase();

        // 모바일 앱 체크 (커스텀 User-Agent 사용 권장)
        if (userAgent.contains("wizsso-android")) {
            return DeviceInfo.ANDROID_APP;
        }
        if (userAgent.contains("wizsso-ios")) {
            return DeviceInfo.IOS_APP;
        }

        // Android 체크
        if (userAgent.contains("android")) {
            return userAgent.contains("mobile") ? DeviceInfo.ANDROID_MOBILE : DeviceInfo.ANDROID_TABLET;
        }

        // iOS 체크
        if (userAgent.contains("iphone")) {
            return DeviceInfo.IOS_MOBILE;
        }
        if (userAgent.contains("ipad")) {
            return DeviceInfo.IOS_TABLET;
        }
        if (userAgent.contains("ipod")) {
            return DeviceInfo.IOS_MOBILE;
        }

        // Windows 체크
        if (userAgent.contains("windows")) {
            return DeviceInfo.WINDOWS_PC;
        }

        // Mac 체크
        if (userAgent.contains("mac os x")) {
            return DeviceInfo.MAC_PC;
        }

        // Linux 체크
        if (userAgent.contains("linux")) {
            return DeviceInfo.LINUX_PC;
        }

        // 기타 모바일 브라우저
        if (userAgent.contains("mobile") || userAgent.contains("webos") || 
            userAgent.contains("blackberry") || userAgent.contains("opera mini")) {
            return DeviceInfo.MOBILE_WEB;
        }

        return DeviceInfo.WEB_BROWSER;
    }

    /**
     * 디바이스 정보 상세 분석
     */
    public static DeviceDetails getDeviceDetails(String userAgent) {
        DeviceInfo deviceType = detectDevice(userAgent);
        
        return DeviceDetails.builder()
                .deviceType(deviceType)
                .osName(extractOS(userAgent))
                .osVersion(extractOSVersion(userAgent))
                .browserName(extractBrowser(userAgent))
                .browserVersion(extractBrowserVersion(userAgent))
                .isMobile(deviceType.isMobile())
                .isApp(deviceType.isApp())
                .rawUserAgent(userAgent)
                .build();
    }

    private static String extractOS(String userAgent) {
        if (userAgent == null) return "Unknown";
        
        userAgent = userAgent.toLowerCase();
        
        if (userAgent.contains("android")) return "Android";
        if (userAgent.contains("iphone") || userAgent.contains("ipad")) return "iOS";
        if (userAgent.contains("windows nt 10.0")) return "Windows 10";
        if (userAgent.contains("windows nt 6.3")) return "Windows 8.1";
        if (userAgent.contains("windows nt 6.2")) return "Windows 8";
        if (userAgent.contains("windows nt 6.1")) return "Windows 7";
        if (userAgent.contains("mac os x")) return "macOS";
        if (userAgent.contains("linux")) return "Linux";
        
        return "Unknown";
    }

    private static String extractOSVersion(String userAgent) {
        if (userAgent == null) return "Unknown";
        
        // Android 버전
        if (userAgent.contains("android")) {
            String[] parts = userAgent.split("android ");
            if (parts.length > 1) {
                String version = parts[1].split(";")[0].split(" ")[0];
                return version;
            }
        }
        
        // iOS 버전
        if (userAgent.contains("os ") && (userAgent.contains("iphone") || userAgent.contains("ipad"))) {
            String[] parts = userAgent.split("os ");
            if (parts.length > 1) {
                String version = parts[1].split(" ")[0].replace("_", ".");
                return version;
            }
        }
        
        return "Unknown";
    }

    private static String extractBrowser(String userAgent) {
        if (userAgent == null) return "Unknown";
        
        userAgent = userAgent.toLowerCase();
        
        if (userAgent.contains("edg/")) return "Edge";
        if (userAgent.contains("chrome/") && !userAgent.contains("edg")) return "Chrome";
        if (userAgent.contains("safari/") && !userAgent.contains("chrome")) return "Safari";
        if (userAgent.contains("firefox/")) return "Firefox";
        if (userAgent.contains("opera") || userAgent.contains("opr/")) return "Opera";
        
        return "Unknown";
    }

    private static String extractBrowserVersion(String userAgent) {
        if (userAgent == null) return "Unknown";
        
        if (userAgent.contains("chrome/")) {
            String[] parts = userAgent.split("chrome/");
            if (parts.length > 1) {
                return parts[1].split(" ")[0];
            }
        }
        
        return "Unknown";
    }

    // Enum: 디바이스 타입
    public enum DeviceInfo {
        ANDROID_APP("Android App", true, true),
        IOS_APP("iOS App", true, true),
        ANDROID_MOBILE("Android Mobile Web", true, false),
        ANDROID_TABLET("Android Tablet Web", true, false),
        IOS_MOBILE("iOS Mobile Web", true, false),
        IOS_TABLET("iOS Tablet Web", true, false),
        WINDOWS_PC("Windows PC", false, false),
        MAC_PC("Mac PC", false, false),
        LINUX_PC("Linux PC", false, false),
        MOBILE_WEB("Mobile Web", true, false),
        WEB_BROWSER("Web Browser", false, false),
        UNKNOWN("Unknown", false, false);

        private final String displayName;
        private final boolean mobile;
        private final boolean app;

        DeviceInfo(String displayName, boolean mobile, boolean app) {
            this.displayName = displayName;
            this.mobile = mobile;
            this.app = app;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isMobile() {
            return mobile;
        }

        public boolean isApp() {
            return app;
        }
    }

    // 상세 디바이스 정보
    @lombok.Builder
    @lombok.Getter
    public static class DeviceDetails {
        private DeviceInfo deviceType;
        private String osName;
        private String osVersion;
        private String browserName;
        private String browserVersion;
        private boolean isMobile;
        private boolean isApp;
        private String rawUserAgent;

        @Override
        public String toString() {
            return String.format("%s | %s %s | %s %s", 
                deviceType.getDisplayName(), 
                osName, osVersion,
                browserName, browserVersion);
        }
    }
}