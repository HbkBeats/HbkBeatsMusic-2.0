package com.hbkbeats.music

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.util.UUID

/**
 * Private space: files and folders hidden from the normal Music/Videos tabs.
 * They only appear in the Private tab after entering the PIN.
 */
object PrivateStore {
    private var prefs: SharedPreferences? = null

    var unlocked by mutableStateOf(false); private set
    var hasPin by mutableStateOf(false); private set
    var hiddenFolders by mutableStateOf<Set<String>>(emptySet()); private set
    var hiddenItems by mutableStateOf<Set<String>>(emptySet()); private set

    /** Set while we open our own video screen so leaving MainActivity doesn't lock the space. */
    var keepOpen = false

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("private_space", Context.MODE_PRIVATE)
        prefs = p
        hasPin = p.contains("pin_hash")
        hiddenFolders = p.getStringSet("folders", emptySet())!!.toSet()
        hiddenItems = p.getStringSet("items", emptySet())!!.toSet()
    }

    private fun key(t: Track) = (if (t.isVideo) "v" else "a") + t.id
    fun isItemHidden(t: Track) = key(t) in hiddenItems
    fun isFolderHidden(t: Track) = t.folderKey in hiddenFolders
    fun isPrivate(t: Track) = isItemHidden(t) || isFolderHidden(t)

    fun hideItem(t: Track) { hiddenItems = hiddenItems + key(t); save() }
    fun unhideItem(t: Track) { hiddenItems = hiddenItems - key(t); save() }
    fun hideFolder(t: Track) { hiddenFolders = hiddenFolders + t.folderKey; save() }
    fun unhideFolder(t: Track) { hiddenFolders = hiddenFolders - t.folderKey; save() }

    private fun save() {
        prefs?.edit()
            ?.putStringSet("folders", HashSet(hiddenFolders))
            ?.putStringSet("items", HashSet(hiddenItems))
            ?.apply()
    }

    private fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return Base64.encodeToString(md.digest((salt + pin).toByteArray()), Base64.NO_WRAP)
    }

    fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString()
        prefs?.edit()?.putString("pin_salt", salt)?.putString("pin_hash", hash(pin, salt))?.apply()
        hasPin = true
    }

    fun unlock(pin: String): Boolean {
        val p = prefs ?: return false
        val ok = hash(pin, p.getString("pin_salt", "") ?: "") == p.getString("pin_hash", "")
        if (ok) unlocked = true
        return ok
    }

    /** Locks the space and stops any private playback. */
    fun lock() {
        unlocked = false
        val t = PlayerManager.current
        if (t != null && isPrivate(t)) PlayerManager.stop()
    }
}
