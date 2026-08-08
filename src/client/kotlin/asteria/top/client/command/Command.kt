package asteria.top.client.command

abstract class Command(val name: String, val aliases: List<String> = emptyList()) {
    abstract fun execute(args: Array<String>)
    abstract fun suggest(args: Array<String>): List<String>
}
