/**
 *  Window Fan Control
 *	Compares two temperatures – indoor vs outdoor, to control a window fan
 * 
 *  Copyright 2020 Peter Miller
 *
 *  Originally based on the "Smart Windows" SmartApp by Eric Gideon
 *  https://github.com/egid/SmartThings/blob/master/SmartApps/smartwindows.groovy
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

String appVersion() { return "1.5" }

definition(
	name: "Window Fan Control",
	namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Utilities",
	author: "Peter Miller",
	description: "Compares two temperatures – indoor vs outdoor, to control a window fan",
	iconUrl: "",
	iconX2Url: "",
	importUrl: "https://raw.githubusercontent.com/pfmiller0/Hubitat/main/Window%20Fan%20Control.groovy"
)

preferences {
	// Remove app label before displaying label text in settings
	resetAppLabel()

	section() {
		input "isPaused", "bool", title: "Pause app", defaultValue: false
	}
	section("<b>Status</b>") {
		String status = ""
		if (state.last_msg) {
			status = status + "Last status: " + state.last_msg + "<br>"
		}
		if (tempMode == "Auto") {
			status = status + "Auto mode state: " + state.tempModeActive + "<br>"
		}
		if (status) {
			paragraph(status)
		}
	}
	section("<b>Temperature control</b>") {
		input "tempTargetCooling", "decimal", title: "Target temperature (Cooling)", width: 6, defaultValue: 69
		input "tempTargetHeating", "decimal", title: "Target temperature (Heating)", width: 6, defaultValue: 71, newLineAfter: true
		input "tempMode", "enum", title: "Mode", options: ["Cooling", "Heating", "Auto"], description: "Enter mode", defaultValue: "Auto", width: 6, newLineAfter: true
		input "tempAutoModeChangeThreshold", "decimal", title: "Auto mode change threshold", width: 6, defaultValue: 5, newLineAfter: true
		input "tempAfternoonAdjust", "decimal", title: "Afternoon temperature adjustment", width: 6, defaultValue: 3
		input "tempHeatAdjust", "decimal", title: "Heating mode temperature adjustment", width: 6, defaultValue: 3
	}

	section("<b>Devices</b>") {
		input "thermoIn", "capability.temperatureMeasurement", title: "Indoor temperature${Double.isNaN(tempAverage(thermoIn))?'':' (' + tempAverage(thermoIn) + ')'}", multiple: true, width: 6, submitOnChange: true
		input "thermoOut", "capability.temperatureMeasurement", title: "Outdoor temperature${Double.isNaN(tempAverage(thermoOut))?'':' (' + tempAverage(thermoOut) + ')'}", multiple: true, width: 6, newLineAfter: true
		input "switchFans", "capability.switch", title: "Window fans", multiple: true, width: 6
		input "windowControl", "capability.contactSensor", title: "Window sensor (disable if open)", required: false, hideWhenEmpty: true, width: 6, newLineAfter: true
		input "switchControl", "capability.switch", title: "Thermostat enabled", required: false, width: 6
	}

	section("<b>Debug</b>") {
		input "debugMode", "bool", title: "Enable debug logging", defaultValue: false, submitOnChange: true
		if (debugMode) {
			input "tempInDebug", "decimal", title: "Inside temp override", width: 6
			input "tempOutDebug", "decimal", title: "Outside temp override", width: 6
		}
	}
}

void installed() {
	initialize()
	logDebug "Installed: $settings"
}

void updated() {
	resetAppLabel()
	unsubscribe()
	initialize()
	logDebug "Updated: $settings"
}

void initialize() {
	if (isPaused == false) {
		state.timeLastChange = state.timeLastChange ?: 0
		state.tempModeActive = state.tempModeActive ?: "Cooling"
		
		subscribe(thermoOut, "temperature", 'temperatureHandler')
		subscribe(thermoIn, "temperature", 'temperatureHandler')
		
		subscribe(windowControl, "contact.closed", 'temperatureHandler')
		subscribe(switchControl, "switch.on", 'temperatureHandler')
		
		subscribe(switchControl, "switch.off", 'thermostateOffHandler')
		subscribe(windowControl, "contact.open", 'thermostateOffHandler')
		
		subscribe(switchFans, "switch", 'fanChange')
		
		temperatureHandler()
	} else {
		addAppLabel("(Paused)", "red")
	}
}

void resetAppLabel() {
	String label = app.getLabel()
	java.util.regex.Matcher m = label =~ / <.*/
	
	if (m) {
		label = label.substring(0, m.start())
		app.updateLabel(label.substring(0, m.start()))
	}
}

