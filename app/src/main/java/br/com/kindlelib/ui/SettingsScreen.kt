package br.com.kindlelib.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.kindlelib.model.ExtraFolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState(initial = vm.settings.value)
    var accessOk by remember { mutableStateOf(hasStorageAccess(context)) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.addFolder(it) }
    }
    val kindlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.saveKindleUri(it) }
    }
    val manageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        accessOk = hasStorageAccess(context)
        if (accessOk) vm.scanLibrary()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(vm::goLibrary) { Icon(Icons.Default.ArrowBack, "Voltar") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Armazenamento", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (accessOk) "Acesso ao armazenamento concedido — o app escaneia apenas as pastas adicionadas abaixo."
                        else "Sem acesso ao armazenamento. Toque no botão abaixo para conceder, ou adicione pastas pelo seletor (funciona sempre).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!accessOk) {
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 30) {
                                manageLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } else {
                                runCatching {
                                    manageLauncher.launch(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    )
                                }
                            }
                        }) { Text("Conceder acesso a todos os arquivos") }
                    } else {
                        OutlinedButton(onClick = { vm.scanLibrary() }) { Text("Escanear novamente") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pastas da biblioteca", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (settings.extraFolders.isEmpty()) {
                        Text("Nenhuma pasta adicionada ainda.", style = MaterialTheme.typography.bodySmall)
                    }
                    settings.extraFolders.forEach { f ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("• ${f.name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.removeFolder(f) }) {
                                Icon(Icons.Default.Delete, "Remover pasta", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    OutlinedButton(onClick = { folderPicker.launch(null) }) {
                        Text("Adicionar pasta de livros...")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Metadados e capas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Buscar capa/metadados automaticamente (Google Books + Open Library)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = settings.autoFetchMeta, onCheckedChange = vm::toggleAutoMeta)
                    }
                    Text("A busca usa internet apenas quando ativada; a biblioteca funciona 100% offline.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Kindle (USB)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (settings.kindleFolderUri.isNotBlank()) "Pasta definida: ${settings.kindleFolderUri.substringAfterLast('/').ifBlank { "documents" }}"
                        else "Nenhuma pasta do Kindle definida ainda.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { kindlePicker.launch(null) }) {
                        Text(if (settings.kindleFolderUri.isNotBlank()) "Mudar pasta documents do Kindle" else "Escolher pasta documents do Kindle")
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Avisar quando um dispositivo USB for conectado", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = settings.autoOpenKindle, onCheckedChange = vm::toggleAutoKindle)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Limitações técnicas do Android", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "1. MTP/USB: o Android não permite que aplicativos manipulem diretamente o protocolo MTP de dispositivos conectados por USB host. Por isso, o acesso aos arquivos do Kindle é feito pelo seletor de pastas do sistema (Storage Access Framework) — o caminho oficial e suportado, inclusive com cabo OTG.\n\n" +
                        "2. Conversão EPUB→MOBI não é embutida: exigiria um conversor completo (como o Calibre). O app envia o arquivo como está — Kindles recentes (10ª geração+) leem EPUB nativamente. Para modelos antigos, use o botão \"Normalizar EPUB\" (reembala no padrão correto) que resolve a maioria dos problemas de compatibilidade.\n\n" +
                        "3. DRM: arquivos com DRM (comprados na Amazon) podem não ter capa/metadados legíveis; transferência ainda é possível, mas o Kindle pode se recusar a abrir sem autorização.\n\n" +
                        "4. Armazenamento: no Android 11+, o app pede permissão \"Todos os arquivos\"; se negada, use a adição de pastas pelo seletor.\n\n" +
                        "5. A loja bloqueada não afeta nada: a transferência por cabo não usa Wi-Fi nem contas Amazon.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Diagnóstico", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val crashFile = remember { java.io.File(context.filesDir, "crash_log.txt") }
                    var crashText by remember { mutableStateOf<String?>(null) }
                    Text(
                        "Se o app fechar sozinho em algum momento, toque abaixo para ver o motivo exato e copiar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = {
                        crashText = if (crashFile.exists()) crashFile.readText() else "Nenhum erro registrado ainda."
                    }) { Text("Ver último erro registrado") }
                    crashText?.let { txt ->
                        Text(
                            txt,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("BookTeca PX v1.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Gerenciador de biblioteca e-books offline (pt-BR)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
