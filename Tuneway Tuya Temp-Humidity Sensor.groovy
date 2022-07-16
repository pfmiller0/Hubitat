/**
 *  Tuneway Tuya Temp & Humidity Sensor 
 *  
 *  Originally derived from SmartThings SmartSense Temp/Humidity Sensor
 *
 *  Copyright 2014 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
import hubitat.zigbee.zcl.DataType

metadata {
	definition(name: "Tuneway Tuya Temp/Humidity Sensor",
		   namespace: "hyposphere.net",
		   author: "Peter Miller",
		   importUrl: "https://raw.githubusercontent.com/pfmiller0/Hubitat/main/Tuneway%20Tuya%20Temp-Humidity%20Sensor.groovy") {
		
		capability "Configuration"
		capability "Battery"
		capability "Refresh"
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"
		capability "Sensor"
		
		attribute "dewpoint", "number"
		attribute "battery", "number"
		attribute "batteryVoltage", "number"
		
		fingerprint profileId: "0104", inClusters: "0000,0001,0402,0405", outClusters: "0019", manufacturer: "_TZ2000_a476raq2", model :"TS0201", deviceJoinName: "Tuneway Tuya Temp & Humidity Sensor" // 0x0019: OTA_CLUSTER
	}

	preferences {
		input "tempOffset", "decimal", title: "Temperature offset", description: "Select how many degrees to adjust the temperature.", range: "-100..100"
		input "humidityOffset", "decimal", title: "Humidity offset", description: "Enter a percentage to adjust the humidity.", range: "*..*"
		input "infoLogging", "bool", title: "Enable info message logging", description: ""
		input "warnLogging", "bool", title: "Enable warn message logging", description: ""
		input "debugLogging", "bool", title: "Enable debug message logging", description: ""
	}
}

def parse(String description) {
	logDebug "description: $description"

	// getEvent will handle temperature and humidity
	Map map = zigbee.getEvent(description)
	
	// Event for dewpoint if temp or humidity are updated
	Map dpEvent = [:]
	
	if (!map) {
		Map descMap = zigbee.parseDescriptionAsMap(description)
		if (descMap?.clusterInt == 0x0405 ) { // Relative humidity cluster ID
			map = parseHumidity(descMap.value)
		} else {
			logWarn "No condition matched: clusterInt: ${descMap?.clusterInt}; commandInt: ${descMap.commandInt}"
		}
	} else if (map.name == "temperature") {
		if (tempOffset) {
			map.value = new BigDecimal((map.value as Float) + (tempOffset as Float)).setScale(2, BigDecimal.ROUND_HALF_UP)
		}
		map.descriptionText = temperatureScale == 'C' ? "${device.displayName} was ${map.value}°C" : "${device.displayName} temperature is ${map.value}°F"
		map.translatable = true
	} else if (map.name == "battery") {
		map.value = Math.round(map.value)
		map.unit = "%"
		map.descriptionText = "${device.displayName} battery level is ${map.value}%"
		map.translatable = true
	} else if (map.name == "batteryVoltage") {
		map.value = Math.round(map.value)
		map.unit = "V"
		map.descriptionText = "${device.displayName} battery voltage is ${map.value}V"
		map.translatable = true
	}

	// Update dew point
	if (map.name == "humidity") { // For Tuya device temp always updates right before humidity, so no  need to update dewpoint for both
		dpEvent = createEvent(getDewPoint(device.currentValue("temperature"), map.value))
	}
	
	logDebug "Parse returned $map"
	if (map) {
		if (dpEvent) {
			return [createEvent(map), dpEvent]
		} else {
			return createEvent(map)
		}
	} else {
		logDebug "Returning empty map"
		return [:]
	}
}

// Calculate humidity (from Konke ZigBee Temperture Humidity Sensor driver)
private parseHumidity(String valueHex) {
	Float humidity = Integer.parseInt(valueHex,16)/100
	//logDebug ("Raw reported humidity = ${humidity}, date = ${valueHex}")
	humidity = humidityOffset ? (humidity + (humidityOffset as Float)) : humidity
	return [
		name: 'humidity',
		value: (Integer) humidity.round(),
		unit: "%",
		descriptionText: "${device.displayName} humidity is ${humidity.round()}%"
	]
}

private Map getDewPoint(Float temp, Float humidity) {
	def result = [:]
	def dp = 0.0
	def unit = ""
	
	if (location.temperatureScale == "F") {
		dp = temp - (9/25) * (100 - humidity)
		unit = "F"
	} else {
		dp = temp - (100 - humidity) / 5
		unit = "C"
	}
	
	result.value = Math.round(dp)
	result.unit = unit
	result.descriptionText = "${device.displayName} dew point is ${result.value} ${result.unit}"
	result.name = 'dewpoint'

	return result
}

////////
// PING is used by Device-Watch in attempt to reach the Device
//
def ping() {
	return zigbee.readAttribute(0x0001, 0x0020) // Read the Battery Level
}

def refresh() {
	logDebug "refresh temperature, humidity, and battery"

	return zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000) +
		zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020) +
		zigbee.readAttribute(0x0405, 0x0000) // pfm - humidity
}

def configure() {
	// Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
	// enrolls with default periodic reporting until newer 5 min interval is confirmed
	//sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

	logDebug "Configuring Reporting and Bindings."
	
	// temperature minReportTime 30 seconds, maxReportTime 5 min. Reporting interval if no activity
	// battery minReport 30 seconds, maxReportTime 6 hrs by default
	return refresh() +
		zigbee.configureReporting(0x0405, 0x0000, DataType.UINT16, 30, 300, 100) +
		zigbee.batteryConfig() +
		zigbee.temperatureConfig(30, 300)
}

void logDebug(msg) { if (debugLogging) { log.debug "${msg}" } }
void logInfo(msg) { if (infoLogging) { log.info "${msg}" } }
void logWarn(msg) { if (warnLogging) { log.warn "${msg}" } }
