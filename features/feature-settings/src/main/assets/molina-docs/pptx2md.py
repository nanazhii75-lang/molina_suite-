#!/usr/bin/env python3
"""Konversi PPTX ke Markdown untuk molina-suite (hanya pustaka standar Python).

Pemakaian: python3 pptx2md.py berkas.pptx   (Markdown ke stdout)

Tiap slide menjadi satu bagian: judul slide menjadi `## Judul` (atau `## Slide N` bila tanpa
judul), isinya mengikuti urutan bentuk di slide. Teks berpoin menjadi daftar bertingkat, tabel
menjadi tabel Markdown, tebal/miring dan tautan dipertahankan, catatan pembicara menjadi kutipan.
Gambar yang punya teks alternatif ditandai; bagan ditandai sebagai tidak dikonversi. Footer,
nomor slide, dan tanggal dibuang.

Kode keluar: 0 berhasil, 2 argumen salah, 3 tidak ada teks, 4 bukan berkas pptx yang valid
(termasuk yang berkata sandi).
"""
import posixpath
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

EXIT_USAGE = 2
EXIT_NO_TEXT = 3
EXIT_BAD_FILE = 4

P_NS = "{http://schemas.openxmlformats.org/presentationml/2006/main}"
A_NS = "{http://schemas.openxmlformats.org/drawingml/2006/main}"
R_NS = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
PKG_NS = "{http://schemas.openxmlformats.org/package/2006/relationships}"
CONTROL_CHARS = re.compile("[\x00-\x08\x0b\x0c\x0e-\x1f]")
SAFE_LINK = re.compile(r"^(?:https?://|mailto:)", re.I)
SKIPPED_PLACEHOLDERS = {"ftr", "sldNum", "dt", "hdr"}
TITLE_PLACEHOLDERS = {"title", "ctrTitle"}
MAX_LEVEL = 6


class FormatError(Exception):
    """Berkas bukan pptx yang bisa dibaca."""


def read_xml(archive, name):
    try:
        return ET.fromstring(CONTROL_CHARS.sub("", archive.read(name).decode("utf-8", errors="replace")).encode("utf-8"))
    except KeyError:
        raise FormatError("Bagian %s tidak ada di berkas." % name)
    except ET.ParseError as error:
        raise FormatError("XML %s rusak: %s" % (name, error))


def read_rels(archive, part_path):
    """Peta Id -> (jenis, target, eksternal?) dari berkas rels milik [part_path]."""
    folder, name = posixpath.split(part_path)
    rels_path = posixpath.join(folder, "_rels", name + ".rels")
    if rels_path not in archive.namelist():
        return {}
    root = read_xml(archive, rels_path)
    rels = {}
    for rel in root.findall(PKG_NS + "Relationship"):
        target = rel.get("Target", "")
        external = rel.get("TargetMode") == "External"
        if not external:
            target = target.lstrip("/") if target.startswith("/") else posixpath.normpath(posixpath.join(folder, target))
        rels[rel.get("Id")] = (rel.get("Type", ""), target, external)
    return rels


def slide_paths(archive):
    root = read_xml(archive, "ppt/presentation.xml")
    rels = read_rels(archive, "ppt/presentation.xml")
    paths = []
    container = root.find(P_NS + "sldIdLst")
    if container is None:
        return paths
    for item in container.findall(P_NS + "sldId"):
        rel = rels.get(item.get(R_NS + "id"))
        if rel:
            paths.append(rel[1])
    return paths


def escape_text(text):
    text = re.sub(r"\\(?=[!-/:-@\[-`{-~])", r"\\\\", text)
    text = text.replace("*", "\\*").replace("`", "\\`").replace("<", "\\<")
    return re.sub(r"(?<!\w)_|_(?!\w)", r"\\_", text)


def safe_href(href):
    if not href or not SAFE_LINK.match(href):
        return None
    return href.replace(" ", "%20").replace("(", "%28").replace(")", "%29")


def paragraph_runs(paragraph, rels):
    """Potongan (teks, tebal, miring, tautan) dari satu paragraf a:p."""
    runs = []
    for child in paragraph:
        if child.tag == A_NS + "r" or (child.tag == A_NS + "fld" and child.get("type", "") != "slidenum"):
            text = child.findtext(A_NS + "t") or ""
            props = child.find(A_NS + "rPr")
            bold = italic = False
            href = None
            if props is not None:
                bold = props.get("b") in ("1", "true")
                italic = props.get("i") in ("1", "true")
                link = props.find(A_NS + "hlinkClick")
                if link is not None:
                    rel = rels.get(link.get(R_NS + "id"))
                    if rel and rel[2]:
                        href = rel[1]
            runs.append((text, bold, italic, href))
        elif child.tag == A_NS + "br":
            runs.append((" ", False, False, None))
    return runs


