package com.oxyflour.foo_mg_app;

import java.lang.reflect.Field;

import android.util.Log;
import android.net.Uri;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.app.Notification;

import android.os.Bundle;
import android.os.SystemClock;

import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.res.Configuration;

import android.widget.RemoteViews;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Button;
import android.widget.ProgressBar;

import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebBackForwardList;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebSettings.ZoomDensity;

public class MainActivity extends Activity
{
	private WebView wv;
	private ProgressBar pb;
	private AlertDialog ad;
	private BroadcastReceiver br;
	private SharedPreferences sp;
	private NotificationManager nm;

	private static final String logTag = "WebCont";
	private static final String keyUrl = "com.oxyflour.webcont.url";
	private static final String defUrl = "http://10.98.106.76:8888/v0.1";
	private static final String jsInter = "ad";
	private static final String brActPrev = "com.oxyflour.webcont.NOTIFY_PREV";
	private static final String brActPlay = "com.oxyflour.webcont.NOTIFY_PLAY";
	private static final String brActNext = "com.oxyflour.webcont.NOTIFY_NEXT";
	private int notifyId = 0;

	private void promptURL(String message)
	{
		// we already have a dialog
		if (ad != null && ad.isShowing()) return;

		final EditText input = new EditText(this);
		input.setText(sp.getString(keyUrl, defUrl));
		ad = new AlertDialog.Builder(this)
			.setMessage(message)
			.setView(input)
			.setPositiveButton("Open", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					String url = input.getText().toString();
					if (!url.isEmpty()) {
						sp.edit().putString(keyUrl, url).commit();
						wv.loadUrl(url);
					}
				}
			})
			.setNegativeButton("Quit", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					finish();
				}
			})
			.show();
	}
	private WebView createWebview() {
		// set up WebView
		WebView wv = (WebView)findViewById(R.id.webview);
		wv.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
		wv.setScrollbarFadingEnabled(true);
		wv.setInitialScale(100);

		pb = (ProgressBar)findViewById(R.id.progressbar);

		// set up WebSetting
		WebSettings ws = wv.getSettings();
		ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
		ws.setBuiltInZoomControls(true);
		ws.setSupportZoom(true);
		ws.setDisplayZoomControls(false);
		ws.setDefaultZoom(ZoomDensity.valueOf("MEDIUM"));
		ws.setEnableSmoothTransition(true);
		ws.setUseWideViewPort(true);
		ws.setLayoutAlgorithm(LayoutAlgorithm.NARROW_COLUMNS);
		// script and storage should be enabled
		ws.setJavaScriptEnabled(true);
		ws.setDomStorageEnabled(true);
		ws.setDatabaseEnabled(true);
		ws.setDatabasePath("/data/data/" + getPackageName() + "/databases/");

		// set up ProgressBar, like the stock browser
		wv.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onProgressChanged(WebView view, int progress) {
				if (progress < 100) {
					pb.setVisibility(View.VISIBLE);
					pb.setProgress(progress);
				}
				else
					pb.setVisibility(View.GONE);
			}
		});

		// make sure links are handled by the webview rather than the stock browser
		wv.setWebViewClient(new WebViewClient() {
			@Override
			public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
				promptURL("Oops, "+description+"\nOpen a new url or Quit?");
			}
		});
