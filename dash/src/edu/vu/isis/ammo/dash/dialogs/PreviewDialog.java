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
package edu.vu.isis.ammo.dash.dialogs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import edu.vu.isis.ammo.dash.Dash;
import edu.vu.isis.ammo.dash.DashAbstractActivity;
import edu.vu.isis.ammo.dash.R;
import edu.vu.isis.ammo.dash.Util;

/**
 * Subclass of AlertDialog used for displaying a preview of media files.
 * 
 * The alert dialog has a container widget within it that is the root view used
 * for displaying media. Based on the type of media being displayed, the layout
 * of the container widget may appear different.
 * 
 * Audio previews have a progress bar and a play/pause button. Image previews
 * display the image (scaled).
 * 
 * @author demetri
 * 
 */
public class PreviewDialog extends AlertDialog implements OnClickListener {

	// ===========================================================
	// Constants
	// ===========================================================
	private static final Logger logger = LoggerFactory
			.getLogger("class.PreviewDialog");

	/** View tags */
	private static final int DONE_BUTTON_TAG = 1;
	private static final int MEDIA_WRAPPER_TAG = 2;
	private static final int CONTROL_WRAPPER_TAG = 3;

	// ===========================================================
	// Fields
	// ===========================================================
	private Context context;

	private LinearLayout mediaWrapper, controlWrapper;
	private Button btnDone;
	private String data;
	private int dataType;

	/** Audio */
	private MediaPlayer mp;
	private Timer progressTimer;

	// ===========================================================
	// Lifecycle
	// ===========================================================
	/**
	 * Make sure to call setDataAndDataType from onPrepareDialog
	 */
	public PreviewDialog(Context aContext) {
		super(aContext);
		context = aContext;
		setView(LayoutInflater.from(context).inflate(
				R.layout.preview_media_dialog, null));
	}

	@Override
	public void onStart() {
		super.onStart();
		this.loadMedia();
	}

	@Override
	protected void onStop() {
		if (mp != null) {
			mp.stop();
		}

		if (progressTimer != null) {
			progressTimer.cancel();
		}

		super.onStop();
	}

	// ===========================================================
	// UI Setup
	// ===========================================================
	@Override
	public void setView(View v) {
		super.setView(v);

		mediaWrapper = (LinearLayout) v.findViewById(R.id.previewMediaWrapper);
		mediaWrapper.setTag(MEDIA_WRAPPER_TAG);

		controlWrapper = (LinearLayout) v
				.findViewById(R.id.previewMediaControls);
		controlWrapper.setTag(CONTROL_WRAPPER_TAG);

		btnDone = (Button) v.findViewById(R.id.previewMediaDoneButton);
		btnDone.setTag(DONE_BUTTON_TAG);
		btnDone.setOnClickListener(this);

		setTitle("Media Preview");
		setIcon(R.drawable.app_icon);
	}

	// ===========================================================
	// Media Loading
	// ===========================================================
	public void setDataAndDataType(String dataPath, int aDataType) {
		data = dataPath;
		dataType = aDataType;
	}

	private void loadMedia() {
		logger.trace("Data URI for PreviewDialog: {}", data);
		mediaWrapper.removeAllViews();

		if (data == null) {
			logger.error("loadMedia:  Please call setDataAndDataType in onPrepareDialog");
			Util.makeToast(context, "Could not load media");
			return;
		}

		switch (dataType) {
		case DashAbstractActivity.TEXT_TYPE:
			loadTextPreview();
			break;
		case DashAbstractActivity.AUDIO_TYPE:
			loadAudioPreview();
			break;

		case DashAbstractActivity.IMAGE_TYPE:
			loadImagePreview();
			break;
		case DashAbstractActivity.VIDEO_TYPE:
			loadVideoPreview();
			break;
		default:
			Toast.makeText(context, "Error loading preview", Toast.LENGTH_LONG)
					.show();
			cancel();
		}
	}

	// Play the audio file.
	private void loadAudioPreview() {
		LayoutInflater inflater = this.getLayoutInflater();
		inflater.inflate(R.layout.dash_audio_preview_controls, mediaWrapper);

		try {

			mp = new MediaPlayer();
			mp.setDataSource(data);
			mp.prepare();

			// Setup Seekbar used for audio file navigation.
			final SeekBar seekbar = (SeekBar) mediaWrapper
					.findViewById(R.id.dash_audio_preview_controls_seek_bar);
			seekbar.setMax(mp.getDuration());

			final ImageButton playBtn = (ImageButton) mediaWrapper
					.findViewById(R.id.dash_audio_preview_controls_play);
			playBtn.setOnClickListener(new android.view.View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mp.isPlaying()) {
						mp.pause();
						progressTimer.cancel();
						playBtn.setImageResource(R.drawable.dash_audio_play_selector);
					} else {
						mp.seekTo(seekbar.getProgress());
						mp.start();
						playBtn.setImageResource(R.drawable.dash_audio_pause_selector);

						progressTimer = new Timer();
						progressTimer.scheduleAtFixedRate(new TimerTask() {
							@Override
							public void run() {
								seekbar.setProgress(mp.getCurrentPosition());
							}
						}, 0, 500);
					}
				}
			});

			mp.setOnCompletionListener(new OnCompletionListener() {
				@Override
				public void onCompletion(MediaPlayer mp) {
					playBtn.setImageResource(R.drawable.dash_audio_play_selector);
					seekbar.setProgress(0);
					progressTimer.cancel();
				}
			});

			seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar bar, int progress,
						boolean fromUser) {
					if (fromUser) {
						if (mp.isPlaying()) {
							mp.seekTo(progress);
						}
					}
				}

				@Override
				public void onStartTrackingTouch(SeekBar arg0) {
				}

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}
			});

		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void loadTextPreview() {
		ScrollView sv = new ScrollView(context);
		TextView tv = new TextView(context);
		File f = new File(data);

		// This casting should be okay.
		byte[] buffer = new byte[(int) f.length()];
		FileInputStream fis;
		try {
			fis = new FileInputStream(data);

			int ret = 0;
			while (ret >= 0) {
				ret = fis.read(buffer);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		String s = new String(buffer);
		tv.setText(s);

		// Build the view.
		sv.addView(tv);
		mediaWrapper.addView(sv);
	}

	// Create an image view with the image
	private void loadImagePreview() {
		ImageView iv = new ImageView(context);
		iv.setImageURI(Uri.parse(data));
		mediaWrapper.addView(iv);
	}

	private void loadVideoPreview() {
		VideoView vv = new VideoView(context);
		
		LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		MediaController mc = new MediaController(context);
		
		vv.setLayoutParams(lp);
		vv.setVideoPath(data);
		vv.setMediaController(mc);
		
		mc.setVisibility(View.GONE);
		
		LinearLayout ll = (LinearLayout) findViewById(R.id.previewMediaWrapper);
		ll.removeAllViews();
		ll.addView(vv);
		
		vv.start();
	}

	// ===========================================================
	// User Interaction
	// ===========================================================
	@Override
	public void onClick(View v) {
		switch ((Integer) v.getTag()) {
		case DONE_BUTTON_TAG:
			this.cancel();
			break;
		default:
			// do nothing.
		}
	}

}
