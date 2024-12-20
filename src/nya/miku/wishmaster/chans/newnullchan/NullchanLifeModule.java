package nya.miku.wishmaster.chans.newnullchan;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;

import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.message.BasicHeader;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.http.JSONEntry;
import nya.miku.wishmaster.http.interactive.SimpleCaptchaException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class NullchanLifeModule extends NewNullchanModule {

    static final String CHAN_NAME = "0chan.life";
    private static final String DEFAULT_DOMAIN = "0chan.life";
    private static final String ONION_DOMAIN = "nullchanxrt7ecw7sifwei466qli3sku725tusz22w22opkfcxksinyd.onion";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, ONION_DOMAIN };

    private static final String PREF_KEY_DOMAIN = "domain";
    private static final String PREF_KEY_USE_ONION = "PREF_KEY_USE_ONION";

    public NullchanLifeModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }

    @Override
    public String getChanName() {
        return CHAN_NAME;
    }

    @Override
    public String getDisplayingName() {
        return "Øчан (0chan.life)";
    }

    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_0chan, null);
    }

    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        CheckBoxPreference httpsPref = addHttpsPreference(preferenceGroup, true);
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setSummary(R.string.pref_domain);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        preferenceGroup.addPreference(domainPref);
        CheckBoxPreference onionPref = new LazyPreferences.CheckBoxPreference(context);
        onionPref.setTitle(R.string.pref_use_onion);
        onionPref.setSummary(R.string.pref_use_onion_summary);
        onionPref.setKey(getSharedKey(PREF_KEY_USE_ONION));
        onionPref.setDefaultValue(false);
        onionPref.setDisableDependentsState(true);
        preferenceGroup.addPreference(onionPref);
        domainPref.setDependency(getSharedKey(PREF_KEY_USE_ONION));
        httpsPref.setDependency(getSharedKey(PREF_KEY_USE_ONION));
        addProxyPreferences(preferenceGroup);
        addClearCookiesPreference(preferenceGroup);
    }

    @Override
    protected String getUsingDomain() {
        String prefDomain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), "");
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false) ? ONION_DOMAIN :
                prefDomain.length() == 0 ? DEFAULT_DOMAIN : prefDomain;
    }

    @Override
    protected String[] getAllDomains() {
        String domain = getUsingDomain();
        for (String d : DOMAINS) if (domain.equals(d)) return DOMAINS;
        String[] domains = new String[DOMAINS.length + 1];
        System.arraycopy(DOMAINS, 0, domains, 0, DOMAINS.length);
        domains[DOMAINS.length] = domain;
        return domains;
    }

    @Override
    protected boolean useHttps() {
        return !preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false) && useHttps(true);
    }

    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        updateSession(listener, task);

        String url, parent;
        String comment = model.comment;

        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = getChanName();
        urlModel.boardName = model.boardName;
        if (model.threadNumber == null) {
            urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
            urlModel.boardPage = UrlPageModel.DEFAULT_FIRST_PAGE;
        } else {
            urlModel.type = UrlPageModel.TYPE_THREADPAGE;
            urlModel.threadNumber = model.threadNumber;
        }
        String referer = buildUrl(urlModel);

        if (model.threadNumber != null) {
            Pattern referencePattern = Pattern.compile("^\\s*>>(\\d+)");
            Matcher matcher = referencePattern.matcher(comment);
            if (matcher.find()) {
                parent = matcher.group(1);
                JSONObject post = getPost(parent, listener, task);
                if (post != null && model.threadNumber.equals(post.optString("threadId"))) {
                    comment = matcher.replaceFirst("");
                } else {
                    parent = getOpPostID(model, listener, task);
                }
            } else {
                parent = getOpPostID(model, listener, task);
            }
            url = getUsingUrl() + "api/thread/reply?parent=" + parent + "&session=" + sessionId;
        } else {
            url = getUsingUrl() + "api/thread/create?board=" + model.boardName + "&session=" + sessionId;
        }
        JSONObject jsonPayload = new JSONObject();
        jsonPayload.put("board", JSONObject.NULL);
        jsonPayload.put("thread", JSONObject.NULL);
        jsonPayload.put("parent", JSONObject.NULL);
        jsonPayload.put("sage", model.sage);
        jsonPayload.put("message", comment);
        JSONArray images = new JSONArray();
        if (model.attachments != null && model.attachments.length > 0) {
            for (int i=0; i<model.attachments.length; ++i) {
                images.put(uploadFile(model.attachments[i], listener, task));
            }
        }
        jsonPayload.put("images", images);

        String captchaId = null;
        try {
            captchaId = captchas.keySet().iterator().next();
        } catch (NoSuchElementException e) {
        }
        captchaId = validateCaptcha(captchaId, listener, task);
        jsonPayload.put("captcha", captchaId != null ? captchaId : JSONObject.NULL);
        JSONEntry payload = new JSONEntry(jsonPayload);

        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, referer) };
        HttpRequestModel request = HttpRequestModel.builder().setPOST(payload).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        String response;
        JSONObject result = null;
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, task, true);
        } catch (HttpWrongStatusCodeException e1) {
            try {
                result = new JSONObject(e1.getHtmlString());
            } catch (JSONException e2) {
            }
            if (result != null) {
                int errorCode = result.optInt("error", 0);
                if (errorCode == 403) {
                    String require = result.getJSONObject("details").optString("require");
                    if (require.equals("captcha")) {
                        throw new NullchanLifeCaptchaException();
                    } else throw new Exception(result.optString("message"));
                }
            }
            checkCloudflareError(e1, url);
            throw e1;
        }
        result = new JSONObject(response);
        if (!result.optBoolean("ok", false)) {
            JSONArray errors = result.optJSONArray("errors");
            if (errors != null && errors.length() > 0) {
                String errorMessage = errors.optString(0, "");
                if (errorMessage.length() > 0) throw new Exception(errorMessage);
            }
            throw new Exception(response);
        }
        JSONObject post = result.getJSONObject("post");
        urlModel = new UrlPageModel();
        urlModel.type = UrlPageModel.TYPE_THREADPAGE;
        urlModel.boardName = post.optString("boardDir", model.boardName);
        urlModel.chanName = getChanName();
        urlModel.threadNumber = post.optString("threadId", model.threadNumber);
        urlModel.postNumber = post.optString("id", null);
        return this.buildUrl(urlModel);
    }

    private class NullchanLifeCaptchaException extends SimpleCaptchaException {
        private static final long serialVersionUID = 1L;

        public String captchaId = "";

        protected Bitmap getNewCaptcha() throws Exception {
            ExtendedCaptchaModel captcha = NullchanLifeModule.this
                    .getNewCaptcha(null, null, null, new CancellableTask() {
                            @Override
                            public void cancel() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                    });
            captchaId = captcha.captchaID;
            return captcha.bitmap;
        }

        protected void storeResponse(String response) {
            putCaptcha(captchaId, response);
        }
    }
}
