package com.app.data.mesh

import com.app.data.model.BitchatPacket
import com.app.data.model.MessageType
import com.app.data.utils.BinarySerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.Buffer
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@OptIn(ExperimentalUuidApi::class)
class FragmentManager(
    private val scope: CoroutineScope,
) {
    private val maxFragmentSize = 500
    private val fragmentTimeout = 30.seconds
    private val cleanupInterval = 10.seconds

    private data class FragmentMetadata(
        val originalType: UByte,
        val totalFragments: Int,
        val timestamp: Instant,
        val fragments: MutableMap<Int, ByteArray> = mutableMapOf()
    )

    private val incomingFragments = mutableMapOf<String, FragmentMetadata>()
    private val mutex = Mutex()

    init {
        startPeriodicCleanup()
    }
    
    /**
     * Разбивает большой пакет на список маленьких пакетов-фрагментов.
     */
    fun createFragments(packet: BitchatPacket): List<BitchatPacket> {
        val data = BinarySerializer.encode(packet)
        if (data.size <= maxFragmentSize) {
            return listOf(packet) // Фрагментация не нужна
        }

        val fragmentId = Uuid.random().toString()
        val headerSize = 13
        val dataPerFragment = maxFragmentSize - headerSize
        val totalFragments = (data.size + dataPerFragment - 1) / dataPerFragment

        return List(totalFragments) { index ->
            val start = index * dataPerFragment
            val end = minOf(start + dataPerFragment, data.size)
            val fragmentData = data.sliceArray(start until end)

            val fragmentPayload = createFragmentPayload(
                fragmentId = fragmentId,
                index = index,
                total = totalFragments,
                originalType = packet.type,
                data = fragmentData
            )

            val fragmentType = when (index) {
                0 -> MessageType.FRAGMENT_START
                totalFragments - 1 -> MessageType.FRAGMENT_END
                else -> MessageType.FRAGMENT_CONTINUE
            }

            // Создаем новый пакет-фрагмент
            packet.copy(
                type = fragmentType.value,
                payload = fragmentPayload,
                signature = null // Фрагменты не подписываются индивидуально
            )
        }
    }

    /**
     * Обрабатывает входящий фрагмент и пытается собрать полный пакет.
     * @return Собранный BitchatPacket, если все фрагменты получены, иначе null.
     */
    suspend fun handleFragment(packet: BitchatPacket): BitchatPacket? {
        if (packet.payload.size < 13) return null

        return try {
            val source = Buffer().write(packet.payload)
            val fragmentId = source.readUtf8(8)
            val index = source.readShort().toInt()
            val total = source.readShort().toInt()
            val originalType = source.readByte().toUByte()
            val fragmentData = source.readByteArray()

            mutex.withLock {
                val metadata = incomingFragments.getOrPut(fragmentId) {
                    FragmentMetadata(originalType, total, Clock.System.now())
                }
                
                metadata.fragments[index] = fragmentData
                
                if (metadata.fragments.size == metadata.totalFragments) {
                    // Все фрагменты получены, собираем
                    incomingFragments.remove(fragmentId) // Удаляем из кэша
                    
                    val reassembledPayload = Buffer()
                    for (i in 0 until metadata.totalFragments) {
                        reassembledPayload.write(metadata.fragments[i] ?: return@withLock null)
                    }

                    val originalPacketData = reassembledPayload.readByteArray()
                    BinarySerializer.decode(originalPacketData)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun createFragmentPayload(
        fragmentId: String,
        index: Int,
        total: Int,
        originalType: UByte,
        data: ByteArray
    ): ByteArray {
        val buffer = Buffer()
        buffer.write(fragmentId.encodeToByteArray().take(8).toByteArray())
        buffer.writeShort(index)
        buffer.writeShort(total)
        buffer.writeByte(originalType.toInt())
        buffer.write(data)
        return buffer.readByteArray()
    }
    
    private fun startPeriodicCleanup() {
        scope.launch {
            while (isActive) {
                delay(cleanupInterval)
                cleanupOldFragments()
            }
        }
    }

    private suspend fun cleanupOldFragments() {
        mutex.withLock {
            val cutoffTime = Clock.System.now() - fragmentTimeout
            val fragmentsToRemove = incomingFragments.filter { it.value.timestamp < cutoffTime }.keys
            
            fragmentsToRemove.forEach {
                incomingFragments.remove(it)
            }
        }
    }
    
    fun shutdown() {
        scope.cancel()
    }
}