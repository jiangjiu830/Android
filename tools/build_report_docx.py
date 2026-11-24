"""把 docs/实验报告.md 转换成 Word 文档（docs/实验报告.docx）。

支持的 Markdown 子集：一/二/三级标题、段落（含硬换行合并）、无序与有序列表、
表格、代码块、图片（两两一行）、行内 `代码` 与 **加粗**。

用法：python build_report_docx.py
"""

import os
import re

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Inches, Pt, RGBColor

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
MD_PATH = os.path.join(PROJECT, "docs", "实验报告.md")
DOCX_PATH = os.path.join(PROJECT, "docs", "实验报告.docx")

BODY_FONT = "宋体"
HEADING_FONT = "黑体"
CODE_FONT = "Consolas"
IMAGE_WIDTH = 2.35
TEXT_COLOR = RGBColor(0x1A, 0x1A, 0x1A)
CODE_COLOR = RGBColor(0x1E, 0x4D, 0x3B)
HEADING_COLOR = RGBColor(0x1F, 0x3B, 0x31)

INLINE_PATTERN = re.compile(r"(\*\*.+?\*\*|`[^`]+`)")

CJK_RANGES = (
    (0x3000, 0x303F),
    (0x4E00, 0x9FFF),
    (0xFF00, 0xFFEF),
)


def is_cjk(char):
    return any(low <= ord(char) <= high for low, high in CJK_RANGES)


def needs_space(left, right):
    if left in "，。；：、）」』《！？…·":
        return False
    if is_cjk(left) or is_cjk(right):
        return False
    return True


def join_lines(lines):
    """把被 Markdown 折行的同一段落合并成一行。"""
    result = ""
    for raw in lines:
        line = raw.strip()
        if not line:
            continue
        if not result:
            result = line
        elif needs_space(result[-1], line[0]):
            result += " " + line
        else:
            result += line
    return result


def set_run_font(run, name, size=None, bold=None, color=TEXT_COLOR):
    run.font.name = name
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:eastAsia"), name)
    rfonts.set(qn("w:ascii"), name)
    rfonts.set(qn("w:hAnsi"), name)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.font.bold = bold
    if color is not None:
        run.font.color.rgb = color


def add_inline_runs(paragraph, text, size=10.5, font=BODY_FONT, bold=False):
    """渲染行内标记：**加粗** 与 `代码`。"""
    for part in INLINE_PATTERN.split(text):
        if not part:
            continue
        if part.startswith("**") and part.endswith("**") and len(part) > 4:
            run = paragraph.add_run(part[2:-2])
            set_run_font(run, font, size, bold=True)
        elif part.startswith("`") and part.endswith("`") and len(part) > 2:
            run = paragraph.add_run(part[1:-1])
            set_run_font(run, CODE_FONT, size - 0.5, color=CODE_COLOR)
        else:
            run = paragraph.add_run(part)
            set_run_font(run, font, size, bold=bold)


def style_document(document):
    normal = document.styles["Normal"]
    normal.font.name = BODY_FONT
    normal.font.size = Pt(10.5)
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), BODY_FONT)

    for name, size in (("Heading 1", 15), ("Heading 2", 13), ("Heading 3", 11.5)):
        style = document.styles[name]
        style.font.name = HEADING_FONT
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = HEADING_COLOR
        rpr = style._element.get_or_add_rPr()
        rfonts = rpr.find(qn("w:rFonts"))
        if rfonts is None:
            rfonts = OxmlElement("w:rFonts")
            rpr.append(rfonts)
        rfonts.set(qn("w:eastAsia"), HEADING_FONT)

    for section in document.sections:
        section.top_margin = Cm(2.4)
        section.bottom_margin = Cm(2.2)
        section.left_margin = Cm(2.6)
        section.right_margin = Cm(2.4)


def add_paragraph(document, text, indent_first=True, indent_left=0.0):
    paragraph = document.add_paragraph()
    paragraph.paragraph_format.space_after = Pt(5)
    paragraph.paragraph_format.line_spacing = 1.4
    if indent_first:
        paragraph.paragraph_format.first_line_indent = Pt(21)
    if indent_left:
        paragraph.paragraph_format.left_indent = Cm(indent_left)
    add_inline_runs(paragraph, text)
    return paragraph


def add_bullet(document, text):
    paragraph = document.add_paragraph(style="List Bullet")
    paragraph.paragraph_format.space_after = Pt(2)
    paragraph.paragraph_format.line_spacing = 1.3
    add_inline_runs(paragraph, text)


def add_numbered(document, prefix, text):
    paragraph = document.add_paragraph()
    paragraph.paragraph_format.space_after = Pt(2)
    paragraph.paragraph_format.line_spacing = 1.3
    paragraph.paragraph_format.left_indent = Cm(0.85)
    paragraph.paragraph_format.first_line_indent = Cm(-0.55)
    add_inline_runs(paragraph, prefix + " " + text)


def add_code_block(document, lines):
    paragraph = document.add_paragraph()
    paragraph.paragraph_format.space_before = Pt(4)
    paragraph.paragraph_format.space_after = Pt(8)
    paragraph.paragraph_format.left_indent = Cm(0.6)
    paragraph.paragraph_format.line_spacing = 1.1
    shading = OxmlElement("w:shd")
    shading.set(qn("w:val"), "clear")
    shading.set(qn("w:fill"), "F3F5F3")
    paragraph._p.get_or_add_pPr().append(shading)
    for index, line in enumerate(lines):
        if index:
            line_run = paragraph.add_run("\n")
            set_run_font(line_run, CODE_FONT, 9, color=CODE_COLOR)
        run = paragraph.add_run(line)
        set_run_font(run, CODE_FONT, 9, color=CODE_COLOR)


