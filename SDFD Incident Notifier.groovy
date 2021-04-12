/**
 *  SDFD Incident Notifier
 *
 *  Copyright 2020 Peter Miller
 *
 * 2021-04-11: Switched logging to event log. Cleaned up incident list and variable names.
 * 2021-02-07: Switched to asynhttpget. Added tracking of updated incidents
 */
definition(
    name: "SDFD Incident Notifier",
    namespace: "hyposphere.net",
    author: "Peter Miller",
    description: "Retrieve SDFD incidents, and provide notification for local incidents.",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
	section() {
		input "isPaused", "bool", title: "Pause app", defaultValue: false
	}
	section("Settings") {
		input "updateTime", "number", title: "Update frequency (mins)", defaultValue: 5
		input "devNotify", "capability.notification", title: "Notification device", multiple: false, required: false
	}
	section("Debug") {
		input "debugMode", "bool", title: "Enable debug logging", defaultValue: false
	}

}

List<String> IGNORE_INC() { ["Medical", "Medical Alert Alarm", "Logistics", "Facilities", "Duty Mechanic", "Carbon Monoxide Alarm", "Move Up", "CAD Test", "Ringing Alarm", "Elevator Rescue", "Lock in/out", "DMS", "Special Service"] }
List<String> REDUNDANT_TYPES() { ["Traffic Accidents", "Single Resource", "Single Engine Response", "Advised Incident (misc.)", "Structure Commercial", "Traffic Accident Freeway (NC)", "Nat Gas SING ENG SDGE", "Vehicle vs. Structure"] }
//List<String> NO_NOTIFICATION_TYPES() { ["Vehicle fire freeway", "Ringing alarm highrise", "Traffic Accident FWY", "Extinguished fire", "Page"] }
List<String> AMBULANCE_UNITS() { ["M", "AM", "BLS", "Sdge"] }
	
void installed() {
	if (debugMode) log.debug "Installed with settings: ${settings}"

	initialize()
	incidentCheck()
}

void updated() {
	if (debugMode) log.debug "Updated with settings: ${settings}"

	unsubscribe()
	unschedule()
	initialize()
	incidentCheck()
}

void initialize() {
	if (isPaused == false) {
		if (debugMode) {
			state.prevIncNum = "AA00000000"
			state.activeIncidents = []
		} else {
			state.prevIncNum = state.prevIncNum ? state.prevIncNum : "AA00000000"
		}

		schedule('0 */' + updateTime + ' * ? * *', incidentCheck)
	}
}

def uninstalled() {
	unsubscribe()
	unschedule()
}

void incidentCheck() {
	String url="https://webapps.sandiego.gov/SDFireDispatch/api/v1/Incidents?_=" + now()

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
	} catch (e) {
		if (debugMode) log.debug "There was an error: $e"	
	}
}

void httpResponse(hubitat.scheduling.AsyncResponse resp, Map data) {
	List<Map> allIncidents = []
	List<Map> fsIncidents = []
	List<Map> otherIncidents = []
	List<Map> activeIncidents = []
	List<Map> updatedActiveIncidents = []
	String newMaxIncNum = ""

	if (resp.getStatus() != 200 ) {
		log.debug "HTTP error: " + resp.getStatus()
		return
	}

	allIncidents = filterIncidentType(cleanupList(resp.getJson()), IGNORE_INC())
	allIncidents = filterOnlyMedUnits(allIncidents, AMBULANCE_UNITS())
	fsIncidents = allIncidents.findAll { it.IncidentNumber.substring(0, 2) == "FS" }
	otherIncidents = allIncidents.findAll { it.IncidentNumber.substring(0, 2) != "FS" }
	activeIncidents = state.activeIncidents ? state.activeIncidents : []
	updatedActiveIncidents = []
	
	// Get and log updated incidents
	activeIncidents = removeResolvedIncidents(allIncidents, activeIncidents)
	updatedActiveIncidents = getUpdatedActiveIncidents(allIncidents, activeIncidents)
	// Update active incidents with new data
	updatedActiveIncidents.each { cur ->
		activeIncidents[activeIncidents.findIndexOf { it.IncidentNumber == cur.IncidentNumber }].putAll(cur)
	}
	
	if (updatedActiveIncidents != []) logIncidents(updatedActiveIncidents, true)
	
	fsIncidents = newIncidents(fsIncidents)
	if (fsIncidents != []) {
		newMaxIncNum = fsIncidents*.IncidentNumber.max()
		activeIncidents.addAll(fsIncidents)
		
		logIncidents(fsIncidents + otherIncidents, false)
		fsIncidents = localIncidents("E5", fsIncidents)
		
		if (fsIncidents != []) devNotify.deviceNotification incidentsToStr(fsIncidents, "min")
		
		if (newMaxIncNum > state.prevIncNum) {
			state.prevIncNum = newMaxIncNum
		}	
	}
	
	state.activeIncidents = activeIncidents
}

