/**
 *  SDFD Incident Notifier
 *
 *  Copyright 2020 Peter Miller
 *
 *  2021-02-07: Switched to asynhttpget. Added tracking of updated incidents
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
    	input "devNotify", "capability.notification", title: "Notification device", multiple: false, required: false
	}
	section("Debug") {
		input "debugMode", "bool", title: "Enable debug logging", defaultValue: false
	}

}

List<String> IGNORE_INC() { ["Medical", "Medical Alert Alarm", "Logistics", "Facilities", "Duty Mechanic", "Carbon Monoxide Alarm", "Move Up", "CAD Test", "Ringing Alarm", "Elevator Rescue", "Lock in/out", "DMS", "Special Service"] }
List<String> REDUNDANT_TYPES() { ["Traffic Accidents", "Single Resource", "Single Engine Response", "Advised Incident (misc.)", "Structure Commercial", "Traffic Accident Freeway (NC)", "Nat Gas SING ENG SDGE", "Vehicle vs. Structure"] }
List<String> AMBULANCE_UNITS() { ["M", "AM", "BLS", "Sdge"] }

//@Field static List<String> IGNORE_INC = ["Medical", "Medical Alert Alarm", "Logistics", "Facilities", "Duty Mechanic", "Carbon Monoxide Alarm", "Move Up", "CAD Test", "Ringing Alarm", "Elevator Rescue", "Lock in/out", "DMS", "Special Service"]
//@Field static List<String> REDUNDANT_TYPES = ["Traffic Accidents", "Single Resource", "Single Engine Response", "Advised Incident (misc.)", "Structure Commercial", "Traffic Accident Freeway (NC)", "Nat Gas SING ENG SDGE", "Vehicle vs. Structure"]
//@Field static List<String> AMBULANCE_UNITS = ["M", "AM", "BLS"]

	
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
			state.prevMasterIN = "AA00000000"
			state.activeIncidents = []
		} else {
			state.prevMasterIN = state.prevMasterIN ? state.prevMasterIN : "AA00000000"
		}

		// run every 5 minutes
		schedule('0 */5 * ? * *', incidentCheck)
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
	String newMaxMasterIN = ""

	if (resp.getStatus() != 200 ) {
		log.debug "HTTP error: " + resp.getStatus()
		return
	}

	allIncidents = filterIncidentType(resp.getJson(), IGNORE_INC())
	allIncidents = filterOnlyMedUnits(allIncidents, AMBULANCE_UNITS())
	fsIncidents = allIncidents.findAll { it.MasterIncidentNumber.substring(0, 2) == "FS" }
	otherIncidents = allIncidents.findAll { it.MasterIncidentNumber.substring(0, 2) != "FS" }
	activeIncidents = state.activeIncidents ? state.activeIncidents : []
	updatedActiveIncidents = []
	
	// Get and log updated incidents
	activeIncidents = removeResolvedIncidents(allIncidents, activeIncidents)
	updatedActiveIncidents = getUpdatedActiveIncidents(allIncidents, activeIncidents)
	// Update active incidents with new data
	updatedActiveIncidents.each { cur ->
		activeIncidents[activeIncidents.findIndexOf { it.MasterIncidentNumber == cur.MasterIncidentNumber }].putAll(cur)
	}
	
	if (updatedActiveIncidents != []) log.info "SDFD Updated Incidents:\n" + incidentsToStr(updatedActiveIncidents, "updated")
	
	fsIncidents = newIncidents(fsIncidents)
	if (fsIncidents != []) {
		newMaxMasterIN = fsIncidents*.MasterIncidentNumber.max()
		activeIncidents.addAll(getActiveList(fsIncidents))
		
		log.info "SDFD Incidents:\n" + incidentsToStr(fsIncidents + otherIncidents, "full")
		fsIncidents = localIncidents("E5", fsIncidents)
		
		if (fsIncidents != []) devNotify.deviceNotification incidentsToStr(fsIncidents, "min")
		
		if (newMaxMasterIN > state.prevMasterIN) {
			state.prevMasterIN = newMaxMasterIN
		}	
	}
	
	state.activeIncidents = activeIncidents
}

List<Map> filterIncidentType(List<Map> incidents, List<String> types) {	
	return incidents.findAll { inc -> types.every {type -> type != inc.CallType} }
}

List<Map> filterOnlyMedUnits(List<Map> incidents, List<String> medUnits) {	
	// TODO: Rewrite to use medUnits list
	
	return incidents.findAll { inc -> !inc.Units.every {it.Code =~ '^M[0-9]+$' || it.Code =~ '^AM[0-9]+$' || it.Code =~ '^BLS[0-9]+$' } }
}

boolean unitCalled(Map<String, List> incident, String unit) {
	return incident.Units.any { it.Code == unit }
}

List<Map> localIncidents(String localUnit, List<Map> incidents) {
	return incidents.findAll { unitCalled(it, localUnit) }
}

List<Map> newIncidents(List<Map> incidents) {	
	return incidents.findAll { it.MasterIncidentNumber.substring(2) > state.prevMasterIN.substring(2) }
}

List<Map> getActiveList(List<Map> incidents) {
	List<Map> activeInc = []
	
	incidents.each { inc ->
		activeInc << [MasterIncidentNumber: inc.MasterIncidentNumber, ResponseDate: inc.ResponseDate, CallType: inc.CallType, Address: inc.Address, CrossStreet: inc.CrossStreet, Units: inc.Units*.Code]
	}
	
	return activeInc
}

List<Map> getUpdatedActiveIncidents(List<Map> allIncidents, List<Map> activeIncidents) {
	List<Map> updatedInc = []
	Map prev = null
	
	allIncidents.each { cur ->
		prev = activeIncidents.find { it.MasterIncidentNumber == cur.MasterIncidentNumber }
		if (prev && (cur.CallType != prev.CallType || cur.Units*.Code != prev.Units)) {
			updatedInc << [MasterIncidentNumber: cur.MasterIncidentNumber, ResponseDate: cur.ResponseDate, CallType: cur.CallType, Address: cur.Address, CrossStreet: cur.CrossStreet, Units: cur.Units*.Code]
		}
	}
	//log.debug "Updated list: " + updatedInc
	return updatedInc
}

List<Map> removeResolvedIncidents(List<Map> allIncidents, List<Map> activeIncidents) {
	return activeIncidents.findAll { inc ->
		allIncidents.any { it.MasterIncidentNumber == inc.MasterIncidentNumber }
	}
}

String incidentToStr(Map<String, List> inc, String format) {
    List<String> listIgnoreTypes = REDUNDANT_TYPES()
	String out = ""
	String CrossStreet = inc.CrossStreet ? "|$inc.CrossStreet" : ""
    String IncidentType = inc.CallType == inc.IncidentTypeName || listIgnoreTypes.any { it == inc.IncidentTypeName } ? "-" : "[$inc.IncidentTypeName]"
	String incNum = debugMode ? " ($inc.MasterIncidentNumber)" : ""
	
	if (format == "full") {
		java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("HH:mm:ss");
		out = df.format(toDateTime(inc.ResponseDate)) + incNum + " $inc.CallType $IncidentType $inc.Address$CrossStreet:"
	} else if (format == "min") {
		out = "$inc.CallType - $inc.Address$CrossStreet:"
	} else if (format == "updated") {
		// Updated incidents list has a flattened units list, so just append and return
		out = "UPDATED" + incNum + " $inc.CallType - $inc.Address$CrossStreet:"
		inc.Units.each {
			out = out + " $it"
		}
		return out
	}
		
	inc.Units.each {
		out = out + " $it.Code"
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
