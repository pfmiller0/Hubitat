/**
 *  SmartSense Temp/Humidity Sensor
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
	definition(name: "SmartSense Temp/Humidity Sensor", namespace: "smartthings", author: "SmartThings") {
		capability "Configuration"
		capability "Battery"
		capability "Refresh"
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"
		//capability "Health Check"
		capability "Sensor"
		
		attribute "dewPoint", "number"
		
		fingerprint profileId: "0104", inClusters: "0001,0003,0020,0402,0B05,FC45", outClusters: "0019,0003", manufacturer: "CentraLite", model: "3310-S", deviceJoinName: "Multipurpose Sensor"
		fingerprint profileId: "0104", inClusters: "0001,0003,0020,0402,0B05,FC45", outClusters: "0019,0003", manufacturer: "CentraLite", model: "3310-G", deviceJoinName: "Centralite Multipurpose Sensor" //Centralite Temp & Humidity Sensor
		fingerprint profileId: "0104", inClusters: "0001,0003,0020,0402,0B05,FC45", outClusters: "0019,0003", manufacturer: "CentraLite", model: "3310", deviceJoinName: "Multipurpose Sensor"
		fingerprint profileId: "0104", deviceId: "0302", inClusters: "0000,0001,0003,0402", manufacturer: "Heiman", model: "b467083cfc864f5e826459e5d8ea6079", deviceJoinName: "Orvibo Multipurpose Sensor" //Orvibo Temperature & Humidity Sensor
		fingerprint profileId: "0104", deviceId: "0302", inClusters: "0000,0001,0003,0402", manufacturer: "HEIMAN", model: "888a434f3cfc47f29ec4a3a03e9fc442", deviceJoinName: "Orvibo Multipurpose Sensor" //Orvibo Temperature & Humidity Sensor
		fingerprint profileId: "0104",  inClusters: "0000, 0001, 0003, 0009, 0402", manufacturer: "HEIMAN", model: "HT-EM", deviceJoinName: "HEIMAN Multipurpose Sensor" //HEIMAN Temperature & Humidity Sensor
		fingerprint profileId: "0104",  inClusters: "0000, 0001, 0003, 0402, 0B05", manufacturer: "HEIMAN", model: "HT-EF-3.0", deviceJoinName: "HEIMAN Multipurpose Sensor" //HEIMAN Temperature & Humidity Sensor
		fingerprint profileId: "0104", deviceId: "0302", inClusters: "0000,0001,0003,0020,0402,0405", outClusters: "0003,000A,0019", manufacturer: "frient A/S", model :"HMSZB-110", deviceJoinName: "frient Multipurpose Sensor" // frient Humidity Sensor
		fingerprint profileId: "0104", inClusters: "0000,0001,0402,0405", outClusters: "0019", manufacturer: "_TZ2000_a476raq2", model :"TS0201", deviceJoinName: "Tuneway Tuya Temp & Humidity Sensor" // 0x0019: OTA_CLUSTER
	}

	preferences {
		input "tempOffset", "decimal", title: "Temperature offset", description: "Select how many degrees to adjust the temperature.", range: "-100..100", displayDuringSetup: false
		input "humidityOffset", "decimal", title: "Humidity offset", description: "Enter a percentage to adjust the humidity.", range: "*..*", displayDuringSetup: false
		input "infoLogging", "bool", title: "Enable info message logging", description: ""
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
		if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
			if (descMap.attrInt == 0x0021) {
				map = getBatteryPercentageResult(Integer.parseInt(descMap.value,16))
			} else {
				map = getBatteryResult(Integer.parseInt(descMap.value, 16))
			}
		} else if (descMap?.clusterInt == zigbee.TEMPERATURE_MEASUREMENT_CLUSTER && descMap.commandInt == 0x07) {
			if (descMap.data[0] == "00") {
				logDebug "TEMP REPORTING CONFIG RESPONSE: $descMap"
				sendEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
			} else {
				logWarn "TEMP REPORTING CONFIG FAILED- error code: ${descMap.data[0]}"
			}
		} else if (descMap?.clusterInt == 0x0405 && descMap.commandInt == 0x07) { // Relative humidity cluster ID
			if (descMap.data[0] == "00") {
				logDebug "HUMIDITY REPORTING CONFIG RESPONSE: $descMap"
				sendEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
			} else {
				logWarn "HUMIDITY REPORTING CONFIG FAILED- error code: ${descMap.data[0]}"
			}
		} else if (descMap?.clusterInt == 0x0405 ) { // Relative humidity cluster ID
			map = parseHumidity(descMap.value)
		} else {
			logWarn "No condition matched: clusterInt: ${descMap?.clusterInt}; commandInt: ${descMap.commandInt}"
		}
	} else if (map.name == "temperature") {
		if (tempOffset) {
			map.value = new BigDecimal((map.value as float) + (tempOffset as float)).setScale(1, BigDecimal.ROUND_HALF_UP)
		}
		map.descriptionText = temperatureScale == 'C' ? "${device.displayName} was ${map.value}°C" : "${device.displayName} temperature is ${map.value}°F"
		map.translatable = true
	}

	// Update dew point
	if (map.name == "temperature") {
		dpEvent = createEvent(getDewPoint(map.value, device.currentValue("humidity")))
	} else if (map.name == "humidity") {
		dpEvent = createEvent(getDewPoint(device.currentValue("temperature"), map.value))
	}
	
	logDebug "Parse returned $map"
	if (map) {
		if (dpEvent) {
			logDebug "dpEvent returned"
			return [createEvent(map), dpEvent]
		} else {
			logDebug "No dpEvent"
			return createEvent(map)
		}
	} else {
		logDebug "Returning empty map"
		return [:]
	}
}

// Calculate humidity (from Konke ZigBee Temperture Humidity Sensor driver
private parseHumidity(valueHex) {
	float humidity = Integer.parseInt(valueHex,16)/100
	//logDebug ("Raw reported humidity = ${humidity}, date = ${valueHex}")
	humidity = humidityOffset ? (humidity + (humidityOffset as float)) : humidity
	return [
		name: 'humidity',
		value: (int) humidity.round(),
		unit: "%",
		descriptionText: "${device.displayName} humidity is ${humidity.round()}%"
	]
}

def getBatteryPercentageResult(rawValue) {
	logDebug "Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
	def result = [:]

	if (0 <= rawValue && rawValue <= 200) {
		result.name = 'battery'
		result.translatable = true
		result.value = Math.round(rawValue / 2)
		result.descriptionText = "${device.displayName} battery is ${result.value}%"
	}

	return result
}

private Map getBatteryResult(rawValue) {
	logDebug 'Battery'
	def linkText = getLinkText(device)

	def result = [:]

	def volts = rawValue / 10
	if (!(rawValue == 0 || rawValue == 255)) {
		def minVolts = isFrientSensor() ? 2.3 : 2.1
		def maxVolts = 3.0
		def pct = (volts - minVolts) / (maxVolts - minVolts)
		def roundedPct = Math.round(pct * 100)
		if (roundedPct <= 0)
			roundedPct = 1
		result.value = Math.min(100, roundedPct)
		result.descriptionText = "${device.displayName} battery is ${result.value}%"
		result.name = 'battery'

	}

	return result
}

private Map getDewPoint(float temp, float humidity) {
	def result = [:]
	def dp = 0.0
	
	if (location.temperatureScale == "F") {
		dp = temp - (9/25) * (100 - humidity)
	} else {
		dp = temp - (100 - humidity) / 5
	}
	
	result.value = dp
	result.descriptionText = "${device.displayName} dew point is ${result.value}%"
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

	def manufacturer = device.getDataValue("manufacturer")

	if (manufacturer == "Heiman"|| manufacturer == "HEIMAN") {
		return zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021, [destEndpoint: 0x01])+
			zigbee.readAttribute(0x0402, 0x0000, [destEndpoint: 0x01])+
			zigbee.readAttribute(0x0405, 0x0000, [destEndpoint: 0x02])
	} else if (isFrientSensor()) {
		return zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020)+
			zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000)+
			zigbee.readAttribute(zigbee.RELATIVE_HUMIDITY_CLUSTER, 0x0000)
	} else if (manufacturer == "CentraLite") {
		return zigbee.readAttribute(0xFC45, 0x0000, ["mfgCode": 0x104E]) +   // New firmware
			zigbee.readAttribute(0xFC45, 0x0000, ["mfgCode": 0xC2DF]) +   // Original firmware
			zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000) +
			zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020)
	} else {
		return zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000) +
			zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020) +
			zigbee.readAttribute(0x0405, 0x0000) // pfm - humidity
	}
}

def configure() {
	// Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
	// enrolls with default periodic reporting until newer 5 min interval is confirmed
	//sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

	logDebug "Configuring Reporting and Bindings."
	
	// temperature minReportTime 30 seconds, maxReportTime 5 min. Reporting interval if no activity
	// battery minReport 30 seconds, maxReportTime 6 hrs by default
	def manufacturer = device.getDataValue("manufacturer")
	if (manufacturer == "Heiman"|| manufacturer == "HEIMAN") {
		return refresh() +
			zigbee.temperatureConfig(30, 300) +
			zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021, DataType.UINT8, 30, 21600, 0x10) +
			zigbee.configureReporting(0x0405, 0x0000, DataType.UINT16, 30, 3600, 100, [destEndpoint: 0x02])
	} else if (isFrientSensor()) {
		return refresh() + 
			zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_CLUSTER, 0x0000, DataType.UINT16, 60, 600, 1*100) +
			zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000, DataType.INT16, 60, 600, 0xA) +
			zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020, DataType.UINT8, 30, 21600, 0x1)
	} else if (manufacturer == "CentraLite") {
		return refresh() +
			zigbee.configureReporting(0xFC45, 0x0000, DataType.UINT16, 30, 3600, 100, ["mfgCode": 0x104E]) +   // New firmware
			zigbee.configureReporting(0xFC45, 0x0000, DataType.UINT16, 30, 3600, 100, ["mfgCode": 0xC2DF]) +   // Original firmware
			zigbee.batteryConfig() +
			zigbee.temperatureConfig(30, 300)
	} else {
		return refresh() +
			zigbee.configureReporting(0x0405, 0x0000, DataType.UINT16, 30, 300, 100) +
			zigbee.batteryConfig() +
			zigbee.temperatureConfig(30, 300)
	}
}

private Boolean isFrientSensor() {
	device.getDataValue("manufacturer") == "frient A/S"
}

void logTrace(msg) { log.trace "${device.label} ${msg}" }
void logDebug(msg) { if(debugLogging) { log.debug "${msg}" } }
void logInfo(msg) { if (infoLogging) {log.info "${msg}" } }
void logWarn(msg) { log.warn "${msg}" }
