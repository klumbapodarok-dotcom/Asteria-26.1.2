package asteria.top.client.mixin

import asteria.top.client.command.CommandManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.figuramc.figura.mixin.gui.ChatScreenAccessor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ChatScreen::class)
abstract class ChatScreenMixin : Screen(Component.empty()) {
    private val asteriaInput: EditBox
        get() = (this as ChatScreenAccessor).input

    private var suggestions = emptyList<String>()
    private var suggestionIndex = 0

    @Inject(method = ["init()V", "init(II)V"], at = [At("TAIL")], require = 0)
    private fun onInit(ci: CallbackInfo) {
        suggestions = emptyList()
        suggestionIndex = 0
    }

    @Inject(method = ["extractRenderState"], at = [At("TAIL")])
    private fun onExtractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float, ci: CallbackInfo) {
        val text = asteriaInput.value
        if (text.startsWith(".")) {
            suggestions = CommandManager.getSuggestions(text)
            if (suggestions.isNotEmpty()) {
                val font = Minecraft.getInstance().font
                var y = asteriaInput.y - 2 - suggestions.size * 10
                
                val width = suggestions.maxOf { font.width(it) } + 8
                graphics.fill(asteriaInput.x, y - 2, asteriaInput.x + width, asteriaInput.y - 2, -0x80000000.toInt())

                for ((i, suggestion) in suggestions.withIndex()) {
                    val color = if (i == suggestionIndex) 0xFFFFFFAA.toInt() else 0xFFAAAAAA.toInt()
                    graphics.text(font, suggestion, asteriaInput.x + 4, y, color)
                    y += 10
                }
            } else {
                suggestionIndex = 0
            }
        } else {
            suggestions = emptyList()
            suggestionIndex = 0
        }
    }

    @Inject(method = ["keyPressed"], at = [At("HEAD")], cancellable = true)
    private fun onKeyPressed(event: net.minecraft.client.input.KeyEvent, ci: CallbackInfoReturnable<Boolean>) {
        val keyCode = event.key()
        if (suggestions.isNotEmpty()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
                suggestionIndex = (suggestionIndex - 1 + suggestions.size) % suggestions.size
                ci.returnValue = true
                return
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                suggestionIndex = (suggestionIndex + 1) % suggestions.size
                ci.returnValue = true
                return
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
                asteriaInput.value = suggestions[suggestionIndex] + " "
                suggestions = CommandManager.getSuggestions(asteriaInput.value)
                suggestionIndex = 0
                ci.returnValue = true
                return
            }
        }
    }

    @Inject(method = ["mouseClicked"], at = [At("HEAD")], cancellable = true)
    private fun onMouseClicked(event: net.minecraft.client.input.MouseButtonEvent, active: Boolean, ci: CallbackInfoReturnable<Boolean>) {
        if (suggestions.isNotEmpty() && event.button() == 0) {
            val mc = Minecraft.getInstance()
            val mouseX = mc.mouseHandler.getScaledXPos(mc.window)
            val mouseY = mc.mouseHandler.getScaledYPos(mc.window)
            val font = mc.font
            val width = suggestions.maxOf { font.width(it) } + 8
            if (mouseX >= asteriaInput.x && mouseX <= asteriaInput.x + width) {
                var yStart = asteriaInput.y - 2 - suggestions.size * 10
                for (suggestion in suggestions) {
                    if (mouseY >= yStart && mouseY < yStart + 10) {
                        asteriaInput.value = suggestion + " "
                        suggestions = CommandManager.getSuggestions(asteriaInput.value)
                        suggestionIndex = 0
                        ci.returnValue = true
                        return
                    }
                    yStart += 10
                }
            }
        }
    }

    @Inject(method = ["handleChatInput"], at = [At("HEAD")], cancellable = true)
    private fun onHandleChatInput(message: String, addToChat: Boolean, ci: CallbackInfo) {
        if (message.startsWith(".")) {
            if (CommandManager.handle(message)) {
                Minecraft.getInstance().gui.chat.addRecentChat(message)
                ci.cancel()
            }
        }
    }
}
