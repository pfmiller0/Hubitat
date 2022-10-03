/**
 *  Energy Cost Tracker
 *
 *  Copyright 2021 Peter Miller
 */

#include hyposphere.net.plib

definition(
	name: "Energy Cost Tracker",
	namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Utilities",
	author: "Peter Miller",
	description: "Track how much energy is costing.",
	category: "My Apps",
	iconUrl: "",
	iconX2Url: "",
	importUrl: "https://raw.githubusercontent.com/pfmiller0/Hubitat/main/Energy%20Cost%20Tracker.groovy"
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
		
		if (state.totalEnergyCost) {
			section("Energy cost: <b>\$${state.totalEnergyCost}</b>") { }
		
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
			input "energyDev", "capability.energyMeter", title: "Energy meter", multiple: false
			//input "notifyDev", "capability.notification", title: "Notification device", multiple: false, required: false
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
	//app.updateLabel(switchDev.getLabel() + " On-Time Tracker")
}

void initialize() {
	if (isPaused == false) {
		resetAppLabel()
		/***
        state.remove('totalEnergyCost')
		/***/
		state.totalEnergyCost = state.totalEnergyCost ?: 0.0
		state.lastBillingPeriod = state.lastBillingPeriod ?: getBillingPeriod()
		state.lastHourRate = state.lastHourRate ?: getBillingRate(state.lastBillingPeriod)
		state.prevTotalUse = state.prevTotalUse ?: energyDev?.latestValue("energy")
		state.oldMonthTotal = state.oldMonthTotal ?: energyDev.latestValue("currMonthTotal")

		schedule('0 0 * ? * *', 'updateUsage')
		/***/
		updateUsage()
		/***/
	} else {
		addAppLabel("Paused", "red")
	}
}

Boolean isHoliday(Date d) {
	Integer YEAR = d[Calendar.YEAR]
	String MONTH = sprintf('%02d', d[Calendar.MONTH]+1)
	String DAY = sprintf('%02d', d[Calendar.DAY_OF_MONTH])
	
	if (YEAR > 2024) {
		if (d[Calendar.HOUR_OF_DAY] == 17) {
			log.warn "isHoliday: Holiday calendar for $YEAR missing"
		}
	}
	
	switch("$MONTH-$DAY") {
		case "01-01": // New Year
		case "03-31": // Cesar Chavez Day
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

BigDecimal getBillingRate(String period) {
	BigDecimal delivery = 0.26467
	BigDecimal generation = 0.0
	
	switch(period) {
		case "on-peak":
		case "sum-on-peak":
		case "win-on-peak":
			generation = 0.29697
			break
		case "off-peak":
		case "sum-off-peak":
		case "win-off-peak":
			generation = 0.22574
			break
		case "super-off-peak":
		case "sum-super-off-peak":
		case "win-super-off-peak":
			generation = 0.14854
			break
	}
	
	return delivery + generation
}

String getBillingPeriod() {
	Date d = new Date()
	
	Integer hour = d[Calendar.HOUR_OF_DAY]
	Integer month = d[Calendar.MONTH]+1
	Integer day_of_week = d[Calendar.DAY_OF_WEEK]
		
	/* https://www.sdge.com/regulatory-filing/16026/residential-time-use-periods
		Peak 16-21
			Summer (June 1 - October 31)
				weekday off peak: 06 - 16; 21 - 00
				weekday Super off peak: 00 - 06
				weekend off peak: 14 - 16; 21 - 00
				weekend Super off peak: 00 - 14

			Winter (November 1 - May 31)
				weekday off peak: 06 - 16; Excluding 10 - 14 in March and April; 21 - 00
				weekday Super off peak: 00 - 06; 10 - 14 in March and April
				weekend off peak: 14 - 16; 21 - 00
				weekend Super off peak: 00 - 14
	*/
	if ((6..10).contains(month)) { // summer
		if ((2..6).contains(day_of_week) && ! isHoliday(d)) { // weekday
			switch (hour) {
				case 16..20:
					//log.debug "sum-on-peak-wd"
					return "on-peak"
					break
				case 6..15:
				case 21..23:
					//log.debug "sum-off-peak-wd"
					return "off-peak"
					break
				case 0..5:
					//log.debug "sum-super-off-peak-wd"
					return "super-off-peak"
					break
			}
		} else { // weekend / holiday
			switch (hour) {
				case 16..20:
					//log.debug "sum-on-peak-we"
					return "on-peak"
					break
				case 14..15:
				case 21..23:
					//log.debug "sum-off-peak-we"
					return "off-peak"
					break
				case 0..13:
					//log.debug "sum-super-off-peak-we"
					return "super-off-peak"
					break
			}
		}
	} else { // winter
		if ((2..6).contains(day_of_week) && ! isHoliday(d)) { // weekday
			switch (hour) {
				case 16..20:
					//log.debug "win-on-peak-wd"
					return "on-peak"
					break
				case 6..15: // Excluding 10..13 in March and April
				case 21..23:
					if (month == 3 || month == 4) {
						if ( hour >= 10 && hour <= 13 ) {
							//log.debug "win-super-off-peak-wd"
							return "super-off-peak"
							break
						}
					}
					//log.debug "win-off-peak-wd"
					return "off-peak"
					break
				case 0..5:
					//log.debug "win-super-off-peak-wd"
					return "super-off-peak"
					break
			}
		} else { // weekend
			switch (hour) {
				case 16..20:
					//log.debug "win-on-peak-we"
					return "on-peak"
					break
				case 14..15:
				case 21..23:
					//log.debug "win-off-peak-we"
					return "off-peak"
					break
				case 0..13:
					//log.debug "win-super-off-peak-we"
					return "super-off-peak"
					break
			}
		}
	}
}

/*
/ Each hour get stored billing rate and calculate cost of last hours usage
/ Then get billing rate for next hour and store
/ TODO: Store cost in device to access from graph. Add price that can be set to device?
*/
void updateUsage(evt) {
	String curBillingPeriod = getBillingPeriod()
	BigDecimal curHourRate = getBillingRate(curBillingPeriod)
	BigDecimal curTotalUse = energyDev.latestValue("energy")
	
	if (curTotalUse != state.prevTotalUse) {
		BigDecimal lastHourUse = 0
		if (curTotalUse > state.prevTotalUse) {
			lastHourUse = curTotalUse - state.prevTotalUse
		} else {
			Date d = new Date()
			BigDecimal curMonthTotal
			if (d[Calendar.DAY_OF_MONTH] == 1) {
				curMonthTotal = energyDev.latestValue("lastMonthTotal")
			} else {
				curMonthTotal = energyDev.latestValue("currMonthTotal")
			}
			lastHourUse = curMonthTotal - state.oldMonthTotal - state.prevTotalUse
			state.oldMonthTotal = curMonthTotal
		}
		BigDecimal lastHourCost = lastHourUse * state.lastHourRate
		
		state.totalEnergyCost += lastHourCost
		
		log.debug "curTotalUse: $curTotalUse, prevTotalUse: $state.prevTotalUse"
		log.debug "Rate: $state.lastHourRate($state.lastBillingPeriod), Used: $lastHourUse, Cost: \$$lastHourCost, TotalCost: \$$state.totalEnergyCost"

		state.prevTotalUse = curTotalUse
	}

	state.lastBillingPeriod = curBillingPeriod
	state.lastHourRate = curHourRate
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
			state.totalEnergyCost = 0.0
			state.showReset = false
			//sendEvent(name: "On time", value: state.totalOnTime, unit: "minutes")
			break
		default:
			log.warn "Unhandled button press: $btn"
	}
}
