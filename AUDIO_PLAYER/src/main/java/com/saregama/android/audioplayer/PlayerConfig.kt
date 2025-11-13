package com.saregama.android.audioplayer

data class PlayerConfig(
    val handleAudioFocus: Boolean = true,
    val duckOnFocusLoss: Boolean = true,
    val stopWithApp: Boolean = false,
    val notificationChannelId: String = "audio_playback",
    val notificationChannelName: String = "Audio playback",
    val smallIconResId: Int? = null,
    // Hook: resolve saved queue IDs to Track list for restore
    val resolver: (suspend (ids: List<String>) -> List<com.saregama.android.audioplayer.model.Track>)? = null,
    val repository: RepositoryConfig? = null,
){
    data class RepositoryConfig(
        /** Return a stable SQLCipher passphrase (e.g., from Android Keystore). */
        val passphraseProvider: suspend () -> ByteArray,
        /** If true, `AudioPlayer.init()` will auto-init the ServiceLocator once. */
        val autoInit: Boolean = true
    )
}