def repeat_header_row(table):
    """跨页时重复表头。"""
    tr_pr = table.rows[0]._tr.get_or_add_trPr()
    header = OxmlElement("w:tblHeader")
    header.set(qn("w:val"), "true")
    tr_pr.append(header)


def no_split_rows(table):
    """禁止表格行跨页拆分，避免上一页残留半行。"""
    for row in table.rows:
        tr_pr = row._tr.get_or_add_trPr()
        if tr_pr.find(qn("w:cantSplit")) is None:
            tr_pr.append(OxmlElement("w:cantSplit"))


def add_table(document, rows):
    header, body = rows[0], rows[1:]
    table = document.add_table(rows=1, cols=len(header))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    repeat_header_row(table)
    for index, text in enumerate(header):
        cell = table.rows[0].cells[index]
        cell.text = ""
        add_inline_runs(cell.paragraphs[0], text, size=9.5, font=HEADING_FONT, bold=True)
        cell.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER
    for row in body:
        cells = table.add_row().cells
        for index, text in enumerate(row[: len(header)]):
            cells[index].text = ""
            add_inline_runs(cells[index].paragraphs[0], text, size=9)
    no_split_rows(table)
    document.add_paragraph().paragraph_format.space_after = Pt(2)


def add_image_row(document, images):
    """把图片两两一行放进表格，避免单张图片占满整页。"""
    table = document.add_table(rows=1, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    for index, (caption_text, path) in enumerate(images):
        cell = table.rows[0].cells[index]
        paragraph = cell.paragraphs[0]
        paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
        if os.path.exists(path):
            paragraph.add_run().add_picture(path, width=Inches(IMAGE_WIDTH))
        else:
            paragraph.add_run("（缺少截图：" + caption_text + "）")
        caption = cell.add_paragraph()
        caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = caption.add_run(caption_text)
        set_run_font(run, BODY_FONT, 8.5, color=RGBColor(0x55, 0x5F, 0x59))
    document.add_paragraph().paragraph_format.space_after = Pt(2)


def resolve_image(relative_path):
    """图片路径按 Markdown 所在目录解析，找不到时按项目根目录解析。"""
    candidates = [
        os.path.join(os.path.dirname(MD_PATH), relative_path),
        os.path.join(PROJECT, relative_path),
    ]
    for candidate in candidates:
        if os.path.exists(candidate):
            return candidate
    return candidates[0]


def split_row(line):
    return [cell.strip() for cell in line.strip().strip("|").split("|")]


def build():
    with open(MD_PATH, encoding="utf-8") as handle:
        lines = handle.read().splitlines()

    document = Document()
    style_document(document)

    index = 0
    paragraph_buffer = []
    pending_images = []

    def flush_paragraph():
        if paragraph_buffer:
            add_paragraph(document, join_lines(paragraph_buffer))
            paragraph_buffer.clear()

    def flush_images():
        flush_paragraph()
        while pending_images:
            add_image_row(document, pending_images[:2])
            del pending_images[:2]

    while index < len(lines):
        line = lines[index]
        stripped = line.strip()

        if not stripped:
            flush_paragraph()
            index += 1
            continue

        if stripped.startswith("```"):
            flush_paragraph()
            flush_images()
            code = []
            index += 1
            while index < len(lines) and not lines[index].strip().startswith("```"):
                code.append(lines[index])
                index += 1
            add_code_block(document, code)
            index += 1
            continue

        heading = re.match(r"^(#{1,3})\s+(.*)$", stripped)
        if heading:
            flush_images()
            level = len(heading.group(1))
            text = heading.group(2).strip()
            if level == 1:
                paragraph = document.add_paragraph()
                paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
                paragraph.paragraph_format.space_after = Pt(18)
                add_inline_runs(paragraph, text, size=20, font=HEADING_FONT, bold=True)
                for run in paragraph.runs:
                    run.font.color.rgb = HEADING_COLOR
            else:
                document.add_heading(text, level=level - 1)
            index += 1
            continue

        if stripped.startswith("|"):
            flush_images()
            rows = []
            while index < len(lines) and lines[index].strip().startswith("|"):
                row = split_row(lines[index])
                if not all(re.fullmatch(r":?-{2,}:?", cell) for cell in row):
                    rows.append(row)
                index += 1
            if rows:
                add_table(document, rows)
            continue

        image = re.match(r"^!\[(.*?)\]\((.*?)\)$", stripped)
        if image:
            flush_paragraph()
            pending_images.append((image.group(1), resolve_image(image.group(2))))
            index += 1
            continue

        bullet = re.match(r"^[-*]\s+(.*)$", stripped)
        if bullet:
            flush_images()
            add_bullet(document, bullet.group(1))
            index += 1
            continue

        numbered = re.match(r"^(\d+)\.\s+(.*)$", stripped)
        if numbered:
            flush_images()
            add_numbered(document, numbered.group(1) + ".", numbered.group(2))
            index += 1
            continue

        paragraph_buffer.append(stripped)
        index += 1

    flush_images()
    document.save(DOCX_PATH)
    print("saved", DOCX_PATH)


if __name__ == "__main__":
    build()
