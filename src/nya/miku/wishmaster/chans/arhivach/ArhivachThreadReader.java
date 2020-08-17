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

package nya.miku.wishmaster.chans.arhivach;

import android.annotation.SuppressLint;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.common.Logger;

/**
 * Created by Kalaver <Kalaver@users.noreply.github.com> on 03.07.2015.
 */
@SuppressLint("SimpleDateFormat")
public class ArhivachThreadReader  implements Closeable {
    private static final String TAG = "ArhivachThreadReader";

    private static final DateFormat CHAN_DATEFORMAT;
    static {
        DateFormatSymbols chanSymbols = new DateFormatSymbols();
        chanSymbols.setShortWeekdays(new String[] { "", "Вск", "Пнд", "Втр", "Срд", "Чтв", "Птн", "Суб" });

        CHAN_DATEFORMAT = new SimpleDateFormat("dd/MM/yy EEE HH:mm:ss", chanSymbols);
        CHAN_DATEFORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }

    private static final Pattern URL_PATTERN =
            Pattern.compile("((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])");
    private static final Pattern ATTACHMENT_PX_SIZE_PATTERN = Pattern.compile("(\\d+)\\s*[x×х]\\s*(\\d+)"); // \u0078 \u00D7 \u0445
    private static final Pattern ATTACHMENT_SIZE_PATTERN =
            Pattern.compile("([\\d.]+)\\s*([кkмm])?[бb]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern IMAGE_WITH_TITLE_PATTERN = Pattern.compile("src=\"([^\"]+)\"(?:.*?title=\"([^\"]*)\")?");

    private static final char[] DATA_START = "class=\"thread_inner\"".toCharArray();
    private static final char[] TAG_CLOSE = ">".toCharArray();

    private static final int FILTER_THREAD_END = 0;
    private static final int FILTER_ATTACHMENT = 1;
    private static final int FILTER_ATTACHMENT_ORIGINAL = 2;
    private static final int FILTER_ATTACHMENT_THUMBNAIL = 3;
    private static final int FILTER_START_COMMENT_BODY = 4;
    private static final int FILTER_START_COMMENT = 5;
    private static final int FILTER_END_COMMENT = 6;

    private static final int FILTER_SAGE = 7;
    private static final int FILTER_NAME = 8;
    private static final int FILTER_TRIP = 9;
    private static final int FILTER_TIME = 10;
    private static final int FILTER_ID_START = 11;
    private static final int FILTER_SUBJECT = 12;
    private static final int FILTER_ID = 13;
    private static final int FILTER_MAIL = 14;
    private static final int FILTER_OP = 15;
    private static final int FILTER_DELETED = 16;
    private static final int FILTER_BADGE = 17;

    public static final char[][] FILTERS_OPEN = {
            "</html>".toCharArray(),

            "<div class=\"post_image_block\"".toCharArray(),
            "<a".toCharArray(),
            "<img".toCharArray(),

            "class=\"post_comment_body\"".toCharArray(),
            "class=\"post_comment\"".toCharArray(),
            "</div>".toCharArray(),

            "class=\"poster_sage\"".toCharArray(),

            "class=\"poster_name\"".toCharArray(),

            "class=\"poster_trip\"".toCharArray(),

            "class=\"post_time\">".toCharArray(),

            "class=\"post_id\"".toCharArray(),

            "<h1 class=\"post_subject\">".toCharArray(),

            "id=\"".toCharArray(),

            "href=\"mailto:".toCharArray(),

            "label-success\">OP".toCharArray(),

            "class=\"post post_deleted\"".toCharArray(),

            "<img hspace=".toCharArray(),
    };

    private static final char[][] FILTERS_CLOSE = {
            null,

            TAG_CLOSE,
            "</a>".toCharArray(),
            TAG_CLOSE,

            TAG_CLOSE,
            TAG_CLOSE,
            null,

            TAG_CLOSE,

            "</span>".toCharArray(),

            "</span>".toCharArray(),

            "</span>".toCharArray(),

            "</span>".toCharArray(),

            "</h1>".toCharArray(),

            "\"".toCharArray(),

            TAG_CLOSE,

            "</span>".toCharArray(),

            TAG_CLOSE,

            TAG_CLOSE,
    };

    private final Reader _in;

    private StringBuilder readBuffer = new StringBuilder();
    private List<ThreadModel> threads;
    private ThreadModel currentThread;
    private List<PostModel> postsBuf;
    private PostModel currentPost;
    private List<AttachmentModel> currentAttachments;


    public ArhivachThreadReader(Reader reader) {
        _in = reader;
    }

    public ArhivachThreadReader(InputStream in) {
        this(new BufferedReader(new InputStreamReader(in)));
    }


