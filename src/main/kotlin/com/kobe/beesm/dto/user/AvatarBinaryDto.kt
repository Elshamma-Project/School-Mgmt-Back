package com.kobe.beesm.dto.user

/** Binaire avatar compte (UI) — exposé via API publique du module user. */
data class AvatarBinaryDto(
    val contentType: String,
    val sizeBytes: Long,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AvatarBinaryDto) return false
        return contentType == other.contentType &&
            sizeBytes == other.sizeBytes &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = contentType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
