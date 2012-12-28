/**
 * siir.es.adbWireless.adbWireless.java
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 **/

package siir.es.adbWireless;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class Utils {

	public static void saveWiFiState(Context context, boolean wifiState) {
		SharedPreferences settings = context.getSharedPreferences("wireless", 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean("wifiState", wifiState);
		editor.commit();
	}

	public static void WiFidialog(final Context context) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(context.getString(R.string.no_wifi)).setCancelable(true)
				.setPositiveButton(context.getString(R.string.button_exit), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						System.exit(0);
					}
				}).setNegativeButton(R.string.button_activate_wifi, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						Utils.enableWiFi(context, true);
						dialog.cancel();
					}
				});
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.create();
		builder.setTitle(R.string.no_wifi_title);
		builder.show();
	}

	public static boolean adbStart(Context context) {
		try {
			setProp("service.adb.tcp.port", adbWireless.PORT);
			if (isProcessRunning("adbd")) {
				runRootCommand("stop adbd");
			}
			runRootCommand("start adbd");
			adbWireless.mState = true;
			SharedPreferences settings = context.getSharedPreferences("wireless", 0);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean("mState", adbWireless.mState);
			editor.commit();

			adbWireless.remoteViews.setImageViewResource(R.id.widgetButton, R.drawable.widgetoff);
			ComponentName cn = new ComponentName(context, adbWidgetProvider.class);
			AppWidgetManager.getInstance(context).updateAppWidget(cn, adbWireless.remoteViews);

			if (prefsNoti(context)) {
				showNotification(context, R.drawable.ic_stat_adbwireless, context.getString(R.string.noti_text) + " " + getWifiIp(context));
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public static boolean adbStop(Context context) throws Exception {
		try {
			setProp("service.adb.tcp.port", "-1");
			runRootCommand("stop adbd");
			runRootCommand("start adbd");
			adbWireless.mState = false;
			SharedPreferences settings = context.getSharedPreferences("wireless", 0);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean("mState", adbWireless.mState);
			editor.commit();
			adbWireless.remoteViews.setImageViewResource(R.id.widgetButton, R.drawable.widgeton);
			ComponentName cn = new ComponentName(context, adbWidgetProvider.class);
			AppWidgetManager.getInstance(context).updateAppWidget(cn, adbWireless.remoteViews);
			adbWireless.mNotificationManager.cancelAll();
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public static boolean isProcessRunning(String processName) throws Exception {
		boolean running = false;
		Process process = null;
		process = Runtime.getRuntime().exec("ps");
		BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line = null;
		while ((line = in.readLine()) != null) {
			if (line.contains(processName)) {
				running = true;
				break;
			}
		}
		in.close();
		process.waitFor();
		return running;
	}

	public static boolean hasRootPermission() {
		Process process = null;
		DataOutputStream os = null;
		boolean rooted = true;
		try {
			process = Runtime.getRuntime().exec("su");
			os = new DataOutputStream(process.getOutputStream());
			os.writeBytes("exit\n");
			os.flush();
			process.waitFor();
			if (process.exitValue() != 0) {
				rooted = false;
			}
		} catch (Exception e) {
			rooted = false;
		} finally {
			if (os != null) {
				try {
					os.close();
					process.destroy();
				} catch (Exception e) {
				}
			}
		}
		return rooted;
	}

	public static boolean runRootCommand(String command) {
		Process process = null;
		DataOutputStream os = null;
		try {
			process = Runtime.getRuntime().exec("su");
			os = new DataOutputStream(process.getOutputStream());
			os.writeBytes(command + "\n");
			os.writeBytes("exit\n");
			os.flush();
			process.waitFor();
		} catch (Exception e) {
			return false;
		} finally {
			try {
				if (os != null) {
					os.close();
				}
				process.destroy();
			} catch (Exception e) {
			}
		}
		return true;
	}

	public static boolean setProp(String property, String value) {
		Process process = null;
		DataOutputStream os = null;
		try {
			process = Runtime.getRuntime().exec("su");
			os = new DataOutputStream(process.getOutputStream());
			os.writeBytes("setprop " + property + " " + value + "\n");
			os.writeBytes("exit\n");
			os.flush();
			process.waitFor();
		} catch (Exception e) {
			return false;
		} finally {
			try {
				if (os != null) {
					os.close();
				}
				process.destroy();
			} catch (Exception e) {
			}
		}
		return true;
	}

	public static String getWifiIp(Context context) {
		WifiManager mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		int ip = mWifiManager.getConnectionInfo().getIpAddress();
		return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
	}

	public static void enableWiFi(Context context, boolean enable) {
		if (enable) {
			Toast.makeText(context, R.string.turning_on_wifi, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(context, R.string.turning_off_wifi, Toast.LENGTH_LONG).show();
		}
		WifiManager mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		mWifiManager.setWifiEnabled(enable);
	}

	public static boolean checkWifiState(Context context) {
		try {
			WifiManager mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
			if (!mWifiManager.isWifiEnabled() || wifiInfo.getSSID() == null) {
				return false;
			}

			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@SuppressWarnings("deprecation")
	public static void showNotification(Context context, int icon, String text) {
		final Notification notifyDetails = new Notification(icon, text, System.currentTimeMillis());
		notifyDetails.flags = Notification.FLAG_ONGOING_EVENT;

		if (prefsSound(context)) {
			notifyDetails.defaults |= Notification.DEFAULT_SOUND;
		}

		if (prefsVibrate(context)) {
			notifyDetails.defaults |= Notification.DEFAULT_VIBRATE;
		}

		Intent notifyIntent = new Intent(context, adbWireless.class);
		notifyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent intent = PendingIntent.getActivity(context, 0, notifyIntent, 0);
		notifyDetails.setLatestEventInfo(context, context.getResources().getString(R.string.noti_title), text, intent);
		adbWireless.mNotificationManager.notify(adbWireless.START_NOTIFICATION_ID, notifyDetails);
	}

	public static boolean prefsVibrate(Context context) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
		return pref.getBoolean(context.getResources().getString(R.string.pref_vibrate_key), true);
	}

	public static boolean prefsSound(Context context) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
		return pref.getBoolean(context.getResources().getString(R.string.pref_sound_key), true);
	}

	public static boolean prefsNoti(Context context) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
		return pref.getBoolean(context.getResources().getString(R.string.pref_noti_key), true);
	}

	public static boolean prefsHaptic(Context context) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
		return pref.getBoolean(context.getResources().getString(R.string.pref_haptic_key), true);
	}

	public static boolean prefsWiFiOn(Context context) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
		return pref.getBoolean(context.getResources().getString(R.string.pref_wifi_on_key), false);
	}

	public static boolean prefsWiFiOff(Context context) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
		return pref.getBoolean(context.getResources().getString(R.string.pref_wifi_off_key), true);

	}

}