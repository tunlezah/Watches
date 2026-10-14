package io.github.tunlezah.watches.search

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.koin.core.module.Module
import org.koin.dsl.module

/** Needs ACCESS_NETWORK_STATE. Reports offline only when there is definitely no internet-capable network. */
class AndroidConnectivity(context: Context) : Connectivity {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

fun androidSearchModule(context: Context): Module = module {
    single<Connectivity> { AndroidConnectivity(context.applicationContext) }
}
