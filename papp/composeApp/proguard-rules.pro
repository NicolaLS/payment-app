#######################################################################
# Strip ALL Android Log calls everywhere (app + all deps)
#######################################################################
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}

#######################################################################
# Strip nwc-kmp’s logging facade (quiet the library at call sites)
# Safe even if the lib changes its backend; affects only NwcLog calls.
#######################################################################
# -assumenosideeffects class io.github.nostr.nwc.logging.NwcLog {
#    public *** trace(...);
#    public *** debug(...);
#    public *** info(...);
#    public *** warn(...);
#    public *** error(...);
#    public *** log(...);
#}
# FIXME: The above does not work because logs are inline...fix logging upstream
# until then we do this, should be fine but nukes everything

# Strip ALL stdout/stderr printing in release
-assumenosideeffects class java.io.PrintStream {
  public *** print(...);
  public *** println(...);
  public *** printf(...);
  public *** format(...);
  public *** append(...);
  public *** write(...);
  public void flush();
}