/**
 *  Multi-level Light
 *
 *  Copyright 2021 Peter Miller
 */
 
definition(
    name: "Multi-level Light",
    namespace: "hyposphere.net",
    parent: "hyposphere.net:P's Light Controls",
	author: "Peter Miller",
	description: "Allow buttons to toggle between multiple light color/level settings",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/pfmiller0/Hubitat/main/Multi-level%20Light.groovy"
)

preferences {
	section() {
		input "isPaused", "bool", title: "Pause app", defaultValue: false
	}
	section("<b>Devices</b>") {
    	input "light", "capability.colorTemperature", title: "Light", multiple: false
		input "button", "capability.pushableButton", title: "Button", multiple: false
	}
	section("<b>Preferences</b>") {
		input "handleToggle", "bool", title: "Handle single press (toggle on/off)"
		if (handleToggle) {
			input "resetLightWithOn", "bool", title: "Reset light when light is turned on"
		} else {
			input "turnOnToRaisedWithHold", "bool", title: "Go to raised level when turned on with long press"
		}
	}
	section("<b>Light levels</b>") {
		input "lightRaisePrimaryTemp", "number", title: "Raised primary temperature", width: 4, range: "1500..9000", defaultValue: 3500
		input "lightRaisePrimaryLevel", "number", title: "Raised primary level", width: 4, range: "0..100", defaultValue: 50
		input "lightRaiseSecTemp", "number", title: "Raised secondary temperature", width: 4, range: "1500..9000", defaultValue: 3500
		input "lightRaiseSecLevel", "number", title: "Raised secondary level", width: 4, range: "0..100", defaultValue: 75
		input "lightLowerPrimaryTemp", "number", title: "Lowered primary temperature", width: 4, range: "1500..9000", defaultValue: 2500
		input "lightLowerPrimaryLevel", "number", title: "Lowered primary level", width: 4, range: "0..100", defaultValue: 10
		input "lightLowerSecTemp", "number", title: "Lowered secondary temperature", width: 4, range: "1500..9000", defaultValue: 2500
		input "lightLowerSecLevel", "number", title: "Lowered secondary level", width: 4, range: "0..100", defaultValue: 25
	}
	section("<b>Motion Controls</b>") {
		input "motionActive", "capability.switch", title: "Motion control active flag", multiple: false
	}
}

def installed() {
	if (logDebug) log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	if (logDebug) log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	if (isPaused == false) {
		if (handleToggle == true) {
			subscribe(button, "pushed", 'toggleLights')
		}
		subscribe(button, "doubleTapped", 'lowerLights')
		subscribe(button, "held", 'raiseLights')
	}
}

void toggleLights(evt) {
	if (light.latestValue("switch") == "off" || motionActive?.latestValue("switch") == "on") {
		if (resetLightWithOn) {
			light.setColorTemperature(lightRaisePrimaryTemp, lightRaisePrimaryLevel)
		} else {
			light.on()
		}
	} else {
		light.off()
	}
}

void raiseLights(evt) {
	if (light.latestValue("switch") == "off" || motionActive?.latestValue("switch") == "on") {
		if ( (handleToggle && resetLightWithOn) || (!handleToggle && turnOnToRaisedWithHold) ) {
			light.setColorTemperature(lightRaiseSecTemp, lightRaiseSecLevel)
		} else {
			light.setColorTemperature(lightRaisePrimaryTemp, lightRaisePrimaryLevel)
		}
	} else if (light.latestValue("level") <= lightRaisePrimaryLevel - 3 ||
	           light.latestValue("level") >= lightRaisePrimaryLevel + 3) {
		light.setColorTemperature(lightRaisePrimaryTemp, lightRaisePrimaryLevel)
	} else {
		light.setColorTemperature(lightRaiseSecTemp, lightRaiseSecLevel)
	}
	if ( motionActive ) motionActive.off()
}

void lowerLights(evt) {
	if (light.latestValue("switch") == "off" ||
		motionActive?.latestValue("switch") == "on" ||
		light.latestValue("level") <= lightLowerPrimaryLevel - 3 ||
		light.latestValue("level") >= lightLowerPrimaryLevel + 3)
	{
		light.setColorTemperature(lightLowerPrimaryTemp, lightLowerPrimaryLevel)
	} else {
		light.setColorTemperature(lightLowerSecTemp, lightLowerSecLevel)
	}
	if ( motionActive ) motionActive.off()
