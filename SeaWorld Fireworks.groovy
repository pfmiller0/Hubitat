/**
 *  SeaWorld Fireworks
 *
 *  Copyright 2022 Peter Miller
 */

#include hyposphere.net.plib
import Math.*

definition(
    name: "SeaWorld Fireworks",
    namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Utilities",
    author: "Peter Miller",
    description: "SeaWorld Fireworks notifier",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: ""
)

preferences {
	java.text.SimpleDateFormat df
	
	section() {
		input "isPaused", "bool", title: "Pause app", defaultValue: false
	}
	if (state.todayShow) {
		df = new java.text.SimpleDateFormat("EEEE, h:mm aa")
		section("<b>Today's show</b>") {
			paragraph '<table style="border:1px solid silver; border-collapse:collapse; width:100%;">' + "${df.format(toDateTime(state.todayShow))}" + "</table>"
		}
	}
	section("<b>Last show</b>") {
		df = new java.text.SimpleDateFormat("EEEE, d MMM yyyy h:mm aa");
		paragraph '<table style="border:1px solid silver; border-collapse:collapse; width:100%;">' + "${df.format(toDateTime(state.prevShow ?: "2008-08-08T20:08:08+0700"))}" + "</table>"
	}
	section("<b>Settings</b>") {
		//input "sourceURL", "text", title: "Source URL", required: true, description: "URL to JSON file", defaultValue: "https://www.seaworld.com/api/sitecore/Marquee/LoadDayData?itemId=a6ac339a-375c-461c-9540-faebba25b60c"
		input "notifyDevice", "capability.notification", title: "Notification device", multiple: false, required: false
	}
}

void initialize() {
	if (isPaused == false) {
		resetAppLabel()
		
		//state.jsonFieldCount = state.jsonFieldCount ?: 0
		unschedule("SiteCheck")
		schedule('0 1 0 ? * *', "SiteCheck")
	} else {
		addAppLabel("Paused", "red")
	}
}

def installed() {
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
	String baseURL = "https://www.seaworld.com/api/sitecore/Marquee/LoadDayData?itemId=a6ac339a-375c-461c-9540-faebba25b60c"
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
	List<Map> EventsData
	Date FireworksTime
	Integer fieldCount
	String tzOffset = sprintf("%03d:00", (location.timeZone.getOffset(now())/(1000 * 60 * 60)).intValue())

    String respMimetype = ''
    if (resp.getHeaders() && resp.getHeaders()["Content-Type"]) {
    	respMimetype = resp.getHeaders()["Content-Type"].split(";")[0]
	}

	if (resp.getStatus() != 200 || respMimetype != "application/json") {
		if (respMimetype != "application/json" ) {
			log.error "Response type '${respMimetype}', JSON expected"
		}
		log.error "HTTP error: " + resp.getStatus()
		return
	}
	
	EventsData = resp.getJson().EventGroups
	EventsData.each { if (it.Events[0].Title.toLowerCase().indexOf("firework") >= 0) {FireworksTime = toDateTime(it.Events[0].StartTime + tzOffset)} }
	if (FireworksTime) {
		state.prevShow = state.todayShow
		state.todayShow = FireworksTime
		
		/***/
		Calendar c = Calendar.getInstance()
		c.setTime(FireworksTime)
		c.add(Calendar.MINUTE, -60)
		runOnce(c.getTime(), "sendNotice")
		/***
		FireworksTime[Calendar.MINUTE] = FireworksTime[Calendar.MINUTE] - 60
		runOnce(FireworksTime, "sendNotice")
		/***/
		
		//log.info "Notify at ${c.getTime()}"
	} else {
		state.todayShow = ""
		//log.info "No fireworks"
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
    
    notifyDevice.deviceNotification "SeaWorld fireworks at ${showTime}"
}
