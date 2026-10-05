package io.github.yighy.orgline.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** Reads and writes .org files in a folder picked by the user (Storage Access Framework). */
class OrgFolder(private val resolver: ContentResolver, val treeUri: Uri) {

    data class FileRef(val uri: Uri, val name: String, val path: String, val lastModified: Long)

    /** All .org files in the folder and its subfolders, skipping hidden ones (.git, .stfolder…). */
    fun listOrgFiles(): List<FileRef> {
        val result = mutableListOf<FileRef>()
        collect(DocumentsContract.getTreeDocumentId(treeUri), "", result)
        return result.sortedBy { it.path.lowercase() }
    }

    private fun collect(parentId: String, prefix: String, out: MutableList<FileRef>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val path = prefix + name
                if (c.getString(2) == Document.MIME_TYPE_DIR) {
                    collect(id, "$path/", out)
                } else if (name.endsWith(".org", ignoreCase = true)) {
                    out += FileRef(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), name, path, c.getLong(3))
                }
            }
        }
    }

    fun lastModified(uri: Uri): Long =
        resolver.query(uri, arrayOf(Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        } ?: 0L

    fun read(uri: Uri): String =
        resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Cannot open $uri")

    fun write(uri: Uri, text: String) {
        // "wt" truncates the file before writing
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Cannot write $uri")
    }

    /** Deletes the file from the folder. */
    fun delete(uri: Uri) {
        if (!DocumentsContract.deleteDocument(resolver, uri)) error("Cannot delete $uri")
    }

    /** Renames the file in its folder; returns its new URI. */
    fun rename(uri: Uri, name: String): Uri =
        DocumentsContract.renameDocument(resolver, uri, name) ?: error("Cannot rename to $name")

    /** Creates an empty .org file at the root of the folder. */
    fun createFile(name: String): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val fileName = if (name.endsWith(".org", ignoreCase = true)) name else "$name.org"
        // Generic MIME type so the provider keeps the .org extension as given
        return DocumentsContract.createDocument(resolver, parent, "application/octet-stream", fileName)
            ?: error("Cannot create $fileName")
    }
}
