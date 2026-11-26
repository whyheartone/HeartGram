package com.exteragram.messenger.icons

import org.telegram.messenger.R
import java.lang.reflect.Modifier
import java.util.LinkedHashMap

/** Minimal IconManager bridge for PillIconPickerSheet's built-in icon grid. */
object IconManager {

    @Volatile
    private var cachedIcons: LinkedHashMap<String, Int>? = null

    fun initialize(force: Boolean) {
        if (cachedIcons == null || force) {
            cachedIcons = loadSystemIcons()
        }
    }

    fun getSystemIcons(): Map<String, Int> {
        val current = cachedIcons
        if (current != null) {
            return current
        }
        val loaded = loadSystemIcons()
        cachedIcons = loaded
        return loaded
    }

    private fun loadSystemIcons(): LinkedHashMap<String, Int> {
        val result = LinkedHashMap<String, Int>()
        val fields = R.drawable::class.java.fields
        for (field in fields) {
            if (!Modifier.isStatic(field.modifiers) || field.type != Int::class.javaPrimitiveType) {
                continue
            }
            val name = field.name
            if (name.endsWith("_solar") || name.endsWith("_remix")) {
                continue
            }
            if (name.startsWith("msg_") || name.startsWith("filled_") || name.startsWith("outline_")
                || name.startsWith("menu_") || name.startsWith("weather_") || name.startsWith("pillstack_")
                || name.startsWith("baseline_") || name.startsWith("ic_")) {
                try {
                    result[name] = field.getInt(null)
                } catch (_: Throwable) {
                }
            }
        }
        return result
    }
}
