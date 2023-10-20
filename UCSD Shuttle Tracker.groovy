/**
 *  UCSD Shuttle Checker
 *
 *  Copyright 2023 Peter Miller
 */

#include hyposphere.net.plib

definition(
    name: "UCSD Shuttle Checker",
    namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Utilities",
    author: "Peter Miller",
    description: "UCSD Shuttle Checker",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: ""
)

preferences {
	page(name: "mainPage", uninstall: "false") {
		section() {
			input "isPaused", "bool", title: "Pause app", defaultValue: false
		
			java.text.SimpleDateFormat df
			if (state.curNotice) {
				df = new java.text.SimpleDateFormat("EEEE, h:mm aa")
				section("<b>Current status</b>") {
					paragraph '<table style="border:1px solid silver; border-collapse:collapse; width:100%;">' + "${state.curNotice}" + "</table>"
				}
			}
		}
		section("<b>Settings</b>") {
			//input "sourceURL", "text", title: "Source URL", required: true, description: "URL to JSON file", defaultValue: "https://www.seaworld.com/api/sitecore/Marquee/LoadDayData?itemId=a6ac339a-375c-461c-9540-faebba25b60c"
			input "notifyDevice", "capability.notification", title: "Notification device", multiple: false, required: false, width: 3
			input 'SpeechDevice', 'capability.speechSynthesis', title: "Notification speaker", width: 3
		}
		section("<b>Shuttle info</b>") {
			href "myHref", page: "shuttleInfoPage", title: "See shuttle info", width: 3, params: [myKey: "My value"]
			href "myHref", page: "shuttleStatusPage", title: "See shuttle status", width: 3, params: [myKey: "My value"]
        }
		section("<b>Test</b>") {
			input name: "btnLeft", type: "button", title: "Watch for leaving", width: 12
		}
	}
	page(name: "shuttleInfoPage", title: "Shuttle Info", nextPage: "mainPage", uninstall: false)
	page(name: "shuttleStatusPage", title: "Shuttle Status", nextPage: "mainPage", uninstall: false)
}

def shuttleInfoPage(params) {
	dynamicPage(name: "shuttleInfoPage", title: "Shuttle Info") {
		section() {
			paragraph "<textarea readonly=true cols=80 rows=10>${queryShuttleInfo()}</textarea>"
		}
	}
}

def shuttleStatusPage(params) {
	dynamicPage(name: "shuttleStatusPage", title: "Shuttle Status") {
		section() {
			paragraph "<textarea readonly=true cols=80 rows=10>${queryShuttleStatus()?:"No notices"}</textarea>"
		}
	}
}

void initialize() {
	if (isPaused == false) {
		resetAppLabel()
		
		//state.jsonFieldCount = state.jsonFieldCount ?: 0
		unschedule()
		//   Sec Min Hr Date Month Day Yr
		schedule('0 40 6,16 ? * 3-5 *', "notifyStatus")
		schedule('0 5 7-17 ? * 3-5 *', "notifyOnDeparture")
	} else {
		addAppLabel("Paused", "red")
	}
}

def installed() {
	initialize()
}

def updated() {
	initialize()
	queryShuttleTime()
}

def uninstalled() {
	unschedule()
}

Boolean isHoliday(Date d) {
	Integer YEAR = d[Calendar.YEAR]
	String MONTH = sprintf('%02d', d[Calendar.MONTH]+1)
	String DAY = sprintf('%02d', d[Calendar.DAY_OF_MONTH])
	
	if (YEAR > 2024) {
		if (d[Calendar.HOUR_OF_DAY] == 20) {
			log.warn "isHoliday: Holiday calendar for $YEAR missing"
		}
	}
	
	switch("$MONTH-$DAY") {
		case "01-01": // New Year
		case "03-31": // Cesar Chavez Day
		case "06-19": // Juneteenth
		case "07-04": // July 4th
		case "11-11": // Veterans Day
		case "12-25": // Christmas
			return true
			break
	}	
	switch("$YEAR-$MONTH-$DAY") {
		case "2022-01-17": // Martin Luther King Jr. Day
		case "2022-02-21": // Presidents’ Day
		case "2022-05-30": // Memorial Day
		case "2022-09-05": // Labor Day
		case "2022-11-24": // Thanksgiving Day
		case "2022-11-25": // Day after Thanksgiving
		case "2022-12-26": // Christmas Day (Observed)
		
		case "2023-01-16": // Martin Luther King Jr. Day
		case "2023-02-20": // Presidents’ Day
		case "2023-05-29": // Memorial Day
		case "2023-09-04": // Labor Day
		case "2023-11-23": // Thanksgiving Day
		case "2023-11-24": // Day after Thanksgiving
		
		case "2024-01-15": // Martin Luther King Jr. Day
		case "2024-02-19": // Presidents’ Day
		case "2024-05-27": // Memorial Day
		case "2024-09-02": // Labor Day
		case "2024-11-28": // Thanksgiving Day
		case "2024-11-29": // Day after Thanksgiving
			return true
			break
	}	
	return false
}

