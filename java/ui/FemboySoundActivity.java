package org.telegram.ui;

import android.content.Intent;
import android.net.Uri;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.femboy.FemboyConfig;
import org.telegram.femboy.FemboyChime;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Ветка «Звук»: шлепки патпата по нажатию на название на главном экране. */
public class FemboySoundActivity extends UniversalFragment {

    private static final int ID_ENABLED = 1;
    private static final int ID_STANDARD = 2;
    private static final int ID_OWN = 3;
    private static final int ID_PLAY = 4;

    private static final int PICK_SOUND = 4801;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.FemboySound);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(java.util.ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_ENABLED, LocaleController.getString(R.string.FemboyChime)).setChecked(FemboyConfig.meowEnabled()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.FemboyChimeAbout)));

        final boolean own = FemboyConfig.meowPath() != null;
        items.add(UItem.asHeader(LocaleController.getString(R.string.FemboyWhichSound)));
        items.add(UItem.asRadio(ID_STANDARD, LocaleController.getString(R.string.FemboySoundBuiltIn)).setChecked(!own));
        items.add(UItem.asRadio(ID_OWN, own ? LocaleController.getString(R.string.FemboySoundOwn) : LocaleController.getString(R.string.FemboySoundChoose)).setChecked(own));
        items.add(UItem.asButton(ID_PLAY, LocaleController.getString(R.string.FemboySoundPlay)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.FemboySoundAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ENABLED) {
            FemboyConfig.setMeowEnabled(!FemboyConfig.meowEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_STANDARD) {
            FemboyConfig.setMeowPath(null);
            listView.adapter.update(true);
        } else if (item.id == ID_OWN) {
            final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(intent, PICK_SOUND);
            } catch (Exception ignored) {
                // Не на каждом телефоне есть чем открыть выбор файла.
            }
        } else if (item.id == ID_PLAY) {
            FemboyChime.preview();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_SOUND || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        final File out = new File(ApplicationLoader.getFilesDirFixed(), "femboy_chime_own");
        try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) {
                return;
            }
            final byte[] buf = new byte[16384];
            int read;
            while ((read = in.read(buf)) > 0) {
                os.write(buf, 0, read);
            }
        } catch (Exception e) {
            return;
        }
        FemboyConfig.setMeowPath(out.getAbsolutePath());
        FemboyChime.soundChanged();
        listView.adapter.update(true);
        FemboyChime.preview();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    // Как экраны самого телеграма: рисуемся под полоской жестов, а список
    // получает снизу отступ на её высоту, вместо пустой полосы под ним.
    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (listView != null) {
            listView.setPadding(listView.getPaddingLeft(), listView.getPaddingTop(), listView.getPaddingRight(), bottom);
            listView.setClipToPadding(false);
        }
    }
}
