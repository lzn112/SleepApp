package com.sleepagent.prototype.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One ordered writer; a barrier prevents exporting before the final stop event is saved. */
class InterventionEventWriter(
    scope: CoroutineScope,
    private val persist: suspend (SoundInterventionEventEntity) -> Unit,
    private val onFailure: (Throwable) -> Unit
) {
    private sealed interface Command {
        data class Event(val value: SoundInterventionEventEntity) : Command
        data class Flush(val done: CompletableDeferred<Unit>) : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val worker = scope.launch {
        var failure: Throwable? = null
        for (command in commands) {
            when (command) {
                is Command.Event -> try {
                    persist(command.value)
                } catch (error: Exception) {
                    failure = error
                    onFailure(error)
                }
                is Command.Flush -> {
                    val error = failure
                    if (error == null) command.done.complete(Unit)
                    else command.done.completeExceptionally(error)
                }
            }
        }
    }

    fun append(event: SoundInterventionEventEntity) {
        check(commands.trySend(Command.Event(event)).isSuccess) { "Stimulation log writer is closed" }
    }

    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        commands.send(Command.Flush(done))
        done.await()
    }

    suspend fun close() {
        commands.close()
        worker.join()
    }
}