void addAppLabel(String labelNote, String color) {
	String labelTag = " <span style='color:" + color + "'>" + labelNote + "</span>"
	resetAppLabel()
	
	app.updateLabel(app.getLabel() + labelTag)
}

void thermostateOffHandler(evt) {
	String evtDevID = evt.device.getDeviceNetworkId()
	
	if (evtDevID == switchControl?.getDeviceNetworkId() ) {
		changeFanState("off", "Thermostat disabled. Turning off fan")
		addAppLabel("Thermostat off", "red")
	} else if (evtDevID == windowControl?.getDeviceNetworkId() ) {
		if (switchControl?.latestValue("switch") == "off") {
			logInfo "Thermostat is disabled, ignoring window"
		} else {
			changeFanState("off", "Windows has been opened. Turning off fan")
			addAppLabel("Window open", "red")
		}
	} else {
		logError "${evt.Name} doesn't match known devices"
	}
}

void temperatureHandler(evt) {
	Float tempOut
	Float tempIn
	boolean thermostateEnabled = true

	// Check for thermostat control override switches
	if (switchControl?.latestValue("switch") == "off") {
		logDebug "Thermostat disabled: switched off"
		thermostateEnabled = false
	} else if (windowControl?.latestValue("contact") == "open") {
		logDebug "Thermostat disabled: Window open"
		thermostateEnabled = false
	} else {
		logDebug "Thermostat enabled: switches all on"
		resetAppLabel()
	}

	// Check for temperature sensors active
	if (devicesAnyOnline(thermoOut) && devicesAnyOnline(thermoIn)) {
		if (!debugMode || (tempInDebug == null || tempOutDebug == null)) {
			tempOut = tempRound(tempAverage(thermoOut))
			tempIn = tempRound(tempAverage(thermoIn))
			logDebug "tempIn: $tempIn; tempOut: $tempOut"
		} else {
			tempOut = tempOut ?: tempRound(tempOutDebug)
			tempIn = tempIn ?: tempRound(tempInDebug)
			logDebug "DEBUG tempIn: $tempIn; tempOut: $tempOut"
		}
        
		// Check for auto control thresholds
		if (tempMode == "Auto") {
			if (tempIn > tempTargetHeating + tempAutoModeChangeThreshold) {
				if (state.tempModeActive != "Cooling") {
					logInfo "Auto mode: Cooling enabled"
					sendEvent(name:"mode", value: "cooling", descriptionText:"Auto: mode changed to cooling") 
					state.tempModeActive = "Cooling"
				}
			} else if (tempIn < tempTargetCooling - tempAutoModeChangeThreshold) {
				if (state.tempModeActive != "Heating") {
					logInfo "Auto mode: Heating enabled"
					sendEvent(name:"mode", value: "heating", descriptionText:"Auto: mode changed to heating") 
					state.tempModeActive = "Heating"
				}
			}
			logDebug "Mode: $state.tempModeActive (auto)"
		} else {
			state.tempModeActive = tempMode
			logDebug "Mode: $state.tempModeActive"
		}
    } else {
		// Temperature reading missing, disable thermostat
		logError "Thermostat disabled: Temperature sensors missing"
		thermostateEnabled = false
	}

	// If windowControl or switchControl are null, continue. Otherwise, check if they are off.
	if (thermostateEnabled) {
		if (state.tempModeActive == "Cooling") {
			// If in the later afternoon, subtract a couple degrees as the thermometer cools off slower than the air.
			Date timeLateAfternoonStart = percentDaylightTime("sunset", 0.28)
			Date timeLateAfternoonStop = getSunriseAndSunset(sunsetOffset: 30).sunset
			if (timeOfDayIsBetween(timeLateAfternoonStart, timeLateAfternoonStop, new Date(), location.timeZone)) {
				logDebug "Afternoon adjustment: tempOut = tempOut - $tempAfternoonAdjust"
				tempOut = tempOut - tempAfternoonAdjust
			}

			if (tempOut <= tempIn) { // Not hotter out, check inside temperature
			// Cases for temperatures
				if (tempIn < tempTargetCooling) {
					changeFanState("off", "Cooling: Too cool in, turning fan off (in: $tempIn; out: $tempOut; target: $tempTargetCooling)")
				} else if (tempIn > tempTargetCooling) {
					changeFanState("on", "Cooling: Turning fan on (in: $tempIn; out: $tempOut; target: $tempTargetCooling)")
				} else {
					logDebug "Do nothing: $tempIn is at target (in: $tempIn; out: $tempOut; target: $tempTargetCooling)"
				}
			} else {
				changeFanState("off", "Cooling: Too warm out, turning fan off (in: $tempIn; out: $tempOut; target: $tempTargetCooling)")
			}
		} else { // Heating mode
            // Add temp adjust for heating mode
			tempOut = tempOut - tempHeatAdjust
			if (tempOut >= tempIn) { // Not cooler out, check inside temperature
			// Cases for temperatures
				if (tempIn > tempTargetHeating) {
					changeFanState("off", "Heating: Too warm in, turning fan off (in: $tempIn; out: $tempOut; target: $tempTargetHeating)")
				} else if (tempIn < tempTargetHeating) {
					changeFanState("on", "Heating: Turning fan on (in: $tempIn; out: $tempOut; target: $tempTargetHeating)")
				} else {
					logDebug "Do nothing: $tempIn is at target (in: $tempIn; out: $tempOut; target: $tempTargetHeating)"
				}
			} else {
				changeFanState("off", "Heating: Too cool out, turning fan off (in: $tempIn; out: $tempOut; target: $tempTargetHeating)")
			}
		}
	}
}

