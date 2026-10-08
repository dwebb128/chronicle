package local.oss.chronicle.data.sources.plex

import android.os.Build
import local.oss.chronicle.core.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/**
 * Injects plex required headers
 *
 * If accessing a media server instead of just plex.tv, inject the server url
 */
class PlexInterceptor(
    private val plexPrefsRepo: PlexPrefsRepo,
    private val plexConfig: PlexConfig,
    private val isLoginService: Boolean,
) : Interceptor {
    init {
        if (isLoginService) {
            Timber.i("Inited login intercepter")
        } else {
            Timber.i("Inited media intercepter")
        }
    }

    companion object {
        const val PLATFORM = "Android"
        const val PRODUCT = APP_NAME
        const val DEVICE = "$APP_NAME $PLATFORM"

        /**
         * Client profile that tells Plex what audio formats this app can directly play.
         * Based on Plex API documentation for Profile Augmentations.
         *
         * Declares direct play support for common audiobook formats (AAC, MP3, FLAC, etc.).
         * The Generic profile already includes transcode targets, so we only add the
         * direct play profile to avoid conflicts.
         *
         * Per Plex API spec, musicProfile requires: type, container, audioCodec
         * videoCodec and subtitleCodec use wildcard (*) since not applicable to audio
         *
         * PUBLIC: Used by PlexProgressReporter for scoped interceptor header alignment.
         */
        const val CLIENT_PROFILE_EXTRA =
            "add-direct-play-profile(type=musicProfile&container=mp4,m4a,m4b,mp3,flac,ogg,opus&audioCodec=aac,mp3,flac,vorbis,opus&videoCodec=*&subtitleCodec=*)"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val interceptedUrl = chain.request().url.toString().replace(PLACEHOLDER_URL, plexConfig.url)

        val requestBuilder =
            chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("X-Plex-Platform", PLATFORM)
                .header("X-Plex-Provides", "player")
                .header("X-Plex-Client-Identifier", plexPrefsRepo.uuid)
                .header("X-Plex-Version", BuildConfig.VERSION_NAME)
                .header("X-Plex-Product", PRODUCT)
                .header("X-Plex-Platform-Version", Build.VERSION.RELEASE)
                .header("X-Plex-Session-Identifier", plexConfig.sessionIdentifier)
                .header("X-Plex-Client-Name", APP_NAME)
                .header("X-Plex-Device", DEVICE)
                .header("X-Plex-Device-Name", Build.MODEL)
                .header("X-Plex-Client-Profile-Extra", CLIENT_PROFILE_EXTRA)
                .url(interceptedUrl)

        // Check if URL already contains X-Plex-Token as query parameter
        // If so, don't add it as a header to avoid auth conflicts
        val urlHasToken = chain.request().url.queryParameter("X-Plex-Token") != null

        if (!urlHasToken) {
            val userToken = plexPrefsRepo.user?.authToken
            val serverToken = plexPrefsRepo.server?.accessToken
            val accountToken = plexPrefsRepo.accountAuthToken

            val serviceToken = if (isLoginService) userToken else serverToken
            val authToken = if (serviceToken.isNullOrEmpty()) accountToken else serviceToken

            if (authToken.isNotEmpty()) {
                // usesCleartextTraffic/network_security_config allow plain HTTP broadly, because
                // Android's Network Security Configuration can't restrict cleartext to "private
                // network IPs" declaratively (its <domain> matching has no CIDR/IP-range syntax,
                // only exact hostnames/literal IPs -- see network_security_config.xml). So this is
                // the actual gate: never send the Plex auth token in the clear to anything other
                // than a private/link-local/loopback address or a Plex-issued *.plex.direct host.
                if (schemeOf(interceptedUrl).equals("http", ignoreCase = true) &&
                    !isLocalCleartextHost(hostOf(interceptedUrl))
                ) {
                    Timber.w(
                        "Refusing to send X-Plex-Token over cleartext to non-local host: ${hostOf(interceptedUrl)}",
                    )
                } else {
                    requestBuilder.header("X-Plex-Token", authToken)
                }
            }
        }

        return try {
            chain.proceed(requestBuilder.build())
        } catch (e: java.io.IOException) {
            Timber.w(e, "Network error in PlexInterceptor for ${interceptedUrl}")
            throw e
        }
    }
}

private fun schemeOf(url: String): String? =
    url.substringBefore("://", missingDelimiterValue = "").takeIf { it.isNotEmpty() }

private fun hostOf(url: String): String? {
    val afterScheme = url.substringAfter("://", url)
    val afterUserInfo = afterScheme.substringAfter("@")
    val hostAndPort = afterUserInfo.substringBefore("/")
    val host = hostAndPort.substringBefore(":")
    return host.takeIf { it.isNotEmpty() }
}

/**
 * True if [host] is safe to send the Plex auth token to over cleartext HTTP: a loopback address,
 * an RFC1918 private address, a link-local address, or one of Plex's own *.plex.direct LAN
 * hostnames (e.g. 192-168-1-5.<hash>.plex.direct).
 */
internal fun isLocalCleartextHost(host: String?): Boolean {
    if (host.isNullOrEmpty()) return false
    val normalized = host.trim('[', ']')

    if (normalized.equals("localhost", ignoreCase = true) ||
        normalized == "127.0.0.1" ||
        normalized == "::1"
    ) {
        return true
    }
    if (normalized.equals("plex.direct", ignoreCase = true) ||
        normalized.endsWith(".plex.direct", ignoreCase = true)
    ) {
        return true
    }

    val octets = normalized.split(".")
    if (octets.size != 4) return false
    val parts = octets.map { it.toIntOrNull() }
    if (parts.any { it == null || it !in 0..255 }) return false
    val a = parts[0]!!
    val b = parts[1]!!
    return when {
        a == 10 -> true
        a == 172 && b in 16..31 -> true
        a == 192 && b == 168 -> true
        a == 169 && b == 254 -> true
        else -> false
    }
}
