#!/usr/bin/env python3
"""Tahap 6b - halaman Paket: hitungan n/6, chip status berwarna.

Menyentuh features/feature-settings saja (res + PackagesFragment.kt).
Idempotent; semua validasi dijalankan sebelum ada berkas yang ditulis.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

TAG = "molina-settings-design-6b"
MOD = "features/feature-settings"
RES = MOD + "/src/main/res"
KT = MOD + "/src/main/kotlin/com/molina/suite/feature/settings/packages/PackagesFragment.kt"
ANS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
HDR = '<?xml version="1.0" encoding="utf-8"?>\n<!-- ' + TAG + ' -->\n'

CHIP_ON = HDR + """<shape """ + ANS + """ android:shape="rectangle">
    <solid android:color="@color/ms_chip_on_fill" />
    <stroke
        android:width="1dp"
        android:color="@color/ms_status_on" />
    <corners android:radius="100dp" />
</shape>
"""

STRINGS = HDR + """<resources>
    <string name="ms_packages_count">%1$d/%2$d Terpasang</string>
</resources>
"""

NEW_FILES = [
    ("drawable/ms_bg_chip_on.xml", CHIP_ON),
    ("values/molina_settings_strings.xml", STRINGS),
]

KT_MARKER = "private fun applyChip(chip: TextView, installed: Boolean?)"

E2_OLD = (
    "                    null -> R.string.packages_unknown\n"
    "                }\n"
    "            )\n"
)
E4_OLD = "    private fun setEnabled(button: ImageButton, enabled: Boolean) {\n"

KT_EDITS = [
    (
        "teks hitungan",
        "getString(R.string.packages_summary, current.installed.count { it.value }, current.installed.size)",
        "getString(R.string.ms_packages_count, current.installed.count { it.value }, current.installed.size)",
    ),
    (
        "pemanggilan chip",
        E2_OLD,
        E2_OLD + "            applyChip(row.status, installed)\n",
    ),
    (
        "alpha ikon",
        "        button.alpha = if (enabled) 1f else 0.35f\n",
        "        // Peredupan ikon ditangani selector tint ms_icon_tint, bukan alpha, agar tidak ganda.\n"
        "        button.alpha = 1f\n",
    ),
    (
        "fungsi applyChip",
        E4_OLD,
        "    /** Chip status: hijau lembut bila terpasang, abu-abu netral bila belum ada atau belum diketahui. */\n"
        "    " + KT_MARKER + " {\n"
        "        val on = installed == true\n"
        "        chip.setBackgroundResource(if (on) R.drawable.ms_bg_chip_on else R.drawable.ms_bg_chip_off)\n"
        "        chip.setTextColor(chip.context.getColor(if (on) R.color.ms_status_on else R.color.ms_status_off))\n"
        "    }\n"
        "\n" + E4_OLD,
    ),
]

KT_REFS = [
    ("drawable", "ms_bg_chip_on"),
    ("drawable", "ms_bg_chip_off"),
    ("color", "ms_status_on"),
    ("color", "ms_status_off"),
    ("string", "ms_packages_count"),
]


def read(path):
    with open(path, "r", encoding="utf-8", newline="") as handle:
        return handle.read()


def defined_resources(root):
    found = {"drawable": set(), "color": set(), "string": set()}
    res = os.path.join(root, RES)
    drawable_dir = os.path.join(res, "drawable")
    if os.path.isdir(drawable_dir):
        for name in os.listdir(drawable_dir):
            if name.endswith(".xml"):
                found["drawable"].add(name[:-4])
    values_dir = os.path.join(res, "values")
    if os.path.isdir(values_dir):
        for name in os.listdir(values_dir):
            if name.endswith(".xml"):
                text = read(os.path.join(values_dir, name))
                for kind in ("color", "string"):
                    found[kind].update(re.findall(r'<%s name="([\w.]+)"' % kind, text))
    for rel, body in NEW_FILES:
        kind_dir, _, fname = rel.partition("/")
        if kind_dir == "drawable":
            found["drawable"].add(fname[:-4])
        else:
            for kind in ("color", "string"):
                found[kind].update(re.findall(r'<%s name="([\w.]+)"' % kind, body))
    return found


def main():
    root = os.getcwd()
    kt_path = os.path.join(root, KT)
    if not os.path.isfile(kt_path):
        print("GAGAL: %s tidak ditemukan (jalankan dari root monorepo, setelah git pull)" % KT)
        return 1

    errors = []
    writes = []
    skipped = []

    for rel, body in NEW_FILES:
        try:
            ET.fromstring(body.encode("utf-8"))
        except ET.ParseError as exc:
            errors.append("%s: XML baru tidak valid (%s)" % (rel, exc))
            continue
        full = os.path.join(root, RES, rel)
        if os.path.isfile(full):
            if TAG in read(full):
                skipped.append(rel)
            else:
                errors.append("%s: sudah ada dan bukan dari patch ini; tidak ditimpa" % rel)
            continue
        writes.append((full, rel, body))

    defined = defined_resources(root)
    for kind, name in KT_REFS:
        if name not in defined[kind]:
            hint = " (patch_settings_6 belum terpasang?)" if name.startswith("ms_") and kind != "string" else ""
            errors.append("referensi R.%s.%s tidak ditemukan%s" % (kind, name, hint))

    source = read(kt_path)
    new_source = source
    if "\r\n" in source:
        errors.append("PackagesFragment.kt memakai CRLF; patch hanya mendukung LF")
    elif KT_MARKER in source:
        skipped.append("PackagesFragment.kt")
    else:
        for label, old, new in KT_EDITS:
            count = new_source.count(old)
            if count != 1:
                errors.append("PackagesFragment.kt [%s]: anchor ditemukan %d kali (harus 1)" % (label, count))
                continue
            new_source = new_source.replace(old, new, 1)
        if not errors and new_source != source:
            writes.append((kt_path, "PackagesFragment.kt", new_source))

    if errors:
        print("VALIDASI GAGAL, tidak ada berkas yang ditulis:")
        for message in errors:
            print("  - " + message)
        return 1

    for full, rel, content in writes:
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8", newline="") as handle:
            handle.write(content)
        print("ditulis : " + rel)
    for rel in skipped:
        print("dilewati: %s (patch sudah terpasang)" % rel)
    print("Selesai: %d ditulis, %d dilewati." % (len(writes), len(skipped)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
