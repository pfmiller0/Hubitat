/**
 *  SeaWorld Fireworks
 *
 *  Copyright 2022 Peter Miller
 */

import Math.*

definition(
    name: "SeaWorld Fireworks",
    namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Utilities",
    author: "Peter Miller",
    description: "SeaWorld Fireworks notifier",
    category: "My Apps",
	menu: "Integrations",
    iconUrl: "",
    iconX2Url: "",
    importUrl: ""
)

preferences {
	java.text.SimpleDateFormat df
	
	section("<b>Today's show</b>") {
		if (state.todayShow) {
			df = new java.text.SimpleDateFormat("EEEE, h:mm aa")
			paragraph '<table style="border:1px solid silver; border-collapse:collapse; width:100%;">' + "${state.todayShowType} @ ${df.format(toDateTime(state.todayShow))}" + "</table>"
		} else {
			paragraph 'none found'
		}
	}
	section("<b>Last show</b>") {
		if (state.prevShow) {
			df = new java.text.SimpleDateFormat("EEEE, d MMM yyyy h:mm aa");
			paragraph '<table style="border:1px solid silver; border-collapse:collapse; width:100%;">' + "${state.prevShowType} @ ${df.format(toDateTime(state.prevShow))}" + "</table>"
		} else {
			paragraph 'none found'
		}
	}
	section("<b>Settings</b>") {
		//input "sourceURL", "text", title: "Source URL", required: true, description: "URL to JSON file", defaultValue: "https://www.seaworld.com/api/sitecore/Marquee/LoadDayData?itemId=a6ac339a-375c-461c-9540-faebba25b60c"
		input "notifyDevice", "capability.notification", title: "Notification device", multiple: false, required: false
	}
}

void initialize() {
	state.jsonFieldCount = state.jsonFieldCount ?: 0
	state.failCount = state.failCount ?: 0
	schedule('0 9 0 ? * *', "SiteCheck")
}

def installed() {
	resetAppLabel()
	unschedule("SiteCheck")
	initialize()
}

def updated() {
	initialize()
	SiteCheck()
}

def uninstalled() {
	unschedule()
}

void SiteCheck() {
	String baseURL = "https://seaworld.com/api/sitecore/CalendarEvents/LoadCalendarDayData?itemId=214cee71-fc4a-493b-aaee-18f19f6037f7&sc_site=seaworld-san-diego&sc_itemid=5a724094-08cf-4dd7-acf0-3c89493f724d"

	java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("YYYY-MM-dd");
	
	String url = baseURL + "&date=" + df.format(timeToday("12:00"))
	
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
		log.error("Connection to SeaWorld timed out.")
	} catch (Exception e) {
		log.error("There was an error: $e")
	}
}

void httpResponse(hubitat.scheduling.AsyncResponse resp, Map data) {
    String respMimetype = ''
	
    if (resp.getHeaders() && resp.getHeaders()["Content-Type"]) {
    	respMimetype = resp.getHeaders()['Content-Type']?.toString()?.split(';')[0]?.split(' ')[1]
	}

	if (resp.getStatus() != 200 || respMimetype != "application/json") {
		if (respMimetype != "application/json" ) {
			log.error "Response type '${respMimetype}', JSON expected"
		}
		log.error "HTTP error: " + resp.getStatus()
		state.failCount++
		if (state.failCount == 2) {
			notifyDevice.deviceNotification 'SeaWorld schedule down!'
		}
		return
	} else {
		if (state.failCount > 0) {
			state.failCount = 0
			if (state.failCount > 1) {
				notifyDevice.deviceNotification 'SeaWorld schedule restored'
			}
		}
	}
	
	List<Map> EventsData
	Date showTime
	String showType
	Integer fieldCount
	String tzOffset = sprintf("%03d:00", (location.timeZone.getOffset(now())/(1000 * 60 * 60)).intValue())

	EventsData = resp.getJson().events
	
	EventsData.each { if (it.title.toLowerCase().indexOf("firework") >= 0) { showTime = toDateTime(it.start + tzOffset); showType = "fireworks"} }
	if (! showTime) {
		EventsData.each { if (it.title.toLowerCase().indexOf("drone") >= 0) { showTime = toDateTime(it.start + tzOffset); showType = "drone show"} }
	}
	if (showTime) {
		if (showTime != state.todayShow) {
			state.prevShow = state.todayShow
			state.prevShowType = state.todayShowType
			state.todayShow = showTime
			state.todayShowType = showType
			//log.debug "todayShow set: ${state.todayShow}"
			//log.debug "prevShow set: ${state.prevShow}"
		
			/***/
			Calendar c = Calendar.getInstance()
			c.setTime(showTime)
			c.add(Calendar.MINUTE, -60)
			runOnce(c.getTime(), "sendNotice")
			/***
			showTime[Calendar.MINUTE] = showTime[Calendar.MINUTE] - 60
			runOnce(showTime, "sendNotice")
			/***/
		
			//log.info "Notify at ${c.getTime()}"
		}
	} else {
		state.todayShow = ""
		//log.info "No shows"
	}
	
	fieldCount = resp.getJson().size()
	if (state.jsonFieldCount != fieldCount ) {
		notifyDevice.deviceNotification 'SeaWorld events JSON file updated!'
		state.jsonFieldCount = fieldCount
	}
}

void sendNotice() {
	java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("hh:mm");
	
	String showTime = df.format(toDateTime(state.todayShow))
    
	notifyDevice.deviceNotification "SeaWorld ${state.todayShowType} at ${showTime}"
}
