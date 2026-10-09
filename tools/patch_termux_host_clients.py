#!/usr/bin/env python3
"""Tahap 2b: klien terminal (view client, session client, daftar sesi, root view,
toolbar, FullScreenWorkAround) dipindah dari tipe TermuxActivity ke TermuxHost.
Dry-run default; --apply untuk menulis. Idempoten. Semua berkas ditulis atau tidak sama sekali."""
import difflib
import os
import re
import sys

APPLY = '--apply' in sys.argv
ROOT = 'engines/termux'
E = ROOT + '/app/src/main/java/com/molina/suite/terminal'

HOST = ['getTerminalView', 'getTermuxService', 'getPreferences', 'getProperties', 'getDrawer',
        'getTerminalToolbarViewPager', 'getExtraKeysView', 'setExtraKeysView',
        'getTermuxActivityRootView', 'getTermuxActivityBottomSpaceView',
        'getTermuxTerminalViewClient', 'getTermuxTerminalSessionClient', 'getCurrentSession',
        'isTerminalViewSelected', 'isVisible', 'isOnResumeAfterOnCreate', 'toggleTerminalToolbar',
        'finishActivityIfNotFinishing', 'showToast', 'termuxSessionListNotifyUpdated',
        'getNavBarHeight', 'getHostActivity']
ACT_OK = {'getString', 'getSystemService', 'findViewById', 'getWindow', 'getLayoutInflater',
          'getPackageManager'}


class Gagal(Exception):
    pass


def read(p):
    if not os.path.isfile(p):
        raise Gagal('berkas tidak ada: ' + p)
    with open(p, encoding='utf-8', newline='') as f:
        return f.read()


def eol_of(t):
    return '\r\n' if '\r\n' in t else '\n'


def sub_n(text, pattern, repl, n, label):
    new, c = re.subn(pattern, repl, text, flags=re.M)
    if c != n:
        raise Gagal('%s: ditemukan %d, seharusnya %d' % (label, c, n))
    return new


def code_lines(text):
    for i, line in enumerate(text.splitlines(), 1):
        if line.strip().startswith(('*', '/*', '//')):
            continue
        yield i, line


def leftover(text, pattern, label):
    bad = ['baris %d: %s' % (i, l.strip()) for i, l in code_lines(text) if re.search(pattern, l)]
    if bad:
        raise Gagal('%s: sisa pola %s -> %s' % (label, pattern, ' | '.join(bad)))


def check_hosts(text, label):
    for i, l in code_lines(text):
        for m in re.finditer(r'\bmActivity\.(\w+)\(', l):
            if m.group(1) not in ACT_OK:
                raise Gagal('%s baris %d: mActivity.%s() bukan metode Activity yang diizinkan' % (label, i, m.group(1)))
        for m in re.finditer(r'\bmHost\.(\w+)\(', l):
            if m.group(1) not in HOST:
                raise Gagal('%s baris %d: mHost.%s() tidak ada di TermuxHost' % (label, i, m.group(1)))


def swap_import(t, label):
    return sub_n(t, r'^import com\.molina\.suite\.terminal\.app\.TermuxActivity;',
                 'import com.molina.suite.terminal.app.TermuxHost;', 1, label + ' import')


def ensure_activity_import(t):
    if 'import android.app.Activity;' in t:
        return t
    eol = eol_of(t)
    new, c = re.subn(r'^(import android\.)', lambda m: 'import android.app.Activity;' + eol + m.group(1),
                     t, count=1, flags=re.M)
    if c != 1:
        raise Gagal('tidak ada import android.* sebagai jangkar import Activity')
    return new


def split_field(t, mods, label):
    eol = eol_of(t)
    pat = r'^([ \t]*)' + re.escape(mods) + r'TermuxActivity mActivity;'
    return sub_n(t, pat, lambda m: '%s%sTermuxHost mHost;%s%s%sActivity mActivity;' % (
        m.group(1), mods, eol, m.group(1), mods), 1, label + ' field')


