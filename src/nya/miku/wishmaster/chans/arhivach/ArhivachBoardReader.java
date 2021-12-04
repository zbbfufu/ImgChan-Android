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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.api.util.RegexUtils;

/**
 * Created by Kalaver <Kalaver@users.noreply.github.com> on 03.07.2015.
 */

public class ArhivachBoardReader implements Closeable {
    
    private static final Pattern DATE_PATTERN = Pattern.compile("(?:(\\d+)\\s+)?(\\S+)\\s+(?:(\\d+):(\\d+)|(\\d{4}))");
    private static final String[] MONTH_STRINGS = new String[] { "января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря" };
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("GMT");
    
    private static final Pattern URL_PATTERN =
            Pattern.compile("((?:https?:)?/[-a-zA-Z0-9+&@#/%?=~_|!:,.;]+[-a-zA-Z0-9+&@#/%=~_|])");

    private static final char[] DATA_START = "id=\"thread_row_".toCharArray();
    
    private static final int FILTER_THREAD_END = 0;
    private static final int FILTER_DATE = 1;
    private static final int FILTER_ATTACHMENT = 2;
    private static final int FILTER_ATTACHMENT_ORIGINAL = 3;
    private static final int FILTER_ATTACHMENT_THUMBNAIL = 4;
    private static final int FILTER_START_COMMENT = 5;
    private static final int FILTER_THREAD_LINK = 6;
    private static final int FILTER_END_COMMENT = 7;
    private static final int FILTER_OMITTEDPOSTS = 8;
    private static final int FILTER_THREAD_SAVED = 9;
    private static final int FILTER_THREAD_TAGS = 10;
    
    
    public static final char[][] FILTERS_OPEN = {
            "</tr>".toCharArray(),
            "class=\"thread_date\">".toCharArray(),

            "<div class=\"post_image_block\"".toCharArray(),
            "<a".toCharArray(),
            "<img".toCharArray(),

            "<div class=\"thread_text\"".toCharArray(),

            "<a".toCharArray(),

            "</a>".toCharArray(),

            "class=\"thread_posts_count\"".toCharArray(),

            "label-thread-saved\">".toCharArray(),

            "class=\"thread_tags\"".toCharArray(),


    };
    
    private static final char[][] FILTERS_CLOSE = {
            null,
            "</td>".toCharArray(),

            ">".toCharArray(),
            ">".toCharArray(),
            ">".toCharArray(),

            ">".toCharArray(),

            ">".toCharArray(),

            "</a>".toCharArray(),

            "</span>".toCharArray(),

            "</span>".toCharArray(),

            "</div>".toCharArray(),
    };
    
    private final Reader _in;
    
    private final StringBuilder readBuffer = new StringBuilder();
    private List<ThreadModel> threads;
    private ThreadModel currentThread;
    private List<PostModel> postsBuf;
    private PostModel currentPost;
    private List<AttachmentModel> currentAttachments;
    
    
    public ArhivachBoardReader(Reader reader) {
        _in = reader;
    }
    
    public ArhivachBoardReader(InputStream in) {
        this(new BufferedReader(new InputStreamReader(in)));
    }
    
    
    public ThreadModel[] readPage() throws IOException {
        threads = new ArrayList<>();
        initThreadModel();
        initPostModel();
        skipUntilSequence(DATA_START);
        readData();

        return threads.toArray(new ThreadModel[0]);
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
        postsBuf = new ArrayList<>();
    }
    
    private void initPostModel() {
        currentPost = new PostModel();
        currentPost.number = "unknown";
        currentPost.trip = "";
        currentAttachments = new ArrayList<>();
    }
    
    private void finalizeThread() {
        if (postsBuf.size() > 0) {
            currentThread.posts = postsBuf.toArray(new PostModel[0]);
            currentThread.threadNumber = currentThread.posts[0].number;
            for (PostModel post : currentThread.posts) post.parentThread = currentThread.threadNumber;
            threads.add(currentThread);
            initThreadModel();
        }
    }
    
