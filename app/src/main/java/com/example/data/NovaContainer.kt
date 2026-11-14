package com.example.data

import android.content.Context
import com.example.data.local.ChatRepository
import com.example.data.local.NovaDatabase
import com.example.data.local.NovaPreferencesRepository
import com.example.data.remote.OpenSourceAiRepository

class NovaContainer private constructor(appContext: Context) {
    private val database = NovaDatabase.getInstance(appContext)
    val chatRepository = ChatRepository(database.chatDao())
    val preferencesRepository = NovaPreferencesRepository(appContext)
    val aiRepository = OpenSourceAiRepository(appContext)

    companion object {
        @Volatile
        private var INSTANCE: NovaContainer? = null

        fun get(context: Context): NovaContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NovaContainer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
