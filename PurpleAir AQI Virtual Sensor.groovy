/**
 *  PurpleAir AQI Virtual Sensor
 *
 *  PurpleAir sensor map: https://map.purpleair.com/
 *  API documentation: https://api.purpleair.com/ 
 */
metadata {
	definition (
		name: "PurpleAir AQI Virtual Sensor",
		namespace: "hyposphere.net",
		author: "Peter Miller",
		importUrl: "https://raw.githubusercontent.com/pfmiller0/Hubitat/main/PurpleAir%20AQI%20Virtual%20Sensor.groovy"
	)
	{
		capability "Sensor"
        	capability "Polling"

		attribute "aqi", "number"
		attribute "category", "string" // Description of current air quality
		attribute "sites", "string" // List of sensor sites used
		command "refresh"
	}

	preferences {
		input name: "X_API_Key", type: "text", title: "PurpleAir API key", required: true, description: "Contact contact@purpleair.com to request an API key"
		input name: "update_interval", type: "enum", title: "Update interval", required: true, description: "Minutes between updates", options: ["15", "30", "60", "180"], default: "60"
		input name: "aqi_interval", type: "enum", title: "AQI interval", required: true, description: "Reading averaged over what time", options: ["pm2.5", "pm2.5_10minute", "pm2.5_30minute", "pm2.5_60minute", "pm2.5_6hour", "pm2.5_24hour", "pm2.5_1week"], default: "pm2.5_60minute"
		input name: "device_search", type: "bool", title: "Search for devices", required: true, description: "If false specify device index to use"

		if ( device_search ) {
			input name: "search_coords", type: "text", title: "Search coordinates [lat, long]", required: true, description: "Coordinates at center of sensor search box"
			input name: "search_range", type: "text", title: "Search range [lat, long]", required: true, description: "Size of sensor search box (+/- center of search box coordinates)"
		} else {
			input name: "sensor_index", type: "number", title: "Sensor index", required: true, description: "Select=INDEX in URL when viewing a sensor on map.purpleair.com"
		}
	}
}

// Parse events into attributes. This will never be called but needs to be present in the DTH code.
def parse(String description) {
	log.debug("IQAir: Parsing '${description}'")
}

def installed() {
	configure()
	refresh()
}

def refresh() {
	sensorCheck()
}

def configure() {
	unschedule()

	if ( update_interval == "15" ) {
		runEvery15Minutes(refresh)
	} else if ( update_interval == "30" ) {
		runEvery30Minutes(refresh)
	} else if ( update_interval == "60" ) {
		runEvery1Hour(refresh)
	} else if ( update_interval == "180" ) {
		runEvery3Hours(refresh)
	} else {
		runEvery1Hour(refresh)
	}
}

def updated() {
	configure()
}

def uninstalled() {
	unschedule()
}

void sensorCheck() {
	String url="https://api.purpleair.com/v1/sensors"
	Map httpQuery = [:]
	
	if ( device_search ) {
		float[] coords = parseJson(search_coords)
		float[] range = parseJson(search_range)
		httpQuery = [fields: "name,${aqi_interval}", location_type: "0", max_age: 3600, nwlat: coords[0] + range[0], nwlng: coords[1] - range[1], selat: coords[0] - range[0], selng: coords[1] + + range[1]]
	} else {
		httpQuery = [fields: "name,${aqi_interval}", location_type: "0", max_age: 3600, show_only: "$sensor_index"]
	}

	Map params = [
		uri: url,
		headers: ['X-API-Key': X_API_Key],
		query: httpQuery,
		requestContentType: "application/json",
		contentType: "application/json",
		timeout: 30,
		ignoreSSLIssues: true
	]
 
	// log.debug "params: $params"

	try {
		asynchttpGet('httpResponse', params, [data: null])
	} catch (SocketTimeoutException e) {
		log.error("Connection to PurpleAir timed out.")
	} catch (e) {
		log.error("There was an error: $e")
	}
}

void httpResponse(hubitat.scheduling.AsyncResponse resp, Map data) {
	Integer aqiValue = -1
	
	if (resp.getStatus() != 200 ) {
		log.debug "HTTP error: " + resp.getStatus()
		return
	}
	
	String[][] sensorData = resp.getJson().data
	aqiValue = getPart2_5_AQI(sensorAverage(sensorData, 2))
	AQIcategory = getCategory(aqiValue)
	
	String sites = sensorData.collect { it[1] }.sort()

	if ( sensorData.size() == 0 ) {
		log.debug "No sensor data returned"
	} else {
		if (sensorData.size() == 1) {
			sendEvent(name: "sites", value: sites, descriptionText: "AQI reported from site ${sites}")
		} else {
			sendEvent(name: "sites", value: sites, descriptionText: "Reported AQI is average of sites ${sites}")
		}
		//sendEvent(name: "aqi", value: aqiValue, unit: "AQI", descriptionText: "${device.displayName} AQI level is ${aqiValue}")
		sendEvent(name: "aqi", value: aqiValue, unit: "AQI", descriptionText: "${AQIcategory}")
		sendEvent(name: "category", value: AQIcategory, descriptionText: "${device.displayName} category is ${AQIcategory}")
	}
}

Float sensorAverage(def sensors, int field) {
	Integer count = 0
	Float sum = 0
    
	sensors.each {
		sum = sum + Float.valueOf(it[field])
		count = count + 1
	}
	return sum / count
}

// getAQI and AQILinear functions from https://www.airnow.gov/aqi/aqi-calculator/
Integer getPart2_5_AQI(Float partCount) {
	if ( partCount >= 0 && partCount < 12.1 ) {
		return AQILinear(50,0,12,0,partCount)
	} else if ( partCount >= 12.1 && partCount < 35.5 ) {
		return AQILinear(100,51,35.4,12.1,partCount)
	} else if ( partCount >= 35.5 && partCount < 55.5 ) {
		return AQILinear(150,101,55.4,35.5,partCount)
	} else if ( partCount >= 55.5 && partCount < 150.5 ) {
		return AQILinear(200,151,150.4,55.5,partCount)
	} else if ( partCount >= 150.5 && partCount < 250.5 ) {
		return AQILinear(300,201,250.4,150.5,partCount)
	} else if ( partCount >= 250.5 && partCount < 350.5 ) {
		return AQILinear(400,301,350.4,250.5,partCount)
	} else if ( partCount >= 350.5 && partCount < 500.5 ) {
		return AQILinear(500,401,500.4,350.5,partCount)
	} else if ( partCount >= 500.5 ) {
		return Math.round(partCount)
	} else {
		return -1
	}
}

Integer AQILinear(int AQIhigh, int AQIlow, float Conchigh, float Conclow, float Concentration) {
	float a;
	
	a=((Concentration-Conclow)/(Conchigh-Conclow))*(AQIhigh-AQIlow)+AQIlow;
	
	return Math.round(a)
}

String getCategory(Integer AQI) {
	if ( AQI >= 0 && AQI <= 50 ) {
		return "Good"
	} else if ( AQI > 50 && AQI <= 100 ) {
		return "Moderate"
	} else if ( AQI > 100 && AQI <= 150 ) {
		return "Unhealthy for sensitive groups"
	} else if ( AQI > 150 && AQI <= 200 ) {
		return "Unhealthy"
	} else if ( AQI > 200 && AQI <= 300 ) {
		return "Very unhealthy"
	} else if ( AQI > 300 && AQI <= 500) {
		return "Hazardous"
	} else if ( AQI > 500 ) {
		return "Hazardous!!! AQI is off the charts!"
	} else {
		return "error"
	}
}
