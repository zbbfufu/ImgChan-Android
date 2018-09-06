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

package nya.miku.wishmaster.ui;

import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.*;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.ui.Database.PlaylistEntry;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import nya.miku.wishmaster.ui.tabs.UrlHandler;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class PlaylistFragment extends Fragment implements AdapterView.OnItemClickListener {

    private MainActivity activity;
    private Resources resources;
    private ApplicationSettings settings;
    private LayoutInflater inflater;
    private PagerAdapter pagerAdapter;
    private ViewPager viewPager;
    private List<Pair<ListView, String>> listViews;

    public static final int PAGE_ALL = 0;
    public static final int PAGE_CHANS = 1;
    public static final int PAGE_BOARDS = 2;
    public static final int PAGE_THREADS = 3;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (MainActivity) getActivity();
        resources = MainApplication.getInstance().resources;
        settings = MainApplication.getInstance().settings;
        inflater = LayoutInflater.from(activity);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity.setTitle(R.string.tabs_playlist);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) CompatibilityImpl.setActionBarDefaultIcon(activity);
        viewPager = (ViewPager) inflater.inflate(R.layout.playlist_fragment, container, false);
        update();
        return viewPager;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Object item = parent.getAdapter().getItem(position);
//        if (item instanceof FavoritesEntry) {
//            UrlHandler.open(((FavoritesEntry) item).url, activity);
//        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, R.id.menu_clear_playlist, 101, R.string.menu_clear_playlist).setIcon(android.R.drawable.ic_menu_close_clear_cancel);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_clear_playlist) {
            if (pagerAdapter != null) {
                MainApplication.getInstance().database.clearPlaylist();
                update();
            }
            return true;
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(Menu.NONE, R.id.context_menu_open_browser, 1, R.string.context_menu_open_browser);
        menu.add(Menu.NONE, R.id.context_menu_remove_playlist, 2, R.string.context_menu_remove_playlist);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        View v = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).targetView;
        PlaylistEntry entry = (PlaylistEntry) v.getTag();
        switch (item.getItemId()) {
            case R.id.context_menu_remove_playlist:
                MainApplication.getInstance().database.removeFromPlaylist(entry.chan, entry.board, entry.boardpage, entry.thread);
                for (Pair<ListView,String> p : listViews) ((PlaylistAdapter) p.getLeft().getAdapter()).remove(entry);
                return true;
            case R.id.context_menu_open_browser:
                UrlHandler.launchExternalBrowser(activity, entry.url);
                return true;
        }
        return false;
    }

    public void update() {
        initLists();
        pagerAdapter = new ViewPagerPlaylistAdapter(listViews);
        viewPager.setAdapter(pagerAdapter);
    }

    private void initLists() {
        listViews = new ArrayList<Pair<ListView,String>>();
        List<PlaylistEntry> playlist = MainApplication.getInstance().database.getCurrentPlaylist();
        if (playlist.isEmpty()) {
            listViews.add(Pair.of(getListView(playlist), resources.getString(R.string.playlist_empty)));
            return;
        }
        listViews.add(Pair.of(getListView(playlist), resources.getString(R.string.playlist_all)));
    }

    private ListView getListView(List<PlaylistEntry> list) {
        ListView lv = (ListView) inflater.inflate(R.layout.playlist_listview, viewPager, false);
        lv.setAdapter(new PlaylistAdapter(list, activity));
        lv.setOnItemClickListener(this);
        registerForContextMenu(lv);
        return lv;
    }

    private static class ViewPagerPlaylistAdapter extends PagerAdapter {
        private final List<Pair<ListView, String>> listViews;

        public ViewPagerPlaylistAdapter(List<Pair<ListView, String>> listViews) {
            this.listViews = listViews;
        }

        @Override
        public int getCount() {
            return listViews.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return listViews.get(position).getRight();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View v = listViews.get(position).getLeft();
            container.addView(v);
            return v;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

    }

    private static class PlaylistAdapter extends ArrayAdapter<PlaylistEntry> {
        private int drawablePadding;
        private LayoutInflater inflater;

        public PlaylistAdapter(List<PlaylistEntry> objects, MainActivity activity) {
            super(activity, 0, objects);
            drawablePadding = (int) (activity.getResources().getDisplayMetrics().density * 5 + 0.5f);
            inflater = LayoutInflater.from(activity);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            PlaylistEntry item = getItem(position);
            View v = convertView == null ? inflater.inflate(R.layout.playlist_item_layout, parent, false) : convertView;

            TextView tv1 = (TextView) v.findViewById(R.id.playlist_item_title);
            tv1.setSingleLine();
            tv1.setEllipsize(TextUtils.TruncateAt.END);
            tv1.setText(item.title);
            ChanModule chan = MainApplication.getInstance().getChanModule(item.chan);
            if (chan != null) {
                tv1.setCompoundDrawablesWithIntrinsicBounds(chan.getChanFavicon(), null, null, null);
                tv1.setCompoundDrawablePadding(drawablePadding);
            }

            ImageView im = (ImageView) v.findViewById(R.id.playlist_item_image);
            //im.setImageURI(Uri.parse(item.url));

            v.setTag(item);
            return v;
        }
        
    }
    
}
