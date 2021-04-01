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

package nya.miku.wishmaster.http.hashwall;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.cookie.SetCookie;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

public class HashwallChallengeChecker {
    private static final String TAG = "HashwallChallengeChecker";

    private static final Pattern PATTERN_COOKIE_FORMAT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private HashwallChallengeChecker() {}
    private static HashwallChallengeChecker instance;
    
    /** Получить объект-синглтон */
    public static synchronized HashwallChallengeChecker getInstance() {
        if (instance == null) instance = new HashwallChallengeChecker();
        return instance;
    }

    /**
     * Проверить решение Hashwall, получить cookie
     * @param exception Hashwall исключение
     * @param httpClient HTTP клиент
     * @param task отменяемая задача
     * @param challengeAnswer ответ на рекапчу
     * @return полученная cookie или null, если проверка не прошла
     */
    public Cookie checkSolution(HashwallExceptionAntiDDOS exception, ExtendedHttpClient httpClient, CancellableTask task, String challengeAnswer) {
        HttpResponseModel responseModel = null;
        try {
            List<NameValuePair> pairs = new ArrayList<>();
            pairs.add(new BasicNameValuePair(exception.getCookieName(), exception.getChallenge()));
            pairs.add(new BasicNameValuePair(exception.getQueryParam(), challengeAnswer));
            
            HttpRequestModel rqModel = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8"))
            .setCustomHeaders(new Header[] {
                    new BasicHeader(HttpHeaders.USER_AGENT, MainApplication.getInstance().settings.getUserAgentString()),
                    new BasicHeader(HttpHeaders.ACCEPT, "*/*"),
                    new BasicHeader(HttpHeaders.REFERER, exception.getUrl()),
            }).setNoRedirect(true).build();
            CookieStore cookieStore = httpClient.getCookieStore();
            removeCookie(cookieStore, exception.getCookieName());
            responseModel = HttpStreamer.getInstance().getFromUrl(exception.getBaseUrl(), rqModel, httpClient, null, task);
            for (int i = 0; i < 3  && responseModel.statusCode == 400; ++i) {
                Logger.d(TAG, "HTTP 400");
                responseModel.release();
                responseModel = HttpStreamer.getInstance().getFromUrl(exception.getBaseUrl(), rqModel, httpClient, null, task);
            }
            for (Cookie cookie : cookieStore.getCookies()) {
                if (isHWCookie(cookie, exception.getUrl())) {
                    Logger.d(TAG, "Cookie found: " + cookie.getValue());
                    return cookie;
                }
            }
            Logger.d(TAG, "Cookie is not found");
        } catch (Exception e) {
            Logger.e(TAG, e);
        } finally {
            if (responseModel != null) {
                responseModel.release();
            }
        }
        return null;
    }

    static boolean isHWCookie(Cookie cookie,  String url) {
        try {
            String cookieDomain = cookie.getDomain();
            if (!cookieDomain.startsWith(".")) {
                cookieDomain = "." + cookieDomain;
            }
            String cookieUrl = "." + Uri.parse(url).getHost();
            if (cookieUrl.endsWith(cookieDomain.toLowerCase(Locale.US))) {
                Matcher matcher = PATTERN_COOKIE_FORMAT.matcher(cookie.getValue().toLowerCase(Locale.US));
                return matcher.matches();
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        return false;
    }

    static void removeCookie(CookieStore store, String name) {
        boolean flag = false;
        for (Cookie cookie : store.getCookies()) {
            if (cookie.getName().equals(name)) {
                if (cookie instanceof SetCookie) {
                    flag = true;
                    ((SetCookie) cookie).setExpiryDate(new Date(0));
                } else {
                    Logger.e(TAG, "cannot remove cookie (object does not implement SetCookie): " + cookie.toString());
                }
            }
        }
        if (flag) store.clearExpired(new Date());
    }
}
