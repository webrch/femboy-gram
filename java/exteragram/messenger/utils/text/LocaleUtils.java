package com.exteragram.messenger.utils.text;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;

/** exteraGram's text helpers that plugins call: markdown-ish formatting of their own strings. */
public class LocaleUtils {
    public static CharSequence fullyFormatText(CharSequence text) {
        return text == null ? null : AndroidUtilities.replaceTags(text.toString());
    }

    public static CharSequence formatWithUsernames(CharSequence text, BaseFragment fragment) {
        return fullyFormatText(text);
    }
}