def split_assign(t, label):
    eol = eol_of(t)
    return sub_n(t, r'^([ \t]*)this\.mActivity = activity;',
                 lambda m: '%sthis.mHost = host;%s%sthis.mActivity = host.getHostActivity();' % (
                     m.group(1), eol, m.group(1)), 1, label + ' assign')


def route_host_calls(t):
    pat = r'\bmActivity\.(' + '|'.join(HOST) + r')\('
    return re.subn(pat, r'mHost.\1(', t)


def p_view_client(t):
    L = 'TermuxTerminalViewClient'
    t = swap_import(t, L)
    t = ensure_activity_import(t)
    t = split_field(t, 'final ', L)
    t = sub_n(t, r'public TermuxTerminalViewClient\(TermuxActivity activity, ',
              'public TermuxTerminalViewClient(TermuxHost host, ', 1, L + ' ctor')
    t = split_assign(t, L)
    t = sub_n(t, r'public TermuxActivity getActivity\(\) \{', 'public TermuxHost getHost() {', 1, L + ' getActivity')
    t = sub_n(t, r'^([ \t]*)return mActivity;', lambda m: m.group(1) + 'return mHost;', 1, L + ' return')
    t, c = route_host_calls(t)
    check_hosts(t, L)
    leftover(t, r'\bTermuxActivity\b', L)
    leftover(t, r'(?<![\w.])activity\b', L)
    return t, c + 6


def p_session_client(t):
    L = 'TermuxTerminalSessionClient'
    t = swap_import(t, L)
    t = ensure_activity_import(t)
    t = split_field(t, 'private final ', L)
    t = sub_n(t, r'public TermuxTerminalSessionClient\(TermuxActivity activity\) \{',
              'public TermuxTerminalSessionClient(TermuxHost host) {', 1, L + ' ctor')
    t = split_assign(t, L)
    t, c = route_host_calls(t)
    check_hosts(t, L)
    leftover(t, r'\bTermuxActivity\b', L)
    leftover(t, r'(?<![\w.])activity\b', L)
    return t, c + 4


def p_sessions_list(t):
    L = 'TermuxSessionsListViewController'
    t = swap_import(t, L)
    t = ensure_activity_import(t)
    t = split_field(t, 'final ', L)
    t = sub_n(t, r'public TermuxSessionsListViewController\(TermuxActivity activity, ',
              'public TermuxSessionsListViewController(TermuxHost host, ', 1, L + ' ctor')
    t = sub_n(t, r'\bsuper\(activity\b', 'super(host.getHostActivity()', 1, L + ' super')
    t = split_assign(t, L)
    t, c = route_host_calls(t)
    check_hosts(t, L)
    leftover(t, r'\bTermuxActivity\b', L)
    leftover(t, r'(?<![\w.])activity\b', L)
    return t, c + 5


def p_root_view(t):
    L = 'TermuxActivityRootView'
    eol = eol_of(t)
    t = sub_n(t, r'^import com\.molina\.suite\.terminal\.app\.TermuxActivity;',
              lambda m: m.group(0) + eol + 'import com.molina.suite.terminal.app.TermuxHost;', 1, L + ' import')
    t = sub_n(t, r'public TermuxActivity mActivity;', 'public TermuxHost mHost;', 1, L + ' field')
    t = sub_n(t, r'public void setActivity\(TermuxActivity activity\) \{',
              'public void setHost(TermuxHost host) {', 1, L + ' setter')
    t = sub_n(t, r'^([ \t]*)mActivity = activity;', lambda m: m.group(1) + 'mHost = host;', 1, L + ' assign')
    t = sub_n(t, r'mActivity == null \|\| !mActivity\.isVisible\(\)',
              'mHost == null || !mHost.isVisible()', 1, L + ' isVisible')
    t = sub_n(t, r'mActivity\.getTermuxActivityBottomSpaceView\(\)',
              'mHost.getTermuxActivityBottomSpaceView()', 1, L + ' bottomSpace')
    leftover(t, r'\bmActivity\b', L)
    check_hosts(t, L)
    return t, 6


