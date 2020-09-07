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

package nya.miku.wishmaster.chans.cirno;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;

public class CirnoArchiveBoards {
    private static final String[] ATTACHMENT_FILTERS = new String[] { "jpg", "jpeg", "png", "gif" };

    private static final List<String> POSTING_BOARDS = Arrays.asList("dev", "abe");

    private static final List<BoardModel> BOARDS_LIST = new ArrayList<>();
    private static final Map<String, BoardModel> BOARDS_MAP = new HashMap<>();
    private static final SimpleBoardModel[] BOARDS_SIMPLE_ARRAY;
    static {
        addBoard("d", "Работа сайта", "Обсуждения", "Мод-тян", false);
        addBoard("b", "Бред", "Общее", "Сырно", true);
        addBoard("hr", "Высокое разрешение", "Общее", "Аноним", false);
        addBoard("tran", "Иностранные языки", "Общее", "Е. Д. Поливанов", false);
        addBoard("tv", "Кино и ТВ", "Общее", "К. С. Станиславский", false);
        addBoard("l", "Литература", "Общее", "Ф. М. Достоевский", false);
        addBoard("bro", "My Little Pony", "Общее", "Эпплджек", false);
        addBoard("m", "Макросы/копипаста", "Общее", "Копипаста-гей", false);
        addBoard("mu", "Музыка", "Общее", "Виктор Цой", false);
        addBoard("sci", "Наука", "Общее", "Гриша Перельман", false);
        addBoard("mi", "Оружие", "Общее", "Й. Швейк", false);
        addBoard("x", "Паранормальные явления", "Общее", "Эмма Ай", false);
        addBoard("r", "Просьбы", "Общее", "Аноним", false);
        addBoard("o", "Рисование", "Общее", "Аноним", false);
        addBoard("ph", "Фото", "Общее", "Аноним", false);
        addBoard("s", "Электроника и ПО", "Общее", "Чии", false);
        addBoard("vg", "Видеоигры", "Игры", "Марио", false);
        addBoard("au", "Автомобили", "Транспорт", "Джереми Кларксон", false);
        addBoard("tr", "Транспорт", "Транспорт", "Аноним", false);
        addBoard("a", "Аниме и манга", "Японская культура", "Мокона", false);
        addBoard("aa", "Аниме-арт", "Японская культура", "Ракка", false);
        addBoard("vn", "Визуальные новеллы", "Японская культура", "Сэйбер", false);
        addBoard("c", "Косплей", "Японская культура", "Аноним", false);
        addBoard("rm", "Rozen Maiden", "Японская культура", "Суйгинто", false);
        addBoard("tan", "Сетевые персонажи", "Японская культура", "Уныл-тян", false);
        addBoard("to", "Touhou", "Японская культура", "Нитори", false);
        addBoard("fi", "Фигурки", "Японская культура", "Фигурка анонима", false);
        addBoard("jp", "Япония", "Японская культура", "名無しさん", false);
        addBoard("azu", "Azumanga Daioh", "Закрытые доски", "Осака", false);
        addBoard("dn", "Death Note", "Закрытые доски", "Аноним", true);
        addBoard("gf", "GIF- и FLASH-анимация", "Закрытые доски", "Аноним", true);
        addBoard("an", "Живопись", "Закрытые доски", "Кот Синкая", false);
        addBoard("ne", "Животные", "Закрытые доски", "Пушок", false);
        addBoard("ma", "Манга", "Закрытые доски", "Иноуэ Орихимэ", false);
        addBoard("me", "Меха", "Закрытые доски", "Лакс Кляйн", false);
        addBoard("med", "Медицина", "Закрытые доски", "Антон Буслов", false);
        addBoard("mo", "Мотоциклы", "Закрытые доски", "Аноним", false);
        addBoard("bg", "Настольные игры", "Закрытые доски", "Аноним", false);
        addBoard("ls", "Lucky☆Star", "Закрытые доски", "Цукаса", false);
        addBoard("w", "Обои", "Закрытые доски", "Аноним", false);
        addBoard("old_o", "Оэкаки", "Закрытые доски", "Аноним", false);
        addBoard("p", "Политика", "Закрытые доски", "Аноним", true);
        addBoard("maid", "Служанки", "Закрытые доски", "Госюдзин-сама", false);
        addBoard("sp", "Спорт", "Закрытые доски", "Спортакус", false);
        addBoard("sos", "Suzumiya Haruhi no Yūutsu", "Закрытые доски", "Кёнко", false);
        addBoard("t", "Торренты", "Закрытые доски", "Аноним", false);
        addBoard("fr", "Фурри", "Закрытые доски", "Аноним", false);
        addBoard("abe", "Old Home", "Доски конгломерата", "Chada", false);
        addBoard("misc", "Баннеры", "Разное", "Аноним", false);
        addBoard("tenma", "Юбилейные баннеры", "Разное", "Аноним", false);
        addBoard("dev", "Работа сайта", "Служебные разделы", "", false);

        BOARDS_SIMPLE_ARRAY = new SimpleBoardModel[BOARDS_LIST.size()];
        for (int i = 0; i< BOARDS_LIST.size(); ++i) BOARDS_SIMPLE_ARRAY[i] = new SimpleBoardModel(BOARDS_LIST.get(i));
    }

    static BoardModel getBoard(String boardName) {
        BoardModel board = BOARDS_MAP.get(boardName);
        if (board == null) return createDefaultBoardModel(boardName, boardName, null, "Аноним", false);
        return board;
    }

    static SimpleBoardModel[] getBoardsList() {
        return BOARDS_SIMPLE_ARRAY;
    }

    private static void addBoard(String name, String description, String category, String defaultPosterName, boolean nsfw) {
        BoardModel model = createDefaultBoardModel(name, description, category, defaultPosterName, nsfw);
        BOARDS_LIST.add(model);
        BOARDS_MAP.put(name, model);
    }

    private static BoardModel createDefaultBoardModel(String name, String description, String category, String defaultPosterName, boolean nsfw) {
        BoardModel model = new BoardModel();
        model.chan = CirnoArchiveModule.IIYAKUJI_NAME;
        model.boardName = name;
        model.boardDescription = description;
        model.boardCategory = category;
        model.nsfw = nsfw;
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "GMT+3";
        model.defaultUserName = defaultPosterName;
        model.bumpLimit = 500;

        model.readonlyBoard = !POSTING_BOARDS.contains(name);
        model.requiredFileForNewThread = true;
        model.allowDeletePosts = !model.readonlyBoard;
        model.allowDeleteFiles = !model.readonlyBoard;
        model.allowReport = BoardModel.REPORT_NOT_ALLOWED;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = false;
        model.allowEmails = false;
        model.ignoreEmailIfSage = false;
        model.allowCustomMark = false;
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
        model.attachmentsFormatFilters = ATTACHMENT_FILTERS;
        model.markType = BoardModel.MARK_WAKABAMARK;

        model.firstPage = 0;
        model.lastPage = BoardModel.LAST_PAGE_UNDEFINED;

        model.catalogAllowed = model.readonlyBoard && !name.equals("abe_old");
        return model;
    }
}
