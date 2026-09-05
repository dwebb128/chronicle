package local.oss.chronicle.data.sources.plex

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import local.oss.chronicle.data.model.PlexLibrary
import local.oss.chronicle.data.model.ServerModel
import local.oss.chronicle.data.sources.plex.model.Connection
import local.oss.chronicle.data.sources.plex.model.MediaType
import local.oss.chronicle.data.sources.plex.model.PlexUser
import local.oss.chronicle.features.account.CredentialManager
import java.util.*
import javax.inject.Inject
import kotlin.collections.HashSet

/** A interface for Plex exclusive preferences */
interface PlexPrefsRepo {
    /**
     * The active auth token for the active account/profile. A 20ish character string. Defaults to
     * empty string "" if user is not signed in
     */
    var accountAuthToken: String

    // TODO: exposes the most privileged token we currently have access to (via new class/func-
    //       wouldn't be appropriate to use this class for it)

    /** The active user profile */
    var user: PlexUser?

    /** The active plex library */
    var library: PlexLibrary?

    /** Reference to the connected server */
    var server: ServerModel?

    /**
     * Temporary id used by oAuth to identify the client. Provided by the server. Only valid for
     * a few minutes so no strong need to clear it after login
     */
    var oAuthTempId: Long

    /** Timestamp when server list was last refreshed from plex.tv (epoch millis) */
    var serverListLastRefreshed: Long

    /** Unique user id */
    val uuid: String

    /** Clear all preferences which are handled by PrefsRepo */
    fun clear()
}

/**
 * An implementation of [PlexPrefsRepo] wrapping [SharedPreferences].
 *
 * The auth tokens ([accountAuthToken] and [server]'s access token) are secrets, so they're kept
 * out of the plain-text [prefs] file entirely and stored via [credentialManager]'s
 * EncryptedSharedPreferences (Android Keystore-backed) instead -- the same encrypted store
 * [local.oss.chronicle.features.account.Account] credentials already use. Everything else here
 * (library/server metadata, connections, uuid, etc.) isn't a secret and stays in [prefs] as
 * before. A one-time [migrateLegacyToken] pulls forward -- and wipes -- any token a previous
 * version of the app already wrote to the plain-text file.
 */
