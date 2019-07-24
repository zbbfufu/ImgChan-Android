package nya.miku.wishmaster.chans.kohlchan;

import android.graphics.Bitmap;

import nya.miku.wishmaster.api.AbstractLynxChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.SimpleCaptchaException;

public class KohlchanCaptchaException extends SimpleCaptchaException {
    private static final long serialVersionUID = 1L;

    private String captchaId = "";

    protected Bitmap getNewCaptcha() throws Exception {
        AbstractLynxChanModule.ExtendedCaptchaModel captcha =
                ((KohlchanModule) MainApplication.getInstance()
                        .getChanModule(KohlchanModule.CHAN_NAME))
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
        ((KohlchanModule) MainApplication.getInstance().getChanModule(KohlchanModule.CHAN_NAME))
                .putCaptcha(captchaId, response);
    }
}
