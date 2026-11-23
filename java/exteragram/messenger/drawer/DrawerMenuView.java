package com.exteragram.messenger.drawer;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Боковое меню exteraGram. У FemboyGram своё, телеграмовское; этот класс
 * есть, чтобы плагины, которые его знают, загружались. Поля container и
 * onItemClick названы как у них: плагины берут их рефлексией.
 */
public class DrawerMenuView extends ScrollView {
    private final LinearLayout container;
    private Runnable onItemClick;

    public DrawerMenuView(Context context) {
        super(context);
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        addView(container);
    }

    public void setOnItemClick(Runnable runnable) {
        onItemClick = runnable;
    }
}
