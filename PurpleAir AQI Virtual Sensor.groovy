/**
 *  PurpleAir AQI Virtual Sensor
 *
 *  PurpleAir sensor map: https://map.purpleair.com/
 *  API documentation: https://api.purpleair.com/ 
 */

import groovy.transform.Field

@Field final static String VERSION = "1.3.0"

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
		input "conversion", "enum", title: "Apply conversion", required: false, description: "See map.purpleair.com for details", options: [["US EPA": "US EPA"], ["Woodsmoke": "Woodsmoke"], ["AQ&U": "AQ&U"], ["CF=1": "CF=1"], ["LRAPA": "LRAPA"]]
		if (! conversion) {
			input "avg_period", "enum", title: "Averaging period", required: true, description: "Readings averaged over what time", options: [["pm2.5": "1 min"], ["pm2.5_10minute": "10 mins"], ["pm2.5_30minute": "30 mins"], ["pm2.5_60minute": "1 hour"], ["pm2.5_6hour": "6 hours"], ["pm2.5_24hour": "1 day"], ["pm2.5_1week": "1 week"]], defaultValue: "pm2.5_60minute"
		} else if ( conversion == "US EPA" ) {
			input "hum_history", "bool", title: "Humidity history", required: false, description: "Keep recent history of humidity values to detect bad sensors", defaultValue: false
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
	// Do nothing on install because an API key is required
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
	if (conversion != "US EPA" || ! hum_history || hum_history == "0") {
		state.remove('HUMIDITY_HISTORY')
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
	final String URL="https://api.purpleair.com/v1/sensors"
	String pm25_count = avg_period
	if (conversion) {
		if (conversion == "lrapa" || conversion == "woodsmoke" || conversion == "CF=1") {
			pm25_count="pm2.5_cf_1"
		} else {
			pm25_count = "pm2.5"
		}
	}
		
	String query_fields="name,confidence"
	if (conversion == "US EPA") {
		query_fields+=",humidity,${pm25_count}"
	} else {
		query_fields+=",${pm25_count}"
	}
	if (weighted_avg) {
		query_fields+=",latitude,longitude,position_rating"
	}
	//query_fields="name,latitude,longitude,position_rating,confidence,humidity,${pm25_count},pm10.0"
		
	Map httpQuery
	Float[] coords
	
	if (device_search) {
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
		uri: URL,
		headers: ['X-API-Key': X_API_Key],
		query: httpQuery,
		requestContentType: "application/json",
		contentType: "application/json",
		timeout: 30,
		ignoreSSLIssues: true
	]

	try {
		asynchttpGet('httpResponse', params, [coords: coords, pm25_count: pm25_count])
	} catch (SocketTimeoutException e) {
		log.error("Connection to PurpleAir timed out.")
	} catch (Exception e) {
		log.error("There was an error: $e")
	}
}

void httpResponse(hubitat.scheduling.AsyncResponse resp, Map data) {
	Map RESPONSE_FIELDS = [:]
	Integer aqi2_5Value = -1
	Integer aqi10Value = -1
	String[][] sensorData
	String sites
	List<Map> sensors = []
	
	/*****************************
	if (resp.getStatus() != 200 ) {
		log.error "HTTP error from PurpleAir: " + resp.getStatus()
		return
	}
	/*** Test backoff on error ***/
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
	
	//logDebug "resp: ${resp.getJson().data}"
	
	if ( device_search ) {
		// Filter out lower quality devices
		//sensorData = resp.getJson().data.findAll {it[RESPONSE_FIELDS["confidence"]] >= (confidenceThreshold as Integer) }
		sensorData = resp.getJson().data.findAll {it[RESPONSE_FIELDS["confidence"]] >= 90}
		if ( debugMode ) {
			List confidence = sensorData.collect {['name': it[RESPONSE_FIELDS['name']], 'confidence': it[RESPONSE_FIELDS['confidence']].toInteger()]}
			List dropped = confidence.findAll {it["confidence"] < 90}
			//List dropped = resp.getJson().data.findAll {it[RESPONSE_FIELDS["confidence"]] < 90}
			if ( dropped ) {
				logDebug "Sensor confidence: ${confidence}"
				logDebug "Low confidence sensors dropped: ${dropped}"
			}
		}
	} else {
		sensorData = resp.getJson().data
	}
		
	// Some sensors don't return humidity, fill in missing data with avg from other devices
	// Also detect broken humidity sensors by looking for ones that never change
	// TODO: make function for this?
	Map humidity_history = [:]
	Integer avg_humidity = 50
	if (conversion == "US EPA" && hum_history) {
		humidity_history = state.HUMIDITY_HISTORY?:[:]

		sensorData.each {humidityHistoryUpdate(humidity_history, it[RESPONSE_FIELDS['name']], Integer.valueOf(it[RESPONSE_FIELDS['humidity']]?:0))}
		// This mess is collecting the average of only the functional humidity sensors
		avg_humidity = Math.round(sensorAverage(sensorData.collect {['humidity': humidityDeviceUpdating(humidity_history, it[RESPONSE_FIELDS['name']])?it[RESPONSE_FIELDS['humidity']].toInteger():0]}, 'humidity')?:avg_humidity)
		
		if (avg_humidity == null) {
			log.error 'No valid humidity data returned from sites and "US EPA" conversion selected. US EPA requires humidity data, please choose another option!'
			return
		}

		state.HUMIDITY_HISTORY = humidity_history
	}
	
	//logDebug "RESPONSE_FIELDS: ${RESPONSE_FIELDS}"

	// initialize sensor maps
	// TODO: make function for this?
    final int HUMIDITY_FUDGE = 4 // PurpleAir states humidity sensors are ~4% below ambiant humidity
	Float[] sensor_coords = data.coords
	Float pm25_conv
	
	sensorData.each {
		Integer this_humidity = (humidityDeviceUpdating(humidity_history, it[RESPONSE_FIELDS['name']])?it[RESPONSE_FIELDS['humidity']].toInteger():avg_humidity) + HUMIDITY_FUDGE
		if (weighted_avg) {
			sensor_coords = [it[RESPONSE_FIELDS['latitude']].toFloat(), it[RESPONSE_FIELDS['longitude']].toFloat()]
		}
		pm25_conv = apply_conversion(conversion?:"none", it[RESPONSE_FIELDS[data.pm25_count]].toFloat(), this_humidity)
		sensors << [
			'site': it[RESPONSE_FIELDS['name']],
			'pm25': it[RESPONSE_FIELDS[data.pm25_count]].toFloat(),
			'pm25_conv': pm25_conv,
			'confidence': it[RESPONSE_FIELDS['confidence']].toInteger(),
			'distance': distance(data.coords, sensor_coords),
			'coords': sensor_coords,
			'position_rating': RESPONSE_FIELDS['position_rating']?it[RESPONSE_FIELDS['position_rating']].toInteger():-1,
			'humidity': this_humidity
		]
	}
	if ( debugMode ) {
		log.debug "coords: ${data.coords}"
		log.debug "site: ${sensors.collect { it['site'] }}"
		log.debug "particle ct query: ${data.pm25_count}"
		log.debug "confidence: ${sensors.collect { it['confidence'] }}"
		log.debug "humidity: ${sensors.collect { it['humidity'] }}"
		log.debug "pm2.5: ${sensors.collect { it['pm25'] }}"
		log.debug "pm2.5_conv: ${sensors.collect { it['pm25_conv'] }}"
		log.debug "PM2.5 AQIs: ${sensors.collect { getPart2_5_AQI(it['pm25']) }}"
		log.debug "PM2.5 AQIs (${conversion?:"none"}): ${sensors.collect { getPart2_5_AQI(it['pm25_conv']) }}"
		log.debug "distance: ${sensors.collect { it['distance'] }}"
		log.debug "position_rating: ${sensors.collect { it['position_rating'] }}"
		if ( device_search ) {
			log.debug "unweighted av PM 2.5 aqi (${conversion?:"none"}): ${getPart2_5_AQI(sensorAverage(sensors, 'pm25_conv'))}"
			log.debug "weighted av PM 2.5 aqi (${conversion?:"none"}): ${getPart2_5_AQI(sensorAverageWeighted(sensors, 'pm25_conv', data.coords))}"
		}
	}

	if (! conversion) {
		if ( weighted_avg && device_search) {
			aqi2_5Value = getPart2_5_AQI(sensorAverageWeighted(sensors, 'pm25', data.coords))
		} else {
			aqi2_5Value = getPart2_5_AQI(sensorAverage(sensors, 'pm25'))
		}
	} else {
		if ( weighted_avg && device_search) {
			aqi2_5Value = getPart2_5_AQI(sensorAverageWeighted(sensors, 'pm25_conv', data.coords))
		} else {
			aqi2_5Value = getPart2_5_AQI(sensorAverage(sensors, 'pm25_conv'))
		}
	}
	
	AQIcategory = getCategory(aqi2_5Value)
	
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
			sendEvent(name: "conversion", value: conversion, descriptionText: "PM 2.5 AQI conversion algorithm is ${conversion}")
			//sendEvent(name: "aqi", value: aqi2_5Value, unit: "PM 2.5 AQI (${conversion})", descriptionText: "${device.displayName} AQI level is ${aqi2_5Value}")
			sendEvent(name: "aqi", value: aqi2_5Value, unit: "PM 2.5 AQI (${conversion})", descriptionText: "${AQIcategory}")
		} else {
			//sendEvent(name: "aqi", value: aqi2_5Value, unit: "PM 2.5 AQI", descriptionText: "${device.displayName} AQI level is ${aqi2_5Value}")
			sendEvent(name: "aqi", value: aqi2_5Value, unit: "PM 2.5 AQI", descriptionText: "${AQIcategory}")
		}
	}
}

