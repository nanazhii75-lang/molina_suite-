#!/usr/bin/env python3
"""Tahap 6a - fondasi desain Settings molina.

Token warna/dimensi, gaya teks, tombol outlined, kartu, chip, ikon outline.
Hanya menyentuh features/feature-settings/src/main/res. Idempotent; seluruh
validasi dijalankan sebelum ada berkas yang ditulis.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

TAG = "molina-settings-design-6a"
MOD = "features/feature-settings"
RES = MOD + "/src/main/res"
HDR = '<?xml version="1.0" encoding="utf-8"?>\n<!-- ' + TAG + ' -->\n'
ANS = 'xmlns:android="http://schemas.android.com/apk/res/android"'


def with_hdr(body):
    return HDR + body


# --------------------------------------------------------------------------
# values
# --------------------------------------------------------------------------
TOKENS = r"""<resources>
    <color name="ms_bg">#000000</color>
    <color name="ms_card">#141414</color>
    <color name="ms_card_border">#3A3320</color>
    <color name="ms_amber">#FFB300</color>
    <color name="ms_ripple">#29FFB300</color>
    <color name="ms_text_primary">#FFFFFF</color>
    <color name="ms_text_secondary">#9E9E9E</color>
    <color name="ms_status_on">#4CAF50</color>
    <color name="ms_status_off">#9E9E9E</color>
    <color name="ms_status_error">#E53935</color>
    <color name="ms_chip_on_fill">#264CAF50</color>
    <color name="ms_chip_off_fill">#1F9E9E9E</color>

    <dimen name="ms_space_xs">4dp</dimen>
    <dimen name="ms_space_sm">8dp</dimen>
    <dimen name="ms_space_md">16dp</dimen>
    <dimen name="ms_space_lg">24dp</dimen>
    <dimen name="ms_card_gap">12dp</dimen>
    <dimen name="ms_card_gap_half">6dp</dimen>
    <dimen name="ms_card_radius">12dp</dimen>
    <dimen name="ms_card_stroke">1dp</dimen>
    <dimen name="ms_button_radius">12dp</dimen>
</resources>
"""

STYLES = r"""<resources>
    <style name="Molina.Text">
        <item name="android:textColor">@color/ms_text_primary</item>
        <item name="android:fontFamily">sans-serif</item>
    </style>

    <style name="Molina.Text.Title">
        <item name="android:textSize">20sp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
    </style>

    <style name="Molina.Text.Item">
        <item name="android:textSize">16sp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
    </style>

    <style name="Molina.Text.Body">
        <item name="android:textSize">13sp</item>
        <item name="android:textColor">@color/ms_text_secondary</item>
    </style>

    <style name="Molina.Text.Caption">
        <item name="android:textSize">12sp</item>
        <item name="android:textColor">@color/ms_text_secondary</item>
    </style>

    <style name="Molina.Text.Mono">
        <item name="android:textSize">13sp</item>
        <item name="android:fontFamily">monospace</item>
    </style>

    <style name="Molina.Text.Chip" parent="Molina.Text.Caption">
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:background">@drawable/ms_bg_chip_off</item>
        <item name="android:paddingStart">10dp</item>
        <item name="android:paddingEnd">10dp</item>
        <item name="android:paddingTop">@dimen/ms_space_xs</item>
        <item name="android:paddingBottom">@dimen/ms_space_xs</item>
        <item name="android:maxLines">2</item>
        <item name="android:ellipsize">end</item>
    </style>

    <style name="Molina.Button.Outlined" parent="Widget.Material3.Button.OutlinedButton">
        <item name="strokeColor">@color/ms_outlined_stroke</item>
        <item name="strokeWidth">1dp</item>
        <item name="cornerRadius">@dimen/ms_button_radius</item>
        <item name="rippleColor">@color/ms_ripple</item>
        <item name="android:textColor">@color/ms_outlined_content</item>
        <item name="android:textAllCaps">false</item>
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:minHeight">44dp</item>
    </style>

    <style name="Molina.Button.Text" parent="Widget.Material3.Button.TextButton">
        <item name="cornerRadius">@dimen/ms_button_radius</item>
        <item name="rippleColor">@color/ms_ripple</item>
        <item name="android:textColor">@color/ms_outlined_content</item>
        <item name="android:textAllCaps">false</item>
        <item name="android:fontFamily">sans-serif-medium</item>
    </style>
