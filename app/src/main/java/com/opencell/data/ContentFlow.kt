package com.opencell.data

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Re-runs [query] whenever [uri] changes; emits once immediately. Query errors (e.g. missing permission) emit [fallback]. */
fun <T> ContentResolver.observeQuery(uri: Uri, fallback: T, query: () -> T): Flow<T> =
    callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        registerContentObserver(uri, true, observer)
        trySend(Unit)
        awaitClose { unregisterContentObserver(observer) }
    }
        .conflate()
        .map { runCatching(query).getOrElse { if (it is SecurityException) fallback else throw it } }
        .flowOn(Dispatchers.IO)
