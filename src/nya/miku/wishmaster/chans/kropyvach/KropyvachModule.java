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

package nya.miku.wishmaster.chans.kropyvach;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.Comparator;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class KropyvachModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "kropyva.ch";
    private static final String DEFAULT_DOMAIN = "www.kropyva.ch";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, "kropyva.ch" };
    private static final String[] ATTACHMENT_FORMATS = new String[] { "jpg", "jpeg", "png", "gif", "webm" };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аніме", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Балачки", "", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bugs", "Зауваження", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Кіно", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "f", "Фап", "", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "g", "Відеоігри", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "i", "International", "", false), 
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "k", "Кропиватика", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "l", "Література", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "m", "Музика", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "p", "Політика", "", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "t", "Технології", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "u", "Українська мова", "", false),
    };
    
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    
    public KropyvachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Кропивач";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_uchan, null);
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected String getUsingDomain() {
        String domain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN);
        return TextUtils.isEmpty(domain) ? DEFAULT_DOMAIN : domain;
    }
    
    @Override
    protected String[] getAllDomains() {
        String domain = getUsingDomain();
        for (String d : DOMAINS) if (domain.equals(d)) return DOMAINS;
        String[] domains = new String[DOMAINS.length + 1];
        for (int i=0; i<DOMAINS.length; ++i) domains[i] = DOMAINS[i];
        domains[DOMAINS.length] = domain;
        return domains;
    }

    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        preferenceGroup.addPreference(domainPref);
        super.addPreferencesOnScreen(preferenceGroup);
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.bumpLimit = 250;
        model.attachmentsMaxCount = 4;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        return model;
    }
    
    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        AttachmentModel attachment = super.mapAttachment(object, boardName, isSpoiler);
        if (attachment != null && attachment.thumbnail != null) {
            switch (attachment.type) {
                case AttachmentModel.TYPE_VIDEO:
                case AttachmentModel.TYPE_IMAGE_STATIC:
                case AttachmentModel.TYPE_IMAGE_GIF:
                case AttachmentModel.TYPE_IMAGE_SVG:
                attachment.thumbnail = attachment.thumbnail.substring(0, attachment.thumbnail.lastIndexOf('.')) + ".png";
                break;
            }
        }
        return attachment;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        super.sendPost(model, listener, task);
        return null;
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Видалити";
    }
    
    @Override
    protected String getReportFormValue(DeletePostModel model) {
        return "Поскаржитися";
    }

    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList) throws Exception {
        PostModel[] result = super.getPostsList(boardName, threadNumber, listener, task, oldList);
        Arrays.sort(result, new Comparator<PostModel>() {
            @Override
            public int compare(PostModel p1, PostModel p2) {
                int n1 = Integer.parseInt(p1.number);
                int n2 = Integer.parseInt(p2.number);
                return (n1 == n2) ? 0 : (n1 > n2) ? 1 : -1;
            }
        });
        return result;
    }

    @Override
    protected PostModel mapPostModel(JSONObject object, String boardName) {
        PostModel result = super.mapPostModel(object, boardName);
        result.comment = result.comment.replaceAll("(<a [^>]*href=\"[^#\"]*)(\"[^>]*>&gt;&gt;([0-9]*)</a>)", "$1#$3$2");
        return result;
    }
}
