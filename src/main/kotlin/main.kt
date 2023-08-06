/*
 *  Kotlin client/server for Fibonacci number calculation written in Java-like style.
 *
 *  Args format:
 *      --server --port 12345 --host localhost
 *      --client --port 12345 --host localhost
 *
 *  Protocol:
 *      client -> server: 4 bytes == N      (Int)
 *      server -> client: 8 bytes == fib(N) (Long)
 *
 *  Ivan Rybin, 2021.
 */
import java.io.IOException
import java.lang.Exception
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.Scanner
import java.util.concurrent.Executors
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val connectInfo = parseArgs(args)
    println("connect info: $connectInfo")
    if (connectInfo.isServer) {
        Server(connectInfo).run()
    } else {
        Client(connectInfo).run()
    }
}

class Constants {
    companion object {
        const val PORT = "--port"
        const val HOST = "--host"
        const val SERVER = "--server"
        const val CLIENT = "--client"
        const val REQ_SIZE = 4
        const val RES_SIZE = 8
    }
}

data class ConnectInfo(val host: InetAddress, val port: Int, val isServer: Boolean) {
    override fun toString(): String {
        return "$host:$port"
    }
}

fun parseArgs(args: Array<String>): ConnectInfo {
    if (args.size != 5) {
        System.err.println("invalid args count: ${args.size} != 4")
        exitProcess(1)
    }
    if (!args.contains(Constants.PORT) || !args.contains(Constants.HOST)) {
        System.err.println("args doesn't contain some of key: --port or --host")
        exitProcess(2)
    }
    var port: Int? = null
    var host: InetAddress? = null
    var isServer: Boolean? = null
    var i = 0
    while (i < 5) {
        when (args[i]) {
            Constants.HOST -> {
                host = InetAddress.getByName(args[i + 1]) // if no exception => ok, valid host
                i += 2
            }
            Constants.PORT -> {
                port = args[i + 1].toInt() // if no exception => ok, valid port
                if (port !in 0..65535) {
                    System.err.println("invalid port: ${args[i + 1]}")
                    exitProcess(3)
                }
                i += 2
            }
            Constants.SERVER -> {
                isServer = true
                i += 1
            }
            Constants.CLIENT -> {
                isServer = false
                i += 1
            }
            else -> {
                System.err.println("unknown key: ${args[i]}")
                exitProcess(4)
            }
        }
    }
    return ConnectInfo(host!!, port!!, isServer!!)
}

fun fib(n: Int): Long {
    if (n < 0) {
        return -1
    }
    if (n < 2) {
        return n.toLong()
    }
    var prev = 0L
    var cur = 1L
    for (i in 2..n) {
        val tmp = prev
        prev = cur
        cur += tmp
    }
    return cur
}

class ServerWorker(private val channel: SocketChannel) : Runnable {
    override fun run() {
        try {
            while (!channel.socket().isClosed) {
                val inBuf = ByteBuffer.allocate(Constants.REQ_SIZE)
                channel.read(inBuf) // read request from client
                inBuf.flip()

                val fibN = fib(inBuf.int)

                val outBuf = ByteBuffer.allocate(Constants.RES_SIZE)
                outBuf.putLong(fibN)
                outBuf.flip()

                channel.write(outBuf) // write response to client
            }
        } catch (e: Exception) {
            System.err.println("WORKER DEAD: ${channel.socket().remoteSocketAddress}")
        }
    }
}

class Server(private val connectInfo: ConnectInfo) : Runnable {
    private val workers = Executors.newCachedThreadPool() // simple and inefficient architecture

    override fun run() {
        try {
            val channel = ServerSocketChannel.open().bind(InetSocketAddress(connectInfo.host, connectInfo.port))
            while (!channel.socket().isClosed) {
                val clientChannel = channel.accept()
                println("new client connected: ${clientChannel.socket().remoteSocketAddress}")
                workers.submit(ServerWorker(clientChannel))
            }
        } catch (e: Exception) {
            System.err.println("SERVER DEAD: something went wrong")
            e.printStackTrace()
        } finally {
            workers.shutdownNow()
        }
    }
}

class Client(private val connectInfo: ConnectInfo) : Runnable {
    lateinit var channel: SocketChannel

    override fun run() {
        channel = SocketChannel.open(InetSocketAddress(connectInfo.host, connectInfo.port))
        val scanner = Scanner(System.`in`)
        while (!channel.socket().isClosed) {
            val line = scanner.nextLine().removeSuffix(System.lineSeparator())
            if (line.isEmpty()) {
                println("CLIENT DONE")
                exit()
                return
            } else {
                val n = line.tryParseInt()
                if (n == null) {
                    println("input isn't integer or too large: $line")
                } else {
                    val outBuf = ByteBuffer.allocate(Constants.REQ_SIZE)
                    outBuf.putInt(n)
                    outBuf.flip()
                    channel.write(outBuf) // write request to server

                    val inBuf = ByteBuffer.allocate(Constants.RES_SIZE)
                    channel.read(inBuf) // read response from server
                    inBuf.flip()

                    println("answer: ${inBuf.long}")
                }
            }
        }
    }

    private fun String.tryParseInt() = try { this.toInt() } catch (e: Exception) { null }

    private fun exit() {
        try {
            channel.close()
        } catch (e: IOException) {
            // ignore
        }
    }
}
