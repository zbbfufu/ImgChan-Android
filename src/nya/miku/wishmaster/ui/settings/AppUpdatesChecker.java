/*
 * Overchan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
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
 */

package nya.miku.wishmaster.ui.settings;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import nya.miku.wishmaster.ui.tabs.UrlHandler;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.widget.Toast;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.message.BasicHeader;

public class AppUpdatesChecker {
    private static final String TAG = "AppUpdatesChecker";
    
    private static final String URL_PATH = "https://api.github.com/repos/overchan-project/Overchan-Android/releases/";
    private static final String URL_BETA = "tags/current";
    private static final String URL_STABLE = "latest";

    public static void checkForUpdates(final Activity activity) {
        checkForUpdates(activity, false);
    }

    public static void checkForUpdates(final Activity activity, final boolean silent) {
        final CancellableTask task = new CancellableTask.BaseCancellableTask();
        final ProgressDialog progressDialog;
        if (silent) {
            progressDialog = null;
        } else {
            progressDialog = new ProgressDialog(activity);
            progressDialog.setMessage(activity.getString(R.string.app_update_checking));
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    task.cancel();
                }
            });
            progressDialog.show();
        }
        Async.runAsync(new Runnable() {
            @Override
            public void run() {
                JSONObject response;
                try {
                    String url = URL_PATH + (MainApplication.getInstance().settings.isUpdateAllowBeta() ? URL_BETA : URL_STABLE);
                    ExtendedHttpClient httpClient = new ExtendedHttpClient(null);
                    Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json") };
                    HttpRequestModel request = HttpRequestModel.builder().setGET().setCustomHeaders(customHeaders).build();
                    response = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, null, task, false);
                } catch (Exception e) {
                    response = null;
                }
                process(response);
            }
            private void process(final JSONObject result) {
                if (task.isCancelled()) return;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (task.isCancelled()) return;
                        try {
                            if (!silent) progressDialog.dismiss();
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            return;
                        }
                        try {
                            if (result == null) throw new Exception();
                            String newVersionName = result.getString("name");
                            String currentVersionName = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
                            if (!currentVersionName.equals(newVersionName)) {
                                String newVersionInfo = result.getString("body");
                                final String url = result.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");
                                DialogInterface.OnClickListener onClickYes = new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        UrlHandler.launchExternalBrowser(activity, url);
                                    }
                                };
                                new AlertDialog.Builder(activity).
                                        setTitle(R.string.app_update_update_available).
                                        setMessage(activity.getString(R.string.app_update_update_dialog_text, newVersionName, newVersionInfo)).
                                        setPositiveButton(android.R.string.yes, onClickYes).
                                        setNegativeButton(android.R.string.no, null).
                                        show();
                            } else {
                                if (!silent) Toast.makeText(activity, R.string.app_update_update_not_required, Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            if (!silent) Toast.makeText(activity, R.string.app_update_update_error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }
}
