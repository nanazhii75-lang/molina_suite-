#!/usr/bin/env python3
"""Konversi spreadsheet (XLSX dan ODS) ke Markdown untuk molina-suite (hanya pustaka standar Python).

Pemakaian: python3 sheet2md.py [--max-rows N] berkas.xlsx|berkas.ods   (Markdown ke stdout)

Jenis berkas dikenali dari isinya, bukan ekstensinya. Tiap sheet yang terlihat menjadi satu bagian
`## Nama sheet`. Blok data yang dipisah baris kosong menjadi tabel terpisah; baris pertama tiap
blok menjadi header. Nilai yang ditampilkan adalah nilai tersimpan (hasil rumus): XLSX diformat
sesuai format sel (tanggal/waktu, persen, desimal, ribuan), ODS memakai teks tampilan sel.
Sheet, baris, dan kolom tersembunyi dilewati. Kolom berisi angka diratakan kanan.

Kode keluar: 0 berhasil, 2 argumen salah, 3 tidak ada data, 4 bukan berkas xlsx/ods yang valid
(termasuk yang berkata sandi).
"""
import datetime
import posixpath
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

EXIT_USAGE = 2
EXIT_NO_DATA = 3
EXIT_BAD_FILE = 4

DEFAULT_MAX_ROWS = 5000
MAIN_NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
REL_NS = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
PKG_REL_NS = "{http://schemas.openxmlformats.org/package/2006/relationships}"
ODF_OFFICE = "{urn:oasis:names:tc:opendocument:xmlns:office:1.0}"
ODF_TABLE = "{urn:oasis:names:tc:opendocument:xmlns:table:1.0}"
ODF_TEXT = "{urn:oasis:names:tc:opendocument:xmlns:text:1.0}"
ODF_STYLE = "{urn:oasis:names:tc:opendocument:xmlns:style:1.0}"
ODF_HIDDEN = ("collapse", "filter")
ODF_MAX_REPEAT = 100
CONTROL_CHARS = re.compile("[\x00-\x08\x0b\x0c\x0e-\x1f]")
CELL_REF = re.compile(r"^([A-Za-z]{1,3})(\d+)$")

BUILTIN_DATE_IDS = set(range(14, 23)) | {45, 46, 47}
BUILTIN_DECIMAL = {1: (0, False), 2: (2, False), 3: (0, True), 4: (2, True),
                   37: (0, True), 38: (0, True), 39: (2, True), 40: (2, True)}
BUILTIN_PERCENT = {9: 0, 10: 2}
QUOTED_OR_BRACKET = re.compile(r'"[^"]*"|\[[^\]]*\]|\\.|_.|\*.')


class FormatError(Exception):
    """Berkas bukan xlsx yang bisa dibaca."""


def column_index(letters):
    value = 0
    for char in letters.upper():
        value = value * 26 + (ord(char) - 64)
    return value - 1


def split_ref(ref):
    match = CELL_REF.match(ref or "")
    if not match:
        return None
    return int(match.group(2)) - 1, column_index(match.group(1))


def read_xml(archive, name):
    try:
        data = archive.read(name)
    except KeyError:
        raise FormatError("Bagian %s tidak ada di berkas." % name)
    return data


def iterparse(archive, name, events=("end",)):
    try:
        handle = archive.open(name)
    except KeyError:
        raise FormatError("Bagian %s tidak ada di berkas." % name)
    try:
        for event in ET.iterparse(handle, events=events):
            yield event
    except ET.ParseError as error:
        raise FormatError("XML %s rusak: %s" % (name, error))
    finally:
        handle.close()


def shared_strings(archive):
    if "xl/sharedStrings.xml" not in archive.namelist():
        return []
    strings = []
    for _event, element in iterparse(archive, "xl/sharedStrings.xml"):
        if element.tag == MAIN_NS + "si":
            strings.append("".join(rich_text(element)))
            element.clear()
    return strings


