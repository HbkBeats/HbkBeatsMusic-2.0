package com.hbkbeats.music

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object Prefs {
    lateinit var sp: SharedPreferences
    var recentTick by mutableIntStateOf(0); private set

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences("hbk_prefs", Context.MODE_PRIVATE)
        }
    }

    fun recents(): List<Long> =
        (sp.getString("recents", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }

    fun pushRecent(id: Long) {
        val l = (listOf(id) + recents().filter { it != id }).take(20)
        sp.edit().putString("recents", l.joinToString(",")).apply()
        recentTick++
    }
}
