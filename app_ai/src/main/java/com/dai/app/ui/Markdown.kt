package com.dai.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dai.app.R
import kotlinx.coroutines.delay

private val TOKEN = Regex("""\*\*(.+?)\*\*|`([^`\n]+)`""")
private val HEADING = Regex("^#{1,3}\\s+")

private fun styled(src: String): AnnotatedString = buildAnnotatedString {
    src.lines().forEachIndexed { n, raw ->
        if (n > 0) append("\n")
        var line = raw
        val head = HEADING.find(line)
        if (head != null) line = line.substring(head.value.length)
        else if (line.startsWith("- ") || line.startsWith("* ")) line = "•  " + line.substring(2)
        val start = length
        var last = 0
        TOKEN.findAll(line).forEach { m ->
            append(line.substring(last, m.range.first))
            if (m.groupValues[1].isNotEmpty()) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            } else {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x33808080))) { append(m.groupValues[2]) }
            }
            last = m.range.last + 1
        }
        append(line.substring(last))
        if (head != null) addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp), start, length)
    }
}

@Composable
fun Markdown(text: String) {
    Column {
        text.split("```").forEachIndexed { i, part ->
            if (i % 2 == 0) {
                if (part.isNotBlank()) Text(styled(part.trim('\n')), lineHeight = 22.sp)
            } else {
                CodeBlock(part)
            }
        }
    }
}

@Composable
private fun CodeBlock(part: String) {
    val clip = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val lang = part.substringBefore('\n').trim().ifEmpty { "code" }
    val code = part.substringAfter('\n', "").trimEnd()
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    Surface(
        color = Color(0xFF0F171C), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lang, color = Color(0xFF8DA0A8), fontSize = 12.sp)
                TextButton(onClick = { clip.setText(AnnotatedString(code)); copied = true }) {
                    Text(
                        if (copied) stringResource(R.string.copied) else stringResource(R.string.copy),
                        color = Color(0xFF5FD0C4), fontSize = 12.sp
                    )
                }
            }
            Text(
                code, color = Color(0xFFDBE7EA), fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            )
        }
    }
}
