@file:OptIn(ExperimentalMaterial3Api::class)

package com.dai.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dai.app.BuildConfig
import com.dai.app.ChatViewModel
import com.dai.app.R
import com.dai.app.data.*
import kotlinx.coroutines.launch

@Composable
fun DaiApp(vm: ChatViewModel = viewModel()) {
    DaiTheme {
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var settings by remember { mutableStateOf(false) }
        var whatsNew by remember { mutableStateOf(false) }
        LaunchedEffect(vm.hasKey) { if (vm.hasKey && vm.showWhatsNew()) whatsNew = true }

        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                Drawer(vm, close = { scope.launch { drawer.close() } },
                    openSettings = { settings = true }, openNew = { whatsNew = true })
            }
        ) {
            Scaffold(
                topBar = { TopBar(vm) { scope.launch { drawer.open() } } },
                bottomBar = { Composer(vm) }
            ) { pad -> Messages(vm, Modifier.padding(pad)) }
        }
        if (settings) SettingsDialog(vm) { settings = false }
        if (whatsNew) AlertDialog(
            onDismissRequest = { whatsNew = false; vm.markSeen() },
            title = { Text(stringResource(R.string.whats_new_title)) },
            text = { Text(stringResource(R.string.whats_new_body), lineHeight = 22.sp) },
            confirmButton = { TextButton(onClick = { whatsNew = false; vm.markSeen() }) { Text(stringResource(R.string.close)) } }
        )
    }
}

@Composable
private fun TopBar(vm: ChatViewModel, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.menu)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_logo), null, Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                    Box {
                        Row(Modifier.clickable { open = true }, verticalAlignment = Alignment.CenterVertically) {
                            Text(vm.model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            MODELS.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { vm.setModel(m); open = false }) }
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = {
                if (vm.transcript().isNotBlank()) {
                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, vm.transcript()) }
                    ctx.startActivity(Intent.createChooser(i, null))
                }
            }) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
            IconButton(onClick = { vm.newChat() }) { Icon(Icons.Default.Add, stringResource(R.string.new_chat)) }
        }
    )
}

@Composable
private fun Drawer(vm: ChatViewModel, close: () -> Unit, openSettings: () -> Unit, openNew: () -> Unit) {
    ModalDrawerSheet {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Button(onClick = { vm.newChat(); close() }, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.new_chat))
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(vm.chats, key = { it.id }) { c ->
                NavigationDrawerItem(
                    label = { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = c.id == vm.currentId,
                    onClick = { vm.select(c.id); close() },
                    badge = { IconButton(onClick = { vm.delete(c.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.settings)) }, icon = { Icon(Icons.Default.Settings, null) },
            selected = false, onClick = { close(); openSettings() }, modifier = Modifier.padding(horizontal = 8.dp)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.whats_new)) }, icon = { Icon(Icons.Default.Refresh, null) },
            selected = false, onClick = { close(); openNew() }, modifier = Modifier.padding(horizontal = 8.dp)
        )
        Text(
            "Dai " + BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp, modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun Messages(vm: ChatViewModel, modifier: Modifier) {
    val chat = vm.current
    val state = rememberLazyListState()
    val count = chat?.messages?.size ?: 0
    val tail = chat?.messages?.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(count, tail) { if (count > 0) state.scrollToItem(count - 1) }
    if (chat == null || count == 0) {
        Welcome(vm, modifier)
        return
    }
    LazyColumn(
        modifier.fillMaxSize(), state = state,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(chat.messages) { i, m -> Bubble(vm, m, i == chat.messages.lastIndex) }
    }
}

@Composable
private fun Bubble(vm: ChatViewModel, m: Msg, last: Boolean) {
    val clip = LocalClipboardManager.current
    if (m.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp), modifier = Modifier.widthIn(max = 320.dp)
            ) { SelectionContainer { Text(m.content, Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) } }
        }
    } else {
        Row(Modifier.fillMaxWidth()) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (m.content.isEmpty()) {
                    Text(stringResource(R.string.thinking), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SelectionContainer { Markdown(m.content) }
                    Row {
                        TextButton(onClick = { clip.setText(AnnotatedString(m.content)) }) { Text(stringResource(R.string.copy)) }
                        if (last && !vm.busy) TextButton(onClick = { vm.regenerate() }) { Text(stringResource(R.string.regenerate)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome(vm: ChatViewModel, modifier: Modifier) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.ic_logo), null, Modifier.size(84.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.welcome_sub), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        if (!vm.hasKey) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.setup_step1))
                    Text(stringResource(R.string.setup_step2))
                    Text(stringResource(R.string.setup_step3))
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
                    }) { Text(stringResource(R.string.setup_get_key)) }
                    OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.api_key)) }, singleLine = true)
                    Button(onClick = { vm.saveKey(key) }, enabled = key.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.setup_start))
                    }
                }
            }
        } else {
            listOf(R.string.suggest_1, R.string.suggest_2, R.string.suggest_3, R.string.suggest_4).forEach { id ->
                val prompt = stringResource(id)
                Surface(
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.send(prompt) }
                ) { Text(prompt, Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
private fun Composer(vm: ChatViewModel) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(vm.draft) { vm.draft?.let { text = it; vm.draft = null } }
    Column(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        vm.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.message_hint)) }, maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    )
                )
                FilledIconButton(
                    onClick = { if (vm.busy) vm.stop() else { vm.send(text); text = "" } },
                    enabled = vm.busy || text.isNotBlank(), modifier = Modifier.size(46.dp)
                ) {
                    if (vm.busy) Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(3.dp)))
                    else Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(vm: ChatViewModel, onClose: () -> Unit) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var model by remember { mutableStateOf(vm.model) }
    var sys by remember { mutableStateOf(vm.system) }
    var temp by remember { mutableFloatStateOf(vm.temperature) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.settings)) },
        confirmButton = {
            TextButton(onClick = { vm.saveSettings(key, model, sys, temp); onClose() }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    key, { key = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.api_key)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    supportingText = { Text(stringResource(R.string.key_hint)) }
                )
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.model)) }, singleLine = true)
                OutlinedTextField(sys, { sys = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.system_prompt)) }, minLines = 3)
                Row {
                    TextButton(onClick = { sys = PRESET_CHAT }) { Text(stringResource(R.string.preset_chat)) }
                    TextButton(onClick = { sys = PRESET_CODE }) { Text(stringResource(R.string.preset_code)) }
                }
                Text(stringResource(R.string.temperature, "%.1f".format(temp)))
                Slider(value = temp, onValueChange = { temp = it }, valueRange = 0f..2f)
            }
        }
    )
}
