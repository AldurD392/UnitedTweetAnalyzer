package com.github.aldurd392.UnitedTweetsAnalyzer;

import org.geotools.geometry.Envelope2D;

import java.util.Arrays;
import java.util.HashSet;

/**
 * This class contains the constants we need.
 *
 * Specifically, the bounding box we use to bias the stream.
 */
class Constants {
    private final static double southWestLong = -125.1088;
    private final static double southWestLat = 23.2193;

    private final static double northEastLong = -66.595;
    private final static double northEastLat = 47.4416;

    /**
     * The bounding box containing the USA.
     */
    public final static double[][] boundingBox = {
            {southWestLong, southWestLat}, {northEastLong, northEastLat}
    };

    /**
     * A bounding box containing the whole world.
     * Useful to retrieve only tweets with an attached location.
     */
    public final static double[][] worldWideBox = {
            {-180, -90}, {180, 90}
    };

    /**
     * The same bounding box as en EnvelopeBox,
     * used by the JTS system.
     */
    public final static Envelope2D envelopeBox = new Envelope2D();
    static {
        envelopeBox.include(northEastLong, northEastLat);
        envelopeBox.include(southWestLong, southWestLat);
    }

    /**
     * This intent will let us add the user ID and build a URL to the user profile.
     */
    public final static String twitter_user_intent = "=HYPERLINK(\"https://twitter.com/intent/user?user_id=%d\")";

    /**
     * Limit the number of unlabeled instances we classify.
     */
    public final static int classification_limit = 200;

    /**
     * Those won't be considered stopwords.
     */
    public final static HashSet<String> countryCodes = new HashSet<>(Arrays.asList(
            new String[]{
                    "al", "ak", "az", "ar", "ca", "co",
                    "ct", "de", "dc", "fl", "ga", "hi",
                    "id", "il", "in", "ia", "ks", "ky",
                    "la", "me", "md", "ma", "mi", "mn",
                    "ms", "mo", "mt", "ne", "nv", "nh",
                    "nj", "nm", "ny", "nc", "nd", "oh",
                    "ok", "or", "pa", "ri", "sc", "sd",
                    "tn", "tx", "ut", "vt", "va", "wa",
                    "wv", "wi", "wy", "fr", "af", "al",
                    "dz", "as", "ad", "ao", "ai", "aq",
                    "ag", "ar", "am", "aw", "au", "at",
                    "az", "bs", "bh", "bd", "bb", "by",
                    "be", "bz", "bj", "bm", "bt", "bo",
                    "bq", "ba", "bw", "bv", "br", "io",
                    "bn", "bg", "bf", "bi", "kh", "cm",
                    "ca", "cv", "ky", "cf", "td", "cl",
                    "cn", "cx", "cc", "co", "km", "cg",
                    "cd", "ck", "cr", "hr", "cu", "cw",
                    "cy", "cz", "ci", "dk", "dj", "dm",
                    "do", "ec", "eg", "sv", "gq", "er",
                    "ee", "et", "fk", "fo", "fj", "fi",
                    "fr", "gf", "pf", "tf", "ga", "gm",
                    "ge", "de", "gh", "gi", "gr", "gl",
                    "gd", "gp", "gu", "gt", "gg", "gn",
                    "gw", "gy", "ht", "hm", "va", "hn",
                    "hk", "hu", "is", "in", "id", "ir",
                    "iq", "ie", "im", "il", "it", "jm",
                    "jp", "je", "jo", "kz", "ke", "ki",
                    "kp", "kr", "kw", "kg", "la", "lv",
                    "lb", "ls", "lr", "ly", "li", "lt",
                    "lu", "mo", "mk", "mg", "mw", "my",
                    "mv", "ml", "mt", "mh", "mq", "mr",
                    "mu", "yt", "mx", "fm", "md", "mc",
                    "mn", "me", "ms", "ma", "mz", "mm",
                    "na", "nr", "np", "nl", "nc", "nz",
                    "ni", "ne", "ng", "nu", "nf", "mp",
                    "no", "om", "pk", "pw", "ps", "pa",
                    "pg", "py", "pe", "ph", "pn", "pl",
                    "pt", "pr", "qa", "ro", "ru", "rw",
                    "re", "bl", "sh", "kn", "lc", "mf",
                    "pm", "vc", "ws", "sm", "st", "sa",
                    "sn", "rs", "sc", "sl", "sg", "sx",
                    "sk", "si", "sb", "so", "za", "gs",
                    "ss", "es", "lk", "sd", "sr", "sj",
                    "sz", "se", "ch", "sy", "tw", "tj",
                    "tz", "th", "tl", "tg", "tk", "to",
                    "tt", "tn", "tr", "tm", "tc", "tv",
                    "ug", "ua", "ae", "gb", "us", "um",
                    "uy", "uz", "vu", "ve", "vn", "vg",
                    "vi", "wf", "eh", "ye", "zm", "zw",
                    "ax",
            }
    ));

    /**
     * A set of stopwords ignored while converting locations to a word vector.
     */
    public final static HashSet<String> stopWords = new HashSet<>(Arrays.asList(
            new String[]{"in", "my", "house", "world", "your",
                    "of", "where", "at", "wherev", "www",
                    "the", "town", "street", "state", "point",
                    "planet", "place", "near", "nation", "mind",
                    "http", "home", "from", "you", "write", "worth",
                    "wonderland", "with", "god", "all", "and", "be",
                    "big",
            }
    ));
    static {
        stopWords.removeAll(countryCodes);
    }
}
