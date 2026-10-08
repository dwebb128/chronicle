package local.oss.chronicle.data.sources.plex

import android.os.Build
import local.oss.chronicle.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
                // network_security_config.xml can't restrict cleartext to private addresses (its
                // <domain> matching has no IP-range syntax), so this is the real gate: never send
                // the auth token over plain HTTP to anything but a local-network or *.plex.direct
                // host.
                if (canSendTokenTo(interceptedUrl.toHttpUrlOrNull())) {
                    requestBuilder.header("X-Plex-Token", authToken)
                } else {
                    Timber.w("Refusing to send X-Plex-Token over cleartext to a non-local host")
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

/**
 * Whether the Plex auth token may be attached to a request for [url]: always over HTTPS, and over
 * cleartext HTTP only to a host on the local network (see [isLocalCleartextHost]). A URL OkHttp
 * can't parse gets no token.
 */
internal fun canSendTokenTo(url: HttpUrl?): Boolean {
    if (url == null) return false
    return url.isHttps || isLocalCleartextHost(url.host)
}

/**
 * True if [host] is safe to send the Plex auth token to over cleartext HTTP: a loopback address,
 * an RFC1918 private address, a link-local address, or one of Plex's own *.plex.direct LAN
 * hostnames (e.g. 192-168-1-5.<hash>.plex.direct). [host] is as [HttpUrl.host] reports it, so
 * IPv6 literals arrive without brackets.
 */
internal fun isLocalCleartextHost(host: String?): Boolean {
    if (host.isNullOrEmpty()) return false

    if (host.equals("localhost", ignoreCase = true) || host == "::1") {
        return true
    }
    if (host.equals("plex.direct", ignoreCase = true) ||
        host.endsWith(".plex.direct", ignoreCase = true)
    ) {
        return true
    }

    val octets = host.split(".")
    if (octets.size != 4) return false
    val parts = octets.map { it.toIntOrNull() }
    if (parts.any { it == null || it !in 0..255 }) return false
    val a = parts[0]!!
    val b = parts[1]!!
    return when {
        a == 127 -> true
        a == 10 -> true
        a == 172 && b in 16..31 -> true
        a == 192 && b == 168 -> true
        a == 169 && b == 254 -> true
        else -> false
    }
}
