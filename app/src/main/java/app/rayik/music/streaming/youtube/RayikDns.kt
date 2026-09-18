package app.rayik.music.streaming.youtube

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import okhttp3.Dns
import okhttp3.Interceptor

/**
 * Ensures Googlevideo streaming requests use the IP family (IPv4 vs IPv6)
 * matching the signed `&ip=` parameter in YouTube's streaming URLs.
 *
 * YouTube binds Googlevideo playback tokens to the exact IP that resolved them.
 * On dual-stack networks (common on cellular/Wi-Fi), connecting to Googlevideo
 * over IPv4 with an IPv6-bound token (or vice versa) causes HTTP 403 Forbidden.
 */
object RayikDns : Dns {
  private val currentIpFamily = ThreadLocal<IpFamily?>()

  enum class IpFamily { IPv4, IPv6 }

  val interceptor = Interceptor { chain ->
    val url = chain.request().url
    val host = url.host.lowercase()
    if (host.endsWith("googlevideo.com") || host == "googlevideo.com") {
      val ipParam = url.queryParameter("ip").orEmpty()
      val family = when {
        ipParam.contains(':') -> IpFamily.IPv6
        ipParam.contains('.') -> IpFamily.IPv4
        else -> null
      }
      currentIpFamily.set(family)
    }
    try {
      chain.proceed(chain.request())
    } finally {
      currentIpFamily.remove()
    }
  }

  override fun lookup(hostname: String): List<InetAddress> {
    val addresses = Dns.SYSTEM.lookup(hostname)
    val family = currentIpFamily.get() ?: return addresses
    return when (family) {
      IpFamily.IPv6 -> {
        val v6 = addresses.filterIsInstance<Inet6Address>()
        val v4 = addresses.filterIsInstance<Inet4Address>()
        if (v6.isNotEmpty()) v6 + v4 else addresses
      }
      IpFamily.IPv4 -> {
        val v4 = addresses.filterIsInstance<Inet4Address>()
        val v6 = addresses.filterIsInstance<Inet6Address>()
        if (v4.isNotEmpty()) v4 + v6 else addresses
      }
    }
  }
}
