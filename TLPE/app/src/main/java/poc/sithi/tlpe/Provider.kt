package poc.sithi.tlpe

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import java.io.FileNotFoundException

class Provider : ContentProvider() {
    override fun onCreate(): Boolean {
        return true
    }
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        return try {
            context?.assets?.openFd("system.apk")
        } catch (e: Exception) {
            throw FileNotFoundException("Failed: ${e.message}")
        }
    }
    override fun getType(uri: Uri): String { return "application/vnd.android.package-archive" }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? { return null }
    override fun insert(uri: Uri, values: ContentValues?): Uri? { return null }
    override fun delete(uri: Uri, selection: String?,
                        selectionArgs: Array<out String>?): Int { return 0 }
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int { return 0 }
}