def render_runs(runs, plain=False, line_break=" "):
    merged = []
    for text, bold, italic, href in runs:
        text = text.replace("\n", line_break).replace("\x0b", line_break)
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
        piece = escape_text(core)
        if not plain:
            if italic:
                piece = "*" + piece + "*"
            if bold:
                piece = "**" + piece + "**"
            link = safe_href(href)
            if link:
                piece = "[" + piece + "](" + link + ")"
        parts.append(lead + piece + trail)
    return re.sub(r"\s+", " ", "".join(parts)).strip()


def clean_emphasis(text):
    text = re.sub(r"\*\*(\s+)\*\*", r"\1", text)
    return re.sub(r"(?<!\*)\*(\s+)\*(?!\*)", r"\1", text)


def placeholder_type(shape):
    """(adalah placeholder?, jenisnya) untuk p:sp."""
    props = shape.find(P_NS + "nvSpPr/" + P_NS + "nvPr/" + P_NS + "ph")
    if props is None:
        return False, None
    return True, props.get("type")


def paragraph_bullet(paragraph, is_placeholder, ph_type):
    """Jenis poin paragraf: None (paragraf biasa), 'bullet', atau 'number'."""
    props = paragraph.find(A_NS + "pPr")
    if props is not None:
        if props.find(A_NS + "buNone") is not None:
            return None
        if props.find(A_NS + "buAutoNum") is not None:
            return "number"
        if props.find(A_NS + "buChar") is not None:
            return "bullet"
    if is_placeholder and ph_type not in TITLE_PLACEHOLDERS and ph_type != "subTitle":
        return "bullet"
    return None


def text_blocks(shape, rels):
    """Blok teks dari sebuah p:sp: daftar ('list', level, penanda, teks) atau ('para', teks)."""
    body = shape.find(P_NS + "txBody")
    if body is None:
        return []
    is_placeholder, ph_type = placeholder_type(shape)
    blocks = []
    for paragraph in body.findall(A_NS + "p"):
        runs = paragraph_runs(paragraph, rels)
        text = clean_emphasis(render_runs(runs))
        if not text:
            continue
        kind = paragraph_bullet(paragraph, is_placeholder, ph_type)
        if kind:
            props = paragraph.find(A_NS + "pPr")
            level = int(props.get("lvl", "0")) if props is not None and (props.get("lvl") or "0").isdigit() else 0
            blocks.append(("list", min(level, MAX_LEVEL), "1." if kind == "number" else "-", text))
        else:
            blocks.append(("para", text))
    return blocks


def table_markdown(frame):
    rows = []
    for row in frame.iter(A_NS + "tr"):
        cells = []
        for cell in row.findall(A_NS + "tc"):
            if cell.get("hMerge") in ("1", "true") or cell.get("vMerge") in ("1", "true"):
                cells.append("")
                continue
            body = cell.find(A_NS + "txBody")
            texts = []
            if body is not None:
                for paragraph in body.findall(A_NS + "p"):
                    text = clean_emphasis(render_runs(paragraph_runs(paragraph, {})))
                    if text:
                        texts.append(text)
            cells.append(" <br> ".join(texts).replace("|", "\\|"))
        rows.append(cells)
    rows = [r for r in rows if any(r)]
    if not rows:
        return None
    width = max(len(r) for r in rows)
    rows = [r + [""] * (width - len(r)) for r in rows]
    lines = ["| " + " | ".join(c or " " for c in rows[0]) + " |",
             "| " + " | ".join("---" for _ in range(width)) + " |"]
    lines.extend("| " + " | ".join(c or " " for c in row) + " |" for row in rows[1:])
    return "\n".join(lines)


