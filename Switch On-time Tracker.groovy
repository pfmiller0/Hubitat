/**
 *  Switch On-time Tracker
 *
 *  Copyright 2021 Peter Miller
 */

#include hyposphere.net.plib

definition(
    name: "Switch On-Time Tracker",
    namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Utilities",
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
		
		if (state.notifyTime) {
			section("Time on: <b>" + printTime(getOnTime()) + "</b>") { }
			section("Notify after: <b>" + printTime(Math.round(state.notifyTime)) + "</b>") { }
		
			section("Reset") {
	    		if (! state.showReset) {
					input name: "btnReset", type: "button", title: "Reset counter?"
				} else {
					paragraph "Are you sure you want to reset the counter?"
					input name: "btnCancel", type: "button", title: "No", width: 6
					input name: "btnConfirm", type: "button", title: "<span style='color:red'>Yes</span>", width: 6
				}	
			}
		}	

		section("Settings") {
			input "switchDev", "capability.switch", title: "Switch", multiple: false
			input "notifyDev", "capability.notification", title: "Notification device", multiple: false, required: false
		}
	}
}

void installed() {
	initialize()
	//switchChanged()
	logDebug "Installed: $settings"
}

void updated() {
	unsubscribe()
	initialize()
	logDebug "Updated: $settings"
	app.updateLabel(switchDev.getLabel() + " On-Time Tracker")
}

void initialize() {
	if (isPaused == false) {
		resetAppLabel()
		
		state.totalOnTime = state.totalOnTime ? state.totalOnTime : 0.0
		state.notifyTime = 129600 // 3 months in minutes
		state.turnOnTime = state.turnOnTime ? state.turnOnTime : now()
		
		subscribe(switchDev, "switch", switchChanged)		
	} else {
		addAppLabel("Paused", "red")
	}
}

void switchChanged(evt) {
	//log.debug evt.device.getId()
	
	if (switchDev.latestValue("switch") == "on") {
		state.turnOnTime = now() 
	} else {
		state.totalOnTime += (now() - state.turnOnTime)/(1000*60) 
	}
	
	if (state.totalOnTime > state.notifyTime) {
		notifyDev.deviceNotification "Time to clean the air filter!"
	}	
}

Integer getOnTime() {
	if (switchDev.latestValue("switch") == "on") {
		return Math.round(state.totalOnTime + (now() - state.turnOnTime)/(1000*60))
	} else {
		return Math.round(state.totalOnTime)
	}
}

String printTime(Long mins) {
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

void appButtonHandler(String btn) {
	switch (btn) {
	case "btnReset":
		state.showReset = true
		break
	case "btnCancel":
		state.showReset = false
		break
	case "btnConfirm":
		state.totalOnTime = 0.0
		if (switchDev.latestValue("switch") == "on") {
			state.turnOnTime = now()
		}
		state.showReset = false
		break
	default:
		log.warn "Unhandled button press: $btn"
	}
}
