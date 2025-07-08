package com.app.domain.repository

import com.app.domain.model.Peer
import kotlinx.coroutines.flow.Flow

interface PeerRepository {

    fun getActivePeers(): Flow<List<Peer>>

    suspend fun blockPeer(identityKeyFingerprint: String)

    suspend fun unblockPeer(identityKeyFingerprint: String)

    fun getBlockedPeers(): Flow<Set<String>>
}