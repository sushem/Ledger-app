package com.sushem.expenseTracker

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a minimal but valid .xlsx (OOXML spreadsheet) without pulling in a heavy
 * dependency like Apache POI (which has a history of Android compatibility issues —
 * large method count, JVM-only XML APIs, etc.). Cells are written as either numbers
 * or inline strings, which keeps this self-contained (no shared-strings table needed).
 *
 * Sheet names are the caller's responsibility to keep <=31 chars and free of
 * : \ / ? * [ ] — see MainActivity's uniqueSheetName()/monthDisplayName().
 */
object XlsxWriter {

    fun write(out: OutputStream, sheets: List<Pair<String, List<List<Any>>>>) {
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            entry("[Content_Types].xml", contentTypesXml(sheets.size))
            entry("_rels/.rels", relsXml())
            entry("xl/workbook.xml", workbookXml(sheets.map { it.first }))
            entry("xl/_rels/workbook.xml.rels", workbookRelsXml(sheets.size))
            sheets.forEachIndexed { idx, (_, rows) ->
                entry("xl/worksheets/sheet${idx + 1}.xml", sheetXml(rows))
            }
        }
    }

    private fun colLetter(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun sheetXml(rows: List<List<Any>>): String {
        val rowsXml = StringBuilder()
        rows.forEachIndexed { r, row ->
            rowsXml.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, value ->
                val ref = "${colLetter(c)}${r + 1}"
                when (value) {
                    is Number -> rowsXml.append("<c r=\"$ref\"><v>${value}</v></c>")
                    else -> rowsXml.append(
                        "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escapeXml(value.toString())}</t></is></c>"
                    )
                }
            }
            rowsXml.append("</row>")
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<sheetData>$rowsXml</sheetData></worksheet>"
    }

    private fun contentTypesXml(n: Int): String {
        val overrides = (1..n).joinToString("") {
            "<Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            overrides + "</Types>"
    }

    private fun relsXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private fun workbookXml(names: List<String>): String {
        val sheets = names.mapIndexed { i, n ->
            "<sheet name=\"${escapeXml(n)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
        }.joinToString("")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets>$sheets</sheets></workbook>"
    }

    private fun workbookRelsXml(n: Int): String {
        val rels = (1..n).joinToString("") {
            "<Relationship Id=\"rId$it\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$it.xml\"/>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            rels + "</Relationships>"
    }
}
