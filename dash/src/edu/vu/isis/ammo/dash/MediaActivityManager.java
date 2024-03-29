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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import edu.vu.isis.ammo.dash.provider.IncidentSchema.MediaTableSchema;
import edu.vu.isis.ammo.dash.provider.IncidentSchemaBase.MediaTableSchemaBase;
import edu.vu.isis.ammo.dash.template.AmmoTemplateManagerActivity;

/**
 * See TigrMobile code for more information on the history of this class.
 * 
 * Like the class name indicates, this class is used to manage media created in
 * Dash. Most of these methods are convenience methods (hence the static scope)
 * used throughout Dash for different purposes such as writing data to the SD
 * card and serializing data.
 */
public class MediaActivityManager {

	private static final int THUMBNAIL_HEIGHT = 480;
	public static final String[] EXIF_TAGS = { ExifInterface.TAG_DATETIME,
			ExifInterface.TAG_FLASH, ExifInterface.TAG_FOCAL_LENGTH,
			ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_LATITUDE,
			ExifInterface.TAG_GPS_LATITUDE_REF,
			ExifInterface.TAG_GPS_LONGITUDE,
			ExifInterface.TAG_GPS_LONGITUDE_REF,
			ExifInterface.TAG_GPS_PROCESSING_METHOD,
			ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_IMAGE_LENGTH,
			ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_MAKE,
			ExifInterface.TAG_MODEL, ExifInterface.TAG_ORIENTATION,
			ExifInterface.TAG_WHITE_BALANCE };

	private MediaActivityManager() {
	}

	private static final Logger logger = LoggerFactory
			.getLogger("class.MediaActivityManager");

	public static Uri createPhotoUri(Context context) {
		try {
			return Uri.fromFile(File.createTempFile(
					String.valueOf(System.currentTimeMillis()) + "-", ".jpg",
					DashAbstractActivity.IMAGE_DIR));
		} catch (IOException e) {
			logger.error("::createPhotoUri", e);
			return null;
		}
	}

	public static Intent createPictureIntent(Context context, String id,
			Uri photoUri) {
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
		return intent;
	}

	public static Intent createAudioIntent(Context context, String id) {
		Intent intent = new Intent(context, AudioEntryActivity.class);
		intent.putExtra(AudioEntryActivity.INCIDENT_UUID, id);
		return intent;
	}

	public static Intent createTemplateIntent(Context context, String id,
			String template, String templateData, Location location) {
		return new Intent(context, AmmoTemplateManagerActivity.class)
				.putExtra(AmmoTemplateManagerActivity.TEMPLATE_EXTRA, template)
				.putExtra(AmmoTemplateManagerActivity.JSON_DATA_EXTRA,
						templateData)
				.putExtra(DashAbstractActivity.OPEN_FOR_EDIT_EXTRA, true)
				.putExtra(AmmoTemplateManagerActivity.LOCATION_EXTRA, location);
	}

	public static Bitmap getThumbnail(Uri photoUri) {
		if (photoUri == null) {
			logger.error("::getThumbnail - uri null");
			return null;
		}

		// Create a thumbnail from the full size image cached.
		Bitmap src = BitmapFactory.decodeFile(photoUri.getPath());

		if (src == null) {
			logger.error("::getThumbnail - could not decode file: "
					+ photoUri.getPath());
			return null;
		}

		// Scale width and height so height is 150px.
		int dstWidth = (int) Math
				.round((double) (src.getWidth() * THUMBNAIL_HEIGHT)
						/ src.getHeight());
		int dstHeight = THUMBNAIL_HEIGHT;

		return Bitmap.createScaledBitmap(src, dstWidth, dstHeight, true);
	}

	/**
	 * Ideas from Dash class.
	 * 
	 * @return true on success, false on failure.
	 */
	public static Uri processPicture(ContentResolver contentResolver,
			String id, Bitmap thumbnail, String origFilepath) {
		if (thumbnail == null) {
			logger.error("::processPicture - thumbnail null");
			return null;
		}

		// Write the thumbnail to the sd card and store the URI in our CP.
		String filePath = writeBitmapToSDCard(thumbnail,
				String.valueOf(System.currentTimeMillis()), origFilepath);
		if (filePath == null) {
			return null;
		}

		// Store the fileUri in the content provider.
		return storeInContentProvider(id, MediaTableSchema.IMAGE_DATA_TYPE,
				filePath, contentResolver);
	}

