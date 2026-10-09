package com.famiglia.tripcompanion.planning

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

enum class AiAvailability { UNKNOWN, AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }
class AiFailure(message: String) : Exception(message)

interface OnDeviceAi {
    suspend fun check(): AiAvailability
    suspend fun download(progress: (String) -> Unit): AiAvailability
    suspend fun generate(prompt: String): String
}

class NanoAi : OnDeviceAi {
    private val client = NanoClient()
    private suspend fun <T> call(action: () -> T): T = try {
        runInterruptible(Dispatchers.IO) { action() }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { throw AiFailure(NanoClient.describeError(e)) }

    override suspend fun check() = call { AiAvailability.valueOf(client.check()) }
    override suspend fun download(progress: (String) -> Unit) = call { AiAvailability.valueOf(client.download(progress)) }
    override suspend fun generate(prompt: String) = call { client.generate(prompt) }
}
