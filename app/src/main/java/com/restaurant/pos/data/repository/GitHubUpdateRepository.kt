package com.restaurant.pos.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

class GitHubUpdateRepository(private val context: Context) {

    private val repoOwner = "dynamic-restaurant"
    private val repoName = "pos-app"

    suspend fun checkForUpdates(currentVersionName: String): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonStr)
                val tagVersion = jsonObj.optString("tag_name", "v1.0.0").trim().trimStart('v', 'V')
                val releaseNotes = jsonObj.optString("body", "Bug fixes and performance improvements.")
                
                var apkDownloadUrl = ""
                val assets = jsonObj.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val hasUpdate = compareVersions(tagVersion, currentVersionName) > 0

                return@withContext UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = tagVersion,
                    currentVersion = currentVersionName,
                    downloadUrl = apkDownloadUrl,
                    releaseNotes = releaseNotes
                )
            } else {
                Log.d("GitHubUpdateRepo", "GitHub API returned code: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e("GitHubUpdateRepo", "Error checking for GitHub updates", e)
        }

        return@withContext UpdateInfo(
            hasUpdate = false,
            latestVersion = currentVersionName,
            currentVersion = currentVersionName,
            downloadUrl = "",
            releaseNotes = "System up to date."
        )
    }

    fun startApkDownload(downloadUrl: String, version: String): Long {
        if (downloadUrl.isEmpty()) return -1L
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(downloadUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle("Restaurant POS Update v$version")
                setDescription("Downloading latest release APK...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "restaurant_pos_v$version.apk")
            }
            return downloadManager.enqueue(request)
        } catch (e: Exception) {
            Log.e("GitHubUpdateRepo", "Failed to enqueue APK download", e)
            return -1L
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val clean1 = v1.trim().trimStart('v', 'V')
        val clean2 = v2.trim().trimStart('v', 'V')
        val parts1 = clean1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = clean2.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
