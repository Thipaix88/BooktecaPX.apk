package br.com.kindlelib.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.kindlelib.model.Book
import br.com.kindlelib.model.ReadingStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel) {
    val book by vm.shownBook.collectAsState()
    if (book == null) {
        LaunchedEffect(Unit) { vm.closeDetail() }
        return
    }
    val b = book!!
    var title by remember(b.id) { mutableStateOf(b.title) }
    var author by remember(b.id) { mutableStateOf(b.author) }
    var genre by remember(b.id) { mutableStateOf(b.genre) }
    var series by remember(b.id) { mutableStateOf(b.series) }
    var seriesIndex by remember(b.id) { mutableStateOf(b.seriesIndex) }
    var publisher by remember(b.id) { mutableStateOf(b.publisher) }
    var language by remember(b.id) { mutableStateOf(b.language) }
    var synopsis by remember(b.id) { mutableStateOf(b.synopsis) }
    var collection by remember(b.id) { mutableStateOf(b.collection) }
    var tags by remember(b.id) { mutableStateOf(b.tags.joinToString(", ")) }
    val metaBusy by vm.metaBusy.collectAsState(initial = false)

    LaunchedEffect(title, author, genre, series, seriesIndex, publisher, language, synopsis, collection, tags) {
        vm.updateBook(
            b.copy(
                title = title.trim().ifEmpty { b.title },
                author = author.trim(),
                genre = genre.trim(),
                series = series.trim(),
                seriesIndex = seriesIndex.trim(),
                publisher = publisher.trim(),
                language = language.trim(),
                synopsis = synopsis,
                collection = collection.trim(),
                tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            )
        )
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.setCoverFromUri(b, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalhes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(vm::closeDetail) { Icon(Icons.Default.ArrowBack, "Voltar") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .width(110.dp)
                        .aspectRatio(0.72f)
                        .then(
                            if (b.coverPath == null) Modifier.clickable(enabled = !metaBusy) { vm.fetchMetadata(b, force = true) }
                            else Modifier
                        )
                ) {
                    CoverImage(b, Modifier.fillMaxSize())
                    if (b.coverPath == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            Text(
                                if (metaBusy) "Buscando..." else "Toque para\nbuscar capa",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(b.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(b.author.ifBlank { "Autor desconhecido" }, style = MaterialTheme.typography.bodyMedium)
                    Text("${b.format.label()} • ${b.displaySize()}${if (b.hasDrm) " • DRM" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (b.hasDrm) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, Modifier.size(14.dp), MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("Pode ter restrições de DRM", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Text("Status de leitura", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReadingStatus.entries.forEach { s ->
                    FilterChip(selected = b.status == s, onClick = { vm.setStatus(b, s) }, label = { Text(s.label) })
                }
            }

            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Autor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Gênero") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = series, onValueChange = { series = it }, label = { Text("Série") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = seriesIndex, onValueChange = { seriesIndex = it }, label = { Text("Nº") }, singleLine = true, modifier = Modifier.width(70.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text("Editora") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = language, onValueChange = { language = it }, label = { Text("Idioma") }, singleLine = true, modifier = Modifier.width(100.dp))
            }
            OutlinedTextField(value = collection, onValueChange = { collection = it }, label = { Text("Coleção / categoria") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags (separadas por vírgula)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = synopsis, onValueChange = { synopsis = it }, label = { Text("Sinopse") }, minLines = 4, modifier = Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.fetchMetadata(b, force = true) }, enabled = !metaBusy, modifier = Modifier.weight(1f)) {
                    if (metaBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Buscar metadados\nna internet")
                }
                OutlinedButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Text("Capa da\ngaleria")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.normalizeEpub(b) }, modifier = Modifier.weight(1f), enabled = b.format == br.com.kindlelib.model.BookFormat.EPUB) {
                    Text("Normalizar EPUB")
                }
                OutlinedButton(onClick = { vm.openBookFile(b) }, modifier = Modifier.weight(1f)) {
                    Text("Abrir arquivo")
                }
            }
            Button(onClick = {
                vm.message.value = "Abra a aba Kindle e envie selecionando o livro"
                vm.openKindle()
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Send, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Enviar para o Kindle")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.deleteBook(b) }, modifier = Modifier.fillMaxWidth()) {
                Text("Remover da biblioteca", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
