package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.femboy.FemboyConfig;
import org.telegram.femboy.FemboyMarkup;
import org.telegram.femboy.FemboySpans;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Окошки своего оформления: выбор размера и одно предупреждение.
 */
public class FemboyMarkupAlert {

    /**
     * Ползунок размера с живым примером: цифры тут ничего не говорят.
     *
     * Отрезок текста приходит снаружи и запоминается до открытия окна. Пока
     * окно открыто, выделение в поле уже снято вместе с меню — из-за этого
     * первая версия не применяла размер вообще ни разу.
     */
    public static void showSize(Context context, EditTextCaption editText, int start, int end) {
        if (context == null || editText == null || start < 0 || end <= start) {
            return;
        }
        final int[] chosen = {FemboyMarkup.sizeValue(1.4f)};

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(4), dp(20), 0);

        final TextView preview = new TextView(context);
        preview.setGravity(Gravity.CENTER);
        preview.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        preview.setText(LocaleController.getString(R.string.FemboyMarkupSizeExample));
        layout.addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64));

        final SeekBar bar = new SeekBar(context);
        bar.setMax(13);
        bar.setProgress(chosen[0]);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                chosen[0] = progress;
                preview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16 * FemboyMarkup.sizeOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        preview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16 * FemboyMarkup.sizeOf(chosen[0]));
        layout.addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.FemboyMarkupSize))
                .setView(layout)
                .setPositiveButton(LocaleController.getString(R.string.Done),
                        (d, w) -> editText.makeSelectedFemboy(FemboyMarkup.KIND_SIZE, chosen[0], start, end))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }


    /**
     * Окно кнопки: куда ведёт и какого цвета.
     *
     * Цвет выбирается кружками, а не списком названий: названий у цветов нет,
     * а показать их можно прямо.
     */
    public static void showButton(Context context, EditTextCaption editText, int start, int end) {
        if (context == null || editText == null || start < 0 || end <= start) {
            return;
        }
        final int[] chosen = {0};

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(4), dp(20), 0);

        final android.widget.EditText address = new android.widget.EditText(context);
        address.setHint(LocaleController.getString(R.string.FemboyMarkupButtonHint));
        address.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        address.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        address.setSingleLine(true);
        layout.addView(address, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        final LinearLayout colors = new LinearLayout(context);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.setPadding(0, dp(14), 0, 0);
        final android.view.View[] dots = new android.view.View[FemboySpans.BUTTON_COLORS.length];
        for (int i = 0; i < dots.length; i++) {
            final int index = i;
            final android.view.View dot = new android.view.View(context);
            dot.setBackground(Theme.createCircleDrawable(dp(22), FemboySpans.buttonColor(i)));
            dot.setOnClickListener(v -> {
                chosen[0] = index;
                for (int k = 0; k < dots.length; k++) {
                    dots[k].setScaleX(k == index ? 1.25f : 1f);
                    dots[k].setScaleY(k == index ? 1.25f : 1f);
                }
            });
            dots[i] = dot;
            colors.addView(dot, LayoutHelper.createLinear(22, 22, 0, 0, i == 0 ? 0 : 6, 0, 0));
        }
        dots[0].setScaleX(1.25f);
        dots[0].setScaleY(1.25f);
        layout.addView(colors, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.FemboyMarkupButton))
                .setView(layout)
                .setPositiveButton(LocaleController.getString(R.string.Done), (d, w) -> {
                    // Без ссылки кнопка не кнопка: нажимать нечего. Владелец
                    // на это и наткнулся — плашка нарисовалась, а нажатие не
                    // делало ничего, потому что ссылка осталась пустой.
                    final String url = FemboyMarkup.link(address.getText().toString());
                    if (url.isEmpty()) {
                        android.widget.Toast.makeText(context,
                                LocaleController.getString(R.string.FemboyMarkupButtonNoLink),
                                android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    editText.makeSelectedButton(chosen[0], url, start, end);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    /**
     * Предупреждение про премиум-значки, один раз.
     *
     * Текст говорит ровно то, что происходит: премиума от этого не появится,
     * анимацию увидят только в форке, остальным уедет запасной символ.
     */
    public static void warnEmojiOnce(Context context) {
        if (context == null || FemboyConfig.emojiWarned()) {
            return;
        }
        FemboyConfig.setEmojiWarned(true);
        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.FemboyEmojiWarnTitle))
                .setMessage(LocaleController.getString(R.string.FemboyEmojiWarnText))
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .show();
    }

    /**
     * Предупреждение об оформлении, один раз за всё время.
     *
     * Показывается в тот миг, когда человек впервые применяет оформление, а не
     * перед отправкой. Владелец просил перед отправкой, и я сделал иначе
     * сознательно: отправка в телеграме — одна длинная цепочка, которая сама
     * чистит поле ввода и запускает движение сообщения. Вклиниться в неё
     * вопросом можно только оборвав её и запустив заново из ответа диалога, и
     * тогда в половине случаев текст остаётся в поле или уходит дважды.
     * Предупредить на шаг раньше — тот же смысл и никакой поломки отправки.
     */
    public static void warnOnce(Context context) {
        if (context == null || FemboyConfig.markupWarned()) {
            return;
        }
        FemboyConfig.setMarkupWarned(true);
        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.FemboyMarkupWarnTitle))
                .setMessage(LocaleController.getString(R.string.FemboyMarkupWarnText))
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .show();
    }
}
