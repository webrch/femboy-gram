package org.telegram.ui;

import android.content.Context;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.femboy.FemboyMusic;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;

/**
 * Музыка отдельным экраном: та же страница, что и во вкладке внизу, только со
 * стрелкой «назад». Открывается строкой в настройках FemboyGram и из плеера.
 */
public class FemboyMusicActivity extends BaseFragment {

    private FemboyMusicPage page;

    @Override
    public boolean onFragmentCreate() {
        FemboyMusic.watch();
        return super.onFragmentCreate();
    }

    @Override
    public ActionBar createActionBar(Context context) {
        return null;
    }

    @Override
    public View createView(Context context) {
        page = new FemboyMusicPage(context, new FemboyMusicPage.Host() {
            @Override
            public BaseFragment fragment() {
                return FemboyMusicActivity.this;
            }

            @Override
            public BulletinFactory bulletins() {
                return BulletinFactory.of(FemboyMusicActivity.this);
            }

            @Override
            public boolean inTabs() {
                return false;
            }

            @Override
            public void back() {
                finishFragment();
            }
        });
        fragmentView = page;
        return page;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (page != null) page.onResume();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (page != null && page.isSearching()) {
            if (invoked) page.closeSearch();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public boolean isLightStatusBar() {
        return ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundGray)) > 0.7f;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ArrayList<ThemeDescription> list = new ArrayList<>();
        final ThemeDescription.ThemeDescriptionDelegate delegate = () -> {
            if (page != null) page.updateColors();
        };
        list.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundGray));
        list.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhite));
        return list;
    }
}
