package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

import java.util.ArrayList;

/**
 * Вкладка «Музыка» среди главных вкладок: страница FemboyMusicPage без своей
 * шапки, стеклянная панель вкладок рисуется поверх и размывает то, что под ней.
 */
public class FemboyMusicTabFragment extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    private final BaseFragment tabs;
    private FemboyMusicPage page;

    private final DownscaleScrollableNoiseSuppressor noise;
    private final BlurredBackgroundSourceRenderNode glass;
    private IBlur3Capture capture;
    private final ArrayList<RectF> positions = new ArrayList<>();
    private final RectF tabsPosition = new RectF();

    public FemboyMusicTabFragment(BaseFragment tabs) {
        super();
        this.tabs = tabs;
        positions.add(tabsPosition);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            noise = new DownscaleScrollableNoiseSuppressor();
            glass = new BlurredBackgroundSourceRenderNode(null);
        } else {
            noise = null;
            glass = null;
        }
    }

    @Override
    public ActionBar createActionBar(Context context) {
        return null;
    }

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;
        page = new FemboyMusicPage(context, new FemboyMusicPage.Host() {
            @Override
            public BaseFragment fragment() {
                return FemboyMusicTabFragment.this;
            }

            @Override
            public BulletinFactory bulletins() {
                return BulletinFactory.of(tabs != null ? tabs : FemboyMusicTabFragment.this);
            }

            @Override
            public boolean inTabs() {
                return true;
            }

            @Override
            public void back() {
            }
        }) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                recordGlass(getMeasuredWidth(), getMeasuredHeight());
                super.dispatchDraw(canvas);
            }
        };
        capture = new ViewGroupPartRenderer(page.listView, page, page.listView::drawChild);
        page.listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && noise != null) {
                    noise.onScrolled(dx, dy);
                    invalidateBlur();
                }
            }
        });
        fragmentView = page;
        return page;
    }

    private void recordGlass(int width, int height) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || noise == null || glass == null || glass.inRecording()) return;
        invalidateBlur();
        final Canvas c = glass.beginRecording(width, height);
        c.drawColor(Theme.getColor(Theme.key_windowBackgroundGray));
        if (SharedConfig.chatBlurEnabled()) {
            noise.draw(c, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
        }
        glass.endRecording();
    }

    private void invalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || noise == null || fragmentView == null || capture == null) return;
        final int width = fragmentView.getMeasuredWidth(), height = fragmentView.getMeasuredHeight();
        if (width <= 0 || height <= 0) return;
        final int bottom = height - AndroidUtilities.navigationBarHeight - dp(DialogsActivity.MAIN_TABS_MARGIN);
        final int top = bottom - dp(DialogsActivity.MAIN_TABS_HEIGHT);
        tabsPosition.set(0, top, width, bottom);
        tabsPosition.inset(0, LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0 : -dp(48));
        noise.setupRenderNodes(positions, 1);
        noise.invalidateResultRenderNodes(capture, width, height);
    }

    @Override
    public BlurredBackgroundSourceRenderNode getGlassSource() {
        return glass;
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return page == null || page.canSlide(ev, forward);
    }

    @Override
    public void onParentScrollToTop() {
        if (page != null) page.scrollToTop();
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
    public boolean isLightStatusBar() {
        return ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundGray)) > 0.7f;
    }

    @Override
    public java.util.ArrayList<org.telegram.ui.ActionBar.ThemeDescription> getThemeDescriptions() {
        final java.util.ArrayList<org.telegram.ui.ActionBar.ThemeDescription> list = new java.util.ArrayList<>();
        final org.telegram.ui.ActionBar.ThemeDescription.ThemeDescriptionDelegate delegate = () -> {
            if (page != null) page.updateColors();
        };
        list.add(new org.telegram.ui.ActionBar.ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundGray));
        list.add(new org.telegram.ui.ActionBar.ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhite));
        return list;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (page != null) page.update();
    }
}
