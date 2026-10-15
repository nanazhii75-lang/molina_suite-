#!/usr/bin/env python3
"""Konversi PDF ke Markdown untuk molina-suite.

Masukan : berkas XML dari `pdftohtml -xml -i -fontfullname -enc UTF-8` (poppler-utils).
Keluaran: Markdown ke stdout. Pesan galat ke stderr.

Yang dideteksi: heading (dari ukuran font, atau baris tebal yang berdiri sendiri), paragraf
(baris yang terpotong digabung dan kata yang terpisah tanda hubung disambung), daftar bullet dan
bernomor (bertingkat), tabel, blok kode (font monospasi), tebal/miring, tautan, tata letak dua
kolom, serta header/footer berulang dan nomor halaman (dibuang).

Kode keluar: 0 berhasil, 2 argumen salah, 3 PDF tanpa teks (kemungkinan hasil scan),
4 XML tidak terbaca.
"""
import math
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter

EXIT_USAGE = 2
EXIT_NO_TEXT = 3
EXIT_BAD_XML = 4

CONTROL_CHARS = re.compile("[\x00-\x08\x0b\x0c\x0e-\x1f]")
NORMALIZE = str.maketrans({
    "\ufb00": "ff", "\ufb01": "fi", "\ufb02": "fl", "\ufb03": "ffi", "\ufb04": "ffl",
    "\ufb05": "st", "\ufb06": "st", "\u00ad": "", "\u00a0": " ", "\u200b": "",
    "\u2060": "", "\ufeff": "", "\u2002": " ", "\u2003": " ", "\u2009": " ",
})
MONO_FONT = re.compile(
    r"(mono|courier|consolas|menlo|inconsolata|lucida ?console|source ?code|fira ?code|"
    r"cmtt|typewriter|andale)", re.I)
BOLD_FONT = re.compile(r"(bold|black|heavy|semibold|demibold|extrabold|cmbx|cmbsy|[-,]bd\\b)", re.I)
ITALIC_FONT = re.compile(r"(italic|oblique|cmti|cmsl|cmmi)", re.I)
SUP_OPEN = "\ue000"
SUP_CLOSE = "\ue001"
PAGE_NUMBER = re.compile(
    r"^(?:(?:page|halaman|hal\.?|p\.)\s*)?[-\u2013\u2014\s]*(?:\d{1,4}|[ivxlcdm]{2,7})"
    r"(?:\s*(?:/|of|dari)\s*\d{1,4})?[-\u2013\u2014\s]*$", re.I)
BULLET = re.compile(r"^([\u2022\u25cf\u25cb\u25aa\u25a0\u25e6\u2023\u25b8\u25ba\u00b7\u2219\uf0b7\u2043-]|[\u2013\u2014*])\s+")
BULLET_ONLY = re.compile(r"^[\u2022\u25cf\u25cb\u25aa\u25a0\u25e6\u2023\u25b8\u25ba\u00b7\u2219\uf0b7\u2043\u2013\u2014-]$")
NUMBERED = re.compile(r"^\(?(\d{1,3})[.)]\s+")
LETTERED = re.compile(r"^(\(?(?:[a-z]|[ivx]{1,4})\))\s+", re.I)
DOT_LEADER = re.compile(r"\s*(?:\.\s*){4,}\s*")
TERMINAL = tuple(".!?:;\u2026\u201d\"')]\u3002")
TOC_PAGE = re.compile(r"^(?:\d{1,4}|[ivxlc]{1,6})$", re.I)
SAFE_LINK = re.compile(r"^(?:https?://|mailto:)", re.I)


class Span:
    __slots__ = ("page", "top", "left", "width", "height", "size", "mono", "runs", "plain", "sup")

    def __init__(self, page, top, left, width, height, size, mono, runs, plain):
        self.page = page
        self.top = top
        self.left = left
        self.width = width
        self.height = height
        self.size = size
        self.mono = mono
        self.runs = runs
        self.plain = plain
        self.sup = False

    @property
    def right(self):
        return self.left + self.width

    @property
    def bottom(self):
        return self.top + self.height


class Page:
    def __init__(self, number, width, height):
        self.number = number
        self.width = width
        self.height = height
        self.spans = []