def rich_text(node):
    """Potongan teks dari sebuah elemen si/is, tanpa teks pelafalan (rPh)."""
    for child in node:
        if child.tag == MAIN_NS + "t":
            yield child.text or ""
        elif child.tag == MAIN_NS + "r":
            for part in child.findall(MAIN_NS + "t"):
                yield part.text or ""


def parse_styles(archive):
    """Jenis format tiap indeks gaya sel: (date|percent|decimal|general, parameter)."""
    if "xl/styles.xml" not in archive.namelist():
        return []
    root = ET.fromstring(read_xml(archive, "xl/styles.xml"))
    custom = {}
    formats = root.find(MAIN_NS + "numFmts")
    if formats is not None:
        for item in formats.findall(MAIN_NS + "numFmt"):
            custom[int(item.get("numFmtId", "0"))] = item.get("formatCode", "")
    cell_xfs = root.find(MAIN_NS + "cellXfs")
    if cell_xfs is None:
        return []
    return [classify_format(int(xf.get("numFmtId", "0")), custom)
            for xf in cell_xfs.findall(MAIN_NS + "xf")]


def classify_format(fmt_id, custom):
    if fmt_id in BUILTIN_DATE_IDS:
        return ("date", None)
    if fmt_id in BUILTIN_PERCENT:
        return ("percent", BUILTIN_PERCENT[fmt_id])
    if fmt_id in BUILTIN_DECIMAL:
        return ("decimal", BUILTIN_DECIMAL[fmt_id])
    code = custom.get(fmt_id)
    if not code:
        return ("general", None)
    first = code.split(";")[0]
    bare = QUOTED_OR_BRACKET.sub("", first)
    if re.search(r"[ymdhs]", bare, re.I) and not re.search(r"[0#]", bare):
        return ("date", None)
    decimals = 0
    point = re.search(r"\.([0#]+)", bare)
    if point:
        decimals = len(point.group(1))
    if "%" in bare:
        return ("percent", decimals)
    if re.search(r"[0#]", bare):
        return ("decimal", (decimals, "," in bare))
    return ("general", None)


def format_number(value, style, date1904):
    kind, param = style
    try:
        number = float(value)
    except ValueError:
        return value, False
    if kind == "date":
        return format_date(number, date1904), False
    if kind == "percent":
        return ("%.*f%%" % (param or 0, number * 100)), True
    if kind == "decimal":
        decimals, grouped = param
        text = "{:,.{}f}".format(number, decimals) if grouped else "%.*f" % (decimals, number)
        return text, True
    if number == int(number) and abs(number) < 1e15:
        return str(int(number)), True
    return format(number, ".15g"), True


def format_date(serial, date1904):
    epoch = datetime.datetime(1904, 1, 1) if date1904 else datetime.datetime(1899, 12, 30)
    try:
        moment = epoch + datetime.timedelta(seconds=round(serial * 86400))
    except OverflowError:
        return str(serial)
    if 0 <= serial < 1:
        return moment.strftime("%H:%M:%S")
    if moment.hour == 0 and moment.minute == 0 and moment.second == 0:
        return moment.strftime("%Y-%m-%d")
    return moment.strftime("%Y-%m-%d %H:%M:%S" if moment.second else "%Y-%m-%d %H:%M")


def workbook_sheets(archive):
    """Daftar (nama, jalur bagian) sheet yang terlihat, dan flag date1904."""
    root = ET.fromstring(read_xml(archive, "xl/workbook.xml"))
    date1904 = False
    props = root.find(MAIN_NS + "workbookPr")
    if props is not None and props.get("date1904") in ("1", "true"):
        date1904 = True
    rels = {}
    if "xl/_rels/workbook.xml.rels" in archive.namelist():
        rel_root = ET.fromstring(read_xml(archive, "xl/_rels/workbook.xml.rels"))
        for rel in rel_root.findall(PKG_REL_NS + "Relationship"):
            rels[rel.get("Id")] = rel.get("Target", "")
    sheets = []
    container = root.find(MAIN_NS + "sheets")
    if container is None:
        raise FormatError("Buku kerja tidak memuat daftar sheet.")
    for sheet in container.findall(MAIN_NS + "sheet"):
        if sheet.get("state") in ("hidden", "veryHidden"):
            continue
        target = rels.get(sheet.get(REL_NS + "id"))
        if not target:
            continue
        path = target.lstrip("/") if target.startswith("/") else posixpath.normpath("xl/" + target)
        sheets.append((sheet.get("name", "Sheet"), path))
    return sheets, date1904


