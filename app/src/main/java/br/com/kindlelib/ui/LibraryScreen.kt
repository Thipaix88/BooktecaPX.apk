package br.com.kindlelib.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.kindlelib.model.Book
import br.com.kindlelib.model.BookFormat
import br.com.kindlelib.model.ReadingStatus
import br.com.kindlelib.model.SortBy

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: AppViewModel) {
    val books by vm.books.collectAsState(initial = emptyList())
    val query by vm.query.collectAsState(initial = "")
    val collections by vm.collections.collectAsState(initial = emptyList())
    val scanning by vm.scanning.collectAsState(initial = false)
    val visible = remember(books, query, vm.authorFilter.value, vm.formatFilter.value, vm.statusFilter.value, vm.collectionFilter.value, vm.sortBy.value) {
        vm.filteredBooks()
    }
    var searching by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = vm::setQuery,
                            placeholder = { Text("Buscar título, autor, série, tag...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton({ vm.setQuery("") }) {
                                    Icon(Icons.Default.Close, "Limpar")
                                }
                            }
                        )
                    } else {
                        Column {
                            Text("Biblioteca", fontWeight = FontWeight.Bold)
                            Text("${books.size} livros", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    IconButton({ searching = !searching }) { Icon(Icons.Default.Search, "Buscar") }
                    IconButton(vm::openSettings) { Icon(Icons.Default.Settings, "Configurações") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::openKindle) {
                Icon(Icons.Default.Send, "Enviar para o Kindle")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!searching) FilterRow(vm)
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (visible.isEmpty()) {
                EmptyState(scanning, books.isEmpty())
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { book ->
                        BookCard(book, onClick = { vm.openBook(book.id) },
                            onStatus = { s -> vm.setStatus(book, s) },
                            onSend = { vm.message.value = "Abra a aba Kindle para enviar \"${book.title}\"" },
                            onDelete = { vm.deleteBook(book) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(scanning: Boolean, noBooks: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (scanning) CircularProgressIndicator()
            Text(
                text = if (noBooks && !scanning) "Nenhum livro encontrado ainda.\nBaixe e-books (EPUB/MOBI) para a pasta Downloads\ne toque em atualizar, ou adicione uma pasta em Configurações."
                else "Procurando livros...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Composable
private fun FilterRow(vm: AppViewModel) {
    val authorF by vm.authorFilter.collectAsState(initial = null)
    val formatF by vm.formatFilter.collectAsState(initial = null)
    val statusF by vm.statusFilter.collectAsState(initial = null)
    val collF by vm.collectionFilter.collectAsState(initial = "")
    val sort by vm.sortBy.collectAsState(initial = SortBy.RECENTES)
    val books by vm.books.collectAsState(initial = emptyList())
    val collections by vm.collections.collectAsState(initial = emptyList())
    val authors = remember(books) { books.map { it.author }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }.take(40) }

    var mFormat by remember { mutableStateOf(false) }
    var mColl by remember { mutableStateOf(false) }
    var mSort by remember { mutableStateOf(false) }
    var mAuthor by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(selected = statusF == null, onClick = { vm.setStatusFilter(null) }, label = { Text("Todos") })
        ReadingStatus.entries.forEach { s ->
            FilterChip(selected = statusF == s, onClick = { vm.setStatusFilter(if (statusF == s) null else s) }, label = { Text(s.label) })
        }

        Box {
            FilterChip(selected = formatF != null, onClick = { mFormat = true }, label = { Text(formatF?.label() ?: "Formato") })
            DropdownMenu(mFormat, { mFormat = false }) {
                DropdownMenuItem(text = { Text("Todos") }, onClick = { vm.setFormatFilter(null); mFormat = false })
                BookFormat.entries.forEach { f ->
                    DropdownMenuItem(text = { Text(f.label()) }, onClick = { vm.setFormatFilter(f); mFormat = false })
                }
            }
        }
        Box {
            FilterChip(selected = collF.isNotEmpty(), onClick = { mColl = true }, label = { Text(collF.ifEmpty { "Coleção" }) })
            DropdownMenu(mColl, { mColl = false }) {
                DropdownMenuItem(text = { Text("Todas") }, onClick = { vm.setCollectionFilter(""); mColl = false })
                collections.forEach { c ->
                    DropdownMenuItem(text = { Text("${c.name} (${c.count})") }, onClick = { vm.setCollectionFilter(c.name); mColl = false })
                }
            }
        }
        Box {
            FilterChip(selected = authorF != null, onClick = { mAuthor = true }, label = { Text(authorF ?: "Autor") })
            DropdownMenu(mAuthor, { mAuthor = false }) {
                DropdownMenuItem(text = { Text("Todos") }, onClick = { vm.setAuthorFilter(null); mAuthor = false })
                authors.forEach { a ->
                    DropdownMenuItem(text = { Text(a, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { vm.setAuthorFilter(a); mAuthor = false })
                }
            }
        }
        Box {
            FilterChip(selected = true, onClick = { mSort = true }, label = { Text("Ordenar: ${sort.label}") })
            DropdownMenu(mSort, { mSort = false }) {
                SortBy.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.setSort(s); mSort = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: Book,
    onClick: () -> Unit,
    onStatus: (ReadingStatus) -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .padding(2.dp)
    ) {
        CoverImage(book, Modifier.fillMaxWidth().aspectRatio(0.72f))
        Text(
            book.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            book.author.ifBlank { book.format.label() },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.format.label(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(statusDot(book.status), style = MaterialTheme.typography.labelSmall, color = statusColor(book.status))
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Marcar: não lido") }, onClick = { onStatus(ReadingStatus.NAO_LIDO); menu = false })
            DropdownMenuItem(text = { Text("Marcar: lendo") }, onClick = { onStatus(ReadingStatus.LENDO); menu = false })
            DropdownMenuItem(text = { Text("Marcar: lido") }, onClick = { onStatus(ReadingStatus.LIDO); menu = false })
            DropdownMenuItem(text = { Text("Enviar para o Kindle") }, onClick = { onSend(); menu = false })
            DropdownMenuItem(text = { Text("Detalhes e edição") }, onClick = { onClick(); menu = false })
            DropdownMenuItem(text = { Text("Remover da biblioteca") }, onClick = { onDelete(); menu = false })
        }
    }
}

private fun statusDot(s: ReadingStatus): String = when (s) {
    ReadingStatus.NAO_LIDO -> "não lido"
    ReadingStatus.LENDO -> "lendo"
    ReadingStatus.LIDO -> "lido"
}

@androidx.compose.runtime.Composable
private fun statusColor(s: ReadingStatus): androidx.compose.ui.graphics.Color = when (s) {
    ReadingStatus.NAO_LIDO -> MaterialTheme.colorScheme.onSurfaceVariant
    ReadingStatus.LENDO -> MaterialTheme.colorScheme.secondary
    ReadingStatus.LIDO -> MaterialTheme.colorScheme.primary
}