List<Map> filterIncidentType(List<Map> incidents, List<String> types) {	
	return incidents.findAll { inc -> types.every {type -> type != inc.CallType} }
}

List<Map> filterOnlyMedUnits(List<Map> incidents, List<String> medUnits) {	
	// TODO: Rewrite to use medUnits list
	
	return incidents.findAll { inc -> !inc.Units.every {it =~ '^M[0-9]+$' || it =~ '^AM[0-9]+$' || it =~ '^BLS[0-9]+$' } }
}

boolean unitCalled(Map<String, List> incident, String unit) {
	return incident.Units.any { it == unit }
}

List<Map> localIncidents(String localUnit, List<Map> incidents) {
	return incidents.findAll { unitCalled(it, localUnit) }
}

List<Map> newIncidents(List<Map> incidents) {	
	return incidents.findAll { it.IncidentNumber.substring(2) > state.prevIncNum.substring(2) }
}

List<Map> cleanupList(List<Map> incidents) {
	List<Map> cleanInc = []
	
	incidents.each { inc ->
		cleanInc << [IncidentNumber: inc.MasterIncidentNumber, ResponseDate: inc.ResponseDate, CallType: inc.CallType, IncidentTypeName: inc.IncidentTypeName, Address: inc.Address, CrossStreet: inc.CrossStreet, Units: inc.Units*.Code]
	}
	
	return cleanInc
}


List<Map> getUpdatedActiveIncidents(List<Map> allIncidents, List<Map> activeIncidents) {
	List<Map> updatedInc = []
	Map prev = null
	
	allIncidents.each { cur ->
		prev = activeIncidents.find { it.IncidentNumber == cur.IncidentNumber }
		if (prev && (cur.CallType != prev.CallType || cur.Units != prev.Units)) {
			//updatedInc << [IncidentNumber: cur.MasterIncidentNumber, ResponseDate: cur.ResponseDate, CallType: cur.CallType, IncidentTypeName: cur.IncidentTypeName, Address: cur.Address, CrossStreet: cur.CrossStreet, Units: cur.Units]
			updatedInc << cur
		}
	}
	//log.debug "Updated list: " + updatedInc
	return updatedInc
}


List<Map> removeResolvedIncidents(List<Map> allIncidents, List<Map> activeIncidents) {
	return activeIncidents.findAll { inc ->
		allIncidents.any { it.IncidentNumber == inc.IncidentNumber }
	}
}

void logIncidents(List<Map> incidents, boolean isUpdated) {
	List<String> listIgnoreTypes = REDUNDANT_TYPES()
	java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("HH:mm:ss");
	String IncidentType = ""
	String CrossStreet = ""
	String incDesc = ""
	String incTime = ""
	
	incidents.each { inc ->
		IncidentType = inc.CallType == inc.IncidentTypeName || listIgnoreTypes.any { it == inc.IncidentTypeName } ? "" : " [$inc.IncidentTypeName]"
		CrossStreet = inc.CrossStreet ? "|$inc.CrossStreet" : ""
		if (isUpdated) {
			incTime = "UPDATED"
		} else {
			incTime = df.format(toDateTime(inc.ResponseDate))
		}
		
		incDesc = "${inc.Address}${CrossStreet}:"
		inc.Units.each {
			incDesc = incDesc + " $it"
		}

		sendEvent(name: "${inc.CallType}${IncidentType}", value: "$inc.IncidentNumber ($incTime)", descriptionText: incDesc) 
	}
}

/*
String incidentToStr(Map<String, List> inc, String format) {
    List<String> listIgnoreTypes = REDUNDANT_TYPES()
	String out = ""
	String CrossStreet = inc.CrossStreet ? "|$inc.CrossStreet" : ""
	String IncidentType = inc.CallType == inc.IncidentTypeName || listIgnoreTypes.any { it == inc.IncidentTypeName } ? "-" : "[$inc.IncidentTypeName]"
	String incNum = debugMode ? " ($inc.IncidentNumber)" : ""
	
	if (format == "full") {
		java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("HH:mm:ss");
		out = df.format(toDateTime(inc.ResponseDate)) + incNum + " $inc.CallType $IncidentType $inc.Address$CrossStreet:"
	} else if (format == "min") {
		out = "$inc.CallType - $inc.Address$CrossStreet:"
	} else if (format == "updated") {
		out = "UPDATED" + incNum + " $inc.CallType - $inc.Address$CrossStreet:"
	}
		
	inc.Units.each {
		out = out + " $it"
	}
	
	return out
}

String incidentsToStr(List<Map> incidents, String format) {
	String out = ""
	
	incidents.each {
		out = out + incidentToStr(it, format) + "\n"
	}
	
	return out
}
*/