def read_sheet(archive, path, strings, styles, date1904, max_rows):
    """Membaca sheet: (grid, baris tersembunyi, terpotong?). grid: {baris: {kolom: (teks, angka?)}}."""
    grid = {}
    hidden_rows = set()
    hidden_cols = set()
    truncated = False
    current_row = -1
    next_col = 0
    row_hidden = False
    for event, element in iterparse(archive, path, events=("start", "end")):
        tag = element.tag
        if tag == MAIN_NS + "row" and event == "start":
            row_hidden = element.get("hidden") in ("1", "true")
            current_row = int(element.get("r")) - 1 if element.get("r") else current_row + 1
            if row_hidden:
                hidden_rows.add(current_row)
            next_col = 0
            continue
        if event != "end":
            continue
        if tag == MAIN_NS + "col" and element.get("hidden") in ("1", "true"):
            low = int(element.get("min", "1")) - 1
            high = min(int(element.get("max", "1")) - 1, low + 1000)
            hidden_cols.update(range(low, high + 1))
        elif tag == MAIN_NS + "c":
            ref = split_ref(element.get("r"))
            if ref:
                row_index, col_index = ref
            else:
                row_index, col_index = current_row, next_col
            next_col = col_index + 1
            if row_hidden or truncated:
                element.clear()
                continue
            cell = cell_value(element, strings, styles, date1904)
            if cell is not None and cell[0] != "":
                grid.setdefault(row_index, {})[col_index] = cell
            element.clear()
        elif tag == MAIN_NS + "row":
            if len(grid) >= max_rows:
                truncated = True
            element.clear()
            row_hidden = False
    for row in grid.values():
        for col in list(row):
            if col in hidden_cols:
                del row[col]
    return {r: c for r, c in grid.items() if c}, hidden_rows, truncated


def cell_value(element, strings, styles, date1904):
    kind = element.get("t", "n")
    value_node = element.find(MAIN_NS + "v")
    raw = value_node.text if value_node is not None and value_node.text is not None else None
    if kind == "inlineStr":
        inline = element.find(MAIN_NS + "is")
        if inline is None:
            return None
        return clean("".join(rich_text(inline))), False
    if raw is None:
        formula = element.find(MAIN_NS + "f")
        if formula is not None and formula.text:
            return clean("=" + formula.text), False
        return None
    if kind == "s":
        try:
            return clean(strings[int(raw)]), False
        except (ValueError, IndexError):
            return None
    if kind == "b":
        return ("TRUE" if raw.strip() == "1" else "FALSE"), False
    if kind in ("str", "e", "d"):
        return clean(raw), False
    style_index = int(element.get("s", "0") or 0)
    style = styles[style_index] if 0 <= style_index < len(styles) else ("general", None)
    text, numeric = format_number(raw.strip(), style, date1904)
    return clean(text), numeric


def clean(text):
    return CONTROL_CHARS.sub("", text).replace("\r\n", "\n").replace("\r", "\n")


def escape_cell(text):
    text = text.strip().replace("|", "\\|").replace("\n", "<br>")
    return text


def blocks_of(grid, hidden_rows):
    """Memecah grid menjadi blok baris berurutan; baris tersembunyi tidak dianggap pemisah."""
    blocks = []
    current = []
    previous = None
    for row in sorted(grid):
        if previous is not None and any(r not in hidden_rows for r in range(previous + 1, row)):
            blocks.append(current)
            current = []
        current.append((row, grid[row]))
        previous = row
    if current:
        blocks.append(current)
    return blocks