class Cell:
    def __init__(self, span):
        self.spans = [span]

    def add(self, span):
        self.spans.append(span)

    @property
    def left(self):
        return self.spans[0].left

    @property
    def right(self):
        return self.spans[-1].right

    @property
    def plain(self):
        return normalize_space(" ".join(s.plain for s in self.spans))

    @property
    def mono(self):
        return all(s.mono for s in self.spans)

    @property
    def runs(self):
        out = []
        for index, span in enumerate(self.spans):
            if index:
                out.append((" ", False, False, None))
            out.extend(span.runs)
        return out


class Row:
    def __init__(self, span, group):
        self.cells = []
        self.spans = [span]
        self.top = span.top
        self.bottom = span.bottom
        self.page = span.page
        self.group = group
        self.heading = 0

    def add(self, span):
        self.spans.append(span)
        self.top = min(self.top, span.top)
        self.bottom = max(self.bottom, span.bottom)

    @property
    def height(self):
        return self.bottom - self.top

    def finish(self):
        self.spans.sort(key=lambda s: s.left)
        self.cells = []
        for span in self.spans:
            if self.cells:
                prev = self.cells[-1]
                gap = span.left - prev.right
                size = max(span.size, prev.spans[-1].size)
                lone_marker = len(prev.spans) == 1 and BULLET_ONLY.match(prev.plain)
                if gap <= 1.0 * size or (lone_marker and gap <= 4 * size):
                    prev.add(span)
                    continue
            self.cells.append(Cell(span))
        weights = Counter()
        for span in self.spans:
            weights[span.size] += len(span.plain)
        self.size = weights.most_common(1)[0][0]
        center = (self.top + self.bottom) / 2
        for span in self.spans:
            if (len(self.spans) > 1 and span.size < 0.8 * self.size and len(span.plain) <= 3
                    and span.top + span.height / 2 < center and not span.sup
                    and re.match(r"^[\d*\u2020\u2021\u00a7\u00b6,]+$", span.plain)):
                span.runs = [(SUP_OPEN + "".join(r[0] for r in span.runs).strip() + SUP_CLOSE,
                              False, False, None)]
                span.sup = True

    @property
    def left(self):
        return self.cells[0].left

    @property
    def right(self):
        return self.cells[-1].right

    @property
    def plain(self):
        return normalize_space(" ".join(c.plain for c in self.cells))

    @property
    def mono(self):
        return all(c.mono for c in self.cells)

    @property
    def all_bold(self):
        saw = False
        for span in self.spans:
            for text, bold, _italic, _href in span.runs:
                if text.strip():
                    if not bold:
                        return False
                    saw = True
        return saw


class Table:
    def __init__(self, rows, page, group):
        self.rows = rows
        self.page = page
        self.group = group
        self.top = rows[0].top
        self.bottom = rows[-1].bottom


def normalize_space(text):
    return re.sub(r"\s+", " ", text).strip()


def collect_runs(elem, bold=False, italic=False, href=None, out=None):
    out = [] if out is None else out
    if elem.text:
        out.append((elem.text, bold, italic, href))
    for child in elem:
        tag = child.tag
        collect_runs(
            child,
            bold or tag == "b",
            italic or tag == "i",
            child.get("href") if tag == "a" else href,
            out,
        )
        if child.tail:
            out.append((child.tail, bold, italic, href))
    return out


def parse_pages(data):
    text = data.decode("utf-8", errors="replace")
    text = CONTROL_CHARS.sub("", text)
    text = re.sub(r"<!DOCTYPE[^>]*>", "", text, count=1)
    root = ET.fromstring(text.encode("utf-8"))
    fonts = {}
    for spec in root.iter("fontspec"):
        try:
            size = float(spec.get("size", "0"))
        except ValueError:
            size = 0.0
        fonts[spec.get("id")] = (size, spec.get("family", ""))
    pages = []
    for element in root.iter("page"):
        page = Page(int(element.get("number", len(pages) + 1)),
                    float(element.get("width", "0")), float(element.get("height", "0")))
        for node in element.findall("text"):
            size, family = fonts.get(node.get("font"), (0.0, ""))
            font_bold = bool(BOLD_FONT.search(family))
            font_italic = bool(ITALIC_FONT.search(family))
            runs = [(t.translate(NORMALIZE), b or font_bold, i or font_italic, h)
                    for t, b, i, h in collect_runs(node)]
            plain = normalize_space("".join(r[0] for r in runs))
            if not plain:
                continue
            if len(plain) == 1 and (ord(plain) < 32 or 0x7F <= ord(plain) <= 0x9F or 0xE000 <= ord(plain) <= 0xF8FF):
                runs = [("\u2022", False, False, None)]
                plain = "\u2022"
            height = float(node.get("height", "0"))
            page.spans.append(Span(
                page, float(node.get("top", "0")), float(node.get("left", "0")),
                float(node.get("width", "0")), height, size or height,
                bool(MONO_FONT.search(family)), runs, plain))
        pages.append(page)
    return pages


