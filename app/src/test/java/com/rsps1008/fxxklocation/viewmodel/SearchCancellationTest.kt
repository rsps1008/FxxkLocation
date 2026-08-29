package com.rsps1008.fxxklocation.viewmodel

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCancellationTest {
    @Test
    fun cancellingSearchDisconnectsTheActiveHttpConnection() = runBlocking {
        val connection = BlockingHttpURLConnection()
        val request = launch(Dispatchers.IO) {
            readCancellableHttpResponse(connection)
        }

        assertTrue(connection.awaitReadStarted())
        request.cancelAndJoin()

        assertTrue(connection.wasDisconnected())
    }

    private class BlockingHttpURLConnection : HttpURLConnection(URL("http://localhost")) {
        private val readStarted = CountDownLatch(1)
        private val disconnected = CountDownLatch(1)

        override fun disconnect() {
            disconnected.countDown()
        }

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getInputStream(): InputStream {
            readStarted.countDown()
            return object : InputStream() {
                override fun read(): Int {
                    if (!disconnected.await(2, TimeUnit.SECONDS)) {
                        throw IOException("Test connection was not disconnected")
                    }
                    return -1
                }
            }
        }

        fun awaitReadStarted(): Boolean = readStarted.await(2, TimeUnit.SECONDS)

        fun wasDisconnected(): Boolean = disconnected.count == 0L
    }
}
