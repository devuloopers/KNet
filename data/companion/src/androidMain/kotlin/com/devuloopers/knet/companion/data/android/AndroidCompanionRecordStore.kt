package com.devuloopers.knet.companion.data.android

import android.content.Context
import android.content.SharedPreferences
import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android non-secret registration store. Shared code owns the versioned record schema. */
public class AndroidCompanionRecordStore private constructor(
    private val preferences: SharedPreferences,
    initialContent: String?,
    private val blockingCalls: AndroidBlockingCallExecutor,
) : CompanionRecordStore {
    private val mutableContent: MutableStateFlow<String?> = MutableStateFlow(initialContent)

    override val content: StateFlow<String?> = mutableContent.asStateFlow()

    override suspend fun write(content: String?) {
        blockingCalls.execute {
            val editor = preferences.edit()
            if (content == null) editor.remove(CONTENT_KEY) else editor.putString(CONTENT_KEY, content)
            check(editor.commit()) { "Unable to persist companion registration state." }
            // Publish from the same non-suspending operation so cancellation cannot leave memory behind disk.
            mutableContent.value = content
        }
    }

    public companion object {
        /**
         * Opens and restores the Android record store without blocking the caller's coroutine dispatcher.
         *
         * @param context Android owner whose application context supplies process-scoped preferences.
         * @param ioDispatcher worker dispatcher used for preference creation, reads, and committed writes.
         * @return initialized record store containing the durable registration snapshot.
         */
        public suspend fun create(
            context: Context,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): AndroidCompanionRecordStore {
            val blockingCalls = AndroidBlockingCallExecutor(ioDispatcher)
            return blockingCalls.execute {
                val preferences = context.applicationContext.getSharedPreferences(
                    REGISTRATION_PREFERENCES,
                    Context.MODE_PRIVATE,
                )
                AndroidCompanionRecordStore(
                    preferences = preferences,
                    initialContent = preferences.getString(CONTENT_KEY, null),
                    blockingCalls = blockingCalls,
                )
            }
        }

        private const val REGISTRATION_PREFERENCES: String = "knet_companion_registrations"
        private const val CONTENT_KEY: String = "versioned_content"
    }
}