def in_band(span, page):
    return page.height > 0 and (span.top < 0.08 * page.height or span.bottom > 0.92 * page.height)


def alone(span, page):
    for other in page.spans:
        if other is not span and min(span.bottom, other.bottom) - max(span.top, other.top) >= 0.5 * min(span.height, other.height):
            return False
    return True


def drop_noise(pages):
    """Membuang nomor halaman dan header/footer yang berulang di pinggir halaman."""
    counts = Counter()
    for page in pages:
        keys = {re.sub(r"\d+", "#", s.plain.lower()) for s in page.spans if in_band(s, page)}
        counts.update(keys)
    threshold = max(2, math.ceil(len(pages) * 0.4)) if len(pages) >= 2 else None
    for page in pages:
        kept = []
        for span in page.spans:
            if in_band(span, page):
                key = re.sub(r"\d+", "#", span.plain.lower())
                if (PAGE_NUMBER.match(span.plain) and alone(span, page)) or (threshold and counts[key] >= threshold):
                    continue
            kept.append(span)
        page.spans = kept


def body_size(pages):
    weights = Counter()
    for page in pages:
        for span in page.spans:
            if not span.mono:
                weights[round(span.size, 1)] += len(span.plain)
    return weights.most_common(1)[0][0] if weights else 0.0


def heading_levels(pages, body):
    weights = Counter()
    for page in pages:
        for span in page.spans:
            if not span.mono and span.size >= body * 1.12:
                weights[round(span.size, 1)] += len(span.plain)
    sizes = sorted(weights, reverse=True)
    return {size: min(index + 1, 5) for index, size in enumerate(sizes)}


def split_groups(spans, page):
    """Mengurutkan span halaman ke kelompok sesuai urutan baca (mendukung dua kolom)."""
    width = page.width
    narrow = [s for s in spans if 0.2 * width <= s.width <= 0.55 * width and not s.mono]
    left = [s for s in narrow if s.left + s.width / 2 < width / 2]
    right = [s for s in narrow if s.left + s.width / 2 >= width / 2]
    if len(left) < 3 or len(right) < 3:
        return [spans]
    if sum(len(s.plain) for s in left) / len(left) < 25 or sum(len(s.plain) for s in right) / len(right) < 25:
        return [spans]
    left_edge = sorted(s.right for s in left)[int(len(left) * 0.95) - 1 if len(left) > 1 else 0]
    right_edge = sorted(s.left for s in right)[int(len(right) * 0.05)]
    if left_edge >= right_edge:
        return [spans]
    pairs = sum(1 for a in left for b in right
                if min(a.bottom, b.bottom) - max(a.top, b.top) >= 0.5 * min(a.height, b.height))
    if pairs < 2:
        return [spans]
    gutter = (left_edge + right_edge) / 2

    groups = []
    col_left, col_right = [], []

    def flush():
        if col_left:
            groups.append(list(col_left))
        if col_right:
            groups.append(list(col_right))
        del col_left[:]
        del col_right[:]

    for span in sorted(spans, key=lambda s: (s.top, s.left)):
        if span.width >= 0.55 * width or (span.left < gutter < span.right):
            flush()
            groups.append([span])
        elif span.left + span.width / 2 < gutter:
            col_left.append(span)
        else:
            col_right.append(span)
    flush()
    return groups


def build_rows(spans, group):
    rows = []
    for span in sorted(spans, key=lambda s: (s.top, s.left)):
        placed = False
        for row in reversed(rows[-3:]):
            overlap = min(span.bottom, row.bottom) - max(span.top, row.top)
            if overlap >= 0.55 * min(span.height, row.height):
                row.add(span)
                placed = True
                break
        if not placed:
            rows.append(Row(span, group))
    for row in rows:
        row.finish()
    rows.sort(key=lambda r: r.top)
    return rows


