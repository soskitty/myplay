package com.example.myplay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AlbumStorage(context: Context) {
    private val file = File(context.filesDir, "albums.json")
    private val tracksDir = File(context.filesDir, "tracks").also { it.mkdirs() }

    fun getAll(): List<Album> {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        if (text.isBlank()) return emptyList()

        val arr = JSONArray(text)
        val albums = mutableListOf<Album>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            albums.add(
                Album(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    treeUri = obj.getString("treeUri"),
                    trackIndex = obj.optInt("trackIndex", 0),
                    positionMs = obj.optInt("positionMs", 0),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            )
        }
        return albums.sortedByDescending { it.updatedAt }
    }

    fun upsert(album: Album) {
        val albums = getAll().toMutableList()
        val index = albums.indexOfFirst { it.id == album.id || it.treeUri == album.treeUri }
        val saved = album.copy(updatedAt = System.currentTimeMillis())
        if (index >= 0) albums[index] = saved else albums.add(saved)
        saveAll(albums)
    }

    fun rename(id: Long, name: String) {
        val albums = getAll().map { album ->
            if (album.id == id) album.copy(name = name, updatedAt = System.currentTimeMillis()) else album
        }
        saveAll(albums)
    }

    fun updateProgress(id: Long, trackIndex: Int, positionMs: Int) {
        val albums = getAll().map { album ->
            if (album.id == id) {
                album.copy(trackIndex = trackIndex.coerceAtLeast(0), positionMs = positionMs.coerceAtLeast(0), updatedAt = System.currentTimeMillis())
            } else {
                album
            }
        }
        saveAll(albums)
    }

    fun delete(id: Long) {
        saveAll(getAll().filterNot { it.id == id })
        File(tracksDir, "tracks_$id.json").delete()
    }

    fun loadTrackCache(albumId: Long): List<Pair<String, String>>? {
        val cacheFile = File(tracksDir, "tracks_$albumId.json")
        if (!cacheFile.exists()) return null
        val text = cacheFile.readText()
        if (text.isBlank()) return null
        val arr = JSONArray(text)
        val list = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(obj.getString("name") to obj.getString("uri"))
        }
        return list
    }

    fun saveTrackCache(albumId: Long, tracks: List<Track>) {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(JSONObject().apply {
                put("name", t.name)
                put("uri", t.uri.toString())
            })
        }
        File(tracksDir, "tracks_$albumId.json").writeText(arr.toString())
    }

    private fun saveAll(albums: List<Album>) {
        val arr = JSONArray()
        albums.forEach { album ->
            arr.put(JSONObject().apply {
                put("id", album.id)
                put("name", album.name)
                put("treeUri", album.treeUri)
                put("trackIndex", album.trackIndex)
                put("positionMs", album.positionMs)
                put("updatedAt", album.updatedAt)
            })
        }
        file.writeText(arr.toString())
    }
}
