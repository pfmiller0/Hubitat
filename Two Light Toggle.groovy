/**
 *  Two light Toggle
 *
 *  Copyright 2021 Peter Miller
 */
definition(
    name: "Two Light Toggle",
    namespace: "hyposphere.net",
    author: "Peter Miller",
    description: "Allow a single button to switch between two lights or colors with multiple pushes.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
	section() {
		input "isPaused", "bool", title: "Pause app", defaultValue: false
	}
    section("Lights") {
    	input "colorToggle", "bool", title: "Color toggle mode", submitOnChange: true
		input "primaryLight", "capability.colorControl", title: "Primary Light", multiple: false
		if (!colorToggle) {
			input "secondaryLight", "capability.colorControl", title: "Secondary Light", multiple: false
		} else {
			if (!priTempToggle) {
				input "priColor", "string", title: "Primary Color <i style=\"font-size:80%\">[hue:100, saturation:100, level:100]</i>", defaultValue: '[hue:50, saturation:50, level:50]'
			} else {
				input "priTemp", "string", title: "Primary Temperature <i style=\"font-size:80%\">[colorTemperature:9000, level:100]</i>", defaultValue: '[colorTemperature:4500, level:50]'
			}
			if (!secRandom) {
				if (!secTempToggle) {
					input "secColor", "string", title: "Secondary Color <i style=\"font-size:80%\">[hue:100, saturation:100, level:100]</i>", defaultValue: '[hue:50, saturation:50, level:50]'
				} else {
					input "secTemp", "string", title: "Secondary Temperature <i style=\"font-size:80%\">[colorTemperature:9000, level:100]</i>", defaultValue: '[colorTemperature:4500, level:50]'
				}
			}
			input "priTempToggle", "bool", title: "Primary use color temp", submitOnChange: true
			if (!secRandom) {
				input "secTempToggle", "bool", title: "Secondary use color temp", submitOnChange: true
			}
			input "secRandom", "bool", title: "Secondary use random color", submitOnChange: true
		}
	}
	    section("Button") {
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
			subscribe(roomButton, buttonAction, twoLightToggle)
		} else {
			subscribe(roomButton, buttonAction, twoColorToggle)
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
		toggle(primaryLight)
	} else {
		Map priColor = priTempToggle ? evaluate(priTemp) : evaluate(priColor)
		Map curColor = [:]

		if ( primaryLight.latestValue("colorMode") == "RGB" ) {
			curColor = ["hue": primaryLight.latestValue("hue"), "saturation": primaryLight.latestValue("saturation"), "level": primaryLight.latestValue("level")]
		} else {
			curColor = ["colorTemperature": primaryLight.latestValue("colorTemperature"), "level": primaryLight.latestValue("level")]
		}

		if (curColor != priColor) {
			if (priTempToggle) {
				primaryLight.setLevel(priColor["level"]);
				primaryLight.setColorTemperature(priColor["colorTemperature"])
			} else {
				primaryLight.setColor(priColor)
			}
		} else {
			Map secColor = [:]
			
			if (secRandom) {
				Long time = now()
				Integer hueRnd = (time % 100) + 1
				Integer satRnd = (time % 30) + 1
				secColor = ["hue": hueRnd, "saturation": 100 - satRnd, "level": 50]
			} else {
				secColor = secTempToggle ? evaluate(secTemp) : evaluate(secColor)
			}

			if (secTempToggle) {
				primaryLight.setLevel(secColor["level"]);
				primaryLight.setColorTemperature(secColor["colorTemperature"])
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
