package com.example.myplay

import android.app.AlertDialog
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private val storage by lazy { AlbumStorage(this) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapter: AlbumAdapter
    private lateinit var albumList: RecyclerView
    private lateinit var tvAlbum: TextView
    private lateinit var tvTrack: TextView
    private lateinit var tvTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: Button

    private var albums = emptyList<Album>()
    private var currentAlbum: Album? = null
    private var tracks = emptyList<Track>()
    private var currentTrackIndex = 0
    private var player: MediaPlayer? = null
    private var audioFd: android.os.ParcelFileDescriptor? = null
    private var userSeeking = false
    private val trackCache = mutableMapOf<String, List<Track>>()
    private var timerStopMs: Long? = null
    private var episodeBudget: Int? = null
    private lateinit var btnTimer5min: Button
    private lateinit var btnTimerEp: Button

    private val pickAlbumLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, result.data?.flags?.and(flags) ?: Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addAlbum(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        albumList = findViewById(R.id.album_list)
        tvAlbum = findViewById(R.id.tv_album)
        tvTrack = findViewById(R.id.tv_track)
        tvTime = findViewById(R.id.tv_time)
        seekBar = findViewById(R.id.seek_bar)
        btnPlay = findViewById(R.id.btn_play)

        adapter = AlbumAdapter(mutableListOf(), { currentAlbum?.id }, ::selectAlbum, ::confirmDeleteAlbum)
        albumList.layoutManager = LinearLayoutManager(this)
        albumList.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                adapter.moveItem(from.adapterPosition, to.adapterPosition)
                storage.updateOrder(adapter.getAlbums())
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
        }).attachToRecyclerView(albumList)

        findViewById<View>(R.id.btn_add_album).setOnClickListener { openDirectoryPicker() }
        findViewById<View>(R.id.btn_prev).setOnClickListener { playOffset(-1) }
        findViewById<View>(R.id.btn_next).setOnClickListener { playOffset(1) }
        findViewById<View>(R.id.tv_track).setOnClickListener { showTrackList() }
        btnPlay.setOnClickListener { togglePlay() }

        btnTimer5min = findViewById(R.id.btn_timer_5min)
        btnTimerEp = findViewById(R.id.btn_timer_ep)
        btnTimer5min.setOnClickListener {
            timerStopMs = (timerStopMs ?: System.currentTimeMillis()) + 5 * 60 * 1000
            updateTimerDisplay()
        }
        btnTimerEp.setOnClickListener {
            episodeBudget = (episodeBudget ?: 0) + 1
            updateTimerDisplay()
        }
        findViewById<View>(R.id.btn_timer_cancel).setOnClickListener {
            timerStopMs = null
            episodeBudget = null
            updateTimerDisplay()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) tvTime.text = "${formatTime(progress)} / ${formatTime(bar.max)}"
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                userSeeking = false
                player?.seekTo(bar.progress)
                saveProgress(bar.progress)
                updateNowPlaying()
            }
        })

        loadAlbums()
        albums.firstOrNull()?.let(::selectAlbum)
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
    }

    private fun openDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        pickAlbumLauncher.launch(intent)
    }

    private fun addAlbum(uri: Uri) {
        val directory = DocumentFile.fromTreeUri(this, uri)
        if (directory == null || !directory.isDirectory) {
            Toast.makeText(this, "请选择音频文件夹", Toast.LENGTH_SHORT).show()
            return
        }
        val name = directory.name?.takeIf { it.isNotBlank() } ?: DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':')
        val album = Album(name = name, treeUri = uri.toString())
        storage.upsert(album)
        loadAlbums()
        selectAlbum(albums.first { it.treeUri == uri.toString() })
    }

    private fun loadAlbums() {
        albums = storage.getAll()
        adapter.submitList(albums)
    }

    private fun selectAlbum(album: Album) {
        saveProgress()
        currentAlbum = album
        tracks = trackCache.getOrPut(album.treeUri) { loadTracks(album) }
        currentTrackIndex = album.trackIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        prepareCurrentTrack(album.positionMs, autoStart = false)
        adapter.notifyDataSetChanged()
    }

    private fun loadTracks(album: Album): List<Track> {
        storage.loadTrackCache(album.id)?.let { cached ->
            return cached.map { (name, uri) -> Track(name, Uri.parse(uri)) }
        }
        val directory = DocumentFile.fromTreeUri(this, Uri.parse(album.treeUri)) ?: return emptyList()
        val scanned = directory.listFiles()
            .asSequence()
            .filter { it.isFile && it.name != null && isAudio(it.name!!, it.type) }
            .map { Track(it.name!!, it.uri) }
            .sortedWith { a, b -> NaturalFileName.compare(a.name, b.name) }
            .toList()
        if (scanned.isNotEmpty()) storage.saveTrackCache(album.id, scanned)
        return scanned
    }

    private fun isAudio(name: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("audio/") == true) return true
        val lower = name.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") ||
            lower.endsWith(".wav") || lower.endsWith(".flac") || lower.endsWith(".ogg") || lower.endsWith(".opus")
    }

    private fun prepareCurrentTrack(positionMs: Int, autoStart: Boolean) {
        releasePlayer()
        if (tracks.isEmpty()) {
            tvAlbum.text = currentAlbum?.name ?: "还没有选择专辑"
            tvTrack.text = "这个文件夹里没有找到常见音频文件"
            tvTime.text = "00:00 / 00:00"
            seekBar.progress = 0
            seekBar.max = 0
            btnPlay.text = "播放"
            btnPlay.setBackgroundResource(R.drawable.button_primary)
            return
        }

        val track = tracks[currentTrackIndex]
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        mediaPlayer.setOnErrorListener { _, what, extra ->
            runOnUiThread {
                btnPlay.text = "播放"
                Toast.makeText(this, "无法播放此文件: $what/$extra", Toast.LENGTH_LONG).show()
                updateNowPlaying()
            }
            true
        }
        try {
            audioFd = contentResolver.openFileDescriptor(track.uri, "r")
            if (audioFd == null) {
                Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show()
                updateNowPlaying()
                return
            }
            mediaPlayer.setDataSource(audioFd!!.fileDescriptor)
            mediaPlayer.setOnPreparedListener {
                seekBar.max = it.duration.coerceAtLeast(0)
                val target = positionMs.coerceIn(0, seekBar.max)
                if (target > 0) it.seekTo(target)
                if (autoStart) it.start()
                updateNowPlaying()
                tick()
            }
            mediaPlayer.setOnCompletionListener {
                saveProgress(0)
                if (episodeBudget != null) {
                    episodeBudget = episodeBudget!! - 1
                    if (episodeBudget!! <= 0) {
                        stopPlayback()
                        return@setOnCompletionListener
                    }
                }
                if (currentTrackIndex < tracks.lastIndex) playOffset(1) else {
                    episodeBudget = null
                    updateNowPlaying()
                }
            }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败: ${e.message}", Toast.LENGTH_LONG).show()
            updateNowPlaying()
        }
    }

    private fun releasePlayer() {
        audioFd?.close()
        audioFd = null
        player?.release()
        player = null
    }

    private fun togglePlay() {
        val mediaPlayer = player
        if (mediaPlayer == null) {
            currentAlbum?.let { prepareCurrentTrack(it.positionMs, autoStart = true) }
            return
        }
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            saveProgress()
        } else {
            mediaPlayer.start()
        }
        updateNowPlaying()
        tick()
    }

    private fun playOffset(offset: Int) {
        if (tracks.isEmpty()) return
        saveProgress()
        currentTrackIndex = (currentTrackIndex + offset).coerceIn(0, tracks.lastIndex)
        saveProgress(0)
        prepareCurrentTrack(0, autoStart = true)
    }

    private fun saveProgress(positionOverride: Int? = null) {
        val album = currentAlbum ?: return
        val position = positionOverride ?: safePosition() ?: album.positionMs
        storage.updateProgress(album.id, currentTrackIndex, position)
        currentAlbum = album.copy(trackIndex = currentTrackIndex, positionMs = position)
        loadAlbums()
    }

    private fun confirmDeleteAlbum(album: Album) {
        AlertDialog.Builder(this)
            .setTitle("移除专辑")
            .setMessage("只从 MyPlay 列表移除，不删除手机里的音频文件。")
            .setPositiveButton("移除") { _, _ ->
                if (currentAlbum?.id == album.id) {
                    releasePlayer()
                    currentAlbum = null
                    tracks = emptyList()
                }
                trackCache.remove(album.treeUri)
                storage.delete(album.id)
                loadAlbums()
                updateNowPlaying()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTrackList() {
        if (tracks.isEmpty()) return
        val names = tracks.mapIndexed { i, t -> "${i + 1}. ${t.name}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${currentAlbum?.name}（${tracks.size}集）")
            .setItems(names) { _, which ->
                saveProgress()
                currentTrackIndex = which
                saveProgress(0)
                prepareCurrentTrack(0, autoStart = true)
            }
            .setPositiveButton("取消", null)
            .show()
    }

    private fun updateNowPlaying() {
        val album = currentAlbum
        val track = tracks.getOrNull(currentTrackIndex)
        tvAlbum.text = album?.name ?: "还没有选择专辑"
        tvTrack.text = if (track != null) "${currentTrackIndex + 1}/${tracks.size}  ${track.name}" else "添加专辑后开始播放"
        val mediaPlayer = player
        val position = safePosition() ?: currentAlbum?.positionMs ?: 0
        val duration = safeDuration()?.takeIf { it > 0 } ?: seekBar.max
        if (!userSeeking) {
            seekBar.max = duration.coerceAtLeast(0)
            seekBar.progress = position.coerceIn(0, seekBar.max.coerceAtLeast(0))
        }
        tvTime.text = "${formatTime(position)} / ${formatTime(duration)}"
        val playing = mediaPlayer?.isPlaying == true
        btnPlay.text = if (playing) "暂停" else "播放"
        btnPlay.setBackgroundResource(if (playing) R.drawable.button_pause else R.drawable.button_primary)
    }

    private fun updateTimerDisplay() {
        btnTimer5min.text = timerStopMs?.let { ms ->
            val sec = ((ms - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()
            "+5分钟 · 剩余 %02d:%02d".format(sec / 60, sec % 60)
        } ?: "+5分钟"
        btnTimerEp.text = episodeBudget?.let { "+1集 · 剩余 ${it}集" } ?: "+1集"
    }

    private fun stopPlayback() {
        timerStopMs = null
        episodeBudget = null
        player?.pause()
        updateNowPlaying()
        updateTimerDisplay()
        Toast.makeText(this, "定时停止", Toast.LENGTH_SHORT).show()
    }

    private fun tick() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateNowPlaying()
                updateTimerDisplay()
                val stop = timerStopMs?.let { System.currentTimeMillis() >= it } == true
                if (stop) stopPlayback()
                if (player?.isPlaying == true) handler.postDelayed(this, 1000)
            }
        }, 200)
    }

    private fun safePosition(): Int? = try {
        player?.currentPosition
    } catch (_: IllegalStateException) {
        null
    }

    private fun safeDuration(): Int? = try {
        player?.duration
    } catch (_: IllegalStateException) {
        null
    }
}

fun formatTime(ms: Int): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
