/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
 */
package edu.vu.isis.ammo.dash;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import edu.vu.isis.ammo.dash.incident.provider.IncidentContentDescriptor;
import edu.vu.isis.ammo.dash.preferences.ContactsUtil;

/**
 * Model object used as a wrapper for all data associated with a Dash report.
 * 
 * @author demetri
 * 
 */
public class DashModel {
	private ContentValues model = new ContentValues();
	private Context context;

	private Bitmap thumbnail;
	private Uri photoUri;
	private Uri currentMediaUri;
	private int currentMediaType;
	private String templateData;
	private String description;

	private static final long BASE_DASH_SIZE = 20;
	private static final Logger logger = LoggerFactory
			.getLogger("class.DashModel");

	public DashModel(Context context) {
		this.context = context;
	}

	public ContentValues getContentValues() {
		setAdditionalFields();
		return model;
	}

	public void setContentValues(ContentValues model) {
		this.model = model;
		if (this.model == null) {
			this.model = new ContentValues();
		}
	}

	public String getId() {
		return model.getAsString(IncidentContentDescriptor.Event.Cols.UUID);
	}

	public void setId(String id) {
		model.put(IncidentContentDescriptor.Event.Cols.UUID, id);
	}

	public String getOriginator() {
		return model.getAsString(IncidentContentDescriptor.Event.Cols.ORIGINATOR);
	}

	public void setOriginator(String originator) {
		model.put(IncidentContentDescriptor.Event.Cols.ORIGINATOR, originator);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Long getTime() {
		return model.getAsLong(IncidentContentDescriptor.Event.Cols.MODIFIED_DATE);
	}

	public void setTime(Long time) {
		model.put(IncidentContentDescriptor.Event.Cols.CREATED_DATE, time);
		model.put(IncidentContentDescriptor.Event.Cols.MODIFIED_DATE, time);
	}

	public Location getLocation() {
		if (model.containsKey(IncidentContentDescriptor.Event.Cols.LATITUDE)
				&& model.containsKey(IncidentContentDescriptor.Event.Cols.LONGITUDE)) {
			Integer lat_i = model.getAsInteger(IncidentContentDescriptor.Event.Cols.LATITUDE);
			Integer lon_i = model.getAsInteger(IncidentContentDescriptor.Event.Cols.LONGITUDE);
			if(lat_i == null || lon_i == null) return null;
			double lat_d = Util.scaleIntCoordinate(lat_i);
			double lon_d = Util.scaleIntCoordinate(lon_i);
			return Util.buildLocation(lat_d, lon_d);
		}
		return null;
	}

	public void setLocation(Location location) {
		if (location != null) {
		    // Round location values to 6 decimal places to match
		    // what is displayed on BLOX

			BigDecimal lat = new BigDecimal(location.getLatitude());
			lat = lat.setScale(6, BigDecimal.ROUND_HALF_UP);

			BigDecimal lon = new BigDecimal(location.getLongitude());
			lon = lon.setScale(6, BigDecimal.ROUND_HALF_UP);

			logger.info("Rounded lat and lon to {},{}", lat.toPlainString(),
					lon.toPlainString());

			model.put(IncidentContentDescriptor.Event.Cols.LATITUDE, Util.scaleDoubleCoordinate(lat.doubleValue()));
			model.put(IncidentContentDescriptor.Event.Cols.LONGITUDE, Util.scaleDoubleCoordinate(lon.doubleValue()));
		} else {
			model.put(IncidentContentDescriptor.Event.Cols.LATITUDE, (Double) null);
			model.put(IncidentContentDescriptor.Event.Cols.LONGITUDE, (Double) null);
		}
	}

	public Uri getCurrentMediaUri() {
		return currentMediaUri;
	}

	public void setCurrentMediaUri(Uri currentMediaUri) {
		this.currentMediaUri = currentMediaUri;
	}

	public int getCurrentMediaType() {
		return currentMediaType;
	}

	public void setCurrentMediaType(int currentMediaType) {
		this.currentMediaType = currentMediaType;
	}

	public Uri getPhotoUri() {
		return photoUri;
	}

	public void setImageUri(Uri photoUri) {
		this.photoUri = photoUri;
	}

	public Bitmap getThumbnail() {
		return thumbnail;
	}

	public void setThumbnail(Bitmap thumbnail) {
		this.thumbnail = thumbnail;
	}

	public String getTemplateData() {
		return templateData;
	}

	public void setTemplateData(String templateData) {
		this.templateData = templateData;
	}

	/**
	 * An invalid model is a model that would appear "empty" to the user.
	 * Specifically, this is a model with no description and no media.
	 * 
	 * @return true if model is invalid, false otherwise
	 */
	public boolean isInvalid() {
		return thumbnail == null && photoUri == null && currentMediaUri == null
				&& templateData == null && (description == null || description.length() == 0);
	}

	//
	// Code that massages the data model, to prepare it for delivery:
	//

	private void setAdditionalFields() {
		model.put(IncidentContentDescriptor.Event.Cols.UNIT, ContactsUtil.getUnit(context));
		model.put(IncidentContentDescriptor.Event.Cols.STATUS, IncidentContentDescriptor.EventConstants.STATUS_SENT);
		model.put(IncidentContentDescriptor.Event.Cols.DESCRIPTION, description);

		// For a "dash", these are intentionally blank
		model.put(IncidentContentDescriptor.Event.Cols.CATEGORY_ID, "");
		model.put(IncidentContentDescriptor.Event.Cols.DEST_GROUP_TYPE, "");
		model.put(IncidentContentDescriptor.Event.Cols.DEST_GROUP_NAME, "");
		model.put(IncidentContentDescriptor.Event.Cols.TITLE, "");
		model.put(IncidentContentDescriptor.Event.Cols.DISPLAY_NAME, "<no title>");

		model.put(IncidentContentDescriptor.Event.Cols.MEDIA_COUNT,
				Util.getMediaCount(currentMediaUri, templateData));
		model.put(IncidentContentDescriptor.Event.Cols.SIZE, Util.getSize(BASE_DASH_SIZE,
				context, currentMediaUri, templateData) / 1000.);
	}

}
