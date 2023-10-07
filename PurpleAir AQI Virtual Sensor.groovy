/**
 *  PurpleAir AQI Virtual Sensor
 *
 *  PurpleAir sensor map: https://map.purpleair.com/
 *  API documentation: https://api.purpleair.com/ 
 */

public static String version() { return "1.2.3" }

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
		capability "Initialize"

		attribute "aqi", "number"
		attribute "conversion", "string" // Conversion algorithm
		attribute "category", "string" // Description of current air quality
		attribute "sites", "string" // List of sensor sites used

		command "refresh"
	}

	preferences {
		input "X_API_Key", "text", title: "PurpleAir API key", required: true, description: "Contact contact@purpleair.com to request an API key"
		input "update_interval", "enum", title: "Update interval", required: true, options: [["1": "1 min"], ["5": "5 min"], ["10": "10 min"], ["15": "15 min"], ["30": "30 min"], ["60": "1 hr"], ["180": "3 hr"]], defaultValue: "60"
		input "conversion", "enum", title: "Apply conversion", required: false, description: "See map.purpleair.com for details", options: ["US EPA", "Woodsmoke", "AQ&U", "LRAPA"]
		if (! conversion) {
			input "avg_period", "enum", title: "Averaging period", required: true, description: "Readings averaged over what time", options: [["pm2.5": "1 min"], ["pm2.5_10minute": "10 mins"], ["pm2.5_30minute": "30 mins"], ["pm2.5_60minute": "1 hour"], ["pm2.5_6hour": "6 hours"], ["pm2.5_24hour": "1 day"], ["pm2.5_1week": "1 week"]], defaultValue: "pm2.5_60minute"
		}
		input "device_search", "bool", title: "Search for devices", required: true, description: "If false specify device index to use", defaultValue: true

		if ( device_search ) {
			input "search_coords", "text", title: "Search coordinates [lat, long]", required: true, description: "Coordinates at center of sensor search box", defaultValue: "[" + location.latitude + "," + location.longitude + "]"
			input "search_range", "decimal", title: "Search range", required: true, description: "Size of sensor search box (+/- center of search box coordinates)", defaultValue: 1.5
			input "unit", "enum", title: "Unit", required: true, options: ["miles", "kilometers"], defaultValue: "miles"
			input "weighted_avg", "bool", title: "Weighted average", required: true, description: "Calculate device average weighted by distance", defaultValue: true
			//input "confidenceThreshold", "number", title: "Confidence threshold", required: true, description: "Filter out measurments below this confidence", range: "0..100", defaultValue: 90
		} else {
			input "Read_Key", "text", title: "Private key", required: false, description: "Required to access private devices"
			input "sensor_index", "number", title: "Sensor index", required: true, description: "Select=INDEX in URL when viewing a sensor on map.purpleair.com", defaultValue: 82101
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
	
	if (! conversion) {
		device.deleteCurrentState('conversion')
	}

	if ( update_interval == "1" ) {
		schedule('0 */1 * ? * *', 'refresh')
	} else if ( update_interval == "5" ) {
		schedule('0 */5 * ? * *', 'refresh')
	} else if ( update_interval == "10" ) {
		schedule('0 */10 * ? * *', 'refresh')
	} else if ( update_interval == "15" ) {
		runEvery15Minutes('refresh')
	} else if ( update_interval == "30" ) {
		runEvery30Minutes('refresh')
	} else if ( update_interval == "60" ) {
		runEvery1Hour('refresh')
	} else if ( update_interval == "180" ) {
		runEvery3Hours('refresh')
	} else {
		log.error "Invalid update_interval"
		runEvery1Hour('refresh')
	}
}

def initialize() {
	configure()
}

def updated() {
	configure()
}

def uninstalled() {
	unschedule()
}

void sensorCheck() {
	String url="https://api.purpleair.com/v1/sensors"
	String particles = avg_period
	if (conversion) {
		particles = "pm2.5"
	}
	
	String query_fields="name,${particles},latitude,longitude,confidence,pm1.0,pm2.5_alt,pm2.5_cf_1,pm10.0,humidity,voc,ozone1,position_rating"
	Map httpQuery
	Float[] coords
	
	if ( device_search ) {
		coords = parseJson(search_coords)
		Float[] dist2deg = distance2degrees(coords[0])
		Float[] range = []
				
		if ( unit == "miles" ) {
			range = [search_range/dist2deg[0], search_range/dist2deg[1]]
		} else { // Convert to km
			range = [(search_range/1.609)/dist2deg[0], (search_range/1.609)/dist2deg[1]]
		}
		httpQuery = [fields: query_fields, location_type: "0", max_age: 3600, nwlat: coords[0] + range[0], nwlng: coords[1] - range[1], selat: coords[0] - range[0], selng: coords[1] + + range[1]]
	} else {
		if ( Read_Key ) {
			httpQuery = [fields: query_fields, read_key: Read_Key, show_only: "$sensor_index"]
		} else {
			httpQuery = [fields: query_fields, show_only: "$sensor_index"]
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

	try {
		asynchttpGet('httpResponse', params, [coords: coords, particles: particles])
	} catch (SocketTimeoutException e) {
		log.error("Connection to PurpleAir timed out.")
	} catch (Exception e) {
		log.error("There was an error: $e")
	}
}

void httpResponse(hubitat.scheduling.AsyncResponse resp, Map data) {
	Map RESPONSE_FIELDS = [:]
	Integer aqiValue = -1
	String[][] sensorData
	String sites
	List<Map> sensors = []
	
	/*****************************/
	if (resp.getStatus() != 200 ) {
		log.error "HTTP error from PurpleAir: " + resp.getStatus()
		return
	}
	/*** Test backoff on error ***
	String respMimetype = ''
	if (resp.getHeaders() && resp.getHeaders()["Content-Type"]) {
		respMimetype = resp.getHeaders()["Content-Type"].split(";")[0]
	}

	if (resp.getStatus() != 200 || respMimetype != "application/json" ) {
		if (respMimetype != "application/json" ) {
			log.error "Response type '${respMimetype}', JSON expected"
		}
		state.failCount = state.failCount?:0 + 1
		unschedule('refresh')
		if (state.failCount <= 4 ) {
			log.error "HTTP error from PurpleAir: " + resp.getStatus()
			runIn(Integer.valueOf(update_interval) * state.failCount * 60, 'refresh')
		} else if (state.failCount == 5 ) {
			log.error "HTTP error from PurpleAir: " + resp.getStatus() + " (muting errors)"
			runIn(Integer.valueOf(update_interval * state.failCount) * 60, 'refresh')
		} else {
			runIn(Integer.valueOf(update_interval) * 6 * 60, 'refresh')
		}
		return
	} else {
		if (state.failCount > 0 ) {
			if (state.failCount >= 5 ) {
				log.info "HTTP error from PurpleAir resolved ($state.failCount)"
			}
			state.failCount = 0
			configure()
		}
	}
	/*****************************/
		
	// Set field lookup map
	resp.getJson().fields.eachWithIndex{it,index-> RESPONSE_FIELDS[(it)] = index}
	
	if ( device_search ) {
		// Filter out lower quality devices
		//sensorData = resp.getJson().data.findAll {it[RESPONSE_FIELDS["confidence"]] >= (confidenceThreshold as Integer) }
		sensorData = resp.getJson().data.findAll {it[RESPONSE_FIELDS["confidence"]] >= 90}
		if ( debugMode ) {
			def dropped = resp.getJson().data.findAll {it[RESPONSE_FIELDS["confidence"]] < 90}
			if ( dropped ) {
				log.debug "Low confidence sensors dropped: ${dropped}"
			}
		}
	} else {
		sensorData = resp.getJson().data
	}
	
	// Some sensors don't return humidity, fill in missing data with avg from other devices
	Float avg_humidity = sensorAverage(sensorData.collect {['humidity': Float.valueOf(it[RESPONSE_FIELDS['humidity']]?:0)]}, 'humidity')
	if (avg_humidity == null) {
		if (conversion == "US EPA") {
			log.error 'No humidity returned from sites and "US EPA" conversion selected. US EPA requires humidity data, please choose another option!'
			return
		} else {
			log.debug "humidity null, but not required. setting to 0"
			avg_humidity = 0.0
		}
	}

	// initialize sensor maps
	Float[] sensor_coords
	Float pm2_5_conv
	sensorData.each {
		sensor_coords = [Float.valueOf(it[RESPONSE_FIELDS['latitude']]), Float.valueOf(it[RESPONSE_FIELDS['longitude']])]
		part_count_conv = apply_conversion(conversion?:"none", Float.valueOf(it[RESPONSE_FIELDS[data.particles]]), Float.valueOf(it[RESPONSE_FIELDS['pm2.5_cf_1']]), Float.valueOf(it[RESPONSE_FIELDS['humidity']]?:avg_humidity))
		sensors << [
			'site': it[RESPONSE_FIELDS['name']],
			'pm2_5': it[RESPONSE_FIELDS[data.particles]].toFloat(),
			'pm2_5_conv': pm2_5_conv,
			'confidence': it[RESPONSE_FIELDS['confidence']].toInteger(),
			'distance': distance(data.coords, sensor_coords),
			'position_rating': it[RESPONSE_FIELDS['position_rating']].toInteger(),
			'coords': sensor_coords,
			//'pm1.0': (it[RESPONSE_FIELDS['pm1.0']]?:0).toFloat(),
			'pm2_5_alt': (it[RESPONSE_FIELDS['pm2.5_alt']]?:0).toFloat(),
			'pm2_5_cf_1': (it[RESPONSE_FIELDS['pm2.5_cf_1']]?:0).toFloat(),
			//'pm10.0': (it[RESPONSE_FIELDS['pm10.0']]?:0).toFloat(),
			'voc': (it[RESPONSE_FIELDS['voc']]?:0).toFloat(),
			'ozone': (it[RESPONSE_FIELDS['ozone1']]?:0).toFloat(),
			'humidity': this_humidity
		]
	}
	if ( debugMode ) {
		log.debug "coords: ${data.coords}"
		log.debug "site: ${sensors.collect { it['site'] }}"
		log.debug "particle ct query: ${data.particles}"
		log.debug "pm2_5: ${sensors.collect { it['pm2_5'] }}"
		log.debug "pm2_5_conv: ${sensors.collect { it['pm2_5_conv'] }}"
		log.debug "confidence: ${sensors.collect { it['confidence'] }}"
		log.debug "humidity: ${sensors.collect { it['humidity'] }}"
		//log.debug "pm1.0: ${sensors.collect { it['pm1.0'] }}"
		log.debug "pm2_5_alt: ${sensors.collect { it['pm2.5_alt'] }}"
		log.debug "pm2_5_cf_1: ${sensors.collect { it['pm2.5_cf_1'] }}"
		//log.debug "pm10.0: ${sensors.collect { it['pm10.0'] }}"
		log.debug "voc: ${sensors.collect { it['voc'] }}"
		log.debug "ozone: ${sensors.collect { it['ozone'] }}"
		log.debug "AQIs: ${sensors.collect { getPart2_5_AQI(it['pm2_5']) }}"
		log.debug "AQIs (${conversion?:"none"}): ${sensors.collect { getPart2_5_AQI(it['pm2_5_conv']) }}"
		log.debug "distance: ${sensors.collect { it['distance'] }}"
		log.debug "position_rating: ${sensors.collect { it['position_rating'] }}"
		if ( device_search ) {
			log.debug "unweighted av aqi (${conversion?:"none"}): ${getPart2_5_AQI(sensorAverage(sensors, 'pm2_5_conv'))}"
			log.debug "weighted av aqi (${conversion?:"none"}): ${getPart2_5_AQI(sensorAverageWeighted(sensors, 'pm2_5_conv', data.coords))}"
		}
	}

	if (! conversion) {
		if ( weighted_avg && device_search) {
			aqiValue = getPart2_5_AQI(sensorAverageWeighted(sensors, 'pm2_5', data.coords))
		} else {
			aqiValue = getPart2_5_AQI(sensorAverage(sensors, 'pm2_5'))
		}
	} else {
		if ( weighted_avg && device_search) {
			aqiValue = getPart2_5_AQI(sensorAverageWeighted(sensors, 'pm2_5_conv', data.coords))
		} else {
			aqiValue = getPart2_5_AQI(sensorAverage(sensors, 'pm2_5_conv'))
		}
	}
	
	AQIcategory = getCategory(aqiValue)
	
	sites = sensors.collect { it['site'] }.sort()
	//sites = sensorData.collect { it['site' }.sort().join(', ') // Remove brackets around sites?
	
	if ( sensors.size() == 0 ) {
		log.error "No sensors found in search area"
	} else {
		if (sensors.size() == 1) {
			sendEvent(name: "sites", value: sites, descriptionText: "AQI reported from site ${sites}")
		} else {
			sendEvent(name: "sites", value: sites, descriptionText: "AQI is averaged from ${sensors.size()} sites ${sites}")
		}
		sendEvent(name: "category", value: AQIcategory, descriptionText: "${device.displayName} category is ${AQIcategory}")
		if (conversion) {
			sendEvent(name: "conversion", value: conversion, descriptionText: "AQI conversion algorithm is ${conversion}")
			//sendEvent(name: "aqi", value: aqiValue, unit: "AQI (${conversion})", descriptionText: "${device.displayName} AQI level is ${aqiValue}")
			sendEvent(name: "aqi", value: aqiValue, unit: "AQI (${conversion})", descriptionText: "${AQIcategory}")
		} else {
			//sendEvent(name: "aqi", value: aqiValue, unit: "AQI", descriptionText: "${device.displayName} AQI level is ${aqiValue}")
			sendEvent(name: "aqi", value: aqiValue, unit: "AQI", descriptionText: "${AQIcategory}")
		}
	}
}

Float sensorAverage(List<Map> sensors, String field) {
	Integer count = 0
	Float sum = 0
    
	sensors.each {
		if (it[field]) {
			sum = sum + it[field]
			count = count + 1
		}
	}

	if (sum > 0) {
		return sum / count
	} else {
		return null
	}
}

// TODO: Use position_rating as multiplier to weight accurate positions more
Float sensorAverageWeighted(List<Map> sensors, String field, Float[] coords) {
	Float count = 0.0
	Float sum = 0.0
	ArrayList distances = []
	// ArrayList weights = []
	Float nearest = 0.0
	
	// Weighted average. First find nearest sensor. Then divide sensors distances by nearest distance to get weights.
	sensors.each {
		distances.add(it['distance'])
	}
	nearest = distances.min()
	
	sensors.eachWithIndex { it, i ->
		Float val = it[field]
		Float weight = nearest / Math.sqrt(distances[i])
		// Float weight = nearest / distances[i]
		// if ( debugMode ) log.debug "weight=nearest/distance : ${weight} = ${nearest} / ${distances[i]}"
		// if ( debugMode ) weights.add(weight)
		sum = sum + val * weight
		count = count + weight
	}
	// if ( debugMode ) log.debug "weights: ${weights}"
	// if ( debugMode ) log.debug "distances: ${distances}"
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

Integer AQILinear(Integer AQIhigh, Integer AQIlow, Float Conchigh, Float Conclow, Float Concentration) {
	Float a = ((Concentration-Conclow)/(Conchigh-Conclow))*(AQIhigh-AQIlow)+AQIlow
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

Float apply_conversion(String conversion, Float PM25, Float PM25_cf_1, Float RH) {
	if ( conversion == "US EPA" ) {
		return us_epa_conversion(PM25, RH)
	} else if ( conversion == "Woodsmoke" ) {
		return woodsmoke_conversion(PM25_cf_1)
	} else if ( conversion == "AQ and U" ) {
		return AQandU_conversion(PM25)
	} else if ( conversion == "LRAPA" ) {
		return lrapa_conversion(PM25_cf_1)
	} else {
		return PM25
	}
}

Float us_epa_conversion(Float PM, Float RH) {
	// y={0 ≤ x <30: 0.524*x - 0.0862*RH + 5.75}
	// y={30≤ x <50: (0.786*(x/20 - 3/2) + 0.524*(1 - (x/20 - 3/2)))*x -0.0862*RH + 5.75}
	// y={50 ≤ x <210: 0.786*x - 0.0862*RH + 5.75}
	// y={210 ≤ x <260: (0.69*(x/50 – 21/5) + 0.786*(1 - (x/50 – 21/5)))*x - 0.0862*RH*(1 - (x/50 – 21/5)) + 2.966*(x/50 – 21/5) + 5.75*(1 - (x/50 – 21/5)) + 8.84*(10^{-4})*x^{2}*(x/50 – 21/5)}
	// y={260 ≤ x: 2.966 + 0.69*x + 8.84*10^{-4}*x^2}
	//
	// y= corrected PM2.5 µg/m3
	// x= PM2.5 cf_atm (lower)
	// RH= Relative humidity as measured by the PurpleAir
	//
	// Source: https://cfpub.epa.gov/si/si_public_record_report.cfm?dirEntryId=353088&Lab=CEMM
	// PDF, p26
	if ( PM < 30 ) {
		return 0.524 * PM - 0.0862 * RH + 5.75
	} else if ( PM < 50 ) {
		return (0.786 * (PM/20 - 3/2) + 0.524 * (1 - (PM/20 - 3/2))) * PM -0.0862 * RH + 5.75
	} else if ( PM < 210 ) {
		return 0.786 * PM - 0.0862 * RH + 5.75
	} else if ( PM < 260 ) {
		Float y = 0.69*(PM/50 - 21/5) + 0.786*(1 - (PM/50 - 21/5))
		y = y*PM - 0.0862*RH * (1 - (PM/50 - 21/5))
		y = y + 2.966*(PM/50 - 21/5) + 5.75*(1 - (PM/50 - 21/5))
		y = y + 8.84*(10**(-4))*PM**2*(PM/50 - 21/5)
		return y
	} else {
		return 2.966 + 0.69*x + 8.84*(10**(-4))*(PM**2)
	}
}

Float woodsmoke_conversion(Float PM) {
	// Woodsmoke PM2.5 (µg/m³) = 0.55 x PA (pm2.5_cf_1) + 0.53
	// Source: map.purpleair.com
	return 0.55 * PM + 0.53
}

Float AQandU_conversion(Float PM) {
	// PM2.5 (µg/m³) = 0.778 x PA + 2.65
	// Source: map.purpleair.com
	return 0.778 * PM + 2.65
}

Float lrapa_conversion(Float PM) {
	// 0 - 65 µg/m³ range:
	// LRAPA PM2.5 (µg/m³) = 0.5 x PA (pm2.5_cf_1) – 0.66
	// Source: map.purpleair.com
	//
	// Deprecated? per https://www.lrapa.org/aqi101/
	return 0.5 * PM - 0.66
}

// PM_ALT ("pm2.5_alt", availble from api)

Float distance(Float[] coorda, Float[] coordb) {
	if ( coorda == null || coordb == null ) return 0.0
	// Haversine function from http://www.movable-type.co.uk/scripts/latlong.html
	Double R = 6371000; // metres
	Double φ1 = Math.toRadians(coorda[0]); // φ, λ in radians
	Double φ2 = Math.toRadians(coordb[0]);
	Double Δφ = Math.toRadians(coordb[0]-coorda[0]);
	Double Δλ = Math.toRadians(coordb[1]-coorda[1]);

	Double a = Math.sin(Δφ/2) * Math.sin(Δφ/2) + Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ/2) * Math.sin(Δλ/2);
	Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

	Double d = (R * c) / 1000; // in km
	return d / 1.609 // in miles
}

// Returns miles per degree for a given latitude
Float[] distance2degrees(Float latitude) {	
	Float latMilesPerDegree = 69.172 * Math.cos(Math.toRadians(latitude))
	Float longMilesPerDegree = 68.972
	
	return [latMilesPerDegree, longMilesPerDegree]
}
