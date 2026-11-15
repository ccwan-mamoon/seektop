package io.github.seektop.desktop

import io.github.seektop.ai.AiSettings
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiSettingsBridgeTest {
    @Test fun `saving first provider includes defaults required by JavaScript broker`() {
        val query = aiSettingsQuery(AiSettings(), mapOf("deepseek" to "inert-fixture-key"))
        assertEquals("deepseek", query.getValue("selectedProvider").jsonPrimitive.content)
        assertEquals("", query.getValue("defaultPrompt").jsonPrimitive.content)
        assertTrue(query.getValue("boardPrompts").jsonObject.isEmpty())
        assertEquals("deepseek", query.getValue("providers").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("inert-fixture-key", query.getValue("newKeys").jsonObject.getValue("deepseek").jsonPrimitive.content)
    }
}
