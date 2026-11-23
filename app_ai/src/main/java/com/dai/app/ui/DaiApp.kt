package com.dai.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dai.app.ChatViewModel
import com.dai.app.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.dai.app.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaiApp(vm: ChatViewModel = viewModel()) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF5FD0C4), onPrimary = Color(0xFF08201D)) else lightColorScheme(primary = Color(0xFF0B6E6E))) {
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var settings by remember { mutableStateOf(false) }

        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                    Button(
                        onClick = { vm.newChat(); scope.launch { drawer.close() } },
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    ) { Text(stringResource(R.string.new_chat)) }
                    LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        items(vm.chats, key = { it.id }) { c ->
                            NavigationDrawerItem(
                                label = { Text(c.title, maxLines = 1) },
                                selected = c.id == vm.currentId,
                                onClick = { vm.select(c.id); scope.launch { drawer.close() } },
                                badge = { Text("×", Modifier.clickable { vm.delete(c.id) }.padding(8.dp)) },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                    TextButton(
                        onClick = { settings = true; scope.launch { drawer.close() } },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) { Text(stringResource(R.string.settings)) }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        navigationIcon = { TextButton(onClick = { scope.launch { drawer.open() } }) { Text("☰", fontSize = 20.sp) } },
                        actions = { ProviderMenu(vm) }
                    )
                },
                bottomBar = {
                    Column {
                        vm.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        ChatInput(vm)
                    }
                }
            ) { pad -> Messages(vm.current, Modifier.padding(pad)) }
        }
        if (settings) SettingsDialog(vm) { settings = false }
    }
}

@Composable
fun ProviderMenu(vm: ChatViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(vm.provider.label + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Provider.entries.forEach { p ->
                DropdownMenuItem(text = { Text(p.label) }, onClick = { vm.setProvider(p); open = false })
            }
        }
    }
}

@Composable
fun Messages(chat: Chat?, modifier: Modifier) {
    val state = rememberLazyListState()
    val count = chat?.messages?.size ?: 0
    val tail = chat?.messages?.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(count, tail) { if (count > 0) state.scrollToItem(count - 1) }
    if (chat == null || count == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(88.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium)
                Text(stringResource(R.string.empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(), state = state,
        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(chat.messages) { m ->
            if (m.role == "user") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Text(m.content, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            } else SelectionContainer { Column { Rich(m.content.ifEmpty { "…" }) } }
        }
    }
}

@Composable
fun Rich(text: String) {
    val clip = LocalClipboardManager.current
    text.split("```").forEachIndexed { i, part ->
        if (i % 2 == 0) {
            if (part.isNotBlank()) Text(part.trim())
        } else {
            val lang = part.substringBefore('\n').trim().ifEmpty { "code" }
            val code = part.substringAfter('\n', "").trimEnd()
            Surface(
                color = Color(0xFF0F171C), shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lang, color = Color(0xFF8DA0A8), fontSize = 12.sp)
                        TextButton(onClick = { clip.setText(AnnotatedString(code)) }) { Text(stringResource(R.string.copy), fontSize = 12.sp) }
                    }
                    Text(
                        code, color = Color(0xFFDBE7EA), fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInput(vm: ChatViewModel) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(vm.draft) { vm.draft?.let { text = it; vm.draft = null } }
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.message_hint, vm.provider.label)) }, maxLines = 6, shape = RoundedCornerShape(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { if (vm.busy) vm.stop() else if (text.isNotBlank()) { vm.send(text); text = "" } },
            shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(52.dp)
        ) { Text(if (vm.busy) "■" else "↑", fontSize = 22.sp) }
    }
}

@Composable
fun SettingsDialog(vm: ChatViewModel, onClose: () -> Unit) {
    val keys = remember { Provider.entries.associateWith { mutableStateOf(vm.key(it)) } }
    val models = remember { Provider.entries.associateWith { mutableStateOf(vm.model(it)) } }
    var sys by remember { mutableStateOf(vm.system) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.settings)) },
        confirmButton = {
            TextButton(onClick = {
                vm.saveSettings(keys.mapValues { it.value.value }, models.mapValues { it.value.value }, sys)
                onClose()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Provider.entries.forEach { p ->
                    OutlinedTextField(
                        keys[p]!!.value, { keys[p]!!.value = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.api_key, p.label)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        models[p]!!.value, { models[p]!!.value = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.model_name, p.label)) }, singleLine = true
                    )
                }
                OutlinedTextField(sys, { sys = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.system_prompt)) }, minLines = 3)
                Row {
                    TextButton(onClick = { sys = PRESET_CHAT }) { Text(stringResource(R.string.chat_preset)) }
                    TextButton(onClick = { sys = PRESET_CODE }) { Text(stringResource(R.string.code_preset)) }
                }
            }
        }
    )
}