void appButtonHandler(String btn) {
	switch (btn) {
		case "btnLeft":
			notifyOnDeparture()
			break
		default:
			log.warn "Unhandled button press: $btn"
	}
}

Date parseMSDate(String timestamp) {
	String ts = timestamp.substring(6,timestamp.length()-2)
	return new Date(Long.valueOf(ts))
}

String queryShuttleStatus() {
	String url = "https://tritontransit.ridesystems.net/Services/JSONPRelay.svc/GetTwitterJSON"
	String messages = ""

	if ( isHoliday(new Date()) ) {
		log.debug "skipping for holiday"
		return
	}
		
	Map params = [
		uri: url,
		requestContentType: "application/json",
		contentType: "application/json",
		timeout: 10,
		ignoreSSLIssues: true
	]
 
	if (debugMode) log.debug "params: $params"

	try {
		httpGet(params) { resp ->
			Boolean debugMode = true
			List<Map> NoticeData
			//String tzOffset = sprintf("%03d:00", (location.timeZone.getOffset(now())/(1000 * 60 * 60)).intValue())

			String respMimetype = ''
			if (resp.getHeaders() && resp.getHeaders()["Content-Type"]) {
				respMimetype = resp.getHeaders()["Content-Type"].toString().split(";")[0]
			}
			
			if (resp.getStatus() != 200 || respMimetype != "application/json" ) {
				if (respMimetype != "application/json" ) {
					log.debug "Response type '${respMimetype}', JSON expected"
					return
				}
				log.error "HTTP error: " + resp.getStatus()
				return
			}
	
			if (resp.getJson() != []) {
				log.error "All notices: ${resp.getJson()}"
			}
	
			NoticeData = resp.getData().findAll { it.text =~ "(?i)Hillcrest" }
			if (NoticeData != []) {
				if (debugMode) log.debug "Notices found: ${NoticeData}"
				NoticeData.each { messages += it.text }
			} else {
				log.error "HTTP error: " + resp.getStatus()
				return
			}
		}
	} catch (SocketTimeoutException e) {
		log.error("Connection to TritonTransit timed out.")
	} catch (Exception e) {
		log.error("There was an error: $e")
	}
	
	return messages
}

/******
Sample result:
[
    {
        "created_at":"Tue May 30 22:44:57 -00:00 2023",
        "end_date":"Wed May 31 00:00:00 -00:00 2023",
        "favorited":false,
        "id":308,
        "id_str":null,
        "isAlert":true,
        "isRideSystemsMessage":true,
        "lang":null,
        "retweeted":false,
        "source":null,
        "text":"Delayed Hillcrest route delays : Due to reports of a possible traffic accident on Washington and India St. , Hillcrest will be delayed. We apologize for any inconvenience. ",
        "truncated":false
    }
]
*******/

String queryShuttleInfo() {
	String url = "https://tritontransit.ridesystems.net/Services/JSONPRelay.svc/GetStopArrivalTimes?apiKey=8882812681&routeIds=17&version=2"
	String shuttleInfo = ""

	if ( isHoliday(new Date()) ) {
		log.debug "skipping for holiday"
		return
	}

	Map params = [
		uri: url,
		requestContentType: "application/json",
		contentType: "application/json",
		timeout: 10,
		ignoreSSLIssues: true
	]
 
	if (debugMode) log.debug "params: $params"

	try {
		httpGet(params){ resp ->
			//String tzOffset = sprintf("%03d:00", (location.timeZone.getOffset(now())/(1000 * 60 * 60)).intValue())
	
			String respMimetype = ''
 			if (resp.getHeaders() && resp.getHeaders()["Content-Type"]) {
 				respMimetype = resp.getHeaders()["Content-Type"].toString().split(";")[0]
			}
	
			if (resp.getStatus() != 200) { //|| respMimetype != "application/json" ) {
    		    if (respMimetype != "application/json" ) {
					log.error "Response type '${respMimetype}', JSON expected"
				}
				log.error "HTTP error: " + resp.getStatus()
				return
			}
			
			Map StopData = resp.getData().find { it.RouteStopId == 202 }

			if (StopData && StopData.Times) {
				//log.debug "Hillcrest stop info: ${StopData}"
				shuttleInfo += "Active shuttles: ${StopData.Times.size()}\n\n"
				StopData.Times.each { bus ->
					Integer id = bus.VehicleId
					//log.debug "(bus ${id}) Hillcrest times: ${bus}"
					shuttleInfo += "(bus ${id}) Hillcrest arriving?: ${bus.IsArriving}\n"
					shuttleInfo += "(bus ${id}) Hillcrest arrival time: ${parseMSDate(bus.EstimateTime)}\n"
					shuttleInfo += "(bus ${id}) Hillcrest seconds: ${bus.Seconds}\n"
					//notifyDevice.deviceNotification "Hillcrest shuttle notice: ${messages}"
				}
			} else {
				log.error "No Hillcrest stop times found"
				shuttleInfo += "No Hillcrest stop times found"
			}
		}
	} catch (SocketTimeoutException e) {
		log.error("Connection to TritonTransit timed out.")
	} catch (Exception e) {
		log.error("There was an error: $e")
	}
	
	return shuttleInfo
}

