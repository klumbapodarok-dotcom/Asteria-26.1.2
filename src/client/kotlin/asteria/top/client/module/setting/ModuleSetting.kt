package asteria.top.client.module.setting

abstract class ModuleSetting<T>(
    val name: String,
    val defaultValue: T,
) {
    private var visiblePredicate: () -> Boolean = { true }

    var value: T = defaultValue
        protected set

    abstract fun displayValue(): String
    abstract fun adjust(direction: Int)

    fun visibleWhen(predicate: () -> Boolean): ModuleSetting<T> {
        visiblePredicate = predicate
        return this
    }

    fun isVisible(): Boolean {
        return visiblePredicate()
    }

    open fun setRaw(value: T) {
        this.value = value
    }

    open fun reset() {
        this.value = defaultValue
    }
}
