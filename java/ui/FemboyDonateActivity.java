package org.telegram.ui;

import android.view.View;

import org.telegram.femboy.FemboyConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Gifts.GiftSheet;

import java.util.ArrayList;

/**
 * Donate and supporter-badge instructions. No payment data is entered here:
 * the user pays in their usual app, we only show the details and copy the ID.
 */
public class FemboyDonateActivity extends UniversalFragment {

    private static final int ID_YOOMONEY = 1;
    private static final int ID_STARS = 2;
    private static final int ID_MY_ID = 3;
    private static final int ID_WRITE = 4;

    private final boolean badge;

    public FemboyDonateActivity() {
        this(false);
    }

    public FemboyDonateActivity(boolean badge) {
        this.badge = badge;
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(badge ? R.string.FemboyBadgeBuyTitle : R.string.FemboyDonate);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    private static String s(int res) {
        return LocaleController.getString(res);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (!badge) {
            items.add(UItem.asShadow(s(R.string.FemboyDonateJust)));
            items.add(FemboyRowCell.Factory.of(ID_YOOMONEY, R.drawable.femboy_ic_star,
                    s(R.string.FemboyDonateYoomoney), s(R.string.FemboyDonateYoomoneyNote)));
            items.add(FemboyRowCell.Factory.of(ID_STARS, R.drawable.femboy_ic_star,
                    s(R.string.FemboyDonateStars), s(R.string.FemboyDonateStarsNote)));
            items.add(UItem.asShadow(LocaleController.formatString(R.string.FemboyDonateNote,
                    s(R.string.FemboyDonateBadgeHint))));
            return;
        }

        items.add(UItem.asShadow(LocaleController.formatString(R.string.FemboyBadgeBuyAbout,
                s(R.string.FemboyBadgeBuyPrice))));

        items.add(UItem.asHeader(s(R.string.FemboyBadgeBuyStepPay)));
        items.add(FemboyRowCell.Factory.of(ID_YOOMONEY, R.drawable.femboy_ic_star,
                s(R.string.FemboyDonateYoomoney), s(R.string.FemboyBadgeBuyYoomoneyNote)));
        items.add(FemboyRowCell.Factory.of(ID_STARS, R.drawable.femboy_ic_star,
                s(R.string.FemboyDonateStars), s(R.string.FemboyBadgeBuyStarsNote)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(s(R.string.FemboyBadgeBuyStepId)));
        items.add(FemboyRowCell.Factory.of(ID_MY_ID, R.drawable.femboy_ic_tag,
                String.valueOf(getUserConfig().getClientUserId()), s(R.string.FemboyBadgeBuyIdNote)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(s(R.string.FemboyBadgeBuyStepWrite)));
        items.add(FemboyRowCell.Factory.of(ID_WRITE, R.drawable.femboy_ic_chat,
                s(R.string.FemboyBadgeBuyWrite), s(R.string.FemboyBadgeBuyWriteNote)));
        items.add(UItem.asShadow(s(R.string.FemboyBadgeBuyAfter)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_YOOMONEY) {
            Browser.openUrl(getContext(), FemboyConfig.DONATE_YOOMONEY);
        } else if (item.id == ID_STARS) {
            openGift();
        } else if (item.id == ID_MY_ID) {
            AndroidUtilities.addToClipboard(String.valueOf(getUserConfig().getClientUserId()));
            BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.FemboyDonateCopied)).show();
        } else if (item.id == ID_WRITE) {
            Browser.openUrl(getContext(), FemboyConfig.FEEDBACK_URL);
        }
    }

    private void openGift() {
        if (getContext() == null) {
            return;
        }
        if (getMessagesController().getUser(FemboyConfig.DONATE_GIFT_USER) != null) {
            showDialog(new GiftSheet(getContext(), currentAccount, FemboyConfig.DONATE_GIFT_USER, null, null));
            return;
        }
        Browser.openUrl(getContext(), "tg://user?id=" + FemboyConfig.DONATE_GIFT_USER);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
