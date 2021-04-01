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

import android.app.Activity;

import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.api.HttpChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.InteractiveException;

public class HashwallExceptionAntiDDOS extends InteractiveException {
    private static final long serialVersionUID = 1L;

    private static final String SERVICE_NAME = "Hashwall";
    private static final String TAG = "HashwallException";

    private static final Pattern PATTERN_ENTRY_POINT = Pattern.compile("addEventListener\\(.+?\\{([^}]+)");
    private static final Pattern PATTERN_PARAMETER = Pattern.compile("['\"]([^'\"]+)['\"]");

    private String url;
    private String baseUrl;
    private String chanName;
    private String cookieName;
    private String queryParam;
    private String passPhrase;
    private String challenge;
    private int upperBound;

    public String getCookieName() {
        return cookieName;
    }

    public String getChallenge() {
        return challenge;
    }

    public int getUpperBound() {
        return upperBound;
    }

    public String getQueryParam() {
        return queryParam;
    }

    public String getPassPhrase() {
        return passPhrase;
    }

    public String getUrl() {
        return url;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    
    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    public static HashwallExceptionAntiDDOS newInstance(String url, String arguments, String html, String chanName) {
        try {
            HashwallExceptionAntiDDOS res = new HashwallExceptionAntiDDOS();
            res.url = url;
            res.chanName = chanName;
            res.passPhrase = MainApplication.getInstance().settings.getUserAgentString();
            res.cookieName = arguments.split("=")[0];
            res.challenge = arguments.split("=")[1];
            res.queryParam = null;
            res.upperBound = -1;
            Matcher m;
            m = PATTERN_ENTRY_POINT.matcher(html);
            if (m.find()) {
                m = PATTERN_PARAMETER.matcher(m.group(1));
                while (m.find() && (res.queryParam == null || res.upperBound == -1)) {
                    if (!m.group(1).equals(res.cookieName)) {
                        if (StringUtils.isNumeric(m.group(1))) {
                            res.upperBound = Integer.parseInt(m.group(1));
                        } else {
                            res.queryParam = m.group(1);
                        }
                    }
                }
            }
            if (res.queryParam == null || res.upperBound == -1) {
                return null;
            }
            try {
                URL baseUrl = new URL(url);
                res.baseUrl = baseUrl.getProtocol() + "://" + baseUrl.getHost() + "/";
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
            return res;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void handle(Activity activity, CancellableTask task, Callback callback) {
        HashwallChallengeUIHandler.handleHashwall(this, (HttpChanModule) MainApplication.getInstance().getChanModule(chanName), activity, task, callback);
    }
}
