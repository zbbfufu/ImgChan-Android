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

package nya.miku.wishmaster.api;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.http.stormwall.StormwallException;
import nya.miku.wishmaster.http.streamer.HttpRequestException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongResponseDetector;
import nya.miku.wishmaster.http.streamer.HttpWrongResponseException;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public abstract class StormwallChanModule extends AbstractChanModule {
    
    protected static final String PREF_KEY_STORMWALL_COOKIE_VALUE = "PREF_KEY_STORMWALL_COOKIE";
    protected static final String PREF_KEY_STORMWALL_COOKIE_DOMAIN = "PREF_KEY_STORMWALL_COOKIE_DOMAIN";

    protected static final String STORMWALL_FIREWALL_HEADER_NAME = "X-FireWall-Protection";
    protected static final String STORMWALL_FIREWALL_TRUE_VALUE = "True";
    
    protected static final String STORMWALL_COOKIE_NAME = "swp_token";
    
    public StormwallChanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    protected boolean canStormwall() {
        return true;
    }
    
    protected String getStormwallCookieDomain() {
        return preferences.getString(getSharedKey(PREF_KEY_STORMWALL_COOKIE_DOMAIN), null);
    }
    
    private static final HttpWrongResponseDetector stormwallDetector = new HttpWrongResponseDetector() {
        @Override
        public void check(final HttpResponseModel model) {
            for (Header header: model.headers) {
                if (header.getName().equalsIgnoreCase(STORMWALL_FIREWALL_HEADER_NAME) &&
                    header.getValue().equalsIgnoreCase(STORMWALL_FIREWALL_TRUE_VALUE)) {
                    HttpWrongResponseException e = new HttpWrongResponseException("Stormwall");
                    e.setHtmlBytes(HttpStreamer.tryGetBytes(model.stream));
                    throw e;
                }
            }
        }
    };

    private void handleWrongResponse(String url, HttpWrongResponseException e) throws HttpWrongResponseException, StormwallException {
        if ("Stormwall".equals(e.getMessage())) {
            String fixedUrl = fixRelativeUrl(url);
            String html = e.getHtmlString();
            StormwallException swe = StormwallException.withRecaptcha(fixedUrl, html, getChanName());
            if (swe == null) swe = StormwallException.antiDDOS(fixedUrl, html, getChanName());
            throw swe;
        }
        throw e;
    }

    protected void checkForStormwall(String url, HttpResponseModel model) throws HttpWrongResponseException, StormwallException {
        try {
            stormwallDetector.check(model);
        } catch (HttpWrongResponseException e) {
            handleWrongResponse(url, e);
        }
    }

    @Override
    protected void initHttpClient() {
        if (canStormwall()) {
            String stormwallCookieValue = preferences.getString(getSharedKey(PREF_KEY_STORMWALL_COOKIE_VALUE), null);
            String stormwallCookieDomain = getStormwallCookieDomain();
            if (stormwallCookieValue != null && stormwallCookieDomain != null) {
                BasicClientCookie c = new BasicClientCookie(STORMWALL_COOKIE_NAME, stormwallCookieValue);
                c.setDomain(stormwallCookieDomain);
                httpClient.getCookieStore().addCookie(c);
            }
            
        }
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        super.saveCookie(cookie);
        if (cookie != null) {
            if (canStormwall() && cookie.getName().equals(STORMWALL_COOKIE_NAME)) {
                preferences.edit().
                        putString(getSharedKey(PREF_KEY_STORMWALL_COOKIE_VALUE), cookie.getValue()).
                        putString(getSharedKey(PREF_KEY_STORMWALL_COOKIE_DOMAIN), cookie.getDomain()).commit();
            }
        }
    }

    @Override
    protected CaptchaModel downloadCaptcha(String url, ProgressListener listener, CancellableTask task) throws Exception {
        Bitmap captchaBitmap = null;
        HttpRequestModel requestModel = HttpRequestModel.DEFAULT_GET;
        HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(url, requestModel, httpClient, listener, task);
        try {
            checkForStormwall(url, responseModel);
            InputStream imageStream = responseModel.stream;
            captchaBitmap = BitmapFactory.decodeStream(imageStream);
        } finally {
            responseModel.release();
        }
        CaptchaModel captchaModel = new CaptchaModel();
        captchaModel.type = CaptchaModel.TYPE_NORMAL;
        captchaModel.bitmap = captchaBitmap;
        return captchaModel;
    }

    @Override
    public void downloadFile(String url, OutputStream out, ProgressListener listener, CancellableTask task) throws Exception {
        if (!canStormwall()) {
            super.downloadFile(url, out, listener, task);
            return;
        }
        String fixedUrl = fixRelativeUrl(url);
        try {
            HttpRequestModel rqModel = HttpRequestModel.DEFAULT_GET;
            HttpStreamer.getInstance().downloadFileFromUrl(fixedUrl, out, rqModel, httpClient, listener, task, false, stormwallDetector);
        } catch (HttpWrongResponseException e) {
            handleWrongResponse(fixedUrl, e);
        }
    }

    @Override
    protected JSONObject downloadJSONObject(String url, boolean checkIfModified, ProgressListener listener, CancellableTask task) throws Exception {
        if (!canStormwall()) return super.downloadJSONObject(url, checkIfModified, listener, task);
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            JSONObject object = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, false, stormwallDetector);
            if (task != null && task.isCancelled()) throw new Exception("interrupted");
            if (listener != null) listener.setIndeterminate();
            return object;
        } catch (HttpWrongResponseException e) {
            handleWrongResponse(url, e);
            return null;
        }
    }

    @Override
    protected JSONArray downloadJSONArray(String url, boolean checkIfModified, ProgressListener listener, CancellableTask task) throws Exception {
        if (!canStormwall()) return super.downloadJSONArray(url, checkIfModified, listener, task);
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            JSONArray array = HttpStreamer.getInstance().getJSONArrayFromUrl(url, rqModel, httpClient, listener, task, false, stormwallDetector);
            if (task != null && task.isCancelled()) throw new Exception("interrupted");
            if (listener != null) listener.setIndeterminate();
            return array;
        } catch (HttpWrongResponseException e) {
            handleWrongResponse(url, e);
            return null;
        }
    }
}
