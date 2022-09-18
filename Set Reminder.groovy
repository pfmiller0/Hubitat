/**
 *  Set reminder
 *
 *  Copyright 2022 Peter Miller
 */

#include hyposphere.net.plib

definition(
	name: "Set Reminder",
	namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Utilities",
	author: "Peter Miller",
	description: "Set a reminder after a given time",
	category: "My Apps",
	iconUrl: "",
	iconX2Url: "",
	importUrl: "https://raw.githubusercontent.com/pfmiller0/Hubitat/main/Set%20Reminder.groovy"
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
					input name: "btnReset", type: "button", title: "Reset timer?"
				} else {
					paragraph "Are you sure you want to reset the timer?"
					input name: "btnCancel", type: "button", title: "No", width: 6
					input name: "btnConfirm", type: "button", title: "<span style='color:red'>Yes</span>", width: 6
				}	
			}
		}	

		section("Settings") {
			input "initialReminderHours", "decimal", title: "Reminder time in hours", defaultValue: 48, required: true
			input "followupReminderHours", "decimal", title: "Follow up reminder time in hours", defaultValue: 24, required: false
			input "reminderMsg", "string", title: "Reminder message", defaultValue: "Water the plants!", required: true
			input "trigger", "capability.switch", title: "Trigger switch", multiple: false
			input "notifyDev", "capability.notification", title: "Notification device", multiple: false, required: false
			input "thisName", "text", title: "Name this reminderer", submitOnChange: true
			if(thisName) app.updateLabel("$thisName")		
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
	//app.updateLabel(trigger.getLabel() + " On-Time Tracker")
}

void initialize() {
	if (isPaused == false) {
		resetAppLabel()		
		subscribe(trigger, "switch.on", 'startTimer')		
	} else {
		addAppLabel("Paused", "red")
	}
}

void startTimer(evt) {
	//state.reminderSetTime = now()
	Date reminderTime = timeToday("18:00")
	Calendar c = Calendar.getInstance()
	c.setTime(reminderTime)
	c.add(Calendar.HOUR, (Integer) Math.round(initialReminderHours))
	runOnce(c.getTime(), 'sendNotification')
	//runIn(Math.round(initialReminderHours*60*60), 'sendNotification', [:])
}

void sendNotification() {
	notifyDev.deviceNotification reminderMsg
	if (followupReminderHours) {
		runIn(Math.round(followupReminderHours*60*60), 'sendNotification', [:])
	}
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
			unsubscribe()
			state.showReset = false
			break
		default:
			log.warn "Unhandled button press: $btn"
	}
}
