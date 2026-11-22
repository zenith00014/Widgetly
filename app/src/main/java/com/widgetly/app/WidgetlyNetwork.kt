package com.widgetly.app

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Shared HTTP configuration. Prefer the device's DNS, but fall back to Google
 * DNS-over-HTTPS when the system resolver cannot resolve a host.
 */
object WidgetlyNetwork {
    private val bootstrapClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }

    private val dnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)),
                InetAddress.getByAddress(byteArrayOf(8, 8, 4, 4))
            )
            .build()
    }

    private val resilientDns: Dns by lazy {
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                try {
                    return Dns.SYSTEM.lookup(hostname)
                } catch (systemError: UnknownHostException) {
                    try {
                        return dnsOverHttps.lookup(hostname).ifEmpty { throw systemError }
                    } catch (fallbackError: Exception) {
                        throw UnknownHostException(
                            "Unable to resolve host $hostname using system DNS or DNS-over-HTTPS"
                        ).apply {
                            initCause(fallbackError)
                            addSuppressed(systemError)
                        }
                    }
                }
            }
        }
    }

    fun newBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .dns(resilientDns)
        .retryOnConnectionFailure(true)
}

fun playbackErrorMessage(error: Throwable, fallback: String): String {
    val causes = generateSequence(error) { it.cause }.take(12).toList()
    return when {
        causes.any { it is UnknownHostException } ->
            "YouTube could not be reached. Check your internet connection or try another network."
        causes.any { it is SocketTimeoutException } ->
            "The YouTube request timed out. Check your connection and try again."
        else -> error.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }
}
