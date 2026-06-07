package com.fankes.coloros.notify.framework

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.fankes.coloros.notify.core.ModuleInfo
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
        val configUpdatedAt = RuleStore.localConfigUpdatedAt
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
            val result = syncBlocking(service)
            val callbacks = synchronized(syncLock) {
                pendingCallbacks.remove(configUpdatedAt).orEmpty()
            }
            if (callbacks.isNotEmpty()) {
                mainHandler.post { callbacks.forEach { it(result) } }
            }
        }
    }

    fun syncBlocking(service: XposedService): Result<SyncResult> = runCatching {
        val remotePrefs = service.getRemotePreferences(RuleStore.GROUP_CONFIG)
        val localConfigUpdatedAt = RuleStore.localConfigUpdatedAt
        val committed = RuleStore.mirrorTo(remotePrefs)
        check(committed) { "远程偏好设置提交失败" }

        val payload = RuleStore.rulesJson.toByteArray(Charsets.UTF_8)
        service.openRemoteFile(RuleStore.RULES_FILE_NAME).use { pfd ->
            FileOutputStream(pfd.fileDescriptor).use { output ->
                output.channel.truncate(0)
                output.write(payload)
                output.flush()
                output.fd.sync()
            }
        }

        val mirroredCount = remotePrefs.getInt(RuleStore.KEY_RULES_COUNT, -1)
        check(mirroredCount == RuleStore.rulesCount) {
            "远程规则数量不一致：local=${RuleStore.rulesCount}, remote=$mirroredCount"
        }
        val mirroredUpdatedAt = remotePrefs.getLong(RuleStore.KEY_CONFIG_UPDATED_AT, -1L)
        check(mirroredUpdatedAt == localConfigUpdatedAt) {
            "远程配置版本不一致：local=$localConfigUpdatedAt, remote=$mirroredUpdatedAt"
        }
        val mirroredJson = service.openRemoteFile(RuleStore.RULES_FILE_NAME).use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
        }
        check(mirroredJson == RuleStore.rulesJson) { "远程规则文件校验失败" }

        RuleStore.markRemoteMirrorSuccess(localConfigUpdatedAt)
        SyncResult(
            rulesCount = mirroredCount,
            payloadBytes = payload.size,
            configUpdatedAt = localConfigUpdatedAt,
        )
    }

    fun syncIfPending(service: XposedService) {
        if (!RuleStore.hasPendingRemoteSync) return
        syncAsync(service) { result ->
            result.onFailure {
                Log.w(
                    ModuleInfo.LOG_TAG,
                    "框架服务恢复后自动镜像失败：${it.message ?: it.javaClass.simpleName}",
                    it
                )
            }
        }
    }
}