    public ThreadModel[] readPage() throws IOException {
        threads = new ArrayList<ThreadModel>();
        initThreadModel();
        initPostModel();
        skipUntilSequence(DATA_START);
        readData();

        return threads.toArray(new ThreadModel[threads.size()]);
    }

    private void readData() throws IOException {
        int filtersCount = FILTERS_OPEN.length;
        int[] pos = new int[filtersCount];
        int[] len = new int[filtersCount];
        for (int i=0; i<filtersCount; ++i) len[i] = FILTERS_OPEN[i].length;

        int curChar;
        while ((curChar = _in.read()) != -1) {
            for (int i=0; i<filtersCount; ++i) {
                if (curChar == FILTERS_OPEN[i][pos[i]]) {
                    ++pos[i];
                    if (pos[i] == len[i]) {
                        handleFilter(i);
                        pos[i] = 0;
                    }
                } else {
                    if (pos[i] != 0) pos[i] = curChar == FILTERS_OPEN[i][0] ? 1 : 0;
                }
            }
        }
        finalizeThread();
    }

    private void initThreadModel() {
        currentThread = new ThreadModel();
        currentThread.postsCount = 0;
        currentThread.attachmentsCount = 0;
        postsBuf = new ArrayList<PostModel>();
    }

    private void initPostModel() {
        currentPost = new PostModel();
        currentPost.number = "unknown";
        currentPost.trip = "";
        currentAttachments = new ArrayList<AttachmentModel>();
    }

    private void finalizeThread() {
        if (postsBuf.size() > 0) {
            currentThread.posts = postsBuf.toArray(new PostModel[postsBuf.size()]);
            currentThread.threadNumber = currentThread.posts[0].number;
            for (PostModel post : currentThread.posts) post.parentThread = currentThread.threadNumber;
            threads.add(currentThread);
            initThreadModel();
        }
    }

