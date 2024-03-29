/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.vu.isis.ammo.dash;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.widget.Toast;

import edu.vu.isis.ammo.dash.preferences.DashPreferences;
import edu.vu.isis.ammo.dash.provider.IncidentSchemaBase.MediaTableSchemaBase;
import edu.vu.isis.ammo.util.CoordinateConversion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import transapps.gallery.GalleryAPI;
import transapps.gallery.GalleryDao;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Set of utility methods used throughout Dash for tasks like location
 * conversions and file reading/manipulation.
 * 
 * @author demetri
 * @author adrian
 */
public class Util {
    private static final DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss MM-dd-yyyy",
            Locale.US);
    private static final Logger logger = LoggerFactory.getLogger("class.Util");

    /**
     * how much to multiply/divide location values by to store/retrieve them
     * from the database
     */
    public static final double COORDINATE_SCALE_FACTOR = 1000000; // One million

    private Util() {
    }

    public static String toMGRSString(Location location) {
        if (location == null)
            return "";

        return new CoordinateConversion().latLon2MGRUTM(location.getLatitude(), location.getLongitude());
    }

    public static Location toLocation(String mgrs) {
        if (mgrs == null) {
            return null;
        }

        try {
            double[] location = new CoordinateConversion().mgrutm2LatLon(mgrs);
            return buildLocation(location[0], location[1]);
        } catch (Exception exception) {
            // wow, this code just throws random exceptions if the input is
            // invalid
            return null;
        }
    }

    public static Location buildLocation(double latitude, double longitude) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        return location;
    }

    public static String toString(Location location, Context context) {
        if (DashPreferences.isMGRSPreference(context)) {
            return toMGRSString(location);
        }
        return location.getLatitude() + ", " + location.getLongitude();
    }

    public static String formatTime(long time) {
        return dateFormat.format(time);
    }

    /**
     * Convert an integer representation of a coordinate in the Dash database
     * to a double value that is a valid latitude or longitude coordinate
     * 
     * @param coordinate The coordinate to convert
     * @return A double representation of the coordinate
     */
    public static double scaleIntCoordinate(int coordinate) {
        return ((double)coordinate) / COORDINATE_SCALE_FACTOR;
    }
    
    /**
     * Convert a double latitude or longitude value to an integer value
     * to store in the Dash database
     * 
     * @param coordinate The coordinate to convert
     * @return An integer representation of the coordinate
     */
    public static int scaleDoubleCoordinate(double coordinate) {
        return (int) (coordinate * COORDINATE_SCALE_FACTOR);
    }

    /**
     * Create files for collector
     * 
     * @throws IOException
     */
    public static void setupFilePaths() throws IOException {
        String state = android.os.Environment.getExternalStorageState();
        if (!state.equals(android.os.Environment.MEDIA_MOUNTED)) {
            throw new IOException("SD Card is not mounted.  It is " + state + ".");
        }

        if (!createPath(DashAbstractActivity.CAMERA_DIR))
            throw new IOException("error with camera directory :" + DashAbstractActivity.CAMERA_DIR);
        if (!createPath(DashAbstractActivity.IMAGE_DIR))
            throw new IOException("error with image directory :" + DashAbstractActivity.IMAGE_DIR);
        if (!createPath(DashAbstractActivity.AUDIO_DIR))
            throw new IOException("error with audio directory :" + DashAbstractActivity.AUDIO_DIR);
        if (!createPath(DashAbstractActivity.TEXT_DIR))
            throw new IOException("error with text directory :" + DashAbstractActivity.TEXT_DIR);
        if (!createPath(DashAbstractActivity.VIDEO_DIR))
            throw new IOException("error with video directory :" + DashAbstractActivity.VIDEO_DIR);
        if (!createPath(DashAbstractActivity.TEMPLATE_DIR))
            throw new IOException("error with template directory :"
                    + DashAbstractActivity.TEMPLATE_DIR);
    }

    private static boolean createPath(File directory) {
        try {
            if (directory.exists())
                return true;
            if (directory.mkdirs())
                return true;
            logger.error("could not create paths {}", directory.toString());
            Util.drillDirectory(directory);

        } catch (SecurityException ex) {
            logger.error("security exception {}", ex.getLocalizedMessage());
        }
        return false;
    }

    private static int drillDirectory(File dir) {
        if (dir == null)
            return 0;
        int level = Util.drillDirectory(dir.getParentFile()) + 1; // print
                                                                  // parents
                                                                  // first

        if (dir.exists()) {
            logger.error("l:{} {} read:{} write:{}",
                    new Object[] {
                            level, dir.toString(), dir.canRead(), dir.canWrite()
                    });
        }
        return level;
    }

    public static void makeToast(Context context, String string) {
        Toast.makeText(context, string, Toast.LENGTH_SHORT).show();
        logger.info(string);
    }

    /**
     * @return bytes
     */
    public static long getSize(long baseDashSize, Context context, Uri currentMediaUri,
            String templateData) {
        return baseDashSize +
                getSize(context.getContentResolver(), currentMediaUri) +
                getSize(templateData);
    }

    public static int getMediaCount(Uri currentMediaUri, String templateData) {
        int mediaCount = 0;
        if (currentMediaUri != null) {
            mediaCount++;
        }

        if (templateData != null) {
            mediaCount++;
        }
        return mediaCount;
    }

    private static int getSize(String data) {
        return data == null ? 0 : data.getBytes().length;
    }

    /**
     * @return bytes for an object inserted into the ContentResolver with a DATA
     *         object to a path on the system, or 0 on error.
     */
    private static long getSize(ContentResolver contentResolver, Uri uri) {
        if (uri == null) {
            return 0;
        }
        String filePath = getString(contentResolver, uri, MediaTableSchemaBase.DATA);
        if (filePath == null) {
            return 0;
        }
        File file = new File(filePath);
        if (!file.exists()) {
            logger.error("::getSize: File does not exist: " + file);
            return 0;
        }

        return file.length();
    }

    private static String getString(ContentResolver contentResolver, Uri uri, String field) {
        if (uri == null || field == null) {
            logger.error("::getString: No uri or field.");
            return null;
        }
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(uri, new String[] {
                field
            }, null, null, null);
            if (cursor == null || cursor.moveToFirst() == false
                    || cursor.getColumnIndex(field) == -1) {
                logger.error("::getString: No data for uri/field: " + uri + ", " + field);
                return null;
            }

            String string = cursor.getString(cursor.getColumnIndex(field));
            if (string == null) {
                logger.error("::getString: No data at uri/field: " + uri + ", " + field);
                return null;
            }
            return string;
        } catch (Exception e) {
            logger.error("::getString: No data at uri/field: " + uri + ", " + field, e);
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    
    public static void addToGallery(File file, String tag, String desc, String mimeType, Context context) {
        GalleryAPI api = new GalleryAPI(context);  // "this" is a Context
        GalleryDao dao = new GalleryDao("DASH",  // This value is a place holder when using updateDefault()
                file.getAbsolutePath(),  // Since this image will be manged by gallery use the full path as it's ID.
                tag,  // This is the string the user sees associated with images taken for Dash (also for filtering)- should identify Dash images as a class and NOT be unique.
                file.lastModified(),
                file.length(),
                desc,  // Description of image
                mimeType,
                file.getAbsolutePath(),
                null,  // med thumb- leave null and Gallery will handle.
                null,  // small thumb-  leave null and Gallery will handle
                file.getName(),  // Title
                Integer.MAX_VALUE,  // latitude - the next five values will be extracted from EXIF if possible when set to MAX_VALUE or -1.0 for last.
                Integer.MAX_VALUE,  // longitude
                Integer.MAX_VALUE,  // altitude
                Integer.MAX_VALUE,  // orientation
                -1.0);
        // Use insertDefault to allow Gallery to manage the image once added (i.e. you do not have a Gallery provider to manage the image).
        api.insertDefault(dao);
    }
}