/*
		// use reflection to modify the zoom range
		// see http://stackoverflow.com/questions/10410936/zooming-in-out-on-android-webview-without-a-set-limit
		Class<?> wvc = wv.getClass();
		Object mProviderInstance = null, mZoomManagerInstance = null;
		try {
			Field mProviderField = wvc.getDeclaredField("mProvider");
			mProviderField.setAccessible(true);
			mProviderInstance = mProviderField.get(wv);
			// retrieve the ZoomManager from the WebView
			Class<?> webViewClassicClass = Class.forName("android.webkit.WebViewClassic");
			Field mZoomManagerField = webViewClassicClass.getDeclaredField("mZoomManager");
			mZoomManagerField.setAccessible(true);
			mZoomManagerInstance = mZoomManagerField.get(mProviderInstance);
			// modify the "default max zoom scale" value, which controls the upper limit
			// and set it to something very large; e.g. Float.MAX_VALUE
			Class<?> zoomManagerClass = Class.forName("android.webkit.ZoomManager");
			Field mDefaultMaxZoomScaleField = zoomManagerClass.getDeclaredField("mDefaultMaxZoomScale");
			mDefaultMaxZoomScaleField.setAccessible(true);
			mDefaultMaxZoomScaleField.set(mZoomManagerInstance, Float.MAX_VALUE);
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
*/
		return wv;
	}
	private Notification createNotify(String title, String text, String patt, Boolean isPlaying, long base) {
		RemoteViews cv = new RemoteViews(getPackageName(), R.layout.notify);
		cv.setTextViewText(R.id.noti_title, title);
		cv.setInt(R.id.play_btn, "setBackgroundResource",
			isPlaying ? R.drawable.pause_button : R.drawable.play_button);
		cv.setChronometer(R.id.noti_mete, SystemClock.elapsedRealtime()-base, text + " " + patt, true);
		if (!isPlaying) cv.setChronometer(R.id.noti_mete, SystemClock.elapsedRealtime()-base, "", false);

		cv.setOnClickPendingIntent(R.id.prev_btn, PendingIntent.getBroadcast(this,
			0, new Intent(brActPrev), PendingIntent.FLAG_UPDATE_CURRENT));
		cv.setOnClickPendingIntent(R.id.play_btn, PendingIntent.getBroadcast(this,
			0, new Intent(brActPlay), PendingIntent.FLAG_UPDATE_CURRENT));
		cv.setOnClickPendingIntent(R.id.next_btn, PendingIntent.getBroadcast(this,
			0, new Intent(brActNext), PendingIntent.FLAG_UPDATE_CURRENT));

		Intent i = new Intent(this, MainActivity.class);
		i.setAction(Intent.ACTION_MAIN);
		i.addCategory(Intent.CATEGORY_LAUNCHER);
		PendingIntent pi = PendingIntent.getActivity(this, R.string.app_name, i, PendingIntent.FLAG_UPDATE_CURRENT);

		Notification n = new Notification.Builder(this)
			.setContentTitle(title)
			.setContentText(text)
			.setContentIntent(pi)
			.setSmallIcon(R.drawable.notify_icon)
			.build();
		n.bigContentView = cv;
		n.flags = Notification.FLAG_ONGOING_EVENT;
		return n;
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		// WebChromeClient require this to update progress
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.requestWindowFeature(Window.FEATURE_PROGRESS);
		this.setProgressBarVisibility(true);
		// load layout/main.xml
		setContentView(R.layout.main);

		// initilize webview control
		wv = createWebview();

		// setup notification
		nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

		// receive broadcast from notification center and send to webview page
		br = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				wv.loadUrl("javascript:"+jsInter+".notified && "+jsInter+".notified('"+intent.getAction()+"')");
			}
		};
		IntentFilter ift = new IntentFilter();
		ift.addAction(brActPrev);
		ift.addAction(brActPlay);
		ift.addAction(brActNext);
		registerReceiver(br, ift);

		// injection object
		final Activity act = this;
		wv.addJavascriptInterface(new Object() {
			public void toast(String s) {
				Toast.makeText(act, s, Toast.LENGTH_SHORT).show();
			}
			public int popmenu(String items) {
				return -1;
			}
			public void notify(String title, String text, String patt, int isPlaying, float current) {
				nm.notify(notifyId, createNotify(title, text, patt, (isPlaying != 0) ? true : false, (int)current*1000));
			}
			public String url() {
				return wv.getUrl();
			}
		}, jsInter);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);


		// load page
		sp = getPreferences(MODE_PRIVATE);
		if (savedInstanceState != null) {
			wv.restoreState(savedInstanceState);
		}
		else { 
			if (sp.contains(keyUrl))
				wv.loadUrl(sp.getString(keyUrl, ""));
			else
				promptURL("Open foo_mg url:");
		}
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	/*
	@Override
	public void onResume(){
		super.onResume();	
	}
	*/

	@Override
	public void onDestroy() {
		nm.cancel(notifyId);
		unregisterReceiver(br);
		super.onDestroy();
	}

	/*
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
	*/

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		wv.saveState(outState);
	}

	@Override
	protected void onRestoreInstanceState(Bundle state) {
		wv.restoreState(state);
		super.onRestoreInstanceState(state);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
		// start tracking is required to trigger long press event
		event.startTracking();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
		if (!event.isLongPress()) {
			if (wv.canGoBack()) wv.goBack();
		else promptURL("Open a new url or Quit?");
		}
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) 
		{
		promptURL("Open a new url or Quit?");
			return true;
		}
		return super.onKeyLongPress(keyCode, event);
	}

}
