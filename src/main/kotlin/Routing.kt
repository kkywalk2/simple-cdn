package com.kkywalk2

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import java.io.File
import java.security.MessageDigest

val originUrl = System.getenv("CDN_ORIGIN") ?: "https://example.com"
val rootDirectory = System.getenv("CDN_ROOT_DIRECTORY") ?: "/Users/user/Downloads"

fun Application.configureRouting() {
    routing {
        get("/cdn/{filename}") {
            val filename = call.parameters["filename"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Filename required")
            val file = File("$rootDirectory/$filename")

            if (file.exists()) {
                serveCachedFile(call, file)
            } else {
                fetchFromOrigin(call, filename, file)
            }
        }
    }
}

fun generateETag(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    return "\"$hash\""
}

suspend fun fetchFromOrigin(call: ApplicationCall, filename: String, localFile: File) {
    val client = HttpClient()
    val url = "$originUrl/$filename"
    val response = client.get(url)

    if (response.status == HttpStatusCode.OK) {
        val fileBytes = response.body<ByteArray>()
        localFile.parentFile?.mkdirs()
        localFile.writeBytes(fileBytes)

        call.respondBytes(fileBytes, ContentType.defaultForFilePath(filename))
    } else {
        call.respond(response.status)
    }
}

suspend fun serveCachedFile(call: ApplicationCall, file: File) {
    val etag = generateETag(file)
    val ifNoneMatch = call.request.headers["If-None-Match"]

    if (ifNoneMatch == etag) {
        return call.respond(HttpStatusCode.NotModified)
    }

    call.response.header(HttpHeaders.ETag, etag)
    call.respondFile(file)
}