def p_fullscreen(t):
    L = 'FullScreenWorkAround'
    t = swap_import(t, L)
    t = sub_n(t, r'public static void apply\(TermuxActivity activity\) \{',
              'public static void apply(TermuxHost host) {', 1, L + ' apply')
    t = sub_n(t, r'new FullScreenWorkAround\(activity\);', 'new FullScreenWorkAround(host);', 1, L + ' new')
    t = sub_n(t, r'private FullScreenWorkAround\(TermuxActivity activity\) \{',
              'private FullScreenWorkAround(TermuxHost host) {', 1, L + ' ctor')
    t = sub_n(t, r'\bactivity\.findViewById\(android\.R\.id\.content\)',
              'host.getHostActivity().findViewById(android.R.id.content)', 1, L + ' content')
    t = sub_n(t, r'\bactivity\.getNavBarHeight\(\)', 'host.getNavBarHeight()', 1, L + ' navbar')
    leftover(t, r'\bTermuxActivity\b', L)
    leftover(t, r'(?<![\w.])activity\b', L)
    return t, 6


def p_toolbar(t):
    L = 'TerminalToolbarViewPager'
    t = swap_import(t, L)
    t = sub_n(t, r'final TermuxActivity mActivity;', 'final TermuxHost mHost;', 2, L + ' field')
    t = sub_n(t, r'public PageAdapter\(TermuxActivity activity, ', 'public PageAdapter(TermuxHost host, ', 1, L + ' PageAdapter')
    t = sub_n(t, r'public OnPageChangeListener\(TermuxActivity activity, ',
              'public OnPageChangeListener(TermuxHost host, ', 1, L + ' OnPageChangeListener')
    t = sub_n(t, r'this\.mActivity = activity;', 'this.mHost = host;', 2, L + ' assign')
    t = sub_n(t, r'LayoutInflater\.from\(mActivity\)', 'LayoutInflater.from(mHost.getHostActivity())', 1, L + ' inflater')
    t, c = re.subn(r'\bmActivity\b', 'mHost', t)
    check_hosts(t, L)
    leftover(t, r'\bmActivity\b', L)
    leftover(t, r'\bTermuxActivity\b', L)
    leftover(t, r'(?<![\w.])activity\b', L)
    return t, c + 8


def p_extra_keys(t):
    L = 'TermuxTerminalExtraKeys'
    t = sub_n(t, r'mTermuxTerminalViewClient\.getActivity\(\)\.getDrawer\(\)',
              'mTermuxTerminalViewClient.getHost().getDrawer()', 1, L)
    return t, 1


def p_activity(t):
    L = 'TermuxActivity'
    t = sub_n(t, r'mTermuxActivityRootView\.setActivity\(this\);',
              'mTermuxActivityRootView.setHost(this);', 1, L)
    return t, 1


def p_host(t):
    L = 'TermuxHost'
    eol = eol_of(t)
    t = sub_n(t, r'^([ \t]*)boolean isOnResumeAfterOnCreate\(\);',
              lambda m: m.group(0) + eol + eol + m.group(1) + 'int getNavBarHeight();', 1, L)
    return t, 1


CHANGES_BLOCK = """
## Tahap 2b: klien terminal memakai TermuxHost, bukan TermuxActivity
- TermuxTerminalViewClient, TermuxTerminalSessionClient, TermuxSessionsListViewController: konstruktor menerima TermuxHost. Metode host lewat mHost, kebutuhan Context/Activity lewat mActivity (= host.getHostActivity()).
- TermuxTerminalViewClient.getActivity() diganti getHost() (pemakai: TermuxTerminalExtraKeys).
- TermuxActivityRootView.setActivity(TermuxActivity) diganti setHost(TermuxHost); TermuxActivity memanggil setHost(this).
- FullScreenWorkAround.apply dan TerminalToolbarViewPager (PageAdapter, OnPageChangeListener) menerima TermuxHost.
- TermuxHost: tambah getNavBarHeight() (metode ke-22).
- Perilaku TermuxActivity tidak berubah; TermuxActivity tetap implementasi TermuxHost.
"""