</resources>
"""

# --------------------------------------------------------------------------
# color state lists
# --------------------------------------------------------------------------
SEL_CONTENT = r"""<selector """ + ANS + r""">
    <item android:state_enabled="false" android:color="#61FFB300" />
    <item android:color="@color/ms_amber" />
</selector>
"""

SEL_STROKE = r"""<selector """ + ANS + r""">
    <item android:state_enabled="false" android:color="@color/ms_card_border" />
    <item android:color="@color/ms_amber" />
</selector>
"""

SEL_ICON = r"""<selector """ + ANS + r""">
    <item android:state_enabled="false" android:color="#3DFFB300" />
    <item android:color="@color/ms_amber" />
</selector>
"""

# --------------------------------------------------------------------------
# drawables
# --------------------------------------------------------------------------
BG_CARD = r"""<shape """ + ANS + r""" android:shape="rectangle">
    <solid android:color="@color/ms_card" />
    <stroke
        android:width="@dimen/ms_card_stroke"
        android:color="@color/ms_card_border" />
    <corners android:radius="@dimen/ms_card_radius" />
</shape>
"""

BG_CARD_RIPPLE = r"""<ripple """ + ANS + r""" android:color="@color/ms_ripple">
    <item android:drawable="@drawable/ms_bg_card" />
    <item android:id="@android:id/mask">
        <shape android:shape="rectangle">
            <solid android:color="#FFFFFFFF" />
            <corners android:radius="@dimen/ms_card_radius" />
        </shape>
    </item>
</ripple>
"""

BG_CHIP_OFF = r"""<shape """ + ANS + r""" android:shape="rectangle">
    <solid android:color="@color/ms_chip_off_fill" />
    <stroke
        android:width="1dp"
        android:color="@color/ms_status_off" />
    <corners android:radius="100dp" />
</shape>
"""


def vector(paths):
    out = ('<vector ' + ANS + '\n'
           '    android:width="24dp"\n'
           '    android:height="24dp"\n'
           '    android:tint="?attr/colorControlNormal"\n'
           '    android:viewportWidth="24"\n'
           '    android:viewportHeight="24">\n')
    for d in paths:
        out += ('    <path\n'
                '        android:fillColor="#00000000"\n'
                '        android:pathData="' + d + '"\n'
                '        android:strokeColor="#FFFFFFFF"\n'
                '        android:strokeLineCap="round"\n'
                '        android:strokeLineJoin="round"\n'
                '        android:strokeWidth="1.8" />\n')
    return out + '</vector>\n'


IC_RUN = vector(["M8,5.5v13l10.5,-6.5z"])
IC_INSTALL = vector(["M12,4v11.5 M7.5,11l4.5,4.5l4.5,-4.5 M5,19.5h14"])
IC_UPDATE = vector(["M20,12a8,8 0,1 1,-8,-8", "M9.5,1.5l3,2.5l-3,2.5"])

# --------------------------------------------------------------------------
# layouts
# --------------------------------------------------------------------------
SETTINGS_ROW = r"""        <LinearLayout
            android:id="@+id/__ID__"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="@dimen/ms_card_gap"
            android:background="@drawable/ms_bg_card_ripple"
            android:clickable="true"
            android:focusable="true"
            android:minHeight="64dp"
            android:orientation="vertical"
            android:padding="@dimen/ms_space_md">

            <TextView
                style="@style/Molina.Text.Item"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/__TITLE__" />

            <TextView
                style="@style/Molina.Text.Body"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ms_space_xs"
                android:text="@string/__SUB__" />
        </LinearLayout>
"""


def settings_row(rid, title, sub):
    return (SETTINGS_ROW.replace("__ID__", rid)
            .replace("__TITLE__", title).replace("__SUB__", sub))


FRAGMENT_SETTINGS = (
    r"""<FrameLayout """ + ANS + r"""
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/ms_bg">

    <LinearLayout
        android:id="@+id/settings_menu"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="@dimen/ms_space_md">

        <TextView
            style="@style/Molina.Text.Title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="@dimen/ms_space_md"
            android:text="@string/settings_menu_title" />

