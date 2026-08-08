package asteria.top.client.gui

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import org.figuramc.figura.avatar.AvatarManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipInputStream

/** Owns the three isolated Figura avatars used by the cosmetics carousel. */
object CosmeticsAvatarPreview {
    private data class Definition(
        val resource: String,
        val cacheName: String,
        val uuid: UUID,
        val entityId: Int,
        var avatarRoot: Path? = null,
        var entity: RemotePlayer? = null,
        var requested: Boolean = false,
    )

    // Order matches CosmeticsMenu.Category: backpacks, headwear, wings.
    private val definitions = arrayOf(
        arrayOf(
            Definition("/assets/asteria/cosmetics/backpack.zip", "backpack", UUID.fromString("a57e7100-0000-4000-8000-000000000001"), -1_570_001),
            Definition("/assets/asteria/cosmetics/backpack.zip", "backpack", UUID.fromString("a57e7100-0000-4000-8000-000000000011"), -1_570_011),
        ),
        arrayOf(
            Definition("/assets/asteria/cosmetics/ushanka.zip", "ushanka", UUID.fromString("a57e7100-0000-4000-8000-000000000002"), -1_570_002),
            Definition("/assets/asteria/cosmetics/ushanka.zip", "ushanka", UUID.fromString("a57e7100-0000-4000-8000-000000000012"), -1_570_012),
        ),
        arrayOf(
            Definition("/assets/asteria/cosmetics/bat_wings.zip", "bat_wings", UUID.fromString("a57e7100-0000-4000-8000-000000000003"), -1_570_003),
            Definition("/assets/asteria/cosmetics/bat_wings.zip", "bat_wings", UUID.fromString("a57e7100-0000-4000-8000-000000000013"), -1_570_013),
        ),
    )

    private var currentLevel: ClientLevel? = null

    fun entityFor(categoryIndex: Int, variant: Int = 0): RemotePlayer? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        ensureLevel(level)
        val category = definitions.getOrNull(categoryIndex) ?: return null
        val definition = category.getOrNull(variant) ?: category.firstOrNull() ?: return null
        val entity = definition.entity ?: return null
        // Do not flash a vanilla skin while Figura is still parsing the bbmodel/scripts.
        return if (AvatarManager.getLoadedAvatar(definition.uuid)?.loaded == true) entity else null
    }

    private fun ensureLevel(level: ClientLevel) {
        if (currentLevel === level && definitions.all { category -> category.all { it.requested } }) return
        currentLevel = level
        definitions.forEachIndexed { categoryIndex, category ->
            category.forEachIndexed { variantIndex, definition ->
                val player = RemotePlayer(level, GameProfile(definition.uuid, "Asteria Cosmetic ${categoryIndex + 1}-${variantIndex + 1}"))
                player.id = definition.entityId
                player.yBodyRot = 0.0f
                player.yHeadRot = 0.0f
                player.yRot = 0.0f
                definition.entity = player
                AvatarManager.ENTITY_CACHE.put(definition.entityId, player)

                val root = definition.avatarRoot ?: extractAvatar(definition)?.also { definition.avatarRoot = it }
                if (root != null) {
                    definition.requested = true
                    AvatarManager.loadPreviewAvatar(definition.uuid, root)
                }
            }
        }
    }

    private fun extractAvatar(definition: Definition): Path? {
        val stream = CosmeticsAvatarPreview::class.java.getResourceAsStream(definition.resource) ?: return null
        val cacheRoot = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("asteria")
            .resolve("cosmetics-cache")
            .resolve(definition.cacheName)
            .normalize()
        Files.createDirectories(cacheRoot)

        ZipInputStream(stream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val relative = entry.name.replace('\\', '/')
                if (relative.startsWith("__MACOSX/") || relative.contains("/._")) continue
                val target = cacheRoot.resolve(relative).normalize()
                if (!target.startsWith(cacheRoot)) continue
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    target.parent?.let(Files::createDirectories)
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
                }
                zip.closeEntry()
            }
        }

        Files.walk(cacheRoot).use { paths ->
            return paths
                .filter { Files.isRegularFile(it) && it.fileName.toString() == "avatar.json" }
                .map { it.parent }
                .findFirst()
                .orElse(null)
        }
    }
}
