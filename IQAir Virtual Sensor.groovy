/**
 *  IQAir Virtual Sensor
 */
metadata {
	definition (name: "IQAir Virtual Sensor", namespace: "hyposphere.net", author: "Peter Miller") {
		capability "Sensor"
        capability "Polling"

		attribute "aqi", "number" // Combined AQI value (worst of either Ozone or PM2.5)
		//attribute "category", "string" // Description of current air quality
		command "refresh"
	}

	preferences {
		input "sourceURL", "text", title: "URL to collect air quality from", required: true, description: "https://www.iqair.com/us/usa/california/san-diego/new-hampshire-street"
	}
}

// Parse events into attributes. This will never be called but needs to be present in the DTH code.
def parse(String description) {
	log.debug("IQAir: Parsing '${description}'")
}

def installed() {
	schedule('0 10 * ? * *', AQICheck)
	AQICheck()
}

def updated() {
	AQICheck()
}

def uninstalled() {
	unschedule()
}

void AQICheck() {
	String url=sourceURL

    Map params = [
	    uri: url,
		requestContentType: "text/html",
		contentType: "text/html",
		timeout: 30,
		ignoreSSLIssues: true
	]
 
	if (debugMode) log.debug "params: $params"

	try {
		asynchttpGet('httpResponse', params, [data: null])
	} catch (SocketTimeoutException e) {
		log.error("Connection to IQAir timed out.")
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
	
	aqiValue = parseAQI(resp.getData())
	AQIcategory = getCategory(aqiValue)
	
	//log.info("AQI: $aqiValue")
	//sendEvent(name: "aqi", value: aqiValue, unit: "AQI", descriptionText: "${device.displayName} AQI level is ${aqiValue}")
	sendEvent(name: "aqi", value: aqiValue, unit: "AQI", descriptionText: "${AQIcategory}")
	//sendEvent(name: "category", value: AQIcategory, descriptionText: "${device.displayName} category is ${AQIcategory}")
}

Integer parseAQI(String pageSource) {
	String aqiString = ""

	/* Data to look for:
		class="aqi-value__value"> 24 <!----></p>
								  ^- AQI
	*/
		
	aqiString = pageSource.substring(pageSource.indexOf('class="aqi-value__value">') + 25)
	aqiString = aqiString.substring(0, aqiString.indexOf('<!---->'))
	
	return aqiString.toInteger()
}

String getCategory(Integer AQI) {
	if ( AQI >= 0 && AQI <= 50 ) {
		return "Good"
	} else if ( AQI >= 51 && AQI <= 100 ) {
		return "Moderate"
	} else if ( AQI >= 101 && AQI <= 150 ) {
		return "Unhealthy for sensitive groups"
	} else if ( AQI >= 151 && AQI <= 200 ) {
		return "Unhealthy"
	} else if ( AQI >= 201 && AQI <= 300 ) {
		return "Very unhealthy"
	} else if ( AQI >= 301 ) {
		return "Hazardous"
	} else {
		return "error"
	}
}

def refresh() {
	AQICheck()
}

def poll() {
	AQICheck()
}

def configure() {
	AQICheck()
}