/******
*** Trolley arrival time data:
https://tritontransit.ridesystems.net/Services/JSONPRelay.svc/GetStopArrivalTimes?apiKey=8882812681&routeIds=17&version=2
StopID: 202
*** Sample:
[
  {
    "Color": "#ee230c",
    "RouteDescription": "Hillcrest Express",
    "RouteId": 17,
    "RouteStopId": 202,
    "ShowDefaultedOnMap": true,
    "ShowEstimatesOnMap": true,
    "StopDescription": "Hillcrest Medical Center (202)",
    "StopId": 69,
    "Times": [
      {
        "EstimateTime": "/Date(1686849835296)/",
        "IsArriving": false,
        "IsDeparted": false,
        "OnTimeStatus": 0,
        "ScheduledArrivalTime": null,
        "ScheduledDepartureTime": null,
        "ScheduledTime": null,
        "Seconds": 752,
        "Text": "",
        "Time": "/Date(1686849835296)/",
        "VehicleId": 7
      },
      {
        "EstimateTime": "/Date(1686850662296)/",
        "IsArriving": false,
        "IsDeparted": false,
        "OnTimeStatus": 0,
        "ScheduledArrivalTime": null,
        "ScheduledDepartureTime": null,
        "ScheduledTime": null,
        "Seconds": 1579,
        "Text": "",
        "Time": "/Date(1686850662296)/",
        "VehicleId": 12
      }
    ]
  },
]
*******/

Integer getNextHillcrestShuttle() {
	String url = "https://tritontransit.ridesystems.net/Services/JSONPRelay.svc/GetStopArrivalTimes?apiKey=8882812681&routeIds=17&version=2"

	if ( isHoliday(new Date()) ) {
		log.debug "skipping for holiday"
		return
	}

	Map params = [
		uri: url,
		requestContentType: "application/json",
		contentType: "application/json",
		timeout: 10,
		ignoreSSLIssues: true
	]
 
	if (debugMode) log.debug "params: $params"

	Map nearestShuttle = [:]
	try {
		httpGet(params) { resp ->	
			String respMimetype = ''
 			if (resp.getHeaders() && resp.getHeaders()["Content-Type"]) {
  			  	respMimetype = resp.getHeaders()["Content-Type"].toString().split(";")[0]
			}
	
			if (resp.getStatus() != 200) { //|| respMimetype != "application/json" ) {
    		    if (respMimetype != "application/json" ) {
					log.error "Response type '${respMimetype}', JSON expected"
				}
				log.error "HTTP error: " + resp.getStatus()
				return
			}

			Map StopData = resp.getData().find { it.RouteStopId == 202 }
			if (StopData && StopData.Times) {
				StopData.Times.each { bus ->
					if (! nearestShuttle) {
						nearestShuttle = [id: bus.VehicleId, arrivalTime: parseMSDate(bus.EstimateTime)]
					} else {
						if (parseMSDate(bus.EstimateTime) < nearestShuttle["arrivalTime"]) {
							nearestShuttle = [id: bus.VehicleId, arrivalTime: parseMSDate(bus.EstimateTime)]
						}
					}
				}
			} else {
				log.error "No Hillcrest stop times found"
			}
		}
	} catch (SocketTimeoutException e) {
		log.error("Connection to TritonTransit timed out.")
	} catch (Exception e) {
		log.error("There was an error: $e")
	}

	return nearestShuttle["id"]
}

