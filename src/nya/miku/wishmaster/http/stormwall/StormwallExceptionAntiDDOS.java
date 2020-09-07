package nya.miku.wishmaster.http.stormwall;

import android.app.Activity;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.api.HttpChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.MultipurposeException;

import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;

public class StormwallExceptionAntiDDOS extends MultipurposeException {
    private static final long serialVersionUID = 1L;

    private static final String SERVICE_NAME = "Stormwall";
    private static final String TAG = "StormwallException";

    private static final Pattern PATTERN_CE = Pattern.compile(" cE = \"([^\"]+)");
    private static final Pattern PATTERN_CK = Pattern.compile(" cK = (\\d+)");

    protected static final String STORMWALL_COOKIE_NAME = "swp_token";

    private URI url;
    private String chanName;
    private String cE;
    private int cK;
    
    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    public static StormwallExceptionAntiDDOS newInstance(String url, String html, String chanName) {
        try {
            StormwallExceptionAntiDDOS res = new StormwallExceptionAntiDDOS();
            res.url = new URI(url);
            res.chanName = chanName;
            Matcher m;
            m = PATTERN_CE.matcher(html);
            if (m.find()) {
                res.cE = m.group(1);
            } else {
                return null;
            }
            m = PATTERN_CK.matcher(html);
            if (m.find()) {
                res.cK = Integer.parseInt(m.group(1));
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
            BasicClientCookie c = new BasicClientCookie(STORMWALL_COOKIE_NAME, StormwallTokenGenerator.encrypt(cK, cE));
            c.setDomain(url.getHost());
            chan.saveCookie(c);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
