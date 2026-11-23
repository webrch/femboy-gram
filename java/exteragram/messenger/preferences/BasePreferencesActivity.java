package com.exteragram.messenger.preferences;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import com.exteragram.messenger.utils.ui.PopupUtils;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/**
 * FemboyGram's stand-in for exteraGram's settings screen base, with the same shape: plugins
 * that bring their own settings screens (re:extera and the like) extend it.
 */
public abstract class BasePreferencesActivity extends BaseFragment {

    /** Read by name through reflection in some plugins: it is a field here, as in exteraGram. */
    public UniversalRecyclerView listView;

    public abstract String getTitle();

    public abstract void fillItems(ArrayList<UItem> items, UniversalAdapter adapter);

    public abstract void onClick(UItem item, View view, int position, float x, float y);

    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    public void initializeOptionStrings() {
    }

    public boolean needHideTitle() {
        return false;
    }

    public boolean hasHeaderFade() {
        return true;
    }

    @Override
    public View createView(Context context) {
        initializeOptionStrings();
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        if (!needHideTitle()) actionBar.setTitle(getTitle());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView = content;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) listView.adapter.update(false);
    }

    public void scrollToItem(int itemId) {
        if (listView == null || listView.adapter == null) return;
        for (int i = 0; i < listView.adapter.getItemCount(); i++) {
            UItem item = listView.adapter.getItem(i);
            if (item != null && item.id == itemId) {
                listView.smoothScrollToPosition(i);
                return;
            }
        }
    }

    public void showListDialog(UItem item, CharSequence[] options, String title, int currentValue, PopupUtils.OnItemClickListener onSelected) {
        PopupUtils.showDialog(options, title, currentValue, getParentActivity(), which -> {
            onSelected.onClick(which);
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        });
    }

    public void showRestartBulletin() {
        BulletinFactory.of(this).createSimpleBulletin(org.telegram.messenger.R.raw.chats_infotip,
                org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.FemboyPluginsRestart)).show();
    }
}