def is_tabular(row):
    return (len(row.cells) >= 2 and not row.mono and not row.heading
            and all(len(c.plain) <= 60 for c in row.cells)
            and not BULLET_ONLY.match(row.cells[0].plain))


def column_bounds(rows):
    intervals = sorted((c.left, c.right) for r in rows for c in r.cells)
    merged = []
    for left, right in intervals:
        if merged and left <= merged[-1][1] + 4:
            merged[-1][1] = max(merged[-1][1], right)
        else:
            merged.append([left, right])
    return merged


def columns_aligned(rows):
    """Tiap kolom harus rata kiri, kanan, atau tengah; teks biasa yang kebetulan renggang tidak lolos."""
    bounds = column_bounds(rows)
    for low, high in bounds:
        cells = [c for r in rows for c in r.cells if low - 1 <= c.left and c.right <= high + 1]
        if len(cells) < 2:
            continue
        size = max(r.size for r in rows)
        tolerance = 0.8 * size
        edges = (
            [c.left for c in cells],
            [c.right for c in cells],
            [(c.left + c.right) / 2 for c in cells],
        )
        if not any(max(e) - min(e) <= tolerance for e in edges):
            return False
    return True


def detect_tables(rows, group):
    items = []
    i = 0
    while i < len(rows):
        if not is_tabular(rows[i]):
            items.append(rows[i])
            i += 1
            continue
        run = [rows[i]]
        j = i + 1
        while (j < len(rows) and is_tabular(rows[j])
               and rows[j].top - run[-1].top <= 2.4 * run[-1].size):
            run.append(rows[j])
            j += 1
        if len(run) >= 2 and 2 <= len(column_bounds(run)) <= 12 and columns_aligned(run):
            while (j < len(rows) and len(rows[j].cells) >= 1 and not rows[j].mono
                   and rows[j].top - run[-1].bottom <= 0.6 * run[-1].size
                   and not rows[j].cells[0].plain == "" and continuation_of(run, rows[j])):
                run[-1].spans.extend(rows[j].spans)
                run[-1].finish()
                j += 1
            items.append(Table(run, run[0].page, group))
            i = j
        else:
            items.append(rows[i])
            i += 1
    return items


def continuation_of(run, row):
    """Baris yang hanya menyambung teks sel (kolom pertama kosong) dari tabel di atasnya."""
    first = column_bounds(run)[0]
    return row.cells[0].left > first[1]


def escape_text(text):
    text = re.sub(r"\\(?=[!-/:-@\[-`{-~])", r"\\\\", text)
    text = text.replace("*", "\\*").replace("`", "\\`").replace("<", "\\<")
    return re.sub(r"(?<!\w)_|_(?!\w)", r"\\_", text)


def safe_href(href):
    if not href or not SAFE_LINK.match(href):
        return None
    return href.replace(" ", "%20").replace("(", "%28").replace(")", "%29")


def render_runs(runs, plain=False, code=False):
    merged = []
    for text, bold, italic, href in runs:
        if not text:
            continue
        key = (bold, italic, href)
        if merged and merged[-1][1] == key:
            merged[-1][0] += text
        else:
            merged.append([text, key])
    parts = []
    for text, (bold, italic, href) in merged:
        core = text.strip()
        if not core:
            parts.append(" ")
            continue
        lead = " " if text[0].isspace() else ""
        trail = " " if text[-1].isspace() else ""
        if plain:
            piece = escape_text(core)
        elif code:
            piece = "`" + core.replace("`", "'") + "`"
        else:
            piece = escape_text(core)
            if italic:
                piece = "*" + piece + "*"
            if bold:
                piece = "**" + piece + "**"
            link = safe_href(href)
            if link:
                piece = "[" + piece + "](" + link + ")"
        parts.append(lead + piece + trail)
    text = normalize_space("".join(parts)).replace(" " + SUP_OPEN, SUP_OPEN)
    if plain:
        return text.replace(SUP_OPEN, "").replace(SUP_CLOSE, "")
    return text.replace(SUP_OPEN, "<sup>").replace(SUP_CLOSE, "</sup>")


def row_markdown(row, skip=0):
    text = render_runs([r for c in row.cells[skip:] for r in (c.runs + [(" ", False, False, None)])])
    return DOT_LEADER.sub(" \u2026 ", text)


