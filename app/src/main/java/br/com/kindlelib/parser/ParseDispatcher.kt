package br.com.kindlelib.parser

import android.content.Context
import br.com.kindlelib.model.Book
import br.com.kindlelib.model.BookFormat
import br.com.kindlelib.model.ParsedMeta

object Parsers {
    fun parse(book: Book, context: Context, wantCover: Boolean): ParsedMeta {
        val open: () -> java.io.InputStream? = { book.open(context) }
        return when (book.format) {
            BookFormat.EPUB -> EpubParser.parse(open, wantCover)
            BookFormat.MOBI, BookFormat.AZW3 -> MobiParser.parse(open, book.fileSize, wantCover)
            BookFormat.PDF -> MiscParsers.parsePdf(open)
            BookFormat.TXT -> MiscParsers.parseTxt(open)
            BookFormat.FB2 -> MiscParsers.parseFb2(open)
            BookFormat.CBZ -> MiscParsers.parseCbz(open, wantCover)
            else -> ParsedMeta()
        }
    }
}
