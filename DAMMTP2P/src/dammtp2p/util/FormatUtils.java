package dammtp2p.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility functions for formatting large byte quantities (GB, TB), speeds, and timestamps.
 * Designed to handle files exceeding 10 GB using 64-bit {@code long}.
 */
public final class FormatUtils {

    private FormatUtils() {
        // Prevent instantiation
    }

    private static final long KB = 1024L;
    private static final long MB = 1024L * KB;
    private static final long GB = 1024L * MB;
    private static final long TB = 1024L * GB;

    /**
     * Formats a byte count into a human-readable string (B, KB, MB, GB, TB).
     *
     * @param bytes Number of bytes (represented as 64-bit long)
     * @return Formatted string (e.g. "18.7 GB", "512 KB", "1.2 TB")
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 0L) {
            return "0 B";
        }
        if (bytes < KB) {
            return bytes + " B";
        }
        if (bytes < MB) {
            return String.format(Locale.ROOT, "%.1f KB", (double) bytes / KB);
        }
        if (bytes < GB) {
            return String.format(Locale.ROOT, "%.1f MB", (double) bytes / MB);
        }
        if (bytes < TB) {
            return String.format(Locale.ROOT, "%.1f GB", (double) bytes / GB);
        }
        return String.format(Locale.ROOT, "%.2f TB", (double) bytes / TB);
    }

    /**
     * Generates a dynamic Vietnamese date badge for the current day (e.g. "• Hôm nay, 11 Tháng 10, 2026 •").
     */
    public static String formatTodayBanner() {
        SimpleDateFormat sdf = new SimpleDateFormat("'• Hôm nay, 'dd' Tháng 'MM', 'yyyy' •'", Locale.forLanguageTag("vi-VN"));
        return sdf.format(new Date());
    }

    /**
     * Formats a time in milliseconds.
     * Shows HH:mm:ss if the message is from today, or dd/MM HH:mm if from an earlier date.
     */
    public static String formatTime(long epochMillis) {
        if (epochMillis <= 0L) {
            epochMillis = System.currentTimeMillis();
        }
        Date msgDate = new Date(epochMillis);
        Date now = new Date();
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        if (dayFmt.format(msgDate).equals(dayFmt.format(now))) {
            SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            return timeFmt.format(msgDate);
        } else {
            SimpleDateFormat dtFmt = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
            return dtFmt.format(msgDate);
        }
    }

    /**
     * Formats timestamp into full Vietnamese date and time (dd/MM/yyyy HH:mm:ss).
     */
    public static String formatFullDateTime(long epochMillis) {
        if (epochMillis <= 0L) {
            epochMillis = System.currentTimeMillis();
        }
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(epochMillis));
    }

    /**
     * Formats elapsed duration in milliseconds into a readable string (e.g. "1.2s", "02:15").
     */
    public static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        long seconds = millis / 1000;
        if (seconds < 60) {
            return String.format(Locale.ROOT, "%.1fs", (double) millis / 1000.0);
        }
        return formatEta(seconds);
    }

    /**
     * Formats transfer speed in bytes per second into a human-readable string (B/s, KB/s, MB/s, GB/s).
     *
     * @param bytesPerSecond Instantaneous or average speed in bytes per second
     * @return Formatted speed string (e.g. "85.2 MB/s", "1.2 GB/s")
     */
    public static String formatSpeed(double bytesPerSecond) {
        if (Double.isNaN(bytesPerSecond) || Double.isInfinite(bytesPerSecond) || bytesPerSecond <= 0.0) {
            return "0 B/s";
        }
        if (bytesPerSecond < KB) {
            return String.format(Locale.ROOT, "%.0f B/s", bytesPerSecond);
        }
        if (bytesPerSecond < MB) {
            return String.format(Locale.ROOT, "%.1f KB/s", bytesPerSecond / KB);
        }
        if (bytesPerSecond < GB) {
            return String.format(Locale.ROOT, "%.1f MB/s", bytesPerSecond / MB);
        }
        return String.format(Locale.ROOT, "%.2f GB/s", bytesPerSecond / GB);
    }

    /**
     * Formats estimated time of arrival (ETA) in seconds into MM:SS or HH:MM:SS.
     *
     * @param secondsRemaining Estimated seconds remaining (-1 if unknown or calculating)
     * @return Formatted ETA string (e.g. "01:02", "01:23:45", "Đang tính...")
     */
    public static String formatEta(long secondsRemaining) {
        if (secondsRemaining < 0 || secondsRemaining > 359999999L) {
            return "Đang tính...";
        }
        if (secondsRemaining < 60) {
            return String.format(Locale.ROOT, "00:%02d", secondsRemaining);
        }
        if (secondsRemaining < 3600) {
            long minutes = secondsRemaining / 60;
            long seconds = secondsRemaining % 60;
            return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
        }
        long hours = secondsRemaining / 3600;
        long minutes = (secondsRemaining % 3600) / 60;
        long seconds = secondsRemaining % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Formats ETA based on speed and remaining bytes.
     */
    public static String formatEta(double speedBps, long remainingBytes) {
        if (remainingBytes <= 0L) {
            return "00:00";
        }
        if (speedBps <= 0.0) {
            return "Đang tính...";
        }
        long sec = (long) (remainingBytes / speedBps);
        return formatEta(sec);
    }

    /**
     * Calculates transfer percentage safely clamped between 0.0 and 100.0%.
     *
     * @param transferred Bytes transferred so far (64-bit long)
     * @param total       Total file size in bytes (64-bit long)
     * @return Clamped percentage (0.0 to 100.0)
     */
    public static double calculatePercentage(long transferred, long total) {
        if (total <= 0L) {
            return transferred >= total ? 100.0 : 0.0;
        }
        if (transferred <= 0L) {
            return 0.0;
        }
        if (transferred >= total) {
            return 100.0;
        }
        double pct = ((double) transferred / (double) total) * 100.0;
        if (Double.isNaN(pct) || Double.isInfinite(pct)) {
            return 0.0;
        }
        return Math.min(100.0, Math.max(0.0, pct));
    }

    /**
     * Formats progress percentage into a 1-decimal-place string (e.g. "72.4%").
     */
    public static String formatPercentage(long transferred, long total) {
        return String.format(Locale.ROOT, "%.1f%%", calculatePercentage(transferred, total));
    }
}