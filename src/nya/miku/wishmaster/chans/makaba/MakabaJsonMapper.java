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

package nya.miku.wishmaster.chans.makaba;

import static nya.miku.wishmaster.chans.makaba.MakabaConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

import org.apache.commons.lang3.StringEscapeUtils;

import android.content.res.Resources;

/**
 * Методы преобразования JSON-объектов с 2ch.hk в универсальные модели
 * @author miku-nyan
 *
 */
public class MakabaJsonMapper {
    private static final Pattern ICON_PATTERN = Pattern.compile("<img.+?src=\"(.+?)\".+?(?:title=\"(.+?)\")?.*?/>");
    private static final Pattern HASH_LINK_PATTERN_1 = Pattern.compile("<a[^>]+href=\"(.+?/catalog.html)\"[^>]+title=\"([a-z]+)\"[^>]*>");
    private static final Pattern HASH_LINK_PATTERN_2 = Pattern.compile("<a[^>]+title=\"([a-z]+)\"[^>]+href=\"(.+?/catalog.html)\"[^>]*>");

    static BoardModel defaultBoardModel(String boardName, Resources resources) {
        BoardModel model = new BoardModel();
        model.chan = CHAN_NAME;
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "GMT+3";
        model.readonlyBoard = false;
        model.allowDeletePosts = false;
        model.allowDeleteFiles = false;
        model.allowReport = BoardModel.REPORT_WITH_COMMENT;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = true;
        model.allowRandomHash = true;
        model.searchAllowed = true;
        model.catalogAllowed = true;
        model.catalogTypeDescriptions = new String[] {
                resources.getString(R.string.makaba_catalog_standart),
                //resources.getString(R.string.makaba_catalog_last_reply),
                resources.getString(R.string.makaba_catalog_num),
                //resources.getString(R.string.makaba_catalog_image_size)
        };
        model.firstPage = 0;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        model.markType = BoardModel.MARK_BBCODE;

        model.boardName = boardName;
        model.boardDescription = boardName;
        model.boardCategory = "";

        model.defaultUserName = "Аноним";
        model.bumpLimit = 500;
        model.lastPage = BoardModel.LAST_PAGE_UNDEFINED;

        model.nsfw = !SFW_BOARDS.contains(model.boardName);
        model.requiredFileForNewThread = !NO_IMAGES_BOARDS.contains(model.boardName);
        model.attachmentsMaxCount = !NO_IMAGES_BOARDS.contains(model.boardName) ? 4 : 0;
        model.allowSubjects = !NO_SUBJECTS_BOARDS.contains(model.boardName);
        model.allowNames = !NO_USERNAMES_BOARDS.contains(model.boardName);

        return model;
    }

    static BoardModel mapBoardModel(JSONObject source, boolean fromBoardsList, Resources resources) throws JSONException {
        if (!fromBoardsList) source = source.getJSONObject("board");

        BoardModel model = defaultBoardModel(source.getString("id"), resources);
        model.boardDescription = source.getString("name");
        model.boardCategory = source.optString("category");

        model.bumpLimit = source.optInt("bump_limit", model.bumpLimit);
        model.allowNames = source.optBoolean("enable_names", model.allowNames);
        model.lastPage = source.optInt("max_pages", model.lastPage);
        model.defaultUserName = source.optString("default_name", model.defaultUserName);
        try {
            JSONArray iconsArray = source.getJSONArray("icons");
            if (iconsArray.length() > 0) {
                String[] icons = new String[iconsArray.length() + 1];
                icons[0] = resources.getString(R.string.makaba_no_icon);
                for (int i=0; i<iconsArray.length(); ++i) {
                    icons[iconsArray.getJSONObject(i).getInt("num")] = iconsArray.getJSONObject(i).getString("name");
                }
                for (String icon : icons) {
                    if (icon == null) throw new Exception();
                }
                model.allowIcons = true;
                model.iconDescriptions = icons;
            }
        } catch (Exception e) { /* щито поделать, десу, получить список иконок не удалось, или их просто нет */ }
        return model;
    }

    static ThreadModel mapThreadModel(JSONObject source, String boardName) throws JSONException {
        ThreadModel model = new ThreadModel();
        model.threadNumber = source.optString("thread_num");
        model.postsCount = source.optInt("posts_count");
        model.attachmentsCount = source.optInt("files_count");
        JSONArray postsArray = source.getJSONArray("posts");
        model.postsCount += postsArray.length();
        model.posts = new PostModel[postsArray.length()];
        for (int i=0; i<postsArray.length(); ++i) {
            model.posts[i] = mapPostModel(postsArray.getJSONObject(i), boardName);
        }
        model.isSticky = postsArray.getJSONObject(0).optInt("sticky", 0) != 0;
        model.isClosed = postsArray.getJSONObject(0).optInt("closed", 0) != 0;
        model.isCyclical = postsArray.getJSONObject(0).optInt("endless", 0) != 0;
        return model;
    }

