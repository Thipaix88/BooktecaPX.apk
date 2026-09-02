package br.com.kindlelib.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.kindlelib.model.Book
import br.com.kindlelib.model.KindleItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KindleScreen(vm: AppViewModel) {
    val kindleUri by vm.kindleUri.collectAsState(initial = null)
    val kindleBooks by vm.kindleBooks.collectAsState(initial = emptyList())
    val usb by vm.usbDevice.collectAsState(initial = null)
    val progress by vm.transferProgress.collectAsState(initial = null)
    val books by vm.books.collectAsState(initial = emptyList())
    val selected = remember { mutableStateMapOf<String, Book>() }
    var tab by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.saveKindleUri(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transferência Kindle", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(vm::goLibrary) { Icon(Icons.Default.ArrowBack, "Voltar") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (usb != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                    Text(
                        "Dispositivo USB detectado: $usb (protocolo MTP — use o seletor de pasta abaixo)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    "Conecte o Kindle via USB e toque em \"Escolher pasta\" (modo transferência de arquivos). Se não aparecer, selecione manualmente a pasta \"documents\" do Kindle.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { picker.launch(null) }) {
                    Text(if (kindleUri != null) "Mudar pasta do Kindle" else "Escolher pasta documents do Kindle")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (kindleUri != null) "Pasta definida" else "Nenhuma pasta",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val kinUri = kindleUri
            if (kinUri != null) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Enviar livros") })
                    Tab(selected = tab == 1, onClick = {
                        tab = 1
                        vm.refreshKindle()
                    }, text = { Text("No Kindle (${kindleBooks.size})") })
                }

                val frac = progress?.let { if (it.second > 0) it.first.toFloat() / it.second else 0f }
                if (frac != null) {
                    LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth())
                    Text("Enviando ${progress!!.first}/${progress!!.second}...",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp))
                }

                if (tab == 0) {
                    val selectedList = selected.values.toList()
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { vm.sendToKindle(selectedList) }, enabled = selectedList.isNotEmpty() && progress == null) {
                            Icon(Icons.Default.Send, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Enviar selecionados (${selectedList.size})")
                        }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(vm.filteredBooks(), key = { it.id }) { book ->
                            SelectableBookRow(book, selected.containsKey(book.id)) { checked ->
                                if (checked) selected[book.id] = book else selected.remove(book.id)
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        if (kindleBooks.isEmpty()) {
                            item { Text("Nenhum livro encontrado na pasta do Kindle", Modifier.padding(16.dp)) }
                        }
                        items(kindleBooks, key = { it.uri.toString() }) { item ->
                            KindleRow(item, onDelete = { vm.deleteFromKindle(item) })
                        }
                    }
                }
            } else {
                Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Como funciona:\n1. Conecte o Kindle ao celular via cabo USB (OTG) e escolha \"Transferir arquivos\" (MTP).\n2. Toque em \"Escolher pasta documents do Kindle\".\n3. No seletor do Android, o Kindle aparecerá como dispositivo USB — abra a pasta \"documents\".\n4. Selecione os livros e toque em enviar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Nota: o Android não permite que apps acessem diretamente o MTP do Kindle via USB host — por isso usamos o seletor de pastas do sistema (Storage Access Framework), que é o caminho oficial e funciona com cabos OTG.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableBookRow(book: Book, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        CoverImage(book, Modifier.size(width = 28.dp, height = 40.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${book.author.ifBlank { "—" }} • ${book.format.label()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KindleRow(item: KindleItem, onDelete: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Text(br.com.kindlelib.model.formatSize(item.size), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Excluir do Kindle", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
