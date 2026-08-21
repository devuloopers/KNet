package com.devuloopers.knet.testingserver.http2

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Publishes the ephemeral lab certificate over the ordinary HTTP listener for manual strict-TLS testing. */
@RestController
@RequestMapping("/lab/v1/http2")
class Http2TlsLabDiscoveryController(
    private val server: Http2TlsLabServer,
) {
    /** Returns only the public certificate; the generated private key never leaves the TLS listener. */
    @GetMapping("/certificate.pem", produces = [PEM_MEDIA_TYPE])
    fun certificate(): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(PEM_MEDIA_TYPE))
        .body(server.certificatePem)

    private companion object {
        const val PEM_MEDIA_TYPE = "application/x-pem-file"
    }
}