	public static Uri storeInContentProvider(String id, String dataType,
			String filePath, ContentResolver resolver) {
		ContentValues cv = new ContentValues();
		cv.put(MediaTableSchemaBase.EVENT_ID, id);
		cv.put(MediaTableSchemaBase.DATA_TYPE, dataType);
		cv.put(MediaTableSchemaBase.DATA, filePath);
		Uri uri = resolver.insert(MediaTableSchemaBase.CONTENT_URI, cv);
		logger.debug("Camera activity returned. Inserted {} into {}", filePath,
				uri.toString());

		return uri;
	}

	private static String writeBitmapToSDCard(Bitmap bitmap, String filename,
			String origFilepath) {
		FileOutputStream outStream = null;
		File file = null;
		// First, the bitmap is compressed and written to the output stream.
		// Then, if origFilepath is not null, an ExifInterface is used to
		// copy the Exif data to the compressed jpeg file.

		// Make the file and save a compressed bitmap to it
		try {
			String exStoreState = Environment.getExternalStorageState();
			if (!exStoreState.equals(Environment.MEDIA_MOUNTED)) {
				logger.error("::writeBitmapToSDCard - no external storage");
				return null;
			}
			if (!DashAbstractActivity.CAMERA_DIR.exists()) {
				boolean success = DashAbstractActivity.CAMERA_DIR.mkdirs();
				if (!success) {
					logger.error("::writeBitmapToSDCard - error creating directories");
					return null;
				}
			}

			file = new File(DashAbstractActivity.CAMERA_DIR, filename + ".jpg");
			outStream = new FileOutputStream(file);
			bitmap.compress(CompressFormat.JPEG,
					DashAbstractActivity.JPEG_QUALITY, outStream);

		} catch (FileNotFoundException e) {
			logger.error("", e);
			return null;
		} finally {
			try {
				if (outStream != null) {
					outStream.close();
				}
			} catch (IOException e) {
				logger.error("", e);
			}
		}

		// Copy the Exif data
		if (origFilepath != null) {
			try {
				ExifInterface inEi = new ExifInterface(origFilepath);
				ExifInterface outEi = new ExifInterface(file.getAbsolutePath());

				for (int i = 0; i < EXIF_TAGS.length; i++) {
					copyAttribute(inEi, outEi, EXIF_TAGS[i]);
				}

				outEi.saveAttributes();
			} catch (IOException e) {
				logger.error("", e);
			}
		}

		return file.getAbsolutePath();
	}

	private static void copyAttribute(ExifInterface inEi, ExifInterface outEi,
			String tag) {
		String att = inEi.getAttribute(tag);
		if (att != null) {
			outEi.setAttribute(tag, att);
		}
	}

	public static Uri processAudio(ContentResolver contentResolver,
			Intent data, String id) {
		// does nothing because everything is handled by the AudioEntryDialog
		// activity
		return data == null ? null : data.getData();
	}

	public static String processTemplate(ContentResolver contentResolver,
			Intent data, String id) {
		return data == null ? null : data
				.getStringExtra(AmmoTemplateManagerActivity.JSON_DATA_EXTRA);
	}

	public static Uri saveTemplateData(ContentResolver contentResolver,
			String id, String templateData) {
		// Store the text file in the sdcard and create a media entry.
		try {
			File dir = new File(
					DashAbstractActivity.TEMPLATE_DIR.getCanonicalPath());
			String filename = String.valueOf(System.currentTimeMillis());
			File currentFile = new File(dir, filename + "_template.txt");
			FileOutputStream fos = new FileOutputStream(currentFile);
			fos.write(templateData.getBytes());
			fos.close();

			// Insert media entry.
			ContentValues cv = new ContentValues();
			cv.put(MediaTableSchemaBase.EVENT_ID, id);
			cv.put(MediaTableSchemaBase.DATA_TYPE,
					MediaTableSchema.TEMPLATE_DATA_TYPE);
			cv.put(MediaTableSchemaBase.DATA, currentFile.getCanonicalPath());
			Uri uri = contentResolver.insert(MediaTableSchemaBase.CONTENT_URI,
					cv);
			logger.debug("Inserted " + currentFile.getCanonicalPath()
					+ " into " + uri.toString());
			return uri;

		} catch (IOException e) {
			logger.error("::saveTemplateData - ", e);
			return null;
		}
	}

	public static String getPath(ContentResolver contentResolver, Uri mediaUri) {
		try {
			Cursor cursor = contentResolver.query(mediaUri,
					new String[] { MediaTableSchemaBase.DATA }, null, null,
					null);
			if (cursor.getCount() != 1) {
				logger.error("::getPath - media not found");
				return null;
			}
			cursor.moveToFirst();
			return cursor.getString(0);
		} catch (Exception e) {
			// the API is pretty vague on the details of what exceptions Cursor
			// will throw
			logger.error("::getPath - ", e);
			return null;
		}
	}
}
