import pathlib, re, sys
L = pathlib.Path("engines/termux-x11/lorie")
G = L / "build.gradle"
g = G.read_text(encoding="utf-8")
if "hostProjects" in g:
    i = g.index("\nandroid {")
    g = ("apply plugin: 'com.android.library'\n"
         "apply from: 'version.gradle'\n\n"
         'def hostApplicationId = "com.molina.suite"\n' + g[i:])
    a = "        minSdkVersion 24\n"
    assert g.count(a) == 1
    g = g.replace(a, a + "        externalNativeBuild.cmake.abiFilters"
                  " 'arm64-v8a'\n        ndk.abiFilters 'arm64-v8a'\n")
    g, n = re.subn(r"\ndependencies \{.*?\n\}\n", """
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.core:core:1.12.0'
    implementation 'androidx.preference:preference:1.2.1'
    implementation 'androidx.viewpager:viewpager:1.0.0'
    compileOnly project(':engines:termux-x11-stub')
}
""", g, count=1, flags=re.S)
    assert n == 1
    G.write_text(g, encoding="utf-8")
    print("lorie/build.gradle: ok")
else:
    print("lorie/build.gradle: sudah")
S = pathlib.Path("engines/termux-x11/stub/build.gradle")
t = S.read_text(encoding="utf-8")
S.write_text(t.replace("annotation:1.11.0", "annotation:1.8.2"),
             encoding="utf-8")
K = pathlib.Path("settings.gradle.kts")
k = K.read_text(encoding="utf-8")
if "termux-x11-lorie" not in k:
    k = k.rstrip("\n") + """
include(":engines:termux-x11-stub")
project(":engines:termux-x11-stub").projectDir = file("engines/termux-x11/stub")
include(":engines:termux-x11-lorie")
project(":engines:termux-x11-lorie").projectDir = file("engines/termux-x11/lorie")
"""
    K.write_text(k, encoding="utf-8")
    print("settings.gradle.kts: ok")
else:
    print("settings.gradle.kts: sudah")