    static PostModel mapPostModel(JSONObject source, String boardName) throws JSONException {
        PostModel model = new PostModel();
        model.number = source.optString("num");
        model.name = StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlSpanTags(source.optString("name", "")));
        model.subject = StringEscapeUtils.unescapeHtml4(source.optString("subject", ""));
        model.comment = source.optString("comment", "");
        model.email = source.optString("email", "");
        if (model.email.startsWith("mailto:")) model.email = model.email.substring(7);
        model.trip = source.optString("trip", "");
        if (model.trip != null) {
            switch (model.trip) {
                case "!!%adm%!!":
                    model.trip = "## Abu ##";
                    break;
                case "!!%mod%!!":
                    model.trip = "## Mod ##";
                    break;
                case "!!%Inquisitor%!!":
                    model.trip = "## Applejack ##";
                    break;
                case "!!%coder%!!":
                    model.trip = "## Кодер ##";
                    break;
            }
        }
        model.icons = parseIcons(source.optString("icon"));
        model.op = source.optInt("op", 0) == 1;
        model.sage = model.email.toLowerCase(Locale.US).equals("sage") || model.name.contains("ID:\u00A0Heaven");
        model.timestamp = source.getLong("timestamp") * 1000L;
        model.parentThread = source.optString("parent", model.number);
        if (model.parentThread.equals("0")) model.parentThread = model.number;

        model.comment = model.comment.replace("\\r\\n", "").replace("\\t", "  ");
        if (model.number.equals(model.parentThread)) {
            String tag = source.optString("tags", "");
            if (tag.length() > 0) model.subject += " /" + tag + "/";
            model.comment = model.comment
                    .replaceAll("<style.*?>.*?</style>", "")
                    .replaceAll("<script.*?>.*?</script>", "");
            model.comment = RegexUtils.replaceAll(model.comment, HASH_LINK_PATTERN_1, "<a href=\"$1"+HASHTAG_PREFIX+"$2\">");
            model.comment = RegexUtils.replaceAll(model.comment, HASH_LINK_PATTERN_2, "<a href=\"$2"+HASHTAG_PREFIX+"$1\">");
        }

        JSONArray filesArray = source.optJSONArray("files");
        if (filesArray != null && filesArray.length() > 0) {
            model.attachments = new AttachmentModel[filesArray.length()];
            for (int i=0; i<filesArray.length(); ++i) {
                model.attachments[i] = mapAttachmentModel(filesArray.getJSONObject(i), boardName);
            }
        } else model.attachments = null;

        switch (source.optInt("banned", 0)) {
            case 1:
                model.comment = model.comment + "<br/><em><font color=\"red\">(Автор этого поста был забанен. Помянем.)</font></em>";
                break;
            case 2:
                model.comment = model.comment + "<br/><em><font color=\"red\">(Автор этого поста был предупрежден.)</font></em>";
                break;
        }
        if (NO_SUBJECTS_BOARDS.contains(boardName)) model.subject = "";
        return model;
    }

    static AttachmentModel mapAttachmentModel(JSONObject source, String boardName) throws JSONException {
        AttachmentModel model = new AttachmentModel();
        try {
            model.size = source.getInt("size");
            model.width = source.getInt("width");
            model.height = source.getInt("height");
            model.thumbnail = fixAttachmentPath(source.getString("thumbnail"), boardName);
            model.path = fixAttachmentPath(source.getString("path"), boardName);
            String originalName = source.optString("fullname");
            if (originalName.length() > 0) model.originalName = originalName;
            model.type = AttachmentModel.TYPE_IMAGE_STATIC;
            String pathLower = model.path.toLowerCase(Locale.US);
            if (pathLower.endsWith(".gif")) model.type = AttachmentModel.TYPE_IMAGE_GIF;
            else if (pathLower.endsWith(".webm") || pathLower.endsWith(".mp4")) model.type = AttachmentModel.TYPE_VIDEO;
        } catch (Exception e) {
            if (source.has("path")) {
                model.type = AttachmentModel.TYPE_OTHER_FILE;
                model.path = fixAttachmentPath(source.getString("path"), boardName);
            } else {
                model.type = AttachmentModel.TYPE_OTHER_NOTFILE;
            }
        }
        return model;
    }

    static BadgeIconModel[] parseIcons(String html) {
        if (html == null || html.length() == 0) return null;
        Matcher m = ICON_PATTERN.matcher(html);
        List<BadgeIconModel> list = new ArrayList<>();
        while (m.find()) {
            BadgeIconModel icon = new BadgeIconModel();
            icon.source = m.group(1);
            icon.description = m.group(2);
            list.add(icon);
        }
        return list.toArray(new BadgeIconModel[0]);
    }

    private static String fixAttachmentPath(String url, String boardName) {
        if (url.startsWith("://")) return "http" + url;
        if (url.startsWith("/") || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "/".concat(boardName).concat("/").concat(url);
    }
}
