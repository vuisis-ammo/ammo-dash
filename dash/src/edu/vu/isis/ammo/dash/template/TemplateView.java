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
package edu.vu.isis.ammo.dash.template;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.widget.TableLayout;
import android.widget.Toast;
import edu.vu.isis.ammo.dash.Dash;
import edu.vu.isis.ammo.dash.DashAbstractActivity;
import edu.vu.isis.ammo.dash.Util;
import edu.vu.isis.ammo.dash.WorkflowLogger;
import edu.vu.isis.ammo.dash.template.model.Record;
import edu.vu.isis.ammo.dash.template.parsing.AmmoParser;
import edu.vu.isis.ammo.dash.template.view.GuiField;
import edu.vu.isis.ammo.dash.template.view.LocationView;

/**
 * Handles displaying the template once it has been parsed and also 
 * is responsible for initiating parsing either from scratch or from serialized data (read: json).
 * @author adrian
 * @author demetri
 *
 */
public class TemplateView extends TableLayout {

	private static final Logger logger = LoggerFactory.getLogger("class.TemplateView");

	private Record data;
	private String templateDisplayName;
	private Map<String, GuiField> guiFields;
	private boolean openForEdit;
	private Activity activity;
	public LocationView locationView;

	// =============================
	// Lifecycle
	// =============================
	/** Called when the activity is first created. */
	public TemplateView(Activity activity, boolean openForEdit) {
		super(activity);
		this.activity = activity;
		this.openForEdit = openForEdit;
		
		setColumnStretchable(1, true);
	}

	// =============================
	// UI Management
	// =============================
	public boolean loadTemplate(String templateFilename, Location location) {
		AmmoTemplateManagerActivity.checkFiles(activity);
		File templateFile = new File(DashAbstractActivity.TEMPLATE_DIR, templateFilename);
		WorkflowLogger.log("TemplateView - loading template with name: " + templateFile);
		StringBuilder templateDisplayNameHolder = new StringBuilder();
		guiFields = AmmoParser.parseXMLForFileIntoView(activity, templateFile, this, templateDisplayNameHolder, location);
		templateDisplayName = templateDisplayNameHolder.toString();
		
		if (guiFields == null) {
			Toast.makeText(activity, "Could not parse template file", Toast.LENGTH_SHORT).show();
			return false;
		}

		// We want to a handle to the location view in the template (if there is one). 
		// We're going to use the view when we save the template to set a location for the event.
		for (GuiField guiField : guiFields.values()) {
			if (guiField.getTag().equalsIgnoreCase(AmmoParser.LOCATION_TAG)) {
				locationView = (LocationView)guiField;
			}
		}
				
		
		// Now that we've built the layout, populate the contents.
		if (data == null) {
			data = new Record();
		} else {
			fromModel();
		}
		
		if (!openForEdit) {
			for (GuiField guiField : guiFields.values()) {
				guiField.makeReadOnly();
			}
		}
		
		WorkflowLogger.log("TemplateView - finished loading template files");
		
		return true;
	}
	


	public boolean loadTemplateFromJson(String jsonData, Location location) {
		//clear everything
		removeAllViews();
		
		Record data = AmmoTemplateManagerActivity.fromJson(jsonData);
		String template = data.getField(AmmoTemplateManagerActivity.TEMPLATE_NAME_KEY);
		if (template == null || template.length() == 0) {
			Util.makeToast(activity, "Could not load template. Filename not present.");
			return false;
		}
		if(!loadTemplate(template, location)) {
			return false;
		}
		
		setData(data);
		return true;
	}

	public String toText() {
		StringBuilder out = new StringBuilder();
		for (GuiField guiField : guiFields.values()) {
			out.append(guiField.getLabel());
			out.append(": ");
			out.append(guiField.getValue());
			out.append('\n');
		}
		return out.toString();
	}

	private void toModel() {
		if(guiFields == null || data == null) {
			return;
		}
		for (GuiField guiField : guiFields.values()) {
			data.setField(guiField.getId(), guiField.getValue());
		}
		data.setTemplateDisplayName(templateDisplayName);
	}

	private void fromModel() {
		if(guiFields == null || data == null) {
			return;
		}
		for (GuiField guiField : guiFields.values()) {
			guiField.setValue(data.getField(guiField.getId()));
		}
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode != Activity.RESULT_OK) {
			//if the child activity was canceled, ignore.
			return;
		}
		if(requestCode != DashAbstractActivity.MAP_TYPE) {
			//wasn't for me
			return;
		}
		
		if(data == null || data.getStringExtra(AmmoTemplateManagerActivity.LOCATION_FIELD_ID_EXTRA) == null) {
			logger.error("The intent was lacking a location field id");
			Util.makeToast(getContext(), "Could not get map point");
			return;
		}
		
		String id = data.getStringExtra(AmmoTemplateManagerActivity.LOCATION_FIELD_ID_EXTRA);
		
		if(guiFields.get(id) == null || !(guiFields.get(id) instanceof LocationView)) {
			logger.error("The intent had a location field id that we could not find: " + id);
			Util.makeToast(getContext(), "Could not get map point");
			return;
		}
		
		
		if(!((LocationView)guiFields.get(id)).processMapPoint(data)) {
			//toast already displayed, and error log already has details on the error
		}
	}
	
	public Record getData() {
		toModel();
		return data;
	}
	
	public void setData(Record data) {
		this.data = data;
		fromModel();
	}
	
	public String getTemplateDisplayName() {
		return templateDisplayName;
	}
}