    private void finalizePost() {
        if (currentPost.number != null && currentPost.number.length() > 0) {
            currentPost.attachments = currentAttachments.toArray(new AttachmentModel[0]);
            if (currentPost.name == null) currentPost.name = "";
            if (currentPost.subject == null) currentPost.subject = "";
            if (currentPost.comment == null) currentPost.comment = "";
            if (currentPost.email == null) currentPost.email = "";
            if (currentPost.trip == null) currentPost.trip = "";
            currentPost.comment = CryptoUtils.fixCloudflareEmails(currentPost.comment);
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
            case FILTER_DATE:
                String date = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                parseDate(date);
                finalizePost();
                break;
            case FILTER_ATTACHMENT:
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                parseAttachment();
                break;
            case FILTER_START_COMMENT:
                readPost();
                break;
            case FILTER_OMITTEDPOSTS:
                parseOmittedString(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_THREAD_SAVED:
                String data = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                if (data.contains("Сохранен")) {
                    currentThread.isClosed=true;
                }
                break;
            case FILTER_THREAD_TAGS:
                parseTags(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
        }
    }
    
    protected void parseTags(String s) {
        Matcher matcher = Pattern.compile("<a[^>]*>([^<]*)</a>",Pattern.MULTILINE).matcher(s);
        StringBuilder tagsBuilder = new StringBuilder();
        while (matcher.find()) {
            tagsBuilder.append('[').append(matcher.group(1)).append(']');
        }
        currentPost.name = tagsBuilder.toString();
    }
    
    protected void readPost() throws IOException {
        skipUntilSequence(FILTERS_OPEN[FILTER_THREAD_LINK]);
        String link = readUntilSequence(FILTERS_CLOSE[FILTER_THREAD_LINK]);

        Matcher matcher = Pattern.compile("(href *= *\"(.*)\")").matcher(link);

        String url="";
        if (matcher.find()) url = matcher.group(1);

        matcher = Pattern.compile("(\\d+)").matcher(url);
        if (matcher.find()) currentPost.number = matcher.group(0);
        String commentData = readUntilSequence(FILTERS_CLOSE[FILTER_END_COMMENT]);
        matcher = Pattern.compile("<b>(.*)</b>\\s*&mdash;\\s*").matcher(commentData);
        if (matcher.find()) {
            currentPost.subject = StringEscapeUtils.unescapeHtml4(matcher.group(1));
            currentPost.comment = commentData.substring(matcher.group(0).length());
        } else {
            currentPost.comment = commentData;
        }
    }

    private void parseOmittedString(String omitted) {
        Matcher matcher = Pattern.compile("(\\d+)").matcher(omitted);
        if (matcher.find()) {
            try {
                currentThread.postsCount = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) { /* ignored */ }
        }
    }

    private void parseAttachment() throws IOException {
        skipUntilSequence(FILTERS_OPEN[FILTER_ATTACHMENT_ORIGINAL]);
        String attachment = readUntilSequence(FILTERS_CLOSE[FILTER_ATTACHMENT_ORIGINAL]);
        
        String thumbnail = "";
        String original = "";
        
        Matcher matcher = URL_PATTERN.matcher(attachment);
        if (matcher.find()) original = matcher.group(1);
        skipUntilSequence(FILTERS_OPEN[FILTER_ATTACHMENT_THUMBNAIL]);
        attachment = readUntilSequence(FILTERS_CLOSE[FILTER_ATTACHMENT_THUMBNAIL]);
        matcher = URL_PATTERN.matcher(attachment);
        if (matcher.find()) thumbnail = matcher.group(1);
        
        if (original.length() > 0) {
            AttachmentModel model = new AttachmentModel();
            model.size = -1;
            model.width = -1;
            model.height = -1;
            model.path = original;
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
            }
            currentAttachments.add(model);
        }
    }
    
    private void parseDate(String date) {
        Matcher matcher = DATE_PATTERN.matcher(RegexUtils.removeHtmlTags(date));
        if (matcher.matches()) {
            int day, month, year, hour, minute;
            Calendar calendar = Calendar.getInstance(TIME_ZONE);
            
            String dayString = matcher.group(1);
            String monthString = matcher.group(2);
            if (dayString == null) {
                if (monthString.equalsIgnoreCase("вчера")) {
                    calendar.add(Calendar.DAY_OF_MONTH, -1);
                }
                day = calendar.get(Calendar.DAY_OF_MONTH);
                month = calendar.get(Calendar.MONTH);
            } else {
                day = Integer.parseInt(dayString);
                month = MONTH_STRINGS.length - 1;
                while (!monthString.equalsIgnoreCase(MONTH_STRINGS[month])
                        && (month > 0)) {
                    --month;
                }
            }
            
            String yearString = matcher.group(5);
            if (yearString == null) {
                hour = Integer.parseInt(matcher.group(3));
                minute = Integer.parseInt(matcher.group(4));
                year = calendar.get(Calendar.YEAR);
            } else {
                hour = 0;
                minute = 0;
                year = Integer.parseInt(yearString);
            }
            
            calendar.set(Calendar.DAY_OF_MONTH, day);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            currentPost.timestamp = calendar.getTimeInMillis();
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
