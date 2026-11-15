package com.devuloopers.knet.policyqualifier

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.util.Base64
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

/** Independent test UID with a disposable fixture credential and explicit fixture-root trust. */
class Probe : Instrumentation() {
    private var options = Bundle()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); options = arguments ?: Bundle(); start() }
    override fun onStart() {
        val result=Bundle()
        val started=System.nanoTime()
        try {
            val certificate=CertificateFactory.getInstance("X.509").generateCertificate(Base64.decode(options.getString("certificate"),Base64.DEFAULT).inputStream())
            val store=KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("fixture",certificate) }
            val managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            val ssl=SSLContext.getInstance("TLS").apply { init(null,managers.trustManagers,null) }
            if (options.getString("mode") == "halfclose") {
                val address="127.0.0.1:${options.getString("originPort")}"
                (ssl.socketFactory.createSocket("127.0.0.1",19292) as SSLSocket).use { socket ->
                    socket.soTimeout=10_000
                    socket.outputStream.write("CONNECT $address HTTP/1.1\r\nHost: $address\r\nProxy-Authorization: Bearer ${options.getString("device")}:${options.getString("credential")}\r\n\r\n".toByteArray())
                    socket.outputStream.flush()
                    check(readHeader(socket.inputStream).startsWith("HTTP/1.1 200"))
                    socket.outputStream.write(42); socket.outputStream.flush()
                    socket.shutdownOutput()
                    check(socket.inputStream.read() == -1)
                }
                result.putString("passed","true")
                result.putString("elapsedMillis",((System.nanoTime()-started)/1_000_000).toString())
                finish(Activity.RESULT_OK,result)
                return
            }
            val upload=options.getString("upload")=="true"
            val bytes=12_500
            val payload=if(upload) ByteArray(bytes) { 66 } else byteArrayOf()
            val address="127.0.0.1:${options.getString("originPort")}"
            (ssl.socketFactory.createSocket("127.0.0.1",19291) as SSLSocket).use { socket ->
                socket.soTimeout=15_000
                socket.outputStream.write("${if(upload) "POST" else "GET"} http://$address/?bytes=$bytes HTTP/1.1\r\nHost: $address\r\nProxy-Authorization: Bearer ${options.getString("device")}:${options.getString("credential")}\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                socket.outputStream.write(payload); socket.outputStream.flush()
                val header=readHeader(socket.inputStream)
                check(header.startsWith("HTTP/1.1 200")) { header }
                val size=Regex("(?im)^Content-Length: *([0-9]+)").find(header)!!.groupValues[1].toInt()
                val received=ByteArray(size)
                var offset=0
                while(offset<size) { val count=socket.inputStream.read(received,offset,size-offset); check(count>0); offset+=count }
                check(size==if(upload) 4 else bytes)
                check(received.all { it==65.toByte() })
            }
            result.putString("passed","true")
            result.putString("elapsedMillis",((System.nanoTime()-started)/1_000_000).toString())
        } catch(failure:Throwable) {
            result.putString("passed","false")
            result.putString("failure",failure.stackTraceToString())
        }
        finish(Activity.RESULT_OK,result)
    }
    private fun readHeader(input:InputStream):String {
        val text=StringBuilder()
        while(!text.endsWith("\r\n\r\n")) { val next=input.read(); check(next>=0); text.append(next.toChar()); check(text.length<65536) }
        return text.toString()
    }
}
class ProbeActivity : Activity()