Date getHillcrestArrivalTimeById(Integer shuttleId) {
	String url = "https://tritontransit.ridesystems.net/Services/JSONPRelay.svc/GetStopArrivalTimes?apiKey=8882812681&routeIds=17&version=2"

	if ( isHoliday(new Date()) ) {
		log.debug "skipping for holiday"
		return
	} else if (! shuttleId) {
		log.error "No shuttle id"
		return
	}

	Map params = [
		uri: url,
		requestContentType: "application/json",
		contentType: "application/json",
		timeout: 10,
		ignoreSSLIssues: true
	]
 
	if (debugMode) log.debug "params: $params"

	try {
		httpGet(params) { resp ->	
			String respMimetype = ''
 			if (resp.getHeaders() && resp.getHeaders()["Content-Type"]) {
  			  	respMimetype = resp.getHeaders()["Content-Type"].toString().split(";")[0]
			}
	
			if (resp.getStatus() != 200) { //|| respMimetype != "application/json" ) {
    		    if (respMimetype != "application/json" ) {
					log.error "Response type '${respMimetype}', JSON expected"
				}
				log.error "HTTP error: " + resp.getStatus()
				return
			}

			Map StopData = resp.getData().find { it.RouteStopId == 202 }
			if (StopData && StopData.Times) {
				return parseMSDate(StopData.Times.find { it.VehicleId == shuttleId }["EstimateTime"])
			} else {
				log.error "No Hillcrest stop times found"
			}
		}
	} catch (SocketTimeoutException e) {
		log.error("Connection to TritonTransit timed out.")
	} catch (Exception e) {
		log.error("There was an error: $e")
	}
}

Boolean getHillcrestIsArrivingById(Integer shuttleId) {
	String url = "https://tritontransit.ridesystems.net/Services/JSONPRelay.svc/GetStopArrivalTimes?apiKey=8882812681&routeIds=17&version=2"

	if ( isHoliday(new Date()) ) {
		log.debug "skipping for holiday"
		return
	} else if (! shuttleId) {
		log.error "No shuttle id"
		return
	}

	Map params = [
		uri: url,
		requestContentType: "application/json",
		contentType: "application/json",
		timeout: 10,
		ignoreSSLIssues: true
	]
 
	if (debugMode) log.debug "params: $params"

	try {
		httpGet(params) { resp ->	
			String respMimetype = ''
 			if (resp.getHeaders() && resp.getHeaders()["Content-Type"]) {
  			  	respMimetype = resp.getHeaders()["Content-Type"].toString().split(";")[0]
			}
	
			if (resp.getStatus() != 200) { //|| respMimetype != "application/json" ) {
    		    if (respMimetype != "application/json" ) {
					log.error "Response type '${respMimetype}', JSON expected"
				}
				log.error "HTTP error: " + resp.getStatus()
				return
			}

			Map StopData = resp.getData().find { it.RouteStopId == 202 }
			if (StopData && StopData.Times) {
				return StopData.Times.find { it.VehicleId == shuttleId }["IsArriving"]
			} else {
				log.error "No Hillcrest stop times found"
			}
		}
	} catch (SocketTimeoutException e) {
		log.error("Connection to TritonTransit timed out.")
	} catch (Exception e) {
		log.error("There was an error: $e")
	}
}

void notifyStatus() {
	String status = queryShuttleStatus
	
	if ( status ) {
		status = "Hillcrest shuttle notice: ${status}"
		notifyDevice.deviceNotification status
		log.debug status
		//SpeechDevice.speak(status)
		state.curNotice = status
	} else {
		state.curNotice = "No delays"
	}
}

void notifyOnDeparture() {
	Boolean debug = true
	
	if ( isHoliday(new Date()) ) {
		return
	}
	
	Long timeout = now() + 15 * 60 * 1000
	
	Integer bus = getNextHillcrestShuttle()
	if (! bus) {
		log.error "notifyOnDeparture: No shuttle found!"
		return
	}
	Boolean isArriving = getHillcrestIsArrivingById(bus)

	while (! isArriving) {
		pauseExecution(30 * 1000)
		if (debug) log.debug "check arriving"
		isArriving = getHillcrestIsArrivingById(bus)
		if ( isArriving == null ) {
			log.error "notifyOnDeparture: Shuttle status not found"
			return
		} else if ( now() > timeout ) {
			log.error "notifyOnDeparture: timed out"
			return
		}
	}
	if (debug) log.debug "arriving"
	notifyDevice.deviceNotification "Shuttle arriving!"
	while (isArriving) {
		pauseExecution(5 * 1000)
		if (debug) log.debug "check leaving"
		isArriving = getHillcrestIsArrivingById(bus)
		if ( isArriving == null ) {
			log.error "notifyOnDeparture: Shuttle status not found"
			return
		} else if ( now() > timeout ) {
			log.error "notifyOnDeparture: timed out"
			return
		}
	}
	if (debug) log.debug "leaving"
	notifyDevice.deviceNotification "Shuttle is leaving!"
}


















