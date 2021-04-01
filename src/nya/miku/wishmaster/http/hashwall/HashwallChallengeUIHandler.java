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

import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.HttpChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.interactive.InteractiveException;


public class HashwallChallengeUIHandler {
    private HashwallChallengeUIHandler() {}
        
    /**
     * Обработать исключение-запрос проверки Hashwall.
     * Вызывать из фонового потока
     * @param e исключение {@link HashwallExceptionAntiDDOS}
     * @param chan модуль чана
     * @param activity активность, в которой будет создан диалог (в случае проверки с капчей),
     * или в контексте которой будет создан WebView для Anti DDOS проверки с javascript.
     * Используется как доступ к UI потоку ({@link Activity#runOnUiThread(Runnable)})
     * @param task отменяемая задача
     * @param callback интерфейс {@link InteractiveException.Callback}
     */
    static void handleHashwall(final HashwallExceptionAntiDDOS e, final HttpChanModule chan, final Activity activity, final CancellableTask task,
                                final InteractiveException.Callback callback) {
        if (task.isCancelled()) return;
        String result = HashwallChallengeSolver.bruteForceHash(e.getChallenge(), e.getPassPhrase(), e.getUpperBound());
        if (result == null && !task.isCancelled()) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    callback.onError(activity.getString(R.string.error_cloudflare_antiddos));
                }
            });
        }
        Cookie hwCookie = HashwallChallengeChecker.getInstance().checkSolution(e,
                (ExtendedHttpClient) chan.getHttpClient(), task, result);
        if (hwCookie != null) {
            BasicClientCookie c = new BasicClientCookie(hwCookie.getName(), hwCookie.getValue());
            c.setDomain(hwCookie.getDomain());
            c.setComment("Hashwall");
            chan.saveCookie(c);
            if (!task.isCancelled()) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess();
                    }
                });
            }
        } else if (!task.isCancelled()) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    callback.onError(activity.getString(R.string.error_cloudflare_antiddos));
                }
            });
        }
    }
}