"""
    + settings_row("settings_row_library", "settings_library_title", "settings_library_subtitle")
    + "\n"
    + settings_row("settings_row_debian", "settings_debian_title", "settings_debian_subtitle")
    + "\n"
    + settings_row("settings_row_packages", "settings_packages_title", "settings_packages_subtitle")
    + r"""    </LinearLayout>

    <FrameLayout
        android:id="@+id/settings_child_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/ms_bg"
        android:clickable="true"
        android:focusable="true"
        android:visibility="gone" />
</FrameLayout>
"""
)

FRAGMENT_PACKAGES = r"""<LinearLayout """ + ANS + r"""
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/ms_bg"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="@dimen/ms_space_md">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                style="@style/Molina.Text.Title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/settings_packages_title" />

            <TextView
                android:id="@+id/packages_status"
                style="@style/Molina.Text.Body"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ms_space_xs"
                android:text="@string/debian_status_checking" />
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/packages_refresh"
            style="@style/Molina.Button.Outlined"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/debian_refresh" />
    </LinearLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/packages_install_all"
        style="@style/Molina.Button.Outlined"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="@dimen/ms_space_md"
        android:layout_marginEnd="@dimen/ms_space_md"
        android:enabled="false"
        android:text="@string/packages_install_all" />

    <TextView
        style="@style/Molina.Text.Caption"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingStart="@dimen/ms_space_md"
        android:paddingTop="@dimen/ms_space_sm"
        android:paddingEnd="@dimen/ms_space_md"
        android:paddingBottom="@dimen/ms_space_xs"
        android:text="@string/packages_hint" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false">

        <LinearLayout
            android:id="@+id/packages_list"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingTop="@dimen/ms_space_xs"
            android:paddingBottom="@dimen/ms_space_lg" />
    </ScrollView>
</LinearLayout>
"""

ITEM_PACKAGE = r"""<LinearLayout """ + ANS + r"""
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingStart="@dimen/ms_space_md"
    android:paddingTop="@dimen/ms_card_gap_half"
    android:paddingEnd="@dimen/ms_space_md"
    android:paddingBottom="@dimen/ms_card_gap_half">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/ms_bg_card"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/ms_space_md"
        android:paddingTop="@dimen/ms_space_md"
        android:paddingEnd="@dimen/ms_space_sm"
        android:paddingBottom="@dimen/ms_space_md">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/package_item_title"
                style="@style/Molina.Text.Item"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />

            <TextView
                android:id="@+id/package_item_desc"
                style="@style/Molina.Text.Body"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ms_space_xs" />

            <TextView
                android:id="@+id/package_item_status"
                style="@style/Molina.Text.Chip"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ms_space_sm" />
        </LinearLayout>

        <ImageButton
            android:id="@+id/package_item_install"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/packages_install"
            android:src="@drawable/ic_package_install"
            android:tint="@color/ms_icon_tint" />

        <ImageButton
            android:id="@+id/package_item_update"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/packages_update"
            android:src="@drawable/ic_package_update"
            android:tint="@color/ms_icon_tint" />
    </LinearLayout>
</LinearLayout>
"""

FRAGMENT_LIBRARY = r"""<LinearLayout """ + ANS + r"""
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/ms_bg"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/ms_space_md"
        android:paddingTop="@dimen/ms_space_md"
        android:paddingEnd="@dimen/ms_space_sm"
        android:paddingBottom="@dimen/ms_space_sm">

        <TextView
            style="@style/Molina.Text.Title"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/settings_library_title" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/library_restore"
            style="@style/Molina.Button.Text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/library_restore" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/library_add"
            style="@style/Molina.Button.Outlined"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/ms_space_xs"
            android:text="@string/library_add" />
    </LinearLayout>

    <TextView
        android:id="@+id/library_error"
        style="@style/Molina.Text.Body"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="@dimen/ms_space_md"
        android:textColor="@color/ms_status_error"
        android:visibility="gone" />

    <TextView
        android:id="@+id/library_empty"
        style="@style/Molina.Text.Body"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:padding="@dimen/ms_space_lg"
        android:text="@string/library_empty"
        android:visibility="gone" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/library_list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:paddingTop="@dimen/ms_space_xs"
        android:paddingBottom="@dimen/ms_space_lg" />
