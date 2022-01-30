/**
 *  Switch On-time Tracker
 *
 *  Copyright 2021 Peter Miller
 */

#include hyposphere.net.plib

definition(
    name: "Switch On-time Tracker",
    namespace: "hyposphere.net",
    author: "Peter Miller",
    description: "Track how long a switch is turned on.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/pfmiller0/Hubitat/main/Switch%20On-time%20Tracker.groovy"
)


preferences {
	page(name: "mainPage")
  	page(name: "resetPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		section() {
			input "isPaused", "bool", title: "Pause app", defaultValue: false
		}
		section("Settings") {
			input "switches", "capability.switch", title: "Switches", multiple: false
			input "notifyDevice", "capability.notification", title: "Notification device", multiple: false, required: false
		}
		section("Time on") {
			if (state.totalOnTime != null) {
				paragraph '<table style="border:1px solid silver; border-collapse:collapse; width:100%;">' + printTime(getOnTime()) + "</table>"
			}
		}
		section {
			href "resetPage", title: "Reset time?", description: ""
		}
	}
}

def resetPage() {
	dynamicPage(name: "resetPage", title: "Time has been reset!", install: false, uninstall: false) {
		state.totalOnTime = 0.0
		if (switches.latestValue("switch") == "on") {
			state.turnOnTime = now()
		}
	}
}

void installed() {
	initialize()
	logDebug "Installed: $settings"
}

void updated() {
	unsubscribe()
	initialize()
	logDebug "Updated: $settings"
}

void initialize() {
	if (isPaused == false) {
		resetAppLabel()
		
		state.totalOnTime = state.totalOnTime ? state.totalOnTime : 0.0
		
		subscribe(switches, "switch", switchChanged)
		
		switchChanged()
	} else {
		addAppLabel("Paused", "red")
	}
}

void switchChanged(evt) {
	THREE_MONTHS = 129600.0 // in minutes
	
	//log.debug evt.device.getId()
	
	if (switches.latestValue("switch") == "on") {
		state.turnOnTime = now() 
	} else {
		state.totalOnTime += (now() - state.turnOnTime)/(1000*60) 
	}
	
	if (state.totalOnTime > THREE_MONTHS) {
		notifyDevice.deviceNotification "Time to clean the air filter!"
	}	
}

Integer getOnTime() {
	if (switches.latestValue("switch") == "on") {
		return Math.round(state.totalOnTime + (now() - state.turnOnTime)/(1000*60))
	} else {
		return Math.round(state.totalOnTime)
	}
}

String printTime(Integer mins) {
	String out=""
	String td = '<td style="border:1px solid silver;">'
	String tdc = '</td>'
	
	Integer days
	Integer hours

	days = mins.intdiv(1440)
	hours = (mins - (days * 1440)).intdiv(60)
	mins = (mins - (hours * 60) - (days * 1440))
	
	out="${days} days, ${hours} hours, ${mins} minutes"
	
	return out
}
