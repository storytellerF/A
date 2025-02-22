package jvm_based

actual abstract class UsingContextTest actual constructor() {
    actual fun onActivity(block: () -> Unit) {
        block()
    }
}