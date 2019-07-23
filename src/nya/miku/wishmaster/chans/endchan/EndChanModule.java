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

package nya.miku.wishmaster.chans.endchan;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractLynxChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.JSONEntry;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.MimeTypes;
import nya.miku.wishmaster.lib.base64.Base64;
import nya.miku.wishmaster.lib.base64.Base64OutputStream;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class EndChanModule extends AbstractLynxChanModule {
    private static final List<String> DOMAINS_LIST = Arrays.asList(new String[]{
            "endchan.xyz", "endchan.net", "infinow.net", "endchan5doxvprs5.onion", "s6424n4x4bsmqs27.onion", "endchan.i2p"
    });
    private static final String DOMAINS_HINT = "endchan.xyz, endchan.net, infinow.net (cached), endchan5doxvprs5.onion, s6424n4x4bsmqs27.onion, endchan.i2p";
    private static final String TAG = "EndChanModule";
    private static final String DISPLAYING_NAME = "EndChan";
    private static final String CHAN_NAME = "endchan.xyz";
    private static final String DEFAULT_DOMAIN = "endchan.xyz";
    private static final String PREF_KEY_DOMAIN = "domain";
    private String domain;
    
    private String lastCaptchaId;
    private String lastCaptchaAnswer;

    public EndChanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    protected void initHttpClient() {
        updateDomain(preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN));
    }

    @Override
    protected String getUsingDomain() {
        return domain;
    }

    private void addDomainPreferences(PreferenceGroup group) {
        Context context = group.getContext();
        Preference.OnPreferenceChangeListener updateDomainListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (preference.getKey().equals(getSharedKey(PREF_KEY_DOMAIN))) {
                    updateDomain((String) newValue);
                    return true;
                }
                return false;
            }
        };
        PreferenceCategory domainCat = new PreferenceCategory(context);
        domainCat.setTitle(R.string.makaba_prefs_domain_category);
        group.addPreference(domainCat);
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setSummary(resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        domainPref.setOnPreferenceChangeListener(updateDomainListener);
        domainCat.addPreference(domainPref);
    }

    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addPasswordPreference(preferenceGroup);
        if (canHttps()) addHttpsPreference(preferenceGroup, useHttpsDefaultValue());
        addCloudflareRecaptchaFallbackPreference(preferenceGroup);
        addDomainPreferences(preferenceGroup);
        addProxyPreferences(preferenceGroup);
    }

    @Override
    protected String[] getAllDomains() {
        String curDomain = getUsingDomain();
        String[] domains;
        if (DOMAINS_LIST.contains(curDomain)) {
            domains = DOMAINS_LIST.toArray(new String[DOMAINS_LIST.size()]);
        } else {
            domains = DOMAINS_LIST.toArray(new String[DOMAINS_LIST.size() + 1]);
            domains[DOMAINS_LIST.size()] = curDomain;
        }
        return domains;
    }

    private void updateDomain(String domain) {
        if (domain.endsWith("/")) domain = domain.substring(0, domain.length() - 1);
        if (domain.contains("//")) domain = domain.substring(domain.indexOf("//") + 2);
        if (domain.equals("")) domain = DEFAULT_DOMAIN;
        this.domain = domain;
    }

    @Override
    public String getChanName() {
        return CHAN_NAME;
    }

    @Override
    public String getDisplayingName() {
        return DISPLAYING_NAME;
    }

    protected boolean canHttps() {
        return true;
    }

    protected boolean canCloudflare() {
        return true;
    }

    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_endchan, null);
    }


    private String checkFileIdentifier(File file, String mime, ProgressListener listener, CancellableTask task) {
        String hash;
        try {
            hash = computeFileMD5(file);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        String identifier = hash + "-" + mime.replace("/", "");
        String url = getUsingUrl() + "checkFileIdentifier.js?identifier=" + identifier;
        String response = "";
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, null, httpClient, listener, task, false);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        if (response.contains("true")) return hash;
        return null;
    }

    private String base64EncodeFile(File file) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Base64OutputStream b64os = new Base64OutputStream(os, Base64.NO_WRAP);
        FileInputStream fis = new FileInputStream(file);
        IOUtils.copyStream(fis, b64os);
        return os.toString();
    }

    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + ".api/" + (model.threadNumber == null ? "newThread" : "replyThread");

        JSONObject jsonPayload = new JSONObject();
        JSONObject jsonParameters = new JSONObject();
        jsonPayload.put("captchaId", lastCaptchaId);
        jsonParameters.put("name", model.name);
        jsonParameters.put("password", model.password);
        jsonParameters.put("subject", model.subject);
        jsonParameters.put("message", model.comment);
        jsonParameters.put("boardUri", model.boardName);
        jsonParameters.put("email", model.sage ? "sage" : model.email);
        if (model.threadNumber != null)
            jsonParameters.put("threadId", model.threadNumber);
        if (model.captchaAnswer != null && model.captchaAnswer.length() > 0)
            jsonParameters.put("captcha", model.captchaAnswer);
        if (model.icon > 0)
            jsonParameters.put("flag", flagsMap.get(model.boardName).get(model.icon - 1));
        if (model.attachments != null && model.attachments.length > 0) {
            JSONArray files = new JSONArray();
            for (int i = 0; i < model.attachments.length; ++i) {
                String name = model.attachments[i].getName();
                String mime = MimeTypes.forExtension(name.substring(name.lastIndexOf('.') + 1), "");
                String md5 = checkFileIdentifier(model.attachments[i], mime, listener, task);
                JSONObject file = new JSONObject();
                file.put("name", name);
                if (md5 != null) {
                    file.put("md5", md5);
                    file.put("mime", mime);
                } else {
                    file.put("content", "data:" + mime + ";base64," + base64EncodeFile(model.attachments[i]));
                }
                file.put("spoiler", false);
                files.put(file);
            }
            jsonParameters.put("spoiler", model.custommark);
            jsonParameters.put("files", files);
        }
        jsonPayload.put("parameters", jsonParameters);
        JSONEntry payload = new JSONEntry(jsonPayload);
        HttpRequestModel request = HttpRequestModel.builder().setPOST(payload).setNoRedirect(true).build();
        String response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, task, true);
        lastCaptchaId = null;
        JSONObject result = new JSONObject(response);
        String status = result.optString("status");
        if ("ok".equals(status)) {
            UrlPageModel urlPageModel = new UrlPageModel();
            urlPageModel.type = UrlPageModel.TYPE_THREADPAGE;
            urlPageModel.chanName = getChanName();
            urlPageModel.boardName = model.boardName;
            urlPageModel.threadNumber = model.threadNumber;
            if (model.threadNumber == null) {
                urlPageModel.threadNumber = Integer.toString(result.optInt("data"));
            } else {
                urlPageModel.postNumber = Integer.toString(result.optInt("data"));
            }
            return buildUrl(urlPageModel);
        } else if (status.contains("error") || status.contains("blank")) {
            String errorMessage = result.optString("data");
            if (errorMessage.length() > 0) {
                throw new Exception(errorMessage);
            }
        }
        throw new Exception("Unknown Error");
    }

    @Override
    public String deletePost(DeletePostModel model, final ProgressListener listener, final CancellableTask task) throws Exception {
        String url = getUsingUrl() + ".api/" + "deleteContent";
        /*
        if (lastCaptchaAnswer == null) {
            throw new SimpleCaptchaException() {
                private static final long serialVersionUID = 1L;
                @Override
                protected Bitmap getNewCaptcha() throws Exception {
                    return AbstractLynxChanModule.this.getNewCaptcha("", "", listener, task).bitmap;
                }
                @Override
                protected void storeResponse(String response) {
                    lastCaptchaAnswer = response;
                }
            };
        }
        */
        JSONObject jsonPayload = new JSONObject();
        JSONObject jsonParameters = new JSONObject();
        jsonPayload.put("captchaId", lastCaptchaId);
        jsonParameters.put("password", model.password);
        if (lastCaptchaAnswer != null && lastCaptchaAnswer.length() > 0)
            jsonParameters.put("captcha", lastCaptchaAnswer);
        jsonParameters.put("deleteMedia", true);
        if (model.onlyFiles) {
            jsonParameters.put("deleteUploads", true);
        }
        JSONArray jsonArray = new JSONArray();
        JSONObject post = new JSONObject();
        post.put("board", model.boardName);
        post.put("thread", model.threadNumber);
        if (!model.postNumber.equals(model.threadNumber)) post.put("post", model.postNumber);
        jsonParameters.put("postings", jsonArray);
        jsonPayload.put("parameters", jsonParameters);
        JSONEntry payload = new JSONEntry(jsonPayload);
        HttpRequestModel request = HttpRequestModel.builder().setPOST(payload).setNoRedirect(true).build();
        String response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, task, true);
        lastCaptchaId = null;
        lastCaptchaAnswer = null;
        JSONObject result = new JSONObject(response);
        if (result.optString("status").equals("error")) {
            String errorMessage = result.optString("data");
            if (errorMessage.length() > 0) {
                throw new Exception(errorMessage);
            }
        }
        return null;
    }

}
