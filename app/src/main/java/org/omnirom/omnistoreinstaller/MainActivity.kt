package org.omnirom.omnistoreinstaller

import android.Manifest
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val TAG = "OmniStoreInstaller:MainActivity"
    private lateinit var mDownloadManager: DownloadManager
    private val mDownloadReceiver: DownloadReceiver = DownloadReceiver()
    private val mPackageReceiver: PackageReceiver = PackageReceiver()
    private val APPS_BASE_URI = "https://dl.omnirom.org/store/"
    private val STORE_URI = APPS_BASE_URI + "OmniStore.apk"
    private val OMNI_STORE_APP_PKG = "org.omnirom.omnistore"
    private var mNetworkConnected = false;
    private val mNetworkCallback = NetworkCallback()

    inner class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "NetworkCallback onAvailable")
            mNetworkConnected = true
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "NetworkCallback onLost")
            mNetworkConnected = false
        }
    }

    inner class DownloadReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive " + intent?.action)
            if (intent?.action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                stopProgress()
                val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == -1L) {
                    return
                }
                installApp(downloadId!!)
            }
        }
    }

    inner class PackageReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive " + intent?.action)
            if (intent?.action == Intent.ACTION_PACKAGE_ADDED) {
                val pkg = intent.dataString
                if (pkg == "package:" + OMNI_STORE_APP_PKG) {
                    disableMe()
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mDownloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val downloadFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        registerReceiver(mDownloadReceiver, downloadFilter)

        val packageFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        packageFilter.addDataScheme("package")
        registerReceiver(mPackageReceiver, packageFilter)

        findViewById<Button>(R.id.install_store).setOnClickListener {
            if (mNetworkConnected) {
                findViewById<TextView>(R.id.status).visibility = View.INVISIBLE
                downloadStore()
            } else {
                findViewById<TextView>(R.id.status).visibility = View.VISIBLE
            }
        }
        findViewById<TextView>(R.id.install_text).setOnClickListener {
            val name = ComponentName(OMNI_STORE_APP_PKG, OMNI_STORE_APP_PKG + ".MainActivity")
            val intent = Intent()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.component = name
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(mNetworkCallback)

        findViewById<TextView>(R.id.status).visibility = View.INVISIBLE

        if (isInstalled()) {
            findViewById<Button>(R.id.install_store).visibility = View.GONE;
            findViewById<TextView>(R.id.install_text).text = getString(R.string.store_installed)
        } else {
            findViewById<Button>(R.id.install_store).visibility = View.VISIBLE;
            findViewById<TextView>(R.id.install_text).text = getString(R.string.store_welcome)
        }
    }

    override fun onPause() {
        super.onPause()
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(mNetworkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mDownloadReceiver)
        unregisterReceiver(mPackageReceiver)
    }

    private fun disableMe() {
        packageManager.setApplicationEnabledSetting(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun downloadStore() {
        val url: String = STORE_URI
        val request: DownloadManager.Request = DownloadManager.Request(Uri.parse(url))
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "OmniStore.apk")
        //request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        //request.setNotificationVisibility()
        mDownloadManager.enqueue(request)
        startProgress()
    }

    private fun installApp(downloadId: Long) {
        var uri: Uri? = mDownloadManager.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            // includes also cancel
            return
        }

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
}