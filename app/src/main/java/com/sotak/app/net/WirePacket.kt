package com.sotak.app.net

/**
 * Wire format for one audio datagram:
 *
 *   [ 4 bytes big-endian sequence ][ Opus payload ... ]
 *
 * The sequence lets the native jitter buffer detect and repair reordering and
 * loss, which is essential on a wireless link.
 */
object WirePacket {

    const val HEADER_SIZE = 4

    fun pack(sequence: Int, payload: ByteArray): ByteArray {
        val out = ByteArray(HEADER_SIZE + payload.size)
        out[0] = (sequence ushr 24).toByte()
        out[1] = (sequence ushr 16).toByte()
        out[2] = (sequence ushr 8).toByte()
        out[3] = sequence.toByte()
        System.arraycopy(payload, 0, out, HEADER_SIZE, payload.size)
        return out
    }

    fun sequenceOf(data: ByteArray): Int =
        ((data[0].toInt() and 0xFF) shl 24) or
            ((data[1].toInt() and 0xFF) shl 16) or
            ((data[2].toInt() and 0xFF) shl 8) or
            (data[3].toInt() and 0xFF)

    fun payloadOf(data: ByteArray): ByteArray = data.copyOfRange(HEADER_SIZE, data.size)
}