def render_block(block):
    columns = sorted({col for _row, cells in block for col in cells})
    low, high = columns[0], columns[-1]
    visible = [c for c in range(low, high + 1) if any(c in cells for _r, cells in block)]
    header = block[0][1]
    lines = ["| " + " | ".join(escape_cell(header[c][0]) if c in header else " " for c in visible) + " |"]
    aligns = []
    for c in visible:
        body = [cells[c] for _r, cells in block[1:] if c in cells]
        numeric = bool(body) and all(item[1] for item in body)
        aligns.append("---:" if numeric else "---")
    lines.append("| " + " | ".join(aligns) + " |")
    for _row, cells in block[1:]:
        lines.append("| " + " | ".join(escape_cell(cells[c][0]) if c in cells else " " for c in visible) + " |")
    return "\n".join(lines)


def xlsx_sheets(archive, max_rows):
    sheets, date1904 = workbook_sheets(archive)
    strings = shared_strings(archive)
    styles = parse_styles(archive)
    for name, sheet_path in sheets:
        grid, hidden_rows, truncated = read_sheet(archive, sheet_path, strings, styles, date1904, max_rows)
        yield name, grid, hidden_rows, truncated


def odf_text(cell):
    """Teks tampilan sel ODS: paragraf langsung milik sel (catatan/annotation tidak ikut)."""
    paragraphs = []
    for paragraph in cell:
        if paragraph.tag in (ODF_TEXT + "p", ODF_TEXT + "h"):
            paragraphs.append(odf_inline(paragraph))
    return clean("\n".join(paragraphs)).strip()


def odf_inline(node):
    parts = [node.text or ""]
    for child in node:
        if child.tag == ODF_TEXT + "s":
            parts.append(" " * int(child.get(ODF_TEXT + "c", "1")))
        elif child.tag == ODF_TEXT + "tab":
            parts.append(" ")
        elif child.tag == ODF_TEXT + "line-break":
            parts.append("\n")
        elif child.tag != ODF_OFFICE + "annotation":
            parts.append(odf_inline(child))
        parts.append(child.tail or "")
    return "".join(parts)


def ods_sheets(archive, max_rows):
    """Membaca content.xml ODS secara bertahap; menghasilkan (nama, grid, baris tersembunyi, terpotong?)."""
    hidden_styles = set()
    current_style = None
    depth = 0
    sheet = None
    for event, element in iterparse(archive, "content.xml", events=("start", "end")):
        tag = element.tag
        if event == "start":
            if tag == ODF_STYLE + "style":
                current_style = element.get(ODF_STYLE + "name")
            elif tag == ODF_TABLE + "table":
                depth += 1
                if depth == 1:
                    sheet = {
                        "name": element.get(ODF_TABLE + "name", "Sheet"),
                        "hidden": element.get(ODF_TABLE + "style-name") in hidden_styles,
                        "grid": {}, "hidden_rows": set(), "hidden_cols": set(),
                        "row": -1, "col": 0, "cols": 0, "truncated": False,
                    }
            elif tag == ODF_TABLE + "table-row" and depth == 1:
                repeat = min(int(element.get(ODF_TABLE + "number-rows-repeated", "1")), ODF_MAX_REPEAT * 10)
                sheet["first_row"] = sheet["row"] + 1
                sheet["repeat"] = repeat
                sheet["row_hidden"] = element.get(ODF_TABLE + "visibility") in ODF_HIDDEN
                sheet["cells"] = {}
                sheet["col"] = 0
            continue
        if tag == ODF_STYLE + "table-properties" and element.get(ODF_TABLE + "display") == "false" and current_style:
            hidden_styles.add(current_style)
        elif tag == ODF_TABLE + "table-column" and depth == 1:
            repeat = int(element.get(ODF_TABLE + "number-columns-repeated", "1"))
            if element.get(ODF_TABLE + "visibility") in ODF_HIDDEN:
                sheet["hidden_cols"].update(range(sheet["cols"], sheet["cols"] + min(repeat, 1000)))
            sheet["cols"] += repeat
        elif tag in (ODF_TABLE + "table-cell", ODF_TABLE + "covered-table-cell") and depth == 1:
            repeat = int(element.get(ODF_TABLE + "number-columns-repeated", "1"))
            text = odf_text(element) if tag == ODF_TABLE + "table-cell" else ""
            kind = element.get(ODF_OFFICE + "value-type")
            if not text and kind in ("float", "percentage", "currency") and element.get(ODF_OFFICE + "value"):
                text = format(float(element.get(ODF_OFFICE + "value")), ".15g")
            if text:
                numeric = kind in ("float", "percentage", "currency")
                for offset in range(min(repeat, ODF_MAX_REPEAT)):
                    sheet["cells"][sheet["col"] + offset] = (text, numeric)
            sheet["col"] += repeat
            element.clear()
        elif tag == ODF_TABLE + "table-row" and depth == 1:
            first = sheet["first_row"]
            repeat = sheet["repeat"]
            sheet["row"] = first + repeat - 1
            cells = sheet["cells"]
            if cells and not sheet["truncated"]:
                for index in range(repeat):
                    row = first + index
                    if sheet["row_hidden"]:
                        sheet["hidden_rows"].add(row)
                        continue
                    if len(sheet["grid"]) >= max_rows:
                        sheet["truncated"] = True
                        break
                    sheet["grid"][row] = {c: v for c, v in cells.items() if c not in sheet["hidden_cols"]}
            elif sheet["row_hidden"]:
                sheet["hidden_rows"].update(range(first, first + repeat))
            element.clear()
        elif tag == ODF_TABLE + "table":
            if depth == 1 and sheet is not None:
                grid = {r: c for r, c in sheet["grid"].items() if c}
                if not sheet["hidden"]:
                    yield sheet["name"], grid, sheet["hidden_rows"], sheet["truncated"]
                sheet = None
            depth -= 1


def open_archive(path):
    try:
        archive = zipfile.ZipFile(path)
    except (zipfile.BadZipFile, OSError):
        raise FormatError("Bukan berkas xlsx atau ods yang valid (mungkin berkata sandi atau rusak).")
    names = set(archive.namelist())
    if "xl/workbook.xml" in names:
        return archive, xlsx_sheets
    if "content.xml" in names and "mimetype" in names and b"spreadsheet" in archive.read("mimetype"):
        return archive, ods_sheets
    archive.close()
    raise FormatError("Berkas ini bukan spreadsheet xlsx atau ods.")


def convert(path, max_rows):
    archive, reader = open_archive(path)
    with archive:
        sections = []
        for name, grid, hidden_rows, truncated in reader(archive, max_rows):
            if not grid:
                continue
            parts = ["## " + re.sub(r"\s+", " ", name).strip()]
            parts.extend(render_block(block) for block in blocks_of(grid, hidden_rows))
            if truncated:
                parts.append("_Dipotong: sheet ini memuat lebih dari %d baris._" % max_rows)
            sections.append("\n\n".join(parts))
    if not sections:
        sys.stderr.write("Buku kerja tidak memuat data.\n")
        return EXIT_NO_DATA
    sys.stdout.write("\n\n".join(sections) + "\n")
    return 0


def main(argv):
    max_rows = DEFAULT_MAX_ROWS
    paths = []
    index = 0
    while index < len(argv):
        arg = argv[index]
        if arg == "--max-rows" and index + 1 < len(argv) and argv[index + 1].isdigit():
            max_rows = max(1, int(argv[index + 1]))
            index += 1
        elif arg.startswith("--"):
            sys.stderr.write("Opsi tidak dikenal: %s\n" % arg)
            return EXIT_USAGE
        else:
            paths.append(arg)
        index += 1
    if len(paths) != 1:
        sys.stderr.write("Pemakaian: sheet2md.py [--max-rows N] berkas.xlsx|berkas.ods\n")
        return EXIT_USAGE
    try:
        return convert(paths[0], max_rows)
    except FormatError as error:
        sys.stderr.write("%s\n" % error)
        return EXIT_BAD_FILE
    except ET.ParseError as error:
        sys.stderr.write("XML tidak terbaca: %s\n" % error)
        return EXIT_BAD_FILE


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
