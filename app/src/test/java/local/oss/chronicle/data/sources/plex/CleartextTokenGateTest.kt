package local.oss.chronicle.data.sources.plex

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test

/** The Plex auth token must never travel over plain HTTP to a host outside the local network. */
class CleartextTokenGateTest {
    private fun allowed(url: String) = canSendTokenTo(url.toHttpUrl())

    @Test
    fun `https is always allowed`() {
        assertThat(allowed("https://plex.tv/api/v2/user"), `is`(true))
        assertThat(allowed("https://203.0.113.7:32400/library"), `is`(true))
    }

    @Test
    fun `cleartext to local network addresses is allowed`() {
        listOf(
            "http://192.168.1.5:32400/library",
            "http://10.0.0.2:32400/",
            "http://172.16.0.1/",
            "http://172.31.255.255/",
            "http://169.254.10.1/",
            "http://127.0.0.1:32400/",
            "http://localhost:32400/",
            "http://[::1]:32400/",
            "http://192-168-1-5.0123456789abcdef.plex.direct:32400/",
        ).forEach { assertThat(it, allowed(it), `is`(true)) }
    }

    @Test
    fun `cleartext to public hosts is refused`() {
        listOf(
            "http://203.0.113.7:32400/library",
            "http://172.32.0.1/",
            "http://example.com/",
            "http://plex.direct.example.com/",
            "http://notplex.direct/",
        ).forEach { assertThat(it, allowed(it), `is`(false)) }
    }

    @Test
    fun `a local-looking address elsewhere in the URL does not fool the gate`() {
        assertThat(allowed("http://evil.example/x?a=@10.0.0.1"), `is`(false))
        assertThat(allowed("http://evil.example/@192.168.1.1/"), `is`(false))
        assertThat(allowed("http://192.168.1.1@evil.example/"), `is`(false))
    }

    @Test
    fun `an unparseable URL gets no token`() {
        assertThat(canSendTokenTo(null), `is`(false))
    }
}