void changeFanState(String newState, String msg) {
/* TODO:
On windows state change, record time of last fan change and windows state change. If windows state change is more recent that last fan change time, reset last fan change time
 OR
Pass in the name of the device that triggered the change. If the device is the window switch, don't set time stamp.
*/
//	if (secondsSinceLastChange() >= 15) {
	// Don't run if already in state
	if (!switchFans*.latestValue("switch").every { it == newState }) {
		logInfo msg
		state.last_msg = msg
		switchFans.each {
			if (newState == "on") {
   	 			it.on()
			} else {
				it.off()
			}
		}
	}
//	} else {
//		logInfo "Changed too soon (" + secondsSinceLastChange() + "), do nothing."
//	}
}

Date percentDaylightTime (String SunriseOrSunset, Float timePercent) {
    /***************************************************************************************************************
    /  Percent of total daylight hours:
    /  On June 21, daylight time is 14:18, on Dec 21 is 10:00
    /  Divide by 3.5 for ~4hrs in June, 2.8 in Dec
    ****************************************************************************************************************/

	// Get given percent of daylight time in the day (in minutes)
	Integer timeDaylight = Math.round(((getSunriseAndSunset().sunset.time - getSunriseAndSunset().sunrise.time) * timePercent) / (1000 * 60))

	if (SunriseOrSunset == "sunrise") {
		Date timeAfterSunrise = getSunriseAndSunset(sunsetOffset: timeDaylight).sunrise
		logDebug "timeDaylight: $timeDaylight min, sunrise: " + getSunriseAndSunset().sunrise + ", $timePercent (%) time after sunrise: $timeAfterSunrise"
            
		return timeAfterSunrise
    } else if (SunriseOrSunset == "sunset") {
		Date timeBeforeSunset = getSunriseAndSunset(sunsetOffset: -timeDaylight).sunset
		logDebug "timeDaylight: $timeDaylight min, sunset: " + getSunriseAndSunset().sunset + ", $timePercent (%) time before sunset: $timeBeforeSunset"
            
		return timeBeforeSunset
	}
}

void fanChange(evt) {
	String lastChangeHrs
	String lastChangeMin
	
	// Can we use the built in time since last fan state change?
	//     fan.currentState("motion").date.time
	
	lastChangeHrs = sprintf('%d', (Integer) Math.floor(minutesSinceLastChange() / 60))
	lastChangeMin = sprintf('%02d', minutesSinceLastChange() % 60)
	
	logInfo "Fan turned ${evt.value} ($lastChangeHrs:$lastChangeMin since last change)"
	state.timeLastChange = now()
}

Integer minutesSinceLastChange() {
	return Math.round((now() - state.timeLastChange) / (1000 * 60))
}

Float tempRound(Float t) {
	// Round to nearest half degree
	return Math.round(t*2)/2
}

// Return average temperature of all devices
Float tempAverage(List tempDevices) {
	Integer count = 0
    Float sum = 0
    
	tempDevices.each {
		if (it.getStatus() == "ACTIVE") {
			sum = sum + it.latestValue("temperature")
			count = count + 1
		}
	}
	return sum / count
}

boolean devicesAnyOnline(List devices) {
	return devices.any { it.getStatus() == "ACTIVE" }
}

void logTrace(msg) { log.trace "${device.label} ${msg}" }
void logDebug(msg) { if(debugMode) { log.debug "${msg}" } }
void logInfo(msg) { log.info "${msg}" }
void logWarn(msg) { log.warn "${msg}" }
