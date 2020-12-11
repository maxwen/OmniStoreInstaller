/*
 *  Copyright (C) 2020 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnistoreinstaller

import android.Manifest
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.URLUtil
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val TAG = "OmniStoreInstaller:MainActivity"
    private lateinit var mDownloadManager: DownloadManager
    private val mDownloadReceiver: DownloadReceiver = DownloadReceiver()
    private val STORE_APP_APK = "OmniStore.apk"
    private val OMNI_STORE_APP_PKG = "org.omnirom.omnistore"
    private var mDownloadId:Long = -1

    inner class DownloadReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive " + intent?.action)
            if (intent?.action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                stopProgress()
                mDownloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)!!
                if (mDownloadId == -1L) {
                    return
                }
                installApp(mDownloadId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mDownloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val downloadFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        registerReceiver(mDownloadReceiver, downloadFilter)

        findViewById<Button>(R.id.install_store).setOnClickListener {
            if (!isInstalled()) {
                downloadStore()
            } else {
                disableMe()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (isInstalled()) {
            findViewById<TextView>(R.id.install_text).text = getString(R.string.store_installed)
            findViewById<Button>(R.id.install_store).text = getString(R.string.disable_app_button)
        } else {
            findViewById<TextView>(R.id.install_text).text = getString(R.string.store_welcome)
            findViewById<Button>(R.id.install_store).text = getString(R.string.install_store_button)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mDownloadReceiver)
    }

    private fun disableMe() {
        packageManager.setApplicationEnabledSetting(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun downloadStore() {
        startProgress()
        val url: String = getAppsRootUri(this) + STORE_APP_APK
        val checkApp =
            NetworkUtils().CheckAppTask(
                url,
                object : NetworkUtils.NetworkTaskCallback {
                    override fun postAction(networkError: Boolean) {
                        if (networkError) {
                            stopProgress()
                            showNetworkError(url)
                        } else {
                            val request: DownloadManager.Request =
                                DownloadManager.Request(Uri.parse(url))
                            request.setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                STORE_APP_APK
                            )
                            //request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                            //request.setNotificationVisibility()
                            mDownloadManager.enqueue(request)
                        }
                    }
                });
        checkApp.execute()
    }

    private fun installApp(downloadId: Long) {
        var uri: Uri? = mDownloadManager.getUriForDownloadedFile(downloadId)
            ?: // includes also cancel
            return

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(
            uri, "application/vnd.android.package-archive"
        )
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivity(intent)
    }

    private fun isInstalled(): Boolean {
        try {
            packageManager.getApplicationInfo(OMNI_STORE_APP_PKG, PackageManager.GET_META_DATA)
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
    }

    fun startProgress() {
        findViewById<Button>(R.id.install_store).isEnabled = false
        findViewById<FrameLayout>(R.id.progress).visibility = View.VISIBLE
    }

    fun stopProgress() {
        findViewById<Button>(R.id.install_store).isEnabled = true
        findViewById<FrameLayout>(R.id.progress).visibility = View.GONE
    }

    private fun showNetworkError(url: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.dialog_title_network_error))
        builder.setMessage(getString(R.string.dialog_message_network_error));
        builder.setPositiveButton(android.R.string.ok, null)
        builder.create().show()
    }

    private fun getAppsBaseUrl(context: Context): String {
        var s: String? = Settings.System.getString(context.contentResolver, "store_base_url")
            ?: return "https://dl.omnirom.org/"
        return s!!
    }

    private fun getAppsRootUri(context: Context): String {
        var rootUri: String? = Settings.System.getString(context.contentResolver, "store_root_uri")
            ?: "store/"
        if (URLUtil.isNetworkUrl(rootUri)) {
            return rootUri!!;
        }
        val base: Uri = Uri.parse(getAppsBaseUrl(context))
        val u: Uri = Uri.withAppendedPath(base, rootUri)
        return u.toString()
    }
}