void humidityHistoryUpdate(Map history, String site, Integer val) {
	String hr = (new Date())[Calendar.HOUR].toString()

	if ( history.containsKey(site) ) {
		history[(site)][(hr)] = val
	} else {
		history[(site)] = [(hr): val]
	}
}

Boolean humidityDeviceUpdating(Map history, String site) {
	final int MIN_HISTORY = 3
	Integer hr = (new Date())[Calendar.HOUR]
	
	if (! history.containsKey(site) ) {
		return null
	}
	
	String last_hr
	Integer last_val = 0
	for (int i = 0; i <= 11; i++) {
		last_hr = ((hr + 12 - i) % 12).toString()
		//history[(site)].each { logDebug "${it.key}"; q(it.key)}
		if ( history[(site)][(last_hr)] ) {
			last_val = history[(site)][(last_hr)]
			break
		}
	}
	//logDebug "last_val: ${last_val}"
	
	if (last_val == 0) {
		logDebug "${site} hum offline"
		return false
	} else if ( history[(site)].size() < MIN_HISTORY ) {
		logDebug "${site} hum passing. insufficient history"
		return true
	}
	
	if (! history[(site)].any {it.value != last_val} ) {
		logDebug "${site} hum not updating ${history[(site)]}"
	}
	
	return history[(site)].any {it.value != last_val}
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
		log.warn "sensorAverage: No data for field '${field}'"
		return null
	}
}

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
		Float weight = nearest / Math.sqrt(distances[i]) * (it['position_rating']+1)
		sum += val * weight
		count += weight
	}
	// logDebug "weights: ${weights}"
	// logDebug "distances: ${distances}"
	return sum / count
}

