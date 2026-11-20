package tv.glass.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the slideshow folder list from image file paths, so a folder that
 * only holds subfolders (Wallpapers/Cars, Wallpapers/Nature, ...) can be
 * picked and the slideshow cycles through everything under it.
 *
 * <p>A folder is identified by its absolute directory path with a trailing
 * slash, stored behind {@link #KEY_PREFIX}. Older installs stored a
 * MediaStore bucket id instead, which {@link #isDirectoryKey} tells apart.
 *
 * <p>Android-free so it runs in plain JVM unit tests.
 */
final class SlideshowFolders {

    private SlideshowFolders() { /* no instances */ }

    static final String KEY_PREFIX = "dir:";

    private static final String STORAGE = "/storage/";
    private static final String EMULATED = "/storage/emulated/";

    static boolean isDirectoryKey(String key) {
        return key != null && key.startsWith(KEY_PREFIX) && key.length() > KEY_PREFIX.length();
    }

    static String directoryOf(String key) {
        return key.substring(KEY_PREFIX.length());
    }

    /**
     * Folder rows for the picker: {@code {key, displayName, imageCount}},
     * where the count includes every subfolder. Every folder between an
     * image and its storage volume's root is listed, so parents with no
     * images of their own still appear. Sorted by path, parents first.
     */
    static List<String[]> fromImagePaths(List<String> imagePaths) {
        Map<String, int[]> countsByDirectory = new HashMap<>();
        Map<String, String> volumeRootByDirectory = new HashMap<>();
        for (String imagePath : imagePaths) {
            if (imagePath == null) continue;
            int lastSlash = imagePath.lastIndexOf('/');
            if (lastSlash <= 0) continue;
            String imageDirectory = imagePath.substring(0, lastSlash + 1);
            String volumeRoot = volumeRoot(imageDirectory);
            if (imageDirectory.equals(volumeRoot) && !isListableRoot(volumeRoot)) continue;
            String directory = imageDirectory;
            while (directory != null) {
                int[] count = countsByDirectory.get(directory);
                if (count == null) {
                    countsByDirectory.put(directory, new int[]{ 1 });
                    volumeRootByDirectory.put(directory, volumeRoot);
                } else {
                    count[0]++;
                }
                directory = volumeRoot == null ? null : parentWithinVolume(directory, volumeRoot);
            }
        }
        List<String> directories = new ArrayList<>(countsByDirectory.keySet());
        Collections.sort(directories, SlideshowFolders::comparePaths);
        List<String[]> rows = new ArrayList<>(directories.size());
        for (String directory : directories) {
            rows.add(new String[]{
                    KEY_PREFIX + directory,
                    displayName(directory, volumeRootByDirectory.get(directory)),
                    Integer.toString(countsByDirectory.get(directory)[0]) });
        }
        return rows;
    }

    /**
     * Root of the storage volume holding {@code directory}:
     * {@code /storage/emulated/<user>/} for internal storage,
     * {@code /storage/<volume-id>/} for USB drives and SD cards, or
     * {@code null} for any other layout (then only the image's own folder is
     * listed).
     */
    static String volumeRoot(String directory) {
        if (directory == null) return null;
        int segmentsBelowStorage;
        if (directory.startsWith(EMULATED)) segmentsBelowStorage = 2;
        else if (directory.startsWith(STORAGE)) segmentsBelowStorage = 1;
        else return null;
        int end = STORAGE.length();
        for (int i = 0; i < segmentsBelowStorage; i++) {
            int slash = directory.indexOf('/', end);
            if (slash < 0) return null;
            end = slash + 1;
        }
        return directory.substring(0, end);
    }

    /** SQL LIKE pattern matching every file under {@code directory},
     *  escaped for {@code ESCAPE '\'}. */
    static String likePatternUnder(String directory) {
        StringBuilder pattern = new StringBuilder(directory.length() + 4);
        for (int i = 0; i < directory.length(); i++) {
            char c = directory.charAt(i);
            if (c == '\\' || c == '%' || c == '_') pattern.append('\\');
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }

    /** A USB drive or SD card can be picked as a whole. Internal storage
     *  can't: its root also holds Android/, DCIM/, Download/ and the rest. */
    private static boolean isListableRoot(String volumeRoot) {
        return !volumeRoot.startsWith(EMULATED);
    }

    private static String parentWithinVolume(String directory, String volumeRoot) {
        if (directory.length() <= volumeRoot.length()) return null;
        int slash = directory.lastIndexOf('/', directory.length() - 2);
        if (slash < volumeRoot.length() - 1) return null;
        String parent = directory.substring(0, slash + 1);
        if (parent.length() > volumeRoot.length()) return parent;
        return isListableRoot(volumeRoot) ? parent : null;
    }

    /** Path below the volume root, e.g. {@code Pictures/Wallpapers}. Other
     *  volumes keep their id in front ({@code 1A2B-3C4D/Wallpapers}) so a USB
     *  folder never looks like an internal one. */
    private static String displayName(String directory, String volumeRoot) {
        String trimmed = directory.substring(0, directory.length() - 1);
        if (volumeRoot == null) return trimmed.substring(trimmed.lastIndexOf('/') + 1);
        if (volumeRoot.startsWith(EMULATED)) return trimmed.substring(volumeRoot.length());
        return trimmed.substring(STORAGE.length());
    }

    /** Segment-by-segment, case-insensitive, so {@code Pictures/Cars} sorts
     *  right after {@code Pictures} rather than after {@code Pictures Old}. */
    private static int comparePaths(String a, String b) {
        String[] as = a.split("/");
        String[] bs = b.split("/");
        for (int i = 0; i < Math.min(as.length, bs.length); i++) {
            int c = String.CASE_INSENSITIVE_ORDER.compare(as[i], bs[i]);
            if (c != 0) return c;
        }
        return as.length - bs.length;
    }
}