def collect(tree, rels, result):
    """Menelusuri bentuk slide sesuai urutan dokumen (termasuk grup) ke [result]."""
    for shape in tree:
        tag = shape.tag
        if tag == P_NS + "sp":
            is_placeholder, ph_type = placeholder_type(shape)
            if ph_type in SKIPPED_PLACEHOLDERS:
                continue
            if ph_type in TITLE_PLACEHOLDERS:
                runs = []
                for paragraph in shape.iter(A_NS + "p"):
                    runs.extend(paragraph_runs(paragraph, rels) + [(" ", False, False, None)])
                title = render_runs(runs, plain=True)
                if title and not result["title"]:
                    result["title"] = title
                continue
            result["blocks"].extend(text_blocks(shape, rels))
        elif tag == P_NS + "grpSp":
            collect(shape, rels, result)
        elif tag == P_NS + "graphicFrame":
            data = shape.find(A_NS + "graphic/" + A_NS + "graphicData")
            uri = data.get("uri", "") if data is not None else ""
            if data is not None and data.find(A_NS + "tbl") is not None:
                table = table_markdown(data)
                if table:
                    result["blocks"].append(("table", table))
            elif uri.endswith("/chart"):
                result["blocks"].append(("para", "*[Bagan tidak dikonversi]*"))
        elif tag == P_NS + "pic":
            props = shape.find(P_NS + "nvPicPr/" + P_NS + "cNvPr")
            alt = (props.get("descr") or "").strip() if props is not None else ""
            if alt:
                result["blocks"].append(("para", "*[Gambar: " + escape_text(re.sub(r"\s+", " ", alt)) + "]*"))


def notes_text(archive, rels):
    for rel_type, target, external in rels.values():
        if rel_type.endswith("/notesSlide") and not external:
            root = read_xml(archive, target)
            lines = []
            for shape in root.iter(P_NS + "sp"):
                _is, ph_type = placeholder_type(shape)
                if ph_type != "body":
                    continue
                for paragraph in shape.iter(A_NS + "p"):
                    text = render_runs(paragraph_runs(paragraph, {}), plain=True)
                    if text:
                        lines.append(text)
            return lines
    return []


def render_blocks(blocks):
    out = []
    previous = None
    level = 0
    for block in blocks:
        if block[0] == "list":
            # Tingkat tidak boleh melompat lebih dari satu, kalau tidak dianggap blok kode.
            level = min(block[1], level + 1) if previous == "list" else 0
            text = "    " * level + block[2] + " " + block[3]
            if previous == "list":
                out[-1] += "\n" + text
            else:
                out.append(text)
        elif block[0] == "table":
            out.append(block[1])
        else:
            text = block[1]
            if re.match(r"^(?:[#>+=\-]|\d{1,9}[.)]\s)", text):
                text = "\\" + text
            out.append(text)
        previous = block[0]
    return out


def convert(path):
    try:
        archive = zipfile.ZipFile(path)
    except (zipfile.BadZipFile, OSError):
        raise FormatError("Bukan berkas pptx yang valid (mungkin berkata sandi atau rusak).")
    sections = []
    has_text = False
    with archive:
        if "ppt/presentation.xml" not in archive.namelist():
            raise FormatError("Berkas ini bukan presentasi pptx.")
        for number, path_in_zip in enumerate(slide_paths(archive), 1):
            root = read_xml(archive, path_in_zip)
            rels = read_rels(archive, path_in_zip)
            result = {"title": "", "blocks": []}
            tree = root.find(P_NS + "cSld/" + P_NS + "spTree")
            if tree is not None:
                collect(tree, rels, result)
            heading = result["title"] or "Slide %d" % number
            if root.get("show") == "0":
                heading += " _(tersembunyi)_"
            parts = ["## " + heading]
            parts.extend(render_blocks(result["blocks"]))
            notes = notes_text(archive, rels)
            if notes:
                parts.append("\n>\n".join(["> **Catatan pembicara:** " + notes[0]] + ["> " + n for n in notes[1:]]))
            if result["title"] or result["blocks"] or notes:
                has_text = True
            sections.append("\n\n".join(parts))
    if not has_text:
        sys.stderr.write("Presentasi tidak memuat teks.\n")
        return EXIT_NO_TEXT
    sys.stdout.write("\n\n".join(sections) + "\n")
    return 0


def main(argv):
    if len(argv) != 1 or argv[0].startswith("--"):
        sys.stderr.write("Pemakaian: pptx2md.py berkas.pptx\n")
        return EXIT_USAGE
    try:
        return convert(argv[0])
    except FormatError as error:
        sys.stderr.write("%s\n" % error)
        return EXIT_BAD_FILE


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
