package com.nasir.zip2git.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private const val MAX_HIGHLIGHT_CHARS = 60_000

private val COLOR_COMMENT = Color(0xFF8B949E)
private val COLOR_STRING = Color(0xFFA5D6FF)
private val COLOR_NUMBER = Color(0xFF79C0FF)
private val COLOR_KEYWORD = Color(0xFFFF7B72)

private const val NEVER = "(?!)"
private const val C_BLOCK = """/\*[\s\S]*?(?:\*/|\z)"""
private const val HTML_BLOCK = """<!--[\s\S]*?(?:-->|\z)"""
private const val STRING_PATTERN = """["](?:\\.|[^"\\\n])*["]|['](?:\\.|[^'\\\n])*[']|[`](?:\\.|[^`\\])*[`]"""
private const val NUMBER_PATTERN = """\b(?:0[xX][0-9a-fA-F]+|\d+(?:\.\d+)?)\b"""
private const val KEYWORDS =
    "fun|val|var|class|object|interface|return|if|else|when|for|while|do|break|continue|import|package|" +
        "def|from|as|in|is|not|and|or|None|True|False|self|try|catch|except|finally|throw|throws|raise|" +
        "new|this|null|true|false|undefined|const|let|function|async|await|export|default|switch|case|" +
        "public|private|protected|static|final|void|int|long|double|float|boolean|struct|enum|impl|fn|" +
        "mut|use|mod|pub|match|extends|implements|with|yield|lambda|pass|then|fi|done|elif|elseif|" +
        "select|insert|update|delete|where|table|create"

private val HASH_COMMENT_EXT = setOf("py", "rb", "sh", "bash", "yml", "yaml", "toml", "properties", "env")
private val DASH_COMMENT_EXT = setOf("sql", "lua")
private val HTML_EXT = setOf("html", "htm", "xml", "svg")
private val NO_LINE_COMMENT_EXT = setOf("json", "css", "scss")
private val CODE_EXT = setOf(
    "kt", "kts", "java", "js", "jsx", "ts", "tsx", "c", "cpp", "h", "hpp", "cs", "go", "rs", "swift",
    "dart", "php", "py", "rb", "sh", "bash", "yml", "yaml", "toml", "json", "xml", "html", "htm", "svg",
    "css", "scss", "gradle", "properties", "sql", "lua", "env"
)

private fun buildRegex(ext: String): Regex? {
    if (ext !in CODE_EXT) return null

    val block = when {
        ext in HTML_EXT -> HTML_BLOCK
        ext in HASH_COMMENT_EXT -> NEVER
        ext == "lua" -> NEVER
        else -> C_BLOCK
    }
    val line = when {
        ext in HASH_COMMENT_EXT -> "#[^\\n]*"
        ext in DASH_COMMENT_EXT -> "--[^\\n]*"
        ext in HTML_EXT || ext in NO_LINE_COMMENT_EXT -> NEVER
        else -> "//[^\\n]*"
    }

    val pattern = "($block)|($line)|($STRING_PATTERN)|($NUMBER_PATTERN)|(\\b(?:$KEYWORDS)\\b)"
    return try {
        Regex(pattern)
    } catch (e: Exception) {
        null
    }
}

/**
 * Lightweight, language-agnostic syntax highlighter. It never changes the text length,
 * so the identity offset mapping is always valid.
 */
class CodeHighlighter(extension: String) : VisualTransformation {

    private val regex: Regex? = buildRegex(extension.lowercase())

    override fun filter(text: AnnotatedString): TransformedText {
        val r = regex
        val raw = text.text
        if (r == null || raw.length > MAX_HIGHLIGHT_CHARS) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val styled = buildAnnotatedString {
            append(raw)
            for (m in r.findAll(raw)) {
                val color = when {
                    m.groups[1] != null || m.groups[2] != null -> COLOR_COMMENT
                    m.groups[3] != null -> COLOR_STRING
                    m.groups[4] != null -> COLOR_NUMBER
                    m.groups[5] != null -> COLOR_KEYWORD
                    else -> null
                }
                if (color != null && m.range.last >= m.range.first) {
                    addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