    private void finalizePost() {
        if (currentPost.number != null && currentPost.number.length() > 0) {
            ++currentThread.postsCount;
            currentPost.attachments = currentAttachments.toArray(new AttachmentModel[currentAttachments.size()]);
            if (currentPost.name == null) currentPost.name = "";
            if (currentPost.subject == null) currentPost.subject = "";
            if (currentPost.comment == null) currentPost.comment = "";
            if (currentPost.email == null) currentPost.email = "";
            if (currentPost.trip == null) currentPost.trip = "";
            currentPost.comment = CryptoUtils.fixCloudflareEmails(currentPost.comment);
            currentPost.name = StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlTags(currentPost.name));
            currentPost.subject = StringEscapeUtils.unescapeHtml4(CryptoUtils.fixCloudflareEmails(currentPost.subject));
            postsBuf.add(currentPost);
        }
        initPostModel();
    }

    private void handleFilter(int filterIndex) throws IOException {
        switch (filterIndex) {
            case FILTER_THREAD_END:
                finalizeThread();
                break;
            case FILTER_ATTACHMENT:
                parseAttachment();
                break;
            case FILTER_START_COMMENT_BODY:
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                readPost();
                finalizePost();
                break;
            case FILTER_SAGE:
                skipUntilSequence(FILTERS_CLOSE[FILTER_START_COMMENT]);
                currentPost.sage=true;
                break;
            case FILTER_NAME:
                skipUntilSequence(TAG_CLOSE);
                parseName(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_TRIP:
                skipUntilSequence(TAG_CLOSE);
                currentPost.trip = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_TIME:
                parseDate(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_ID_START:
                skipUntilSequence(FILTERS_OPEN[FILTER_ID]);
                currentPost.number=readUntilSequence(FILTERS_CLOSE[FILTER_ID]);
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_SUBJECT:
                currentPost.subject=readUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_MAIL:
                parseEmail(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_OP:
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                currentPost.op=true;
                break;
            case FILTER_DELETED:
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                currentPost.deleted = true;
                break;
            case FILTER_BADGE:
                parseBadgeIcon(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
        }
    }

    protected void parseName(String s) {
        if (s.contains("<img")) {
            parseBadgeIcon(s);
        }
        currentPost.name = s;
    }

    protected void parseEmail(String s) {
        if (s.contains("post_mail")) {
            currentPost.email=s.substring(0,s.indexOf("\""));
        }
    }

    protected void readPost() throws IOException {
        currentPost.comment = readUntilSequence(FILTERS_OPEN[FILTER_END_COMMENT]);
    }

    private void parseAttachment() throws IOException {
        String metaData = readUntilSequence(FILTERS_CLOSE[FILTER_ATTACHMENT]);
        String thumbnail = "";
        String original = "";
        String fileName = null;

        skipUntilSequence(FILTERS_OPEN[FILTER_ATTACHMENT_THUMBNAIL]);
        String attachment = readUntilSequence(FILTERS_CLOSE[FILTER_ATTACHMENT_THUMBNAIL]);
        Matcher matcher = URL_PATTERN.matcher(attachment);
        if (matcher.find()) thumbnail = matcher.group(1);

        skipUntilSequence(FILTERS_OPEN[FILTER_ATTACHMENT_ORIGINAL]);
        attachment = readUntilSequence(FILTERS_CLOSE[FILTER_ATTACHMENT_ORIGINAL]);
        matcher = URL_PATTERN.matcher(attachment);
        if (matcher.find()) {
            original = matcher.group(1);
            fileName = attachment.substring(attachment.lastIndexOf(TAG_CLOSE[0])+1);
        }

        if (original.length() > 0) {
            AttachmentModel model = new AttachmentModel();
            model.size = -1;
            model.width = -1;
            model.height = -1;
            model.path = original;
            model.originalName = StringEscapeUtils.unescapeHtml4(fileName);
            if (thumbnail.length() > 0)
                model.thumbnail = thumbnail;
            else
                model.thumbnail = original;
            String ext = model.path.substring(model.path.lastIndexOf('.') + 1).toLowerCase(Locale.US);
            switch (ext) {
                case "png":
                case "jpg":
                case "jpeg":
                    model.type = AttachmentModel.TYPE_IMAGE_STATIC;
                    break;
                case "gif":
                    model.type = AttachmentModel.TYPE_IMAGE_GIF;
                    break;
                case "webm":
                case "mp4":
                    model.type = AttachmentModel.TYPE_VIDEO;
                    break;
                case "mp3":
                case "ogg":
                    model.type = AttachmentModel.TYPE_AUDIO;
                    break;
                default:
                    model.type = AttachmentModel.TYPE_OTHER_FILE;
                    break;
            }
            matcher = ATTACHMENT_PX_SIZE_PATTERN.matcher(metaData);
            if (matcher.find()) {
                try {
                    int width = Integer.parseInt(matcher.group(1));
                    int height = Integer.parseInt(matcher.group(2));
                    model.width = width;
                    model.height = height;
                } catch (NumberFormatException e) {}
            }
            matcher = ATTACHMENT_SIZE_PATTERN.matcher(metaData);
            if (matcher.find()) {
                try {
                    String digits = matcher.group(1).replace(',', '.');
                    int multiplier = 1;
                    String prefix = matcher.group(2);
                    if (prefix != null) {
                        prefix = prefix.toLowerCase(Locale.US);
                        if (prefix.equals("к") || prefix.equals("k")) multiplier = 1024;
                        else if (prefix.equals("м") || prefix.equals("m")) multiplier = 1024 * 1024;
                    }
                    model.size = Math.round(Float.parseFloat(digits) / 1024 * multiplier);
                } catch (NumberFormatException e) {}
            }
            ++currentThread.attachmentsCount;
            currentAttachments.add(model);
        }
    }

    private void parseBadgeIcon(String s) {
        Matcher matcher = IMAGE_WITH_TITLE_PATTERN.matcher(s);
        if (matcher.find()) {
            BadgeIconModel icon = new BadgeIconModel();
            icon.source = matcher.group(1);
            icon.description = matcher.group(2);
            int size = currentPost.icons == null ? 0 : currentPost.icons.length;
            BadgeIconModel[] icons = new BadgeIconModel[size + 1];
            for (int i = 0; i < size; i++) icons[i] = currentPost.icons[i];
            icons[size] = icon;
            currentPost.icons = icons;
        }
    }

    protected void parseDate(String date) {
        if (date.length() > 0) {
            try {
                currentPost.timestamp = CHAN_DATEFORMAT.parse(date).getTime();
            } catch (Exception e) {
                Logger.e(TAG, "cannot parse date; make sure you choose the right DateFormat for this chan", e);
            }
        }
    }

    private void skipUntilSequence(char[] sequence) throws IOException {
        int len = sequence.length;
        if (len == 0) return;
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            if (curChar == sequence[pos]) {
                ++pos;
                if (pos == len) break;
            } else {
                if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
            }
        }
    }

    private String readUntilSequence(char[] sequence) throws IOException {
        int len = sequence.length;
        if (len == 0) return "";
        readBuffer.setLength(0);
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            readBuffer.append((char) curChar);
            if (curChar == sequence[pos]) {
                ++pos;
                if (pos == len) break;
            } else {
                if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
            }
        }
        int buflen = readBuffer.length();
        if (buflen >= len) {
            readBuffer.setLength(buflen - len);
            return readBuffer.toString();
        } else {
            return "";
        }
    }

    @Override
    public void close() throws IOException {
        _in.close();
    }
}
