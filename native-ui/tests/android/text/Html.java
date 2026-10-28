package android.text;

/** Compile-only host shim. Tests must never claim to exercise Android HTML conversion. */
public final class Html {
    public static final int FROM_HTML_MODE_COMPACT=63;
    public static CharSequence fromHtml(String source,int flags){
        throw new AssertionError("Android HTML conversion requires a device; use plain-text host fixtures");
    }
}
