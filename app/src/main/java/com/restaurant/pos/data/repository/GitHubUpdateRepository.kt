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
    val releaseNotes: String,
    val isError: Boolean = false,
    val errorMessage: String = ""
)

class GitHubUpdateRepository(private val context: Context) {

    private val repoOwner = "shahin0964"
    private val repoName = "Restaurant-POS"

    suspend fun checkForUpdates(currentVersionName: String): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            // 1. First try GitHub Releases API: GET /repos/shahin0964/Restaurant-POS/releases/latest
            val releasesUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            val updateInfo = fetchFromGitHubReleases(releasesUrl, currentVersionName)
            if (updateInfo != null) {
                Log.d("GitHubUpdateRepo", "Successfully fetched release metadata from $repoOwner/$repoName: $updateInfo")
                return@withContext updateInfo
            }

            // 2. Next try raw version.json from main or master branch on shahin0964/Restaurant-POS
            val rawVersionInfo = fetchFromRawVersionJson(repoOwner, repoName, currentVersionName)
            if (rawVersionInfo != null) {
                Log.d("GitHubUpdateRepo", "Successfully fetched version.json from $repoOwner/$repoName: $rawVersionInfo")
                return@withContext rawVersionInfo
            }
        } catch (e: Exception) {
            Log.e("GitHubUpdateRepo", "Error checking for updates from $repoOwner/$repoName", e)
        }

        // If network/API fails or metadata cannot be retrieved, return an explicit error state (NOT "Up to date" or "Latest version")
        Log.e("GitHubUpdateRepo", "Failed to retrieve update metadata from $repoOwner/$repoName")
        return@withContext UpdateInfo(
            hasUpdate = false,
            latestVersion = currentVersionName,
            currentVersion = currentVersionName,
            downloadUrl = "",
            releaseNotes = "Unable to check for updates. Please verify network connection.",
            isError = true,
            errorMessage = "Unable to check for updates. Please try again later."
        )
    }

    private fun fetchFromGitHubReleases(urlString: String, currentVersionName: String): UpdateInfo? {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "Android-POS-App")
            setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
            setRequestProperty("Pragma", "no-cache")
            useCaches = false
            instanceFollowRedirects = true
            connectTimeout = 8000
            readTimeout = 8000
        }

        if (connection.responseCode != 200) {
            Log.w("GitHubUpdateRepo", "Releases URL $urlString returned HTTP ${connection.responseCode}")
            return null
        }

        val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
        val jsonObj = JSONObject(jsonStr)

        val rawTag = jsonObj.optString("tag_name", "").trim()
        if (rawTag.isBlank()) return null

        val tagVersion = rawTag.trimStart('v', 'V')
        val releaseNotes = jsonObj.optString("body", "Bug fixes and performance improvements.")

        var apkDownloadUrl = ""
        val assets = jsonObj.optJSONArray("assets")
        if (assets != null && assets.length() > 0) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
        }

        val hasUpdate = compareVersions(tagVersion, currentVersionName) > 0

        return UpdateInfo(
            hasUpdate = hasUpdate,
            latestVersion = tagVersion,
            currentVersion = currentVersionName,
            downloadUrl = apkDownloadUrl,
            releaseNotes = releaseNotes,
            isError = false
        )
    }

    private fun fetchFromRawVersionJson(owner: String, repo: String, currentVersionName: String): UpdateInfo? {
        val branches = listOf("main", "master")
        for (branch in branches) {
            try {
                val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/version.json"
                val url = URL(rawUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Android-POS-App")
                    setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                    useCaches = false
                    instanceFollowRedirects = true
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                if (connection.responseCode == 200) {
                    val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = JSONObject(jsonStr)

                    val remoteVersionName = jsonObj.optString("versionName", "").ifBlank {
                        jsonObj.optString("version", "")
                    }.trim().trimStart('v', 'V')

                    val remoteVersionCode = jsonObj.optInt("versionCode", -1)
                    val apkUrl = jsonObj.optString("apkUrl", "").ifBlank {
                        jsonObj.optString("downloadUrl", "")
                    }
                    val releaseNotes = jsonObj.optString("releaseNotes", "New update available.")

                    if (remoteVersionName.isNotBlank() || remoteVersionCode > 0) {
                        val hasUpdate = if (remoteVersionCode > 0) {
                            val currentVersionCode = try {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                            } catch (e: Exception) {
                                1
                            }
                            remoteVersionCode > currentVersionCode
                        } else {
                            compareVersions(remoteVersionName, currentVersionName) > 0
                        }

                        return UpdateInfo(
                            hasUpdate = hasUpdate,
                            latestVersion = if (remoteVersionName.isNotBlank()) remoteVersionName else "v$remoteVersionCode",
                            currentVersion = currentVersionName,
                            downloadUrl = apkUrl,
                            releaseNotes = releaseNotes,
                            isError = false
                        )
                    }
                }
            } catch (e: Exception) {
                // Try next branch candidate
            }
        }
        return null
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
                setMimeType("application/vnd.android.package-archive")
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "restaurant_pos_v$version.apk")
            }
            val downloadId = downloadManager.enqueue(request)

            val onComplete = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctxt: Context?, intent: android.content.Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id == downloadId) {
                        try {
                            val downloadedFileUri = downloadManager.getUriForDownloadedFile(downloadId)
                            if (downloadedFileUri != null) {
                                val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(downloadedFileUri, "application/vnd.android.package-archive")
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(installIntent)
                            }
                        } catch (e: Exception) {
                            Log.e("GitHubUpdateRepo", "Error launching APK installer", e)
                        } finally {
                            try {
                                context.unregisterReceiver(this)
                            } catch (e: Exception) {
                                // Ignore unregister errors
                            }
                        }
                    }
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    onComplete,
                    android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    onComplete,
                    android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }

            return downloadId
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