def p_changes(t):
    if 'Tahap 2b' in t:
        raise Gagal('MOLINA-CHANGES.md sudah memuat Tahap 2b tetapi berkas kode belum; keadaan campur, periksa manual')
    eol = eol_of(t)
    add = CHANGES_BLOCK.replace('\n', eol)
    if not t.endswith(eol):
        t += eol
    return t + add, 1


A = E + '/app/'
SPECS = [
    (A + 'terminal/TermuxTerminalViewClient.java', p_view_client, 'final TermuxHost mHost;'),
    (A + 'terminal/TermuxTerminalSessionClient.java', p_session_client, 'private final TermuxHost mHost;'),
    (A + 'terminal/TermuxSessionsListViewController.java', p_sessions_list, 'final TermuxHost mHost;'),
    (A + 'terminal/TermuxActivityRootView.java', p_root_view, 'public TermuxHost mHost;'),
    (A + 'terminal/io/FullScreenWorkAround.java', p_fullscreen, 'apply(TermuxHost host)'),
    (A + 'terminal/io/TerminalToolbarViewPager.java', p_toolbar, 'final TermuxHost mHost;'),
    (A + 'terminal/io/TermuxTerminalExtraKeys.java', p_extra_keys, '.getHost().getDrawer()'),
    (A + 'TermuxActivity.java', p_activity, 'setHost(this)'),
    (A + 'TermuxHost.java', p_host, 'int getNavBarHeight();'),
    (ROOT + '/MOLINA-CHANGES.md', p_changes, None),
]


def main():
    if not os.path.isdir(ROOT):
        print('GAGAL: jalankan dari /storage/emulated/0/molina-suite (engines/termux tidak ada)')
        return 1
    plan = []
    try:
        done_flags = []
        for path, fn, marker in SPECS[:-1]:
            old = read(path)
            done_flags.append(marker in old)
        if any(done_flags) and not all(done_flags):
            names = [os.path.basename(s[0]) for s, d in zip(SPECS[:-1], done_flags) if d]
            raise Gagal('keadaan campur: sebagian berkas sudah diubah (%s); periksa git diff' % ', '.join(names))
        all_done = all(done_flags)
        for path, fn, marker in SPECS:
            old = read(path)
            if all_done:
                plan.append((path, old, old, 0))
                continue
            new, c = fn(old)
            plan.append((path, old, new, c))
    except Gagal as e:
        print('GAGAL: %s' % e)
        print('Tidak ada berkas yang ditulis.')
        return 1

    for path, old, new, c in plan:
        if old == new:
            continue
        print('=' * 72)
        print(path)
        for line in difflib.unified_diff(old.splitlines(), new.splitlines(), 'lama', 'baru', n=0, lineterm=''):
            print(line)

    print('=' * 72)
    print('RINGKASAN (%s)' % ('APPLY' if APPLY else 'DRY-RUN'))
    for path, old, new, c in plan:
        print('  %-70s %s' % (path.replace(ROOT + '/', ''), 'sudah diterapkan' if old == new else 'ubah (%d penggantian)' % c))

    if all(o == n for _, o, n, _ in plan):
        print('Tidak ada perubahan (idempoten).')
        return 0
    if not APPLY:
        print('Dry-run: tidak ada berkas ditulis. Jalankan dengan --apply untuk menulis.')
        return 0
    for path, old, new, c in plan:
        if old != new:
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(new)
    print('Selesai: %d berkas ditulis.' % sum(1 for _, o, n, _ in plan if o != n))
    return 0


sys.exit(main())
