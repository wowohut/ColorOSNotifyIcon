package com.fankes.coloros.notify.framework

import android.os.Handler
import android.os.Looper
import com.fankes.coloros.notify.rules.RuleStore
import io.github.libxposed.service.XposedService
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.LinkedHashMap
import java.util.concurrent.Executors

object RemoteRuleMirror {

    data class SyncResult(
        val rulesCount: Int,
        val payloadBytes: Int,
        val configUpdatedAt: Long,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val syncLock = Any()
    private val pendingCallbacks = LinkedHashMap<Long, MutableList<(Result<SyncResult>) -> Unit>>()

    fun syncAsync(
        service: XposedService,
        onResult: ((Result<SyncResult>) -> Unit)? = null,
    ) {
        val snapshot = RuleStore.captureMirrorSnapshot()
        val configUpdatedAt = snapshot.configUpdatedAt
        synchronized(syncLock) {
            if (pendingCallbacks.containsKey(configUpdatedAt)) {
                if (onResult != null) {
                    pendingCallbacks.getOrPut(configUpdatedAt) { mutableListOf() } += onResult
                }
                return
            }
            pendingCallbacks[configUpdatedAt] = mutableListOf<(Result<SyncResult>) -> Unit>().apply {
                if (onResult != null) add(onResult)
            }
        }
        executor.execute {
            val result = syncBlocking(service, snapshot)
            val callbacks = synchronized(syncLock) {
                pendingCallbacks.remove(configUpdatedAt).orEmpty()
            }
            if (callbacks.isNotEmpty()) {
                mainHandler.post { callbacks.forEach { it(result) } }
            }
        }
    }

    fun syncBlocking(service: XposedService): Result<SyncResult> =
        syncBlocking(service, RuleStore.captureMirrorSnapshot())

    private fun syncBlocking(
        service: XposedService,
        snapshot: RuleStore.MirrorSnapshot,
    ): Result<SyncResult> = runCatching {
        val remotePrefs = service.getRemotePreferences(RuleStore.GROUP_CONFIG)
        val payload = snapshot.rulesJson.toByteArray(Charsets.UTF_8)
        service.openRemoteFile(RuleStore.RULES_FILE_NAME).use { pfd ->
            FileOutputStream(pfd.fileDescriptor).use { output ->
                output.channel.truncate(0)
                output.write(payload)
                output.flush()
                output.fd.sync()
            }
        }

        val mirroredJson = service.openRemoteFile(RuleStore.RULES_FILE_NAME).use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
        }
        check(mirroredJson == snapshot.rulesJson) { "远程规则文件校验失败" }

        val committed = RuleStore.mirrorTo(remotePrefs, snapshot)
        check(committed) { "远程偏好设置提交失败" }

        SyncResult(
            rulesCount = snapshot.rulesCount,
            payloadBytes = payload.size,
            configUpdatedAt = snapshot.configUpdatedAt,
        )
    }
}