</LinearLayout>
"""

ITEM_LIBRARY = r"""<LinearLayout """ + ANS + r"""
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:clickable="true"
    android:focusable="true"
    android:orientation="vertical"
    android:paddingStart="@dimen/ms_space_md"
    android:paddingTop="@dimen/ms_card_gap_half"
    android:paddingEnd="@dimen/ms_space_md"
    android:paddingBottom="@dimen/ms_card_gap_half">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/ms_bg_card_ripple"
        android:duplicateParentState="true"
        android:gravity="center_vertical"
        android:minHeight="64dp"
        android:orientation="horizontal"
        android:paddingStart="@dimen/ms_space_md"
        android:paddingTop="@dimen/ms_space_md"
        android:paddingEnd="@dimen/ms_space_sm"
        android:paddingBottom="@dimen/ms_space_md">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/library_item_name"
                style="@style/Molina.Text.Item"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:ellipsize="end"
                android:maxLines="1" />

            <TextView
                android:id="@+id/library_item_content"
                style="@style/Molina.Text.Mono"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ms_space_xs"
                android:ellipsize="end"
                android:maxLines="2"
                android:textColor="@color/ms_text_secondary" />

            <TextView
                android:id="@+id/library_item_note"
                style="@style/Molina.Text.Caption"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ms_space_xs"
                android:ellipsize="end"
                android:maxLines="2"
                android:visibility="gone" />

            <TextView
                android:id="@+id/library_item_requires"
                style="@style/Molina.Text.Caption"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ms_space_xs"
                android:textColor="@color/ms_status_error"
                android:visibility="gone" />
        </LinearLayout>

        <ImageButton
            android:id="@+id/library_item_run"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/library_run"
            android:src="@drawable/ic_library_run"
            android:tint="@color/ms_icon_tint" />
    </LinearLayout>
</LinearLayout>
"""

FRAGMENT_DEBIAN = r"""<LinearLayout """ + ANS + r"""
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/ms_bg"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="@dimen/ms_space_md">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                style="@style/Molina.Text.Title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/settings_debian_title" />

            <TextView
                android:id="@+id/debian_status"
                style="@style/Molina.Text.Body"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ms_space_xs"
                android:text="@string/debian_status_checking" />
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/debian_refresh"
            style="@style/Molina.Button.Outlined"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/debian_refresh" />
    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false">

        <LinearLayout
            android:id="@+id/debian_actions"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingTop="@dimen/ms_space_xs"
            android:paddingBottom="@dimen/ms_space_lg" />
    </ScrollView>
</LinearLayout>
"""

ITEM_DEBIAN = r"""<LinearLayout """ + ANS + r"""
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingStart="@dimen/ms_space_md"
    android:paddingTop="@dimen/ms_card_gap_half"
    android:paddingEnd="@dimen/ms_space_md"
    android:paddingBottom="@dimen/ms_card_gap_half">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/ms_bg_card"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/ms_space_md"
        android:paddingTop="@dimen/ms_space_md"
        android:paddingEnd="@dimen/ms_space_sm"
        android:paddingBottom="@dimen/ms_space_md">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/debian_item_title"
                style="@style/Molina.Text.Item"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />

            <TextView
                android:id="@+id/debian_item_desc"
                style="@style/Molina.Text.Body"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ms_space_xs" />

            <TextView
                android:id="@+id/debian_item_command"
                style="@style/Molina.Text.Mono"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ms_space_sm"
                android:textIsSelectable="true" />
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/debian_item_copy"
            style="@style/Molina.Button.Text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/debian_copy" />

        <ImageButton
            android:id="@+id/debian_item_run"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/library_run"
            android:src="@drawable/ic_library_run"
            android:tint="@color/ms_icon_tint" />
    </LinearLayout>