def join_text(prev, nxt):
    if re.search(r"(?<=[^\W\d_]{3})-(\**)$", prev) and re.match(r"\**[a-z\u00e0-\u00ff]", nxt):
        return re.sub(r"-(\**)$", r"\1", prev) + nxt
    if re.search(r"[^\W\d_]-$", prev):
        return prev + nxt
    return prev + " " + nxt


class Block:
    def __init__(self, kind, text="", row=None):
        self.kind = kind
        self.text = text
        self.page = row.page if row is not None else 0
        self.group = row.group if row is not None else 0
        self.size = row.size if row is not None else 0
        self.left = row.left if row is not None else 0
        self.last_top = row.top if row is not None else 0
        self.last_bottom = row.bottom if row is not None else 0
        self.last_left = self.left
        self.last_right = row.right if row is not None else 0
        self.level = 0
        self.number = None
        self.text_left = self.left
        self.lines = []
        self.table = None
        self.base = self.left


def list_marker(row):
    plain = row.plain
    first = row.cells[0]
    if BULLET_ONLY.match(first.plain) and len(row.cells) == 1:
        return None
    match = BULLET.match(plain)
    if match:
        text_left = row.cells[1].left if len(row.cells) > 1 and BULLET_ONLY.match(first.plain) \
            else row.left + 1.6 * row.size
        return ("bullet", None, text_left)
    if len(row.cells) > 1 and BULLET_ONLY.match(first.plain):
        return ("bullet", None, row.cells[1].left)
    match = NUMBERED.match(plain)
    if match:
        return ("number", int(match.group(1)), row.left + 1.8 * row.size)
    match = LETTERED.match(plain)
    if match:
        return ("bullet", match.group(1), row.left + 2.2 * row.size)
    return None


MARKER_TEXT = re.compile(
    r"^\s*(?:[\u2022\u25cf\u25cb\u25aa\u25a0\u25e6\u2023\u25b8\u25ba\u00b7\u2219\uf0b7\u2043\u2013\u2014*-]"
    r"|\(?\d{1,3}[.)])\s*")


def markdown_without_marker(row):
    """Markdown isi baris daftar tanpa penanda bullet/nomor di depannya."""
    runs = [r for c in row.cells for r in (c.runs + [(" ", False, False, None)])]
    for index, (text, bold, italic, href) in enumerate(runs):
        if not text.strip():
            continue
        match = MARKER_TEXT.match(text)
        if match:
            runs[index] = (text[match.end():], bold, italic, href)
        break
    return DOT_LEADER.sub(" \u2026 ", render_runs(runs))


def column_right(rows):
    longs = [r.right for r in rows if len(r.plain) >= 40]
    return max(longs) if longs else max((r.right for r in rows), default=0)


def assemble(pages_items, options):
    blocks = []
    for page_index, (items, group_right) in enumerate(pages_items):
        if options["page_breaks"] and page_index > 0 and blocks:
            blocks.append(Block("rule"))
        for item in items:
            if isinstance(item, Table):
                blocks.append(table_block(item))
                continue
            add_row(blocks, item, group_right.get(item.group, 0), options)
    return blocks


def add_row(blocks, row, right_edge, options):
    markdown = row_markdown(row)
    last = blocks[-1] if blocks else None
    if row.mono:
        line, cw = code_line(row)
        pitch = row.top - last.last_top if last is not None and last.kind == "code" else 0
        if (last is not None and last.kind == "code" and last.page == row.page
                and last.group == row.group and pitch <= 2.6 * row.size):
            if pitch > 1.9 * row.size:
                last.lines.append((row.left, "", 0.0))
            last.lines.append((row.left, line, cw))
            last.last_top = row.top
        else:
            block = Block("code", row=row)
            block.lines.append((row.left, line, cw))
            blocks.append(block)
        return
    if row.heading:
        if (last is not None and last.kind == "heading" and last.level == row.heading
                and last.page == row.page and last.group == row.group
                and row.top - last.last_top <= 1.7 * row.size):
            last.text = join_text(last.text, heading_text(row))
            last.last_top = row.top
        else:
            block = Block("heading", heading_text(row), row)
            block.level = row.heading
            blocks.append(block)
        return
    marker = list_marker(row)
    if marker:
        kind, label, text_left = marker
        body = markdown if (kind == "bullet" and label) else markdown_without_marker(row)
        block = Block("list", body, row)
        block.number = label if kind == "number" else None
        block.text_left = text_left
        blocks.append(block)
        return
    if last is not None and last.kind in ("para", "list") and joinable(last, row, markdown, right_edge, options):
        last.text = join_text(last.text, markdown)
        last.last_top = row.top
        last.last_bottom = row.bottom
        last.last_left = row.left
        last.last_right = row.right
        last.page = row.page
        last.group = row.group
        return
    block = Block("para", markdown, row)
    blocks.append(block)


