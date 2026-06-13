package eu.kanade.tachiyomi.util;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;

/**
 * Backport target for {@code URLEncoder.encode(String, Charset)} /
 * {@code URLDecoder.decode(String, Charset)} (added in API 33). A build-time ASM transform rewrites
 * those calls in NewPipeExtractor to these methods, which use the charset-name overload available on
 * all supported Android versions. This lets the latest NewPipeExtractor run on Android 9.
 */
public final class UrlCodecCompat {

    private UrlCodecCompat() {
    }

    public static String encode(String s, Charset charset) {
        try {
            return URLEncoder.encode(s, charset.name());
        } catch (Exception e) {
            return s;
        }
    }

    public static String decode(String s, Charset charset) {
        try {
            return URLDecoder.decode(s, charset.name());
        } catch (Exception e) {
            return s;
        }
    }
}