</LinearLayout>
"""

# (path relatif terhadap RES, isi, harus sudah ada?)
PLAN = [
    ("values/molina_settings_tokens.xml", TOKENS, False),
    ("values/molina_settings_styles.xml", STYLES, False),
    ("color/ms_outlined_content.xml", SEL_CONTENT, False),
    ("color/ms_outlined_stroke.xml", SEL_STROKE, False),
    ("color/ms_icon_tint.xml", SEL_ICON, False),
    ("drawable/ms_bg_card.xml", BG_CARD, False),
    ("drawable/ms_bg_card_ripple.xml", BG_CARD_RIPPLE, False),
    ("drawable/ms_bg_chip_off.xml", BG_CHIP_OFF, False),
    ("drawable/ic_library_run.xml", IC_RUN, True),
    ("drawable/ic_package_install.xml", IC_INSTALL, True),
    ("drawable/ic_package_update.xml", IC_UPDATE, True),
    ("layout/fragment_settings.xml", FRAGMENT_SETTINGS, True),
    ("layout/fragment_packages.xml", FRAGMENT_PACKAGES, True),
    ("layout/item_package.xml", ITEM_PACKAGE, True),
    ("layout/fragment_library.xml", FRAGMENT_LIBRARY, True),
    ("layout/item_library_entry.xml", ITEM_LIBRARY, True),
    ("layout/fragment_debian.xml", FRAGMENT_DEBIAN, True),
    ("layout/item_debian_action.xml", ITEM_DEBIAN, True),
]

REF_RE = re.compile(r"@(color|dimen|style|drawable|string)/([\w.]+)")
ID_RE = re.compile(r"@\+id/(\w+)")


def read(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


def collect_defined(root):
    defined = {"color": set(), "dimen": set(), "style": set(), "drawable": set(), "string": set()}
    for rel, body, _ in PLAN:
        kind_dir, _, fname = rel.partition("/")
        stem = fname[:-4]
        if kind_dir == "color":
            defined["color"].add(stem)
        elif kind_dir == "drawable":
            defined["drawable"].add(stem)
        elif kind_dir == "values":
            for kind in ("color", "dimen", "style"):
                defined[kind].update(re.findall(r'<%s name="([\w.]+)"' % kind, body))
    drawable_dir = os.path.join(root, RES, "drawable")
    if os.path.isdir(drawable_dir):
        for name in os.listdir(drawable_dir):
            if name.endswith(".xml"):
                defined["drawable"].add(name[:-4])
    strings_path = os.path.join(root, RES, "values", "strings.xml")
    if os.path.isfile(strings_path):
        defined["string"].update(re.findall(r'name="(\w+)"', read(strings_path)))
    return defined


def main():
    root = os.getcwd()
    if not os.path.isdir(os.path.join(root, MOD)):
        print("GAGAL: jalankan dari root monorepo (folder %s tidak ditemukan)" % MOD)
        return 1

    defined = collect_defined(root)
    errors = []
    writes = []
    skipped = []

    for rel, body, must_exist in PLAN:
        content = with_hdr(body)
        full = os.path.join(root, RES, rel)

        try:
            ET.fromstring(content.encode("utf-8"))
        except ET.ParseError as exc:
            errors.append("%s: XML baru tidak valid (%s)" % (rel, exc))
            continue

        for kind, name in REF_RE.findall(content):
            if name not in defined[kind]:
                errors.append("%s: referensi @%s/%s tidak ditemukan" % (rel, kind, name))

        exists = os.path.isfile(full)
        if must_exist and not exists:
            errors.append("%s: berkas yang mau diganti tidak ada (repo belum mutakhir?)" % rel)
            continue

        if exists:
            old = read(full)
            if TAG in old:
                skipped.append(rel)
                continue
            if not must_exist:
                errors.append("%s: sudah ada dan bukan dari patch ini; tidak ditimpa" % rel)
                continue
            if rel.startswith("layout/"):
                missing = set(ID_RE.findall(old)) - set(ID_RE.findall(content))
                if missing:
                    errors.append("%s: ID lama hilang di layout baru: %s" % (rel, ", ".join(sorted(missing))))
                    continue

        writes.append((full, rel, content))

    if errors:
        print("VALIDASI GAGAL, tidak ada berkas yang ditulis:")
        for message in errors:
            print("  - " + message)
        return 1

    for full, rel, content in writes:
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(content)
        print("ditulis : " + rel)
    for rel in skipped:
        print("dilewati: %s (patch sudah terpasang)" % rel)
    print("Selesai: %d ditulis, %d dilewati." % (len(writes), len(skipped)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