def heading_text(row):
    return render_runs([r for c in row.cells for r in (c.runs + [(" ", False, False, None)])], plain=True)


def char_width(spans, fallback):
    widths = sorted(s.width / len(s.plain) for s in spans if len(s.plain) >= 2 and s.width > 0)
    return widths[len(widths) // 2] if widths else fallback


def code_line(row):
    unit = max(char_width(row.spans, row.size * 0.6), 1.0)
    out = []
    cursor = row.left
    for cell in row.cells:
        pad = int(round((cell.left - cursor) / unit)) if out else 0
        out.append(" " * max(pad, 0) + "".join(r[0] for r in cell.runs).strip("\n"))
        cursor = cell.right
    return "".join(out), unit


def ends_sentence(text):
    text = re.sub(r"(?:" + SUP_OPEN + r"[^" + SUP_CLOSE + r"]*" + SUP_CLOSE + r")+$", "", text)
    text = re.sub(r"<sup>[^<]*</sup>$", "", text).rstrip("*_\\ ")
    return text.endswith(TERMINAL)


def joinable(last, row, markdown, right_edge, options):
    if last.kind == "list" and row.left < last.text_left - 0.6 * row.size and row.left <= last.left + 0.3 * row.size:
        return False
    if last.page != row.page or last.group != row.group:
        if options["page_breaks"] and last.page != row.page:
            return False
        if ends_sentence(last.text):
            return False
        return bool(re.match(r"\**[a-z\u00e0-\u00ff(]", markdown))
    size = max(last.size, row.size)
    if row.top - last.last_top > 1.7 * size:
        return False
    short = bool(right_edge) and last.last_right < 0.8 * right_edge
    if last.kind == "para" and short and ends_sentence(last.text):
        return False
    if last.kind == "para" and row.left - last.last_left > 0.9 * size:
        return False
    return True


def table_block(table):
    bounds = column_bounds(table.rows)
    grid = []
    for position, row in enumerate(table.rows):
        cells = [""] * len(bounds)
        bare = position == 0 and row.all_bold
        for cell in row.cells:
            center = (cell.left + cell.right) / 2
            index = min(range(len(bounds)), key=lambda k: abs((bounds[k][0] + bounds[k][1]) / 2 - center))
            text = render_runs(cell.runs, plain=bare).replace("|", "\\|")
            cells[index] = (cells[index] + " " + text).strip()
        grid.append(cells)
    block = Block("table", row=table.rows[0])
    block.page, block.group = table.page, table.group
    block.table = grid
    if (len(bounds) == 2 and len(grid) >= 3
            and all(TOC_PAGE.match(re.sub(r"\\", "", r[1])) for r in grid)
            and not TOC_PAGE.match(re.sub(r"\\", "", grid[0][0]))):
        block.kind = "toc"
    return block


def clean_emphasis(text):
    text = re.sub(r"\*\*(\s+)\*\*", r"\1", text)
    return re.sub(r"(?<!\*)\*(\s+)\*(?!\*)", r"\1", text)


def start_escape(text):
    if re.match(r"^(?:[#>+=\-]|\d{1,9}[.)]\s)", text):
        return "\\" + text
    return text


def render(blocks):
    assign_list_bases(blocks)
    out = []
    previous = None
    for block in blocks:
        if block.kind == "rule":
            out.append("---")
            previous = block
            continue
        if block.kind == "heading":
            text = "#" * block.level + " " + block.text
        elif block.kind == "para":
            text = start_escape(clean_emphasis(block.text))
        elif block.kind == "list":
            text = render_list_item(block)
        elif block.kind == "code":
            text = render_code(block)
        elif block.kind == "toc":
            text = "\n".join("- " + r[0] + " \u2026 " + r[1] for r in block.table)
        else:
            text = render_table(block.table)
        if (previous is not None and previous.kind == "list" and block.kind == "list"
                and (previous.number is None) == (block.number is None)):
            out[-1] = out[-1] + "\n" + text
        else:
            out.append(text)
        previous = block
    return "\n\n".join(out).strip() + "\n"


def assign_list_bases(blocks):
    """Menentukan margin kiri terkecil tiap rangkaian item daftar, untuk menghitung kedalamannya."""
    run = []
    for block in blocks + [Block("rule")]:
        if block.kind == "list":
            run.append(block)
            continue
        if run:
            base = min(b.left for b in run)
            for item in run:
                item.base = base
            run = []


def render_list_item(block):
    base = block.base
    step = max(block.size * 1.8, 1.0)
    depth = max(0, int(round((block.left - base) / step)))
    marker = "%d. " % block.number if block.number is not None else "- "
    return "    " * depth + marker + clean_emphasis(block.text)


def render_code(block):
    base = min(left for left, _text, _cw in block.lines)
    widths = sorted(cw for _left, text, cw in block.lines if text and cw > 0)
    unit = widths[len(widths) // 2] if widths else max(block.size * 0.6, 1.0)
    lines = []
    for left, text, _cw in block.lines:
        lines.append(" " * int(round((left - base) / unit)) + text if text else "")
    body = "\n".join(lines)
    fence = "```"
    while fence in body:
        fence += "`"
    return fence + "\n" + body + "\n" + fence


def render_table(grid):
    width = len(grid[0])
    lines = ["| " + " | ".join(c or " " for c in grid[0]) + " |",
             "| " + " | ".join("---" for _ in range(width)) + " |"]
    for row in grid[1:]:
        lines.append("| " + " | ".join(c or " " for c in row) + " |")
    return "\n".join(lines)


def mark_headings(rows, levels, bold_level):
    for index, row in enumerate(rows):
        row.heading = 0
        if row.mono or len(row.plain) > 180:
            continue
        if BULLET_ONLY.match(row.cells[0].plain):
            continue
        level = levels.get(round(row.size, 1), 0)
        if level:
            row.heading = level
            continue
        if row.all_bold and len(row.cells) == 1 and len(row.plain) <= 100 \
                and not row.plain.endswith((".", ",", ";")):
            before = row.top - rows[index - 1].bottom if index > 0 else 99
            after = rows[index + 1].top - row.bottom if index + 1 < len(rows) else 99
            limit = 0.45 * row.size
            if before > limit or after > limit:
                row.heading = bold_level


def convert(data, options):
    pages = parse_pages(data)
    if not any(p.spans for p in pages):
        sys.stderr.write("PDF tidak memuat teks (mungkin hasil scan; butuh OCR).\n")
        return EXIT_NO_TEXT
    if options["strip_repeated"]:
        drop_noise(pages)
    body = body_size(pages)
    levels = heading_levels(pages, body)
    bold_level = min(max(len(levels) + 1, 2), 6)
    pages_items = []
    group_id = 0
    for page in pages:
        items = []
        right_edges = {}
        for spans in split_groups(page.spans, page):
            group_id += 1
            rows = build_rows(spans, group_id)
            if not rows:
                continue
            right_edges[group_id] = column_right(rows)
            mark_headings(rows, levels, bold_level)
            items.extend(detect_tables(rows, group_id))
        pages_items.append((items, right_edges))
    markdown = render(assemble(pages_items, options))
    if not markdown.strip():
        sys.stderr.write("PDF tidak memuat teks yang bisa dibaca.\n")
        return EXIT_NO_TEXT
    sys.stdout.write(markdown)
    return 0


def main(argv):
    options = {"strip_repeated": True, "page_breaks": False}
    paths = []
    for arg in argv:
        if arg == "--keep-headers":
            options["strip_repeated"] = False
        elif arg == "--page-breaks":
            options["page_breaks"] = True
        elif arg.startswith("--"):
            sys.stderr.write("Opsi tidak dikenal: %s\n" % arg)
            return EXIT_USAGE
        else:
            paths.append(arg)
    if len(paths) != 1:
        sys.stderr.write("Pemakaian: pdf2md.py [--keep-headers] [--page-breaks] berkas.xml\n")
        return EXIT_USAGE
    try:
        with open(paths[0], "rb") as handle:
            data = handle.read()
    except OSError as error:
        sys.stderr.write("Tidak bisa membaca %s: %s\n" % (paths[0], error))
        return EXIT_USAGE
    try:
        return convert(data, options)
    except ET.ParseError as error:
        sys.stderr.write("Hasil pdftohtml tidak terbaca: %s\n" % error)
        return EXIT_BAD_XML


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
