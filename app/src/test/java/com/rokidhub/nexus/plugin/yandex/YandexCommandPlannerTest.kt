package com.rokidhub.nexus.plugin.yandex

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexCommandPlannerTest {
    private val info = JSONObject(
        """
        {
          "households": [{"id":"home","name":"Деревня"}],
          "rooms": [{"id":"bath","name":"Ванная","household_id":"home"}],
          "devices": [
            {
              "id":"light-1","name":"Лампа","type":"devices.types.light",
              "household_id":"home","room":"bath",
              "capabilities":[{"type":"devices.capabilities.on_off"}],
              "properties":[]
            },
            {
              "id":"sensor-1","name":"Датчик","type":"devices.types.sensor",
              "household_id":"home","room":"bath","capabilities":[],
              "properties":[{
                "parameters":{"instance":"temperature"},
                "state":{"instance":"temperature","value":21.5}
              }]
            }
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun plansBathroomLightAction() {
        val result = YandexCommandPlanner.plan("включи свет в ванной", info)
        assertTrue(result is PlannedCommand.SetOnOff)
        result as PlannedCommand.SetOnOff
        assertEquals(listOf("light-1"), result.deviceIds)
        assertTrue(result.enabled)
    }

    @Test
    fun readsTemperatureByHouseName() {
        val result = YandexCommandPlanner.plan("какая температура в Деревне", info)
        assertEquals(PlannedCommand.Answer("Ванная: 21.5 °C"), result)
    }
}