// getAQI and AQILinear functions from https://www.airnow.gov/aqi/aqi-calculator
// (https://www.airnow.gov/sites/default/files/custom-js/conc-aqi.js)
Integer getPart2_5_AQI(Float partCount) {
	Float c = Math.floor(10*partCount)/10
	if ( c >= 0 && c < 12.1 ) {
		return AQILinear(50,0,12,0,c)
	} else if ( c >= 12.1 && c < 35.5 ) {
		return AQILinear(100,51,35.4,12.1,c)
	} else if ( c >= 35.5 && c < 55.5 ) {
		return AQILinear(150,101,55.4,35.5,c)
	} else if ( c >= 55.5 && c < 150.5 ) {
		return AQILinear(200,151,150.4,55.5,c)
	} else if ( c >= 150.5 && c < 250.5 ) {
		return AQILinear(300,201,250.4,150.5,c)
	} else if ( c >= 250.5 && c < 350.5 ) {
		return AQILinear(400,301,350.4,250.5,c)
	} else if ( c >= 350.5 && c < 500.5 ) {
		return AQILinear(500,401,500.4,350.5,c)
	} else if ( c >= 500.5 ) {
		return Math.round(c)
	} else {
		return -1
	}
}

Integer getPart10_AQI(Float partCount) {
	Float c = Math.floor(partCount);
	if (c>=0 && c<55) {
		return AQILinear(50,0,54,0,c);
	} else if (c>=55 && c<155) {
		return AQILinear(100,51,154,55,c);
	} else if (c>=155 && c<255) {
		return AQILinear(150,101,254,155,c);
	} else if (c>=255 && c<355) {
		return AQILinear(200,151,354,255,c);
	} else if (c>=355 && c<425) {
		return AQILinear(300,201,424,355,c);
	} else if (c>=425 && c<505) {
		return AQILinear(400,301,504,425,c);
	} else if (c>=505 && c<605) {
		return AQILinear(500,401,604,505,c);
	} else if ( c >= 605 ) {
		return Math.round(c)
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

Float apply_conversion(String conversion, Float PM25, Float RH) {
	if ( conversion == "US EPA" ) {
		return us_epa_conversion(PM25, RH)
	} else if ( conversion == "Woodsmoke" ) {
		return woodsmoke_conversion(PM25)
	} else if ( conversion == "AQ and U" ) {
		return AQandU_conversion(PM25)
	} else if ( conversion == "LRAPA" ) {
		return lrapa_conversion(PM25)
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
	
	Float c
	
	if ( PM < 30 ) {
		c = 0.524 * PM - 0.0862 * RH + 5.75
	} else if ( PM < 50 ) {
		c = (0.786 * (PM/20 - 3/2) + 0.524 * (1 - (PM/20 - 3/2))) * PM -0.0862 * RH + 5.75
	} else if ( PM < 210 ) {
		c = 0.786 * PM - 0.0862 * RH + 5.75
	} else if ( PM < 260 ) {
		c = 0.69*(PM/50 - 21/5) + 0.786*(1 - (PM/50 - 21/5))
		c = c*PM - 0.0862*RH * (1 - (PM/50 - 21/5))
		c = c + 2.966*(PM/50 - 21/5) + 5.75*(1 - (PM/50 - 21/5))
		c = c + 8.84*(10**(-4))*PM**2*(PM/50 - 21/5)
	} else {
		c = 2.966 + 0.69*x + 8.84*(10**(-4))*(PM**2)
	}
	
	return (c >= 0)?c:0
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
	Float c = 0.5 * PM - 0.66
	return (c >= 0)?c:0
}

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

void logDebug(String s) {
	if ( debugMode ) log.debug s
}
