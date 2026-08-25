package com.rokidhub.nexus.plugin.yandex

import org.json.JSONObject

sealed interface PlannedCommand {
    data class Answer(val text: String) : PlannedCommand
    data class SetOnOff(val deviceIds: List<String>, val deviceNames: List<String>, val enabled: Boolean) : PlannedCommand
}

object YandexCommandPlanner {
    fun plan(command: String, userInfo: JSONObject): PlannedCommand {
        val query = normalize(command)
        val homes = items(userInfo, "households").associate {
            it.optString("id") to it.optString("name")
        }
        val rooms = items(userInfo, "rooms").associate {
            it.optString("id") to Pair(it.optString("name"), it.optString("household_id"))
        }
        val selectedHomes = homes.filterValues { matches(query, it) }.keys
        val selectedRooms = rooms.filterValues { matches(query, it.first) }.keys

        if (listOf("температур", "градус", "холод", "жар").any(query::contains)) {
            val readings = mutableListOf<String>()
            for (device in items(userInfo, "devices")) {
                if (selectedHomes.isNotEmpty() && device.optString("household_id") !in selectedHomes) continue
                if (selectedRooms.isNotEmpty() && device.optString("room") !in selectedRooms) continue
                for (property in items(device, "properties")) {
                    val parameters = property.optJSONObject("parameters") ?: continue
                    val state = property.optJSONObject("state") ?: continue
                    if (parameters.optString("instance") != "temperature" || !state.has("value")) continue
                    val value = state.optDouble("value", Double.NaN)
                    if (value.isNaN()) continue
                    val roomName = rooms[device.optString("room")]?.first.orEmpty()
                    val homeName = homes[device.optString("household_id")].orEmpty()
                    val place = roomName.ifBlank { homeName }.ifBlank { device.optString("name", "Датчик") }
                    val formatted = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
                    readings += "$place: $formatted °C"
                }
            }
            return PlannedCommand.Answer(
                readings.takeIf(List<String>::isNotEmpty)?.joinToString("; ")
                    ?: "Не нашёл датчик температуры. Уточни дом или комнату.",
            )
        }

        val enabled = when {
            listOf("выключ", "погаси").any(query::contains) -> false
            listOf("включ", "зажги").any(query::contains) -> true
            else -> return PlannedCommand.Answer("Скажи «включи» или «выключи».")
        }
        var candidates = items(userInfo, "devices").filter { device ->
            device.optString("type") in SUPPORTED_DEVICE_TYPES && items(device, "capabilities").any {
                it.optString("type") == "devices.capabilities.on_off"
            }
        }
        if (selectedHomes.isNotEmpty()) {
            candidates = candidates.filter { it.optString("household_id") in selectedHomes }
        }
        if (selectedRooms.isNotEmpty()) {
            candidates = candidates.filter { it.optString("room") in selectedRooms }
        }
        val wantsLight = listOf("свет", "ламп", "люстр").any(query::contains)
        if (wantsLight) {
            candidates = candidates.filter { it.optString("type") == "devices.types.light" }
        } else {
            val named = candidates.filter { matches(query, it.optString("name")) }
            if (named.isNotEmpty()) candidates = named
        }
        if (candidates.isEmpty()) {
            return PlannedCommand.Answer("Не нашёл подходящее устройство. Назови дом, комнату или точное имя.")
        }
        if (wantsLight && selectedRooms.isEmpty() && candidates.size > 1) {
            return PlannedCommand.Answer("Уточни комнату: свет найден в нескольких местах.")
        }
        return PlannedCommand.SetOnOff(
            deviceIds = candidates.map { it.getString("id") },
            deviceNames = candidates.map { it.optString("name", "устройство") },
            enabled = enabled,
        )
    }

    private fun items(value: JSONObject, key: String): List<JSONObject> {
        val array = value.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
        }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace('ё', 'е')
        .replace(Regex("[^а-яa-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun matches(query: String, name: String): Boolean {
        val expected = normalize(name).split(' ').map(::stem).filter { it.length >= 3 }
        val actual = query.split(' ').map(::stem).filter { it.length >= 3 }.toSet()
        return expected.isNotEmpty() && expected.all(actual::contains)
    }

    private fun stem(value: String): String {
        for (ending in ENDINGS) {
            if (value.endsWith(ending) && value.length - ending.length >= 3) return value.dropLast(ending.length)
        }
        return value
    }

    private val SUPPORTED_DEVICE_TYPES = setOf(
        "devices.types.light",
        "devices.types.switch",
        "devices.types.socket",
    )
    private val ENDINGS = listOf("ами", "ями", "ой", "ей", "ая", "ое", "ый", "ий", "ах", "ях", "а", "я", "е", "и", "у", "ы", "о")
}
