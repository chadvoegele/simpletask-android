package nl.mpcjanssen.simpletask.util

import java.util.concurrent.Executors

private val asyncExecutor = Executors.newCachedThreadPool()

fun executeAsync(action: () -> Unit) {
    asyncExecutor.submit(Runnable { action() })
}
