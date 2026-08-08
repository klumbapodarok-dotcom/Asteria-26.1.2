package asteria.top

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object Asteria : ModInitializer {
    private val logger = LoggerFactory.getLogger("asteria")

	override fun onInitialize() {
		logger.info("Hello Fabric world!")
	}
}
