/*
 *  Device monitor
 *
 *  Copyright 2022 Peter Miller
 */

#include hyposphere.net.plib

definition(
    name: "Device monitor",
    namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Utilities",
    author: "Peter Miller",
    description: "Monitor battery devices",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: ""
)

preferences {
	section() {
		input "isPaused", "bool", title: "Pause app", defaultValue: false
	}
	if (state.notifiedDevs != []) {
		section("Offline devices:") {
			paragraph '<table style="border:1px solid silver; border-collapse:collapse; width:100%;">' + state.notifiedDevs + "</table>"
		}
	}
	section("Settings") {
		input "batteryDevices", "capability.battery", title: "Battery powered devices", multiple: true, required: true
		input "notifyDevice", "capability.notification", title: "Notification device", multiple: false, required: true
	}	
}

void initialize() {
	if (! isPaused) {
		resetAppLabel()
		unschedule()
		schedule('0 5 9,12,15,18,21 ? * *', 'deviceCheckStart')
	} else {
		addAppLabel("Paused", "red")
	}
}

def installed() {
	state.notifiedDevs = []
	initialize()
}

def updated() {
	initialize()
	deviceCheckFinish()
}

def uninstalled() {
	unschedule()
}

void deviceCheckStart() {
	batteryDevices.each {
		it.refresh()
	}

	runInMillis(15 * 60 * 1000, 'deviceCheckFinish',)
}

void deviceCheckFinish() {
 	if (debugMode) log.debug "params: $params"
	List<String> offlineDevs = []
	List<String> notificationList = []
	Long updateThreshold = now() - (6 * 60 * 60 * 1000) // 6 hrs

	batteryDevices.each { dev ->
		if (dev.getLastActivity()?.getTime() < updateThreshold) {
		//if (dev.getStatus() == "INACTIVE") { // Device goes inactive after 24 hrs w/out events
			if (! state.notifiedDevs.any { it == dev.getLabel() }) {
				notificationList << dev.getLabel()
			}
			offlineDevs << dev.getLabel()
		}
	}
	
	if (notificationList != [] ) {
		notifyDevice.deviceNotification "New offline devices found: ${notificationList}"
	}
	
	state.notifiedDevs = offlineDevs
}