class SharedPreferencesPlexPrefsRepo
    @Inject
    constructor(
        private val prefs: SharedPreferences,
        private val moshi: Moshi,
        private val credentialManager: CredentialManager,
    ) : PlexPrefsRepo {
        private companion object {
            const val PREFS_AUTH_TOKEN_KEY = "auth_token"
            const val PREFS_LIBRARY_NAME_KEY = "library_name"
            const val PREFS_LIBRARY_ID_KEY = "library_id"
            const val PREFS_SERVER_NAME_KEY = "server_name"
            const val PREFS_SERVER_ACCESS_TOKEN = "server_token"
            const val PREFS_SERVER_IS_OWNED = "server_owned"
            const val PREFS_SERVER_ID_KEY = "server_id"
            const val PREFS_REMOTE_SERVER_CONNECTIONS_KEY = "remote_server_connections"
            const val PREFS_USER = "user"
            const val PREFS_LOCAL_SERVER_CONNECTIONS_KEY = "local_server_connections"
            const val PREFS_UUID_KEY = "uuid"
            const val PREFS_TEMP_ID = "id"
            const val PREFS_SERVER_LIST_LAST_REFRESHED = "server_list_last_refreshed"
            const val NO_TEMP_ID_FOUND = -1L

            // Keys into CredentialManager's encrypted store -- not SharedPreferences keys.
            const val CRED_ACCOUNT_AUTH_TOKEN = "plex_prefs_account_auth_token"
            const val CRED_SERVER_ACCESS_TOKEN = "plex_prefs_server_access_token"
        }

        override val uuid: String
            @SuppressLint("ApplySharedPref")
            get() {
                var tempUUID = getString(PREFS_UUID_KEY, "")
                if (tempUUID.isEmpty()) {
                    val generatedUUID = UUID.randomUUID().toString()
                    prefs.edit().putString(PREFS_UUID_KEY, generatedUUID).commit()
                    tempUUID = generatedUUID
                }
                return tempUUID
            }

        override var accountAuthToken: String
            get() =
                credentialManager.getCredentials(CRED_ACCOUNT_AUTH_TOKEN)
                    ?: migrateLegacyToken(PREFS_AUTH_TOKEN_KEY, CRED_ACCOUNT_AUTH_TOKEN)

            set(value) {
                credentialManager.storeCredentials(CRED_ACCOUNT_AUTH_TOKEN, value)
                clearLegacyPlaintextKey(PREFS_AUTH_TOKEN_KEY)
            }

        override var user: PlexUser?
            get() {
                val userString = prefs.getString(PREFS_USER, "")
                if (userString.isNullOrEmpty()) {
                    return null
                }
                return moshi.adapter<PlexUser>(PlexUser::class.java).fromJson(userString)
            }

            @SuppressLint("ApplySharedPref")
            set(value) {
                if (value == null) {
                    prefs.edit().remove(PREFS_USER).commit()
                    return
                }
                val userString = moshi.adapter<PlexUser>(PlexUser::class.java).toJson(value)
                prefs.edit().putString(PREFS_USER, userString).commit()
            }

        override var library: PlexLibrary?
            get() {
                val name = getString(PREFS_LIBRARY_NAME_KEY)
                val id = getString(PREFS_LIBRARY_ID_KEY)
                if (name.isEmpty() || id.isEmpty()) {
                    return null
                }
                return PlexLibrary(name, MediaType.ARTIST, id)
            }

            @SuppressLint("ApplySharedPref")
            set(value) {
                if (value == null) {
                    prefs.edit()
                        .remove(PREFS_LIBRARY_ID_KEY)
                        .remove(PREFS_LIBRARY_NAME_KEY).commit()
                    return
                }
                prefs.edit()
                    .putString(PREFS_LIBRARY_NAME_KEY, value.name)
                    .putString(PREFS_LIBRARY_ID_KEY, value.id).commit()
            }

        override var server: ServerModel?
            get() {
                val name = getString(PREFS_SERVER_NAME_KEY)
                val id = getString(PREFS_SERVER_ID_KEY)
                val token: String =
                    credentialManager.getCredentials(CRED_SERVER_ACCESS_TOKEN)
                        ?: migrateLegacyToken(PREFS_SERVER_ACCESS_TOKEN, CRED_SERVER_ACCESS_TOKEN)
                val owned: Boolean = prefs.getBoolean(PREFS_SERVER_IS_OWNED, true)

                val connections = getServerConnections()

                if (name.isEmpty() || token.isEmpty() || connections.isEmpty()) {
                    return null
                }

                return ServerModel(name, connections, id, token, owned)
            }

            @SuppressLint("ApplySharedPref")
            set(value) {
                if (value == null) {
                    prefs.edit()
                        .remove(PREFS_SERVER_ID_KEY)
                        .remove(PREFS_SERVER_ACCESS_TOKEN)
                        .remove(PREFS_SERVER_IS_OWNED)
                        .remove(PREFS_LOCAL_SERVER_CONNECTIONS_KEY)
                        .remove(PREFS_REMOTE_SERVER_CONNECTIONS_KEY)
                        .remove(PREFS_SERVER_NAME_KEY).commit()
                    credentialManager.deleteCredentials(CRED_SERVER_ACCESS_TOKEN)
                    return
                }
                prefs.edit()
                    .putString(PREFS_SERVER_NAME_KEY, value.name)
                    .putString(PREFS_SERVER_ID_KEY, value.serverId)
                    .remove(PREFS_SERVER_ACCESS_TOKEN)
                    .putBoolean(PREFS_SERVER_IS_OWNED, value.owned).commit()
                credentialManager.storeCredentials(CRED_SERVER_ACCESS_TOKEN, value.accessToken)
                putConnections(value.connections)
            }

        private fun getServerConnections(): List<Connection> {
            val localServers =
                getStringSet(PREFS_LOCAL_SERVER_CONNECTIONS_KEY)
                    .map { Connection(uri = it, local = true) }
            val remoteServers =
                getStringSet(PREFS_REMOTE_SERVER_CONNECTIONS_KEY)
                    .map { Connection(uri = it, local = false) }
            return localServers + remoteServers
        }

        // TODO: ensure this is only usable for a certain amount of time
        override var oAuthTempId: Long
            get() = prefs.getLong(PREFS_TEMP_ID, NO_TEMP_ID_FOUND)

            @SuppressLint("ApplySharedPref")
            set(value) {
                prefs.edit().putLong(PREFS_TEMP_ID, value).commit()
            }

        override var serverListLastRefreshed: Long
            get() = prefs.getLong(PREFS_SERVER_LIST_LAST_REFRESHED, 0L)

            @SuppressLint("ApplySharedPref")
            set(value) {
                prefs.edit().putLong(PREFS_SERVER_LIST_LAST_REFRESHED, value).commit()
            }

        override fun clear() {
            server = null
            library = null
            user = null
            accountAuthToken = ""
        }

        @SuppressLint("ApplySharedPref")
        private fun putConnections(connections: List<Connection>) {
            prefs.edit()
                .putStringSet(
                    PREFS_LOCAL_SERVER_CONNECTIONS_KEY,
                    connections.filter { it.local }.map { it.uri }.toSet(),
                )
                .putStringSet(
                    PREFS_REMOTE_SERVER_CONNECTIONS_KEY,
                    connections.filter { !it.local }.map { it.uri }.toSet(),
                ).commit()
        }

        private fun getStringSet(key: String): MutableSet<String> {
            return prefs.getStringSet(key, HashSet<String>()) ?: HashSet()
        }

        /**
         * Retrieve a string stored in shared preferences
         *
         * @param key the key of the item stored in preferences
         * @param defaultValue (optional) the value to return if the desired string cannot be found.
         *                     Defaults to the empty string
         *
         * @return the stored preference value corresponding to the [key] passed in. If there is no
         * corresponding value, return the default value provided
         *
         */
        private fun getString(
            key: String,
            defaultValue: String = "",
        ): String {
            return prefs.getString(key, defaultValue) ?: defaultValue
        }

        /**
         * One-time upgrade path: an older version of the app may have written [legacyPrefsKey]'s
         * token to the plain-text [prefs] file. If so, pull it into the encrypted store under
         * [credentialKey], wipe the plain-text copy, and return it; otherwise return "".
         */
        @SuppressLint("ApplySharedPref")
        private fun migrateLegacyToken(
            legacyPrefsKey: String,
            credentialKey: String,
        ): String {
            val legacyValue = prefs.getString(legacyPrefsKey, null)
            if (legacyValue.isNullOrEmpty()) return ""
            credentialManager.storeCredentials(credentialKey, legacyValue)
            prefs.edit().remove(legacyPrefsKey).commit()
            return legacyValue
        }

        @SuppressLint("ApplySharedPref")
        private fun clearLegacyPlaintextKey(legacyPrefsKey: String) {
            if (prefs.contains(legacyPrefsKey)) {
                prefs.edit().remove(legacyPrefsKey).commit()
            }
        }
    }
