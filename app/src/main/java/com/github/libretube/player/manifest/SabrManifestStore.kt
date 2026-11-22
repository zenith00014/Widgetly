package com.github.libretube.player.manifest
import java.util.concurrent.ConcurrentHashMap

object SabrManifestStore {
    private val manifests = ConcurrentHashMap<String, SabrManifest>()
    fun put(videoId: String, manifest: SabrManifest) {
        manifests[videoId] = manifest
        if (manifests.size > 48) manifests.keys.firstOrNull { it != videoId }?.let(manifests::remove)
    }
    fun get(videoId: String): SabrManifest? = manifests[videoId]
}
