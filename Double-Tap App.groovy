/**
 *  Double-Tap App
 *
 *  Copyright 2022 Peter Miller
 */
definition(
    name: "Double-Tap App",
    namespace: "hyposphere.net",
    author: "Peter Miller",
    description: "Virtual double tap",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/pfmiller0/Hubitat/main/Double-Tap%20App.groovy"
)

preferences {
	section() {
		input "isPaused", "bool", title: "Pause app", defaultValue: false
	}
	section("Button") {
		input "roomButton", "capability.pushableButton", title: "Button", required: true, multiple: false
		input "buttonAction", "enum", title: "Button action", options: ["pushed", "doubleTapped", "held"], required: true, defaultValue: "pushed"
		input "secondPressTime", "number", title: "Double press time limit (milliseconds)", required: true, defaultValue: 500
	}
	section("Switches") {
		input "singleSwitch", "capability.switch", title: "Single press switch", required: true, multiple: false
		input "doubleSwitch", "capability.switch", title: "Double press switch", required: true, multiple: false
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
		subscribe(roomButton, buttonAction, buttonHandler)

		state.LastPress = 0
	}
}

void buttonHandler(evt) {
	if (now() > (state.LastPress + secondPressTime)) {
		runInMillis(secondPressTime + 1, 'singleHandler', [:])
		state.LastPress = now()
	} else {
		toggle(doubleSwitch)
		state.LastPress = 0
		log.debug "Double press"
	}
}

void singleHandler() {
	if (state.LastPress != 0) {
		toggle(singleSwitch)
		log.debug "Single press"
	}
}

void toggle(devSwitch) {
	if (devSwitch.latestValue("switch") == "off") {
		devSwitch.on()
	} else {
		devSwitch.off()
	}
}
