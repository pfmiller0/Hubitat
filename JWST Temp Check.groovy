/**
 *  JWST Temp Check
 *
 *  Copyright 2022 Peter Miller
 */

#include hyposphere.net.plib
import Math.*

definition(
    name: "JWST Temp Check",
    namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Utilities",
    author: "Peter Miller",
    description: "JWST Temp Check",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: ""
)

preferences {
	section() {
		input "isPaused", "bool", title: "Pause app", defaultValue: false
	}
	section("Current temps:") {
		paragraph '<table style="border:1px solid silver; border-collapse:collapse; width:100%;">' + state.lastTemp + "</table>"
	}
	section("Settings:") {
		input "sourceURL", "text", title: "Source URL", required: true, description: "URL to JSON file", defaultValue: "https://jwst.nasa.gov/content/webbLaunch/flightCurrentState2.0.json"
		input "notifyDevice", "capability.notification", title: "Notification device", multiple: false, required: false
		input "update_interval", "enum", title: "Update interval", required: true, description: "Minutes between updates", options: ["15", "30", "60", "180"], defaultValue: "60"
	}
}

void initialize() {
	if (isPaused == false) {
		resetAppLabel()
		
		state.lastTemp = state.lastTemp ?: [tempWarmSide1: 0, tempWarmSide2: 0, tempCoolSide1: 0, tempCoolSide2: 0, tempInstMiriK: 0, tempInstNirCamK: 0, tempInstNirSpecK: 0, tempInstFgsNirissK: 0, tempInstFsmK: 0]
		state.jsonFieldCount = state.jsonFieldCount ?: 0
		
		if ( update_interval == "15" ) {
			runEvery15Minutes('JWSTCheck')
		} else if ( update_interval == "30" ) {
			runEvery30Minutes('JWSTCheck')
		} else if ( update_interval == "60" ) {
			runEvery1Hour('JWSTCheck')
		} else if ( update_interval == "180" ) {
			runEvery3Hours('JWSTCheck')
		} else {
			runEvery1Hour('JWSTCheck')
		}
	} else {
		addAppLabel("Paused", "red")
	}
}

def installed() {
	initialize()
}

def updated() {
	initialize()
	JWSTCheck()
}

def uninstalled() {
	unschedule()
}

void JWSTCheck() {
	String url=sourceURL

    Map params = [
	    uri: url,
		requestContentType: "application/json",
		contentType: "application/json",
		timeout: 30,
		ignoreSSLIssues: true
	]
 
	if (debugMode) log.debug "params: $params"

	try {
		asynchttpGet('httpResponse', params, [data: null])
	} catch (SocketTimeoutException e) {
		log.error("Connection to NASA timed out.")
	} catch (e) {
		log.error("There was an error: $e")
	}
}

void httpResponse(hubitat.scheduling.AsyncResponse resp, Map data) {
	Map JWSTData
	Map JWSTTemp = [:]
	Integer fieldCount
	
	if (resp.getStatus() != 200 ) {
		log.debug "HTTP error: " + resp.getStatus()
		return
	}
	
	JWSTData = resp.getJson().currentState
	fieldCount = JWSTData.size()
	if (state.jsonFieldCount != fieldCount ) {
		notifyDevice.deviceNotification '"Where\'s Webb?" JSON file updated!'
		state.jsonFieldCount = fieldCount
	}
	
	JWSTTemp = [tempWarmSide1: CtoK(JWSTData.tempWarmSide1C), tempWarmSide2: CtoK(JWSTData.tempWarmSide2C), tempCoolSide1: CtoK(JWSTData.tempCoolSide1C), tempCoolSide2: CtoK(JWSTData.tempCoolSide2C), tempInstMiriK: Math.round(JWSTData.tempInstMiriK), tempInstNirCamK: Math.round(JWSTData.tempInstNirCamK),  tempInstNirSpecK: Math.round(JWSTData.tempInstNirSpecK), tempInstFgsNirissK: Math.round(JWSTData.tempInstFgsNirissK), tempInstFsmK: Math.round(JWSTData.tempInstFsmK)]
	
	if (state.lastTemp != JWSTTemp ) {
		Map JWSTTempChanges = [:]
	
		//log.debug JWSTTemp
		JWSTTemp.each { JWSTTempChanges[it.key] = it.value == state.lastTemp[it.key] ? "🚫" : it.value }

		//notifyDevice.deviceNotification "JWST Temp updated: $JWSTTemp"
		notifyDevice.deviceNotification "JWST Temp updated: $JWSTTempChanges"
		
		JWSTTempChanges.each { key, val ->
			if (val != "🚫") {
				sendEvent(name: key, value: val, unit: "K")
			}
		}
		
		state.lastTemp = JWSTTemp
	}
}

Integer CtoK(Float C) {
	return Math.round(C + 273.15)
}
