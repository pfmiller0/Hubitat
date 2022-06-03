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
		input "X_API_Key", "text", title: "PurpleAir API key", required: true, description: "Contact contact@purpleair.com to request an API key"
		input "update_interval", "enum", title: "Update interval", required: true, description: "Minutes between updates", options: ["1", "5", "10", "15", "30", "60", "180"], defaultValue: "60"
		input "avg_period", "enum", title: "Averaging period", required: true, description: "Readings averaged over what time", options: ["pm2.5", "pm2.5_10minute", "pm2.5_30minute", "pm2.5_60minute", "pm2.5_6hour", "pm2.5_24hour", "pm2.5_1week"], defaultValue: "pm2.5_60minute"
		input "device_search", "bool", title: "Search for devices", required: true, description: "If false specify device index to use", defaultValue: true

		if ( device_search ) {
			input "search_coords", "text", title: "Search coordinates [lat, long]", required: true, description: "Coordinates at center of sensor search box", defaultValue: "[" + location.latitude + "," + location.longitude + "]"
			input "search_range", "decimal", title: "Search range", required: true, description: "Size of sensor search box (+/- center of search box coordinates)", defaultValue: 1.5
			input "unit", "enum", title: "Unit", required: true, options: ["miles", "kilometers"], defaultValue: "miles"
			input "weighted_avg", "bool", title: "Weighted average", required: true, description: "Calculate device average weighted by distance", defaultValue: true
			//input "confidenceThreshold", "number", title: "Confidence threshold", required: true, description: "Filter out measurments below this confidence", range: "0..100", defaultValue: 90
		} else {
			input "Read_Key", "text", title: "Private key", required: false, description: "Required to access private devices"
			input "sensor_index", "number", title: "Sensor index", required: true, description: "Select=INDEX in URL when viewing a sensor on map.purpleair.com", defaultValue: 90905
		}
		input "debugMode", "bool", title: "Debug logging", required: true, defaultValue: false
	}
}

// Parse events into attributes. Required for device drivers but not used
def parse(String description) {
	log.debug("IQAir: Parsing '${description}'")
}

def installed() {
	// Do nothing on install because a API key is required
}

def refresh() {
	sensorCheck()
}
def poll() {
	sensorCheck()
}

