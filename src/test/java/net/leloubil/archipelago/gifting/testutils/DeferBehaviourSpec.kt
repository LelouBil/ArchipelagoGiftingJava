package net.leloubil.archipelago.gifting.testutils

import io.kotest.core.spec.style.scopes.AbstractContainerScope
import kotlin.reflect.KProperty

interface TestDeferred<T> {
    operator fun getValue(receiver: Nothing?, property: KProperty<*>): T
}

// lazy that is initialized before running each test in a scope
private data class DeferredImpl<T>(private val getter: suspend () -> T) : TestDeferred<T> {
    private var data: Lazy<T> = lazy { throw IllegalStateException("unreachable") }

    suspend fun run() {
        data = lazyOf(getter())
    }

    override operator fun getValue(receiver: Nothing?, property: KProperty<*>): T {
        if (!data.isInitialized()) throw IllegalStateException("Deferred value used outside of test or defer block")
        return data.value
    }

}

context(scope: AbstractContainerScope)
fun <T> defer(block: suspend () -> T): TestDeferred<T> = DeferredImpl(block).also { d ->
    scope.beforeEach {
        d.run()
    }
}
