package cn.enaium.fabricpvz.api.text;


/**
 * The translation facility of this API: what a mod uses to have its own text follow the language the
 * game is in - a mod's button label, its settings captions, anything it draws itself.
 *
 * <p>A mod does not translate its text; it asks for the translation and gets back either the text of
 * the current language or the text it passed:
 *
 * <pre>{@code
 * String label = Translations.translate("[AUTO_COLLECT]", "Auto Collect");
 * }</pre>
 *
 * <p>Keys live in the same string bank the game's own text lives in, and a mod ships the text it adds
 * itself: its own {@code i18n/<tag>.json} inside its jar - one flat JSON object of key to text, line
 * breaks written {@code \n} - which the installation's i18n mod collects from every loaded mod and
 * writes into the bank, next to the tables of the game's own text and of the player's files. A mod
 * therefore carries its text with it instead of asking another mod to hold it. Without an i18n mod, or
 * for a key no table carries, the fallback is returned, which is why the second argument is not
 * optional.
 *
 * <p>The text is looked up when it is drawn, not when it is stored: a language switch - the one an i18n
 * mod offers in the options screen - takes effect for every label that asks again.
 */
public final class Translations {

    /** What an i18n mod installs: the one that knows the language and the string bank behind it. */
    public interface Source {

        /** The translation of {@code theKey}, or {@code theFallback} when the language has none. */
        String translate(String theKey, String theFallback);

        /** The tag of the language in use, e.g. {@code zh-CN}; empty when nothing is installed. */
        String language();

        /** Whether a translation is in use at all. */
        boolean active();
    }

    private static volatile Source source;

    private Translations() {
    }

    /** Installs the source: called by an i18n mod, never by a mod that reads text. */
    public static void install(Source theSource) {
        source = theSource;
    }

    /** The installed source, or null when no i18n mod is present. */

    public static Source source() {
        return source;
    }

    /**
     * The translation of {@code theKey} in the language in use, or {@code theFallback} when there is
     * no translation installed, the language has no entry for the key, or anything goes wrong while
     * looking it up (a mod's label must never be missing because a translation is).
     */
    public static String translate(String theKey, String theFallback) {
        Source aSource = source;
        if (aSource == null || theKey == null || theKey.isEmpty()) {
            return theFallback;
        }
        try {
            String aValue = aSource.translate(theKey, theFallback);
            return (aValue == null || aValue.isEmpty()) ? theFallback : aValue;
        } catch (RuntimeException exception) {
            return theFallback;
        }
    }

    /** The language in use, or an empty string when no translation is installed. */
    public static String language() {
        Source aSource = source;
        return aSource == null ? "" : aSource.language();
    }

    /** Whether a translation is installed and in use. */
    public static boolean active() {
        Source aSource = source;
        return aSource != null && aSource.active();
    }
}