def configure() {
	unschedule()

	if ( update_interval == "1" ) {
		schedule('0 */1 * ? * *', refresh)
	} else if ( update_interval == "5" ) {
		schedule('0 */5 * ? * *', refresh)
	} else if ( update_interval == "10" ) {
		schedule('0 */10 * ? * *', refresh)
	} else if ( update_interval == "15" ) {
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
	String query_fields="name,${avg_period},latitude,longitude,confidence"
	Map httpQuery
	Float[] coords
	
	if ( device_search ) {
		coords = parseJson(search_coords)
		Float[] dist2deg = distance2degrees(coords[0])
		Float[] range = []
		
		//log.debug getCategory(search_range)

		if ( unit == "miles" ) {
			range = [(search_range as Float)/dist2deg[0], (search_range as Float)/dist2deg[1]]
		} else { // Convert to km
			range = [((search_range as Float)/1.609)/dist2deg[0], ((search_range as Float)/1.609)/dist2deg[1]]
		}
		httpQuery = [fields: query_fields, location_type: "0", max_age: 3600, nwlat: coords[0] + range[0], nwlng: coords[1] - range[1], selat: coords[0] - range[0], selng: coords[1] + + range[1]]
	} else {
		if ( Read_Key ) {
			httpQuery = [fields: query_fields, read_key: Read_Key, max_age: 3600, show_only: "$sensor_index"]
		} else {
			httpQuery = [fields: query_fields, max_age: 3600, show_only: "$sensor_index"]
		}
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

	//log.debug "params: $params"

	try {
		asynchttpGet('httpResponse', params, [coords: coords])
	} catch (SocketTimeoutException e) {
		log.error("Connection to PurpleAir timed out.")
	} catch (e) {
		log.error("There was an error: $e")
	}
}

void httpResponse(hubitat.scheduling.AsyncResponse resp, Map data) {
	Integer aqiValue = -1
	String[][] sensorData
	String sites
	
	if (resp.getStatus() != 200 ) {
		log.error "HTTP error from PurpleAir: " + resp.getStatus()
		return
	}
	
	//log.debug "size: ${resp.getJson().data.size()}"
	
	if ( device_search ) {
		// Filter out lower quality devices
		//sensorData = resp.getJson().data.findAll {it[5] >= (confidenceThreshold as Integer) }
		sensorData = resp.getJson().data.findAll {it[5] >= 90 }
	} else {
		sensorData = resp.getJson().data
	}
	//log.debug sensorData

	if ( debugMode ) {
		log.debug "sites: ${sensorData.collect { it[1] }}"
		log.debug "AQIs: ${sensorData.collect { getPart2_5_AQI( Float.parseFloat(it[2])) }}"
		log.debug "confidence: ${sensorData.collect { it[5] }}"
		log.debug "unweighted av aqi: ${getPart2_5_AQI(sensorAverage(sensorData, 2))}"
		log.debug "coords: ${data.coords}"
		if ( weighted_avg && device_search ) {
			log.debug "weighted av aqi: ${getPart2_5_AQI(sensorAverageWeighted(sensorData, 2, data.coords))}"
		}
	}
	
	if ( weighted_avg && device_search) {
		aqiValue = getPart2_5_AQI(sensorAverageWeighted(sensorData, 2, data.coords))
	} else {
		aqiValue = getPart2_5_AQI(sensorAverage(sensorData, 2))
	}
	
	AQIcategory = getCategory(aqiValue)
	
	sites = sensorData.collect { it[1] }.sort()
	//sites = sites.substring(1, sites.length() - 1) // Remove brackets around sites?
	
	if ( sensorData.size() == 0 ) {
		log.error "No sensors found in search area"
	} else {
		if (sensorData.size() == 1) {
			sendEvent(name: "sites", value: sites, descriptionText: "AQI reported from site ${sites}")
		} else {
			sendEvent(name: "sites", value: sites, descriptionText: "AQI is averaged from ${sensorData.size()} sites ${sites}")
		}
		sendEvent(name: "category", value: AQIcategory, descriptionText: "${device.displayName} category is ${AQIcategory}")
		//sendEvent(name: "aqi", value: aqiValue, unit: "AQI", descriptionText: "${device.displayName} AQI level is ${aqiValue}")
		sendEvent(name: "aqi", value: aqiValue, unit: "AQI", descriptionText: "${AQIcategory}")
	}
}

Float sensorAverage(def sensors,Integer field) {
	Integer count = 0
	Float sum = 0
    
	sensors.each {
		sum = sum + Float.valueOf(it[field])
		count = count + 1
	}
	return sum / count
}

Float sensorAverageWeighted(def sensors, Integer field, Float[] coords) {
	Float count = 0.0
	Float sum = 0.0
	def distances = []
	Float nearest = 0.0
    
	// Weighted average. First find nearest sensor. Then divide sensors distances by nearest distance to get weights.
	sensors.each {
		distances.add(Float.valueOf(distance(coords, [Float.valueOf(it[3]), Float.valueOf(it[4])])))
	}
	nearest = distances.min()
	
	sensors.eachWithIndex { it, i ->
	   	Float val = Float.valueOf(it[field])
	   	Float weight = nearest / distances[i]
		sum = sum + val * weight
	   	count = count + weight
	}
	return sum / count
}

Float distance(Float[] a, def b) {
    Float[] dist2deg = distance2degrees(a[0])

    Float lat_diff = (a[0] - b[0])*dist2deg[0]
    Float lon_diff = (a[1] - b[1])*dist2deg[1]

    return Math.sqrt(lat_diff**2 + lon_diff**2)
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

Integer AQILinear(Integer AQIhigh, Integer AQIlow, Float Conchigh, Float Conclow, Float Concentration) {
	Float a
	
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
		return "Extremely hazardous!"
	} else {
		return "error"
	}
}

// Returns miles per degree for a given latitude
Float[] distance2degrees(Float latitude) {	
	Float latMilesPerDegree = 69.172 * Math.cos(Math.toRadians(latitude))
	Float longMilesPerDegree = 68.972
	
	return [latMilesPerDegree, longMilesPerDegree]
}
