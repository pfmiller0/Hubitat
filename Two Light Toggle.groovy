/**
 *  Two light Toggle
 *
 *  Copyright 2021 Peter Miller
 */
definition(
	name: "Two Light Toggle",
	namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Light Controls",
	author: "Peter Miller",
	description: "Allow a single button to switch between two lights or colors with multiple pushes.",
	category: "My Apps",
	iconUrl: "",
	iconX2Url: "",
	importUrl: "https://raw.githubusercontent.com/pfmiller0/Hubitat/main/Two%20Light%20Toggle.groovy"
)

preferences {
	section() {
		input "isPaused", "bool", title: "Pause app", defaultValue: false
	}
    section("<b>Lights</b>") {
    	input "colorToggle", "bool", title: "Color toggle mode", submitOnChange: true
		input "primaryLight", "capability.colorControl", title: "Primary Light", multiple: false
		if (colorToggle) {
			if (priTempToggle) {
				input "priTempVal", "integer", title: "Primary Temperature", range: "1500..9000", defaultValue: 4500
				input "priTempLevel", "integer", title: "Primary level", range: "0..100", defaultValue: 50
			} else {
				input "priColorHue", "integer", title: "Primary Color Hue", range: "0..100", defaultValue: 50
				input "priColorSat", "integer", title: "Primary Color Saturation", range: "0..100", defaultValue: 50
				input "priColorLevel", "integer", title: "Primary Color level", range: "0..100", defaultValue: 50
			}
			if (!secRandom) {
				if (secTempToggle) {
					input "secTempVal", "integer", title: "Secondary Temperature", range: "1500..9000", defaultValue: 4500
					input "secTempLevel", "integer", title: "Secondary level", range: "0..100", defaultValue: 50
				} else {
					input "secColorHue", "integer", title: "Secondary Color Hue", range: "0..100", defaultValue: 50
					input "secColorSat", "integer", title: "Secondary Color Saturation", range: "0..100", defaultValue: 50
					input "secColorLevel", "integer", title: "Secondary Color level", range: "0..100", defaultValue: 50
				}
			}
			input "priTempToggle", "bool", title: "Primary use color temp", submitOnChange: true
			if (!secRandom) {
				input "secTempToggle", "bool", title: "Secondary use color temp", submitOnChange: true
			}
			input "secRandom", "bool", title: "Secondary use random color", submitOnChange: true
		} else {
			input "secondaryLight", "capability.colorControl", title: "Secondary Light", multiple: false
		}

	}
	section("<b>Button</b>") {
		input "roomButton", "capability.pushableButton", title: "Button", multiple: false
		input "buttonAction", "enum", title: "Button action", options: ["pushed", "doubleTapped", "held"], required: true
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
		//subscribe(location, "systemStart", startupEvent)
		if (!colorToggle) {
			subscribe(roomButton, buttonAction, 'twoLightToggle')
		} else {
			subscribe(roomButton, buttonAction, 'twoColorToggle')
		}

		state.LastPress = 0
	}
}

void twoLightToggle(evt) {
	if (now() > (state.LastPress + 2000)) {
		toggle(primaryLight)
		state.LastPress = now()
	} else {
		if (secondaryLight.latestValue("switch") == "off") {
			secondaryLight.on()
			primaryLight.off()
		} else {
			toggle(primaryLight)
			secondaryLight.off()
		}
		
		state.LastPress = 0
	}
}

void twoColorToggle(evt) {	
	if (now() > (state.LastPress + 2000)) {
		if (primaryLight.latestValue("switch") == "off") {
			if (priTempToggle) {
				primaryLight.setColorTemperature(priTempVal, priTempLevel)
			} else {
				primaryLight.setColor([hue: priColorHue, saturation: priColorSat, level: priColorLevel])
			}
		} else {
			primaryLight.off()
		}
	} else {
		Map curColor = [:]

		if ( primaryLight.latestValue("colorMode") == "RGB" ) {
			curColor = ["hue": primaryLight.latestValue("hue"), "saturation": primaryLight.latestValue("saturation"), "level": primaryLight.latestValue("level")]
		} else {
			curColor = ["colorTemperature": primaryLight.latestValue("colorTemperature"), "level": primaryLight.latestValue("level")]
		}

		if (curColor != priColor) {
			if (priTempToggle) {
				primaryLight.setColorTemperature(priTempVal, priTempLevel)
			} else {
				primaryLight.setColor([hue: priColorHue, saturation: priColorSat, level: priColorLevel])
			}
		} else {
			Map secColor = [:]
			
			if (secRandom) {
				Long time = now()
				Integer hueRnd = (time % 100) + 1
				Integer satRnd = (time % 30) + 1
				secColor = ["hue": hueRnd, "saturation": 100 - satRnd, "level": 50]
			} else {
				secColor = secTempToggle ? [colorTemperature: secTempVal, level: secTempLevel] : [hue: secColorHue, saturation: secColorSat, level: secColorLevel]
			}

			if (secTempToggle) {
				primaryLight.setColorTemperature(secColor["colorTemperature"], secColor["level"])
			} else {
				primaryLight.setColor(secColor)
			}
		}
	}
	
	state.LastPress = now()
}

void toggle(devSwitch) {
	if (devSwitch.latestValue("switch") == "off") {
		devSwitch.on()
	} else {
		devSwitch.off()
	}
}
