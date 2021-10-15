package nya.miku.wishmaster.http.stormwall;

import android.net.Uri;

import java.net.URI;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.api.HttpChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.MultipurposeException;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;

public class StormwallExceptionAntiDDOS extends MultipurposeException {
    private static final long serialVersionUID = 1L;

    private static final String SERVICE_NAME = "Stormwall";
    private static final String TAG = "StormwallException";

    private static final Pattern PATTERN_INPUT = Pattern.compile("= get_jhash\\(([0-9]+)\\)");
    private static final Pattern PATTERN_COOKIE_VALUE2 = Pattern.compile("_HASH__=([^;]+);");

    protected static final String STORMWALL_COOKIE_NAME1 = "_JHASH__";
    protected static final String STORMWALL_COOKIE_NAME2 = "_HASH__";

    private URI url;
    private String chanName;
    private int input;
    
    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    public static StormwallExceptionAntiDDOS newInstance(String url, String html, String chanName) {
        try {
            StormwallExceptionAntiDDOS res = new StormwallExceptionAntiDDOS();
            res.url = new URI(url);
            res.chanName = chanName;
            Matcher m = PATTERN_INPUT.matcher(html);
            if (m.find()) {
                res.input = Integer.parseInt(m.group(1));
            } else {
                return null;
            }
            return res;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String handle(CancellableTask task) {
        try {
            HttpChanModule chan = (HttpChanModule) MainApplication.getInstance().getChanModule(chanName);
            HttpClient http = chan.getHttpClient();
            String answer = StormwallTokenGenerator.encrypt(input);
            Logger.d(TAG, "Stormwall: input=" + input + "; answer=" + answer);
            BasicClientCookie c;
            c = new BasicClientCookie(STORMWALL_COOKIE_NAME1, answer);
            c.setDomain(url.getHost());
            chan.saveCookie(c);
            Matcher matcher = Pattern.compile("([!'()*])")
                    .matcher(Uri.encode(MainApplication.getInstance().settings.getUserAgentString()));
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(sb, "%" + Integer.toHexString((int) matcher.group(1).charAt(0)));
            }
            matcher.appendTail(sb);
            c = new BasicClientCookie("_JUA__", sb.toString());
            c.setDomain(url.getHost());
            chan.saveCookie(c);
            LockSupport.parkNanos(1000000000);
            HttpResponseModel model = HttpStreamer.getInstance().getFromUrl(url.toString(), null, http, null, null);
            for (Header h: model.headers) {
                Logger.d(TAG, "header: "+h.getName() + "=" + h.getValue());
                if (h.getName().equalsIgnoreCase(STORMWALL_COOKIE_NAME2)) {
                    Matcher m = PATTERN_COOKIE_VALUE2.matcher(h.getValue());
                    if (m.find()) {
                        c = new BasicClientCookie(STORMWALL_COOKIE_NAME2, m.group(1));
                        c.setDomain(url.getHost());
                        chan.saveCookie(c);
                        break;
                    }
                }
            }
            model.release();
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
