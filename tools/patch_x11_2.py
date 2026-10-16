import pathlib, re, sys
J = pathlib.Path("engines/termux-x11/lorie/src/main/java"
                 "/com/molina/suite/x11")
if not J.is_dir():
    sys.exit("GAGAL: jalankan patch_x11_1.py dulu")
def sub(path, a, b):
    s = path.read_text(encoding="utf-8")
    if b in s:
        print("sudah:", path.name, a[:30].strip())
        return
    if s.count(a) != 1:
        sys.exit("GAGAL anchor %s: %r" % (path.name, a[:40]))
    path.write_text(s.replace(a, b), encoding="utf-8")
    print("ok:", path.name, a[:30].strip())
app = J / "LorieApp.java"
sub(app, "public class LorieApp extends Application {\n",
"""public class LorieApp extends android.content.ContextWrapper {
    private static LorieApp instance;

    public static synchronized LorieApp of(Context context) {
        if (instance == null) {
            instance = new LorieApp(context.getApplicationContext());
            instance.init();
        }
        return instance;
    }

    private LorieApp(Context base) {
        super(base);
    }

""")
sub(app, """    @Override
    @SuppressLint("ObsoleteSdkInt")
    public void onCreate() {
        super.onCreate();
""", """    @SuppressLint("ObsoleteSdkInt")
    private void init() {
""")
R1 = re.compile(r"\(\(LorieApp\) (\w+)\.getApplicationContext\(\)\)")
R2 = re.compile(r"\(\(LorieApp\) getApplication\(\)\)")
R3 = re.compile(r"\(LorieApp\) getApplication\(\)")
for f in sorted(J.rglob("*.java")):
    s = f.read_text(encoding="utf-8")
    t = R3.sub("LorieApp.of(this)", R2.sub("LorieApp.of(this)",
        R1.sub(r"LorieApp.of(\1)", s)))
    if t != s:
        f.write_text(t, encoding="utf-8")
        print("cast diganti:", f.name)
    if "(LorieApp)" in t:
        sys.exit("GAGAL: cast tersisa di " + f.name)
ep = J / "CmdEntryPoint.java"
sub(ep, '''res.getFile().replace("file:", "") : null;\n''',
'''res.getFile().replace("file:", "") : null;
        String forced = System.getenv("MOLINA_X11_LIB");
        if (forced != null && new java.io.File(forced).canRead())
            libPath = forced;
''')
sub(ep, 'catch (Exception e) {\n                Log.e("CmdEntryPoint", "Failed to dlopen',
 'catch (Throwable e) {\n                Log.e("CmdEntryPoint", "Failed to dlopen')
