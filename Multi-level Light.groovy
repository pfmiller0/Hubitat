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
    section("Devices") {
    	input "light", "capability.colorTemperature", title: "Light", multiple: false
		input "button", "capability.pushableButton", title: "Button", multiple: false
		input "handleToggle", "bool", title: "Handle single press (toggle)"
	}
	section("Light levels") {
		input "lightRaisePrimaryTemp", "number", title: "Raised primary temperature", range: "1500..9000", defaultValue: 3500
		input "lightRaisePrimaryLevel", "number", title: "Raised primary level", range: "0..100", defaultValue: 50
		input "lightRaiseSecTemp", "number", title: "Raised secondary temperature", range: "1500..9000", defaultValue: 3500
		input "lightRaiseSecLevel", "number", title: "Raised secondary level", range: "0..100", defaultValue: 75
		input "lightLowerPrimaryTemp", "number", title: "Lowered primary temperature", range: "1500..9000", defaultValue: 2500
		input "lightLowerPrimaryLevel", "number", title: "Lowered primary level", range: "0..100", defaultValue: 10
		input "lightLowerSecTemp", "number", title: "Lowered secondary temperature", range: "1500..9000", defaultValue: 2500
		input "lightLowerSecLevel", "number", title: "Lowered secondary level", range: "0..100", defaultValue: 25
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
	if (light.latestValue("switch") == "off") {
		light.on()
	} else {
		light.off()
	}
}

void raiseLights(evt) {
	if (light.latestValue("switch") == "off" ||
		light.latestValue("level") <= lightRaisePrimaryLevel - 3 ||
		light.latestValue("level") >= lightRaisePrimaryLevel + 3) {
		
		light.setColorTemperature(lightRaisePrimaryTemp, lightRaisePrimaryLevel)
		light.on()
	} else {
		light.setColorTemperature(lightRaiseSecTemp, lightRaiseSecLevel)
		light.on()
	}
}

void lowerLights(evt) {
	if (light.latestValue("switch") == "off" ||
		light.latestValue("level") <= lightLowerPrimaryLevel - 3 ||
		light.latestValue("level") >= lightLowerPrimaryLevel + 3) {
		
		light.setColorTemperature(lightLowerPrimaryTemp, lightLowerPrimaryLevel)
		light.on()
	} else {
		light.setColorTemperature(lightLowerSecTemp, lightLowerSecLevel)
		light.on()
	}
}
