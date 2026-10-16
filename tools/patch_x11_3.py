import pathlib, re, sys
L = pathlib.Path("engines/termux-x11/lorie")
M = L / "src/main/AndroidManifest.xml"
s = M.read_text(encoding="utf-8")
if 'android:name=".LorieApp"' in s:
    s = s.replace('<application\n        android:name=".LorieApp"\n'
                  '        android:allowBackup="false"\n', "<application\n", 1)
    def fix(m):
        b = m.group(0)
        if 'android:name=".MainActivity"' not in b and \
           'android:name=".LoriePreferences"' not in b:
            return b
        b = re.sub(r"\s*<intent-filter>.*?</intent-filter>", "", b, flags=re.S)
        b = re.sub(r"\s*<meta-data[^>]*/>", "", b)
        return b.replace('android:exported="true"', 'android:exported="false"')
    s = re.sub(r"<activity\b[^>]*?(?:/>|>.*?</activity>)", fix, s, flags=re.S)
    s = s.replace('android:exported="true" tools:ignore="ExportedReceiver"',
                  'android:exported="false"')
    s = re.sub(r"\s*<queries>.*?</queries>", "", s, flags=re.S)
    M.write_text(s, encoding="utf-8")
    print("manifest: ok")
else:
    print("manifest: sudah")
for bad in ("LAUNCHER", "SECONDARY_HOME", "com.termux"):
    if bad in M.read_text(encoding="utf-8"):
        print("PERIKSA manifest masih memuat:", bad)
