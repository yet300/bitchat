package com.app.data.mesh

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class PeerManager(
    private val scope: CoroutineScope
) {
    private val stalePeerTimeout = 3.minutes
    private val cleanupInterval = 1.minutes


    private data class PeerState(
        val nickname: String,
        val lastSeen: Long,
        val rssi: Int? = null,
        var isAnnounced: Boolean = false
    )

    private val peers = mutableMapOf<String, PeerState>()
    private val announcedToPeers = mutableSetOf<String>()
    private val mutex = Mutex()

    private val _peerListUpdatedFlow = MutableSharedFlow<List<String>>(replay = 1)
    val peerListUpdatedFlow: SharedFlow<List<String>> = _peerListUpdatedFlow

    private val _connectionEventsFlow = MutableSharedFlow<PeerManagerDelegate>()
    val connectionEventsFlow: SharedFlow<PeerManagerDelegate> = _connectionEventsFlow

    init {
        startPeriodicCleanup()
    }


    suspend fun updatePeerLastSeen(peerId: String) {
        if (peerId == "unknown") return
        mutex.withLock {
            peers[peerId]?.let {
                peers[peerId] = it.copy(lastSeen = Clock.System.now().toEpochMilliseconds())
            }
        }
    }


    suspend fun addOrUpdatePeer(peerId: String, nickname: String): Boolean {
        if (peerId == "unknown") return false

        mutex.withLock {
            // Логика очистки устаревших пиров с таким же ником
            val now = Clock.System.now().toEpochMilliseconds()
            val stalePeerIDs = peers.filter { (id, state) ->
                state.nickname == nickname && id != peerId && (now - state.lastSeen) > 10.seconds.inWholeMilliseconds
            }.keys.toList()

            stalePeerIDs.forEach { staleId ->
                removePeerInternal(staleId, notifyDelegate = false)
            }

            // Проверяем, новое ли это объявление
            val isFirstAnnounce = peers[peerId]?.isAnnounced != true

            // Обновляем или добавляем пира
            val currentState = peers[peerId]
            peers[peerId] = PeerState(
                nickname = nickname,
                lastSeen = now,
                rssi = currentState?.rssi,
                isAnnounced = true
            )

            if (isFirstAnnounce) {
                _connectionEventsFlow.tryEmit(PeerManagerDelegate.PeerConnected(nickname))
                notifyPeerListUpdate()
                return true
            }
            return false
        }
    }


    suspend fun removePeer(peerId: String) {
        mutex.withLock { removePeerInternal(peerId) }
    }

    private fun removePeerInternal(peerId: String, notifyDelegate: Boolean = true) {
        val removedPeer = peers.remove(peerId)
        announcedToPeers.remove(peerId)
        if (notifyDelegate && removedPeer != null) {
            _connectionEventsFlow.tryEmit(PeerManagerDelegate.PeerDisconnected(removedPeer.nickname))
            notifyPeerListUpdate()
        }
    }

    suspend fun updatePeerRssi(peerId: String, rssi: Int) {
        if (peerId == "unknown") return
        mutex.withLock {
            peers[peerId]?.let {
                peers[peerId] = it.copy(rssi = rssi)
            }
        }
    }


    suspend fun getAllPeerNicknames(): Map<String, String> = mutex.withLock {
        peers.mapValues { it.value.nickname }
    }

    suspend fun getActivePeerIDs(): List<String> = mutex.withLock {
        peers.keys.toList().sorted()
    }


    private fun notifyPeerListUpdate() {
        val peerList = peers.keys.toList().sorted()
        _peerListUpdatedFlow.tryEmit(peerList)
    }

    private fun startPeriodicCleanup() {
        scope.launch {
            while (isActive) {
                delay(cleanupInterval)
                cleanupStalePeers()
            }
        }
    }

    private suspend fun cleanupStalePeers() {
        val now = Clock.System.now().toEpochMilliseconds()
        val peersToRemove = mutex.withLock {
            peers.filter { (_, state) ->
                (now - state.lastSeen) > stalePeerTimeout.inWholeMilliseconds
            }.keys.toList()
        }

        if (peersToRemove.isNotEmpty()) {
            mutex.withLock {
                peersToRemove.forEach { removePeerInternal(it, notifyDelegate = true) }
            }
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}

sealed class PeerManagerDelegate {
    data class PeerConnected(val nickname: String) : PeerManagerDelegate()
    data class PeerDisconnected(val nickname: String) : PeerManagerDelegate()
}