package tv.glass.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SlideshowFoldersTest {

    private static final String INTERNAL = "/storage/emulated/0/";

    @Test public void fromImagePaths_listsParentWithNoImagesOfItsOwn() {
        List<String[]> rows = SlideshowFolders.fromImagePaths(Arrays.asList(
                INTERNAL + "Wallpapers/Cars/a.jpg",
                INTERNAL + "Wallpapers/Cars/b.jpg",
                INTERNAL + "Wallpapers/Nature/c.jpg"));

        assertEquals(Arrays.asList(
                "dir:" + INTERNAL + "Wallpapers/ | Wallpapers | 3",
                "dir:" + INTERNAL + "Wallpapers/Cars/ | Wallpapers/Cars | 2",
                "dir:" + INTERNAL + "Wallpapers/Nature/ | Wallpapers/Nature | 1"),
                describe(rows));
    }

    @Test public void fromImagePaths_sortsChildrenRightAfterTheirParent() {
        List<String[]> rows = SlideshowFolders.fromImagePaths(Arrays.asList(
                INTERNAL + "Pictures Old/a.jpg",
                INTERNAL + "Pictures/Cars/b.jpg",
                INTERNAL + "pictures2/c.jpg"));

        List<String> names = new ArrayList<>();
        for (String[] row : rows) names.add(row[1]);
        assertEquals(Arrays.asList("Pictures", "Pictures/Cars", "Pictures Old", "pictures2"), names);
    }

    @Test public void fromImagePaths_neverListsInternalStorageRoot() {
        List<String[]> rows = SlideshowFolders.fromImagePaths(Arrays.asList(
                INTERNAL + "loose.jpg",
                INTERNAL + "DCIM/d.jpg"));

        assertEquals(Arrays.asList("dir:" + INTERNAL + "DCIM/ | DCIM | 1"), describe(rows));
    }

    @Test public void fromImagePaths_usbDriveRootCanBePicked() {
        List<String[]> rows = SlideshowFolders.fromImagePaths(Arrays.asList(
                "/storage/1A2B-3C4D/Wallpapers/a.jpg",
                "/storage/1A2B-3C4D/b.jpg"));

        assertEquals(Arrays.asList(
                "dir:/storage/1A2B-3C4D/ | 1A2B-3C4D | 2",
                "dir:/storage/1A2B-3C4D/Wallpapers/ | 1A2B-3C4D/Wallpapers | 1"),
                describe(rows));
    }

    @Test public void fromImagePaths_unknownLayout_listsOnlyTheImageFolder() {
        List<String[]> rows = SlideshowFolders.fromImagePaths(Arrays.asList(
                "/mnt/media/Photos/Trip/a.jpg", null, "no-slash.jpg"));

        assertEquals(Arrays.asList("dir:/mnt/media/Photos/Trip/ | Trip | 1"), describe(rows));
    }

    @Test public void volumeRoot_handlesInternalUsbAndOther() {
        assertEquals(INTERNAL, SlideshowFolders.volumeRoot(INTERNAL + "Pictures/"));
        assertEquals("/storage/emulated/10/", SlideshowFolders.volumeRoot("/storage/emulated/10/Pictures/"));
        assertEquals("/storage/1A2B-3C4D/", SlideshowFolders.volumeRoot("/storage/1A2B-3C4D/Pictures/"));
        assertNull(SlideshowFolders.volumeRoot("/sdcard/Pictures/"));
    }

    @Test public void likePatternUnder_escapesWildcards() {
        assertEquals("/storage/emulated/0/My\\_Pics\\%\\\\/%",
                SlideshowFolders.likePatternUnder("/storage/emulated/0/My_Pics%\\/"));
    }

    @Test public void isDirectoryKey_tellsNewKeysFromLegacyBucketIds() {
        assertTrue(SlideshowFolders.isDirectoryKey("dir:/storage/emulated/0/Pictures/"));
        assertFalse(SlideshowFolders.isDirectoryKey("-1739773001"));
        assertFalse(SlideshowFolders.isDirectoryKey("dir:"));
        assertFalse(SlideshowFolders.isDirectoryKey(null));
    }

    private static List<String> describe(List<String[]> rows) {
        List<String> out = new ArrayList<>();
        for (String[] row : rows) out.add(row[0] + " | " + row[1] + " | " + row[2]);
        return out;
    }
}
