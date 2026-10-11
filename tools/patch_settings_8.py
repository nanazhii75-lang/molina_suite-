#!/usr/bin/env python3
"""Perbaikan 6a: style Molina.Text tanpa parent eksplisit dianggap turunan
dari 'Molina' (tidak ada) sehingga AAPT gagal. Deklarasikan tanpa induk."""
import os
import sys
import xml.etree.ElementTree as ET

PATH = "features/feature-settings/src/main/res/values/molina_settings_styles.xml"
OLD = '<style name="Molina.Text">'
NEW = '<style name="Molina.Text" parent="">'


def main():
    if not os.path.isfile(PATH):
        print("GAGAL: %s tidak ditemukan (jalankan dari root monorepo; patch_settings_6 sudah terpasang?)" % PATH)
        return 1
    with open(PATH, "r", encoding="utf-8", newline="") as handle:
        source = handle.read()
    if NEW in source:
        print("dilewati: perbaikan sudah terpasang")
        return 0
    count = source.count(OLD)
    if count != 1:
        print("GAGAL: anchor %s ditemukan %d kali (harus 1); tidak ada berkas yang ditulis" % (OLD, count))
        return 1
    result = source.replace(OLD, NEW, 1)
    try:
        ET.fromstring(result.encode("utf-8"))
    except ET.ParseError as exc:
        print("GAGAL: hasil bukan XML valid (%s); tidak ada berkas yang ditulis" % exc)
        return 1
    with open(PATH, "w", encoding="utf-8", newline="") as handle:
        handle.write(result)
    print("ditulis : " + PATH)
    return 0


if __name__ == "__main__":
    sys.exit(main())
