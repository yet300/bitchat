package com.app.data.model;

enum class MessageType(val value: UByte) {
    ANNOUNCE(0x01u),
    KEY_EXCHANGE(0x02u),
    LEAVE(0x03u),
    MESSAGE(0x04u),
    FRAGMENT_START(0x05u),
    FRAGMENT_CONTINUE(0x06u),
    FRAGMENT_END(0x07u),
    CHANNEL_ANNOUNCE(0x08u),
    CHANNEL_RETENTION(0x09u),
    DELIVERY_ACK(0x0Au),
    DELIVERY_STATUS_REQUEST(0x0Bu),
    READ_RECEIPT(0x0Cu);

    companion object {
        fun fromValue(value: UByte): MessageType? {
            return entries.find { it.value == value }
        }
    }
}