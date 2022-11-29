/**
 *  John Hancock Weather Beacon
 *	Reproduction of the John Hancock building weather beacon in Boston
 *
 *  http://www.celebrateboston.com/strange/weather-beacon.htm
 * 
 *  Copyright 2022 Peter Miller
 */

definition(
	name: "John Hancock Weather Beacon",
	namespace: "hyposphere.net",
	//parent: "hyposphere.net:P's Light Controls",
	author: "Peter Miller",
	description: "Reproduction of the John Hancock building weather beacon in Boston",
	iconUrl: "",
	iconX2Url: "",
)

preferences {
	section() {
		input 'isPaused', 'bool', title: 'Pause app', defaultValue: false
	}
	section('<b>Devices</b>') {
		input 'weather', 'capability.temperatureMeasurement', title: 'Weather device', required: true, multiple: false
		input 'light', 'capability.colorControl', title: 'Light', required: true, multiple: false
		input 'beaconSwitch', 'capability.switch', title: 'Beacon power switch', required: true
	}
	section('<b>Settings</b>') {
		input 'saturationOption', 'decimal', title: 'Saturation (0..1)', required: true, defaultValue: 0.95, range: '0..1'
		input 'flash_interval', 'number', title: 'Flash rate', required: true, defaultValue: 3, range: '1..10'
	}
	section('<b>Debug</b>') {
		input 'debugMode', 'bool', title: 'Debug Mode', submitOnChange: true
		if (debugMode) {
			input 'forecastDebug', 'string', title: 'Forecast override'
			input 'cloudinessDebug', 'number', title: 'Cloudiness override'
		}
	}
}

void installed() {
	initialize()
}

void updated() {
	initialize()
}

void initialize() {
	if (isPaused) {
		unsubscribe()
		unschedule()
		light.off()
	} else {
		subscribe(beaconSwitch, 'switch', 'switchHandler')
		if (beaconSwitch.latestValue("switch") == "off") {
			unsubscribe('updateLight')
			unschedule()
			light.off()
		} else {
			subscribe(weather, 'weather', 'updateLight')
			subscribe(weather, 'condition_text', 'updateLight')
			subscribe(weather, 'cloudiness', 'updateLight')
			subscribe(weather, 'clouds', 'updateLight')
			updateLight()
		}
	}
}

void switchHandler(evt) {
	if (beaconSwitch.latestValue("switch") == "off") {
		unsubscribe(updateLight)
		unschedule()
		light.off()
	} else {
		initialize()
	}
}

void updateLight(evt) {
	Float tempOut
	List<Integer> hsvColor = null
	Float satLevel = saturationOption
	
	if (satLevel > 1) {
		satLevel = 1.0
	} else if (satLevel < 0) {
		satLevel = 0
	} 

	if (weather.getStatus() != "ACTIVE") {
		log.warn "Temp sensor is not online, do nothing"	
	} else {
		hsvColor = getColor()
		light.setColor(["hue": hsvColor[0], "saturation": hsvColor[1], "level": light.latestValue("level")])
	}
}

List<Integer> getColor() {
	List<Integer> red = [0, 100, 0] // red
	List<Integer> blue = [60, 100, 0] // blue
	String forecast
	Integer cloudiness
	
	if (debugMode) {
		forecast = forecastDebug.toLowerCase()
		cloudiness = cloudinessDebug
	} else {
		if (weather.latestValue("weather")) {
			forecast = weather.latestValue("weather").toLowerCase()
		} else if (weather.latestValue("condition_text")) {
			forecast = weather.latestValue("condition_text").toLowerCase()
		} else {
			log.error("No forecast attribute found for this device")
		}
		
		if (weather.latestValue("cloudiness")) {
			cloudiness = weather.latestValue("cloudiness") as Integer
		} else if (weather.latestValue("cloud")) {
			cloudiness = weather.latestValue("cloud") as Integer
		} else {
			log.error("No cloudiness attribute found for this device")
		}
	}
	
	//log.debug("$cloudiness; $forecast")
	
	// Weather codes per openweathermap.org/weather-conditions
	switch (forecast) {
		case ~/.*snow.*/:
		case ~/.*sleet.*/:
			log.debug("Flashing red, snow instead")
			schedule('*/' + flash_interval + ' * * ? * *', 'flash')
			return red
			break
		case ~/.*rain.*/:
		case ~/.*drizzle.*/:
		case ~/.*thunderstorm.*/:
			log.debug("Steady red, rain ahead")
			unschedule('flash')
			light.on()
			return red
			break
		default:
			if (cloudiness > 15) {
				log.debug("Flashing blue, clouds due")
				schedule('*/' + flash_interval + ' * * ? * *', 'flash')
				return blue
			} else {
				log.debug("Steady blue, clear view")
				unschedule('flash')
				light.on()
				return blue
			}
	}
}

void flash() {
	if (light.latestValue("switch") == "on") {
		light.off()
	} else {
		light.on()
	}
}
