definition(
    name: "Average Temperature",
    namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Average Temperatures",
    author: "Bruce Ravenel",
    description: "Average some temperature sensors",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		section {
			input "thisName", "text", title: "Name this temperature averager", submitOnChange: true
			if(thisName) app.updateLabel("$thisName")
			input "tempSensors", "capability.temperatureMeasurement", title: "Select Temperature Sensors", submitOnChange: true, required: true, multiple: true
			paragraph "Sensor weights"
			tempSensors.each {
				if (it.getStatus() == "ACTIVE") {
					input "weight$it.id", "decimal", title: "$it ($it.currentTemperature)", defaultValue: 1.0, submitOnChange: true, width: 3
				} else {
					input "weight$it.id", "decimal", title: "$it (❌)", defaultValue: 1.0, submitOnChange: true, width: 3
				}
			}
			input "useRun", "number", title: "Compute running average over this many sensor events:", defaultValue: 1, submitOnChange: true
			if(tempSensors) paragraph "Current sensor average is ${averageTemp()}°"
			if(useRun > 1) {
				initRun()
				if(tempSensors) paragraph "Current running average is ${averageTemp(useRun)}°"
			}
		}
	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
	offlineCheck()
}

def uninstalled() {
	deleteChildDevice("AverageTemp_${app.id}")
}

def initialize() {
	def averageDev = getChildDevice("AverageTemp_${app.id}")
	if(!averageDev) averageDev = addChildDevice("hubitat", "Virtual Temperature Sensor", "AverageTemp_${app.id}", null, [label: thisName, name: thisName])
	averageDev.setTemperature(averageTemp())
	subscribe(tempSensors, "temperature", 'handler')
}

def initRun() {
	def temp = averageTemp()
	if(!state.run) {
		state.run = []
		for(int i = 0; i < useRun; i++) state.run += temp
	}
}

void offlineCheck() {
	if (tempSensors.any { it.getStatus() != "ACTIVE" }) {
		setOfflineLabel()
	} else {
		unsetOfflineLabel()
	}
}

void setOfflineLabel() {
	String label = app.getLabel()
	
	if ( label.substring(label.length()-2) != " ❌" ) {
		app.updateLabel(label + " ❌")
	}
}

void unsetOfflineLabel() {
	String label = app.getLabel()
	
	if ( label.substring(label.length()-2) == " ❌" ) {
		app.updateLabel(label.substring(0, label.length()-2))
	}
}

Float averageTemp(Integer run = 1) {
	Float total = 0
	Float n = 0
	
	// Todo?: For n devices, get time of n+1th averageDev update back. Any device that hasn't been updated since then give less weight to.
    // How does this handle unequally weighted devices?
	tempSensors.each {
		if (it.getStatus() == "ACTIVE") { // Device goes INACTIVE after 24 hrs w/out events
			total += it.currentTemperature * (settings["weight$it.id"] != null ? settings["weight$it.id"] : 1)
			n += settings["weight$it.id"] != null ? settings["weight$it.id"] : 1
		}
	}
	
	if (n > 0) {		
		def result = total / n
		if(run > 1) {
			total = 0
			state.run.each {total += it}
			result = total / run
		}
		return result.toDouble().round(1)
	} else {
		return null
	}
}

/*
void updatePastDayAvg() {
	Float[] hours = state.hours ?: [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]
	java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("HH");
	Date timeNow = new Date()
	Date timePrevHour = new Date(now() - 1000 * 60 * 60)
	
	def eventsPastHour = averageDev.eventsSince(timePrevHour)
	
	log.info "Hour: ${df.format(timeNow)}"
	hours[Integer.valueOf("11")] = 21.6
	hours[Integer.valueOf(df.format(timeNow))] = 8.8
	
	state.hours = hours
}
*/

/* From PurpleAir 
Float sensorAverageWeighted(def sensors, Integer field, Float[] coords) {
	Float count = 0.0
	Float sum = 0.0
	def distances = []
	Float nearest = 0.0
    
	// Weighted average. First find nearest sensor. Then divide sensors distances by nearest distance to get weights.
	sensors.each {
		distances.add(Float.valueOf(distance(coords, [Float.valueOf(it[3]), Float.valueOf(it[4])])))
	}
	nearest = distances.min()
	
	sensors.eachWithIndex { it, i ->
	   	Float val = Float.valueOf(it[field])
	   	Float weight = nearest / distances[i]
		sum = sum + val * weight
	   	count = count + weight
	}
	return sum / count
}
*/

def handler(evt) {
	def averageDev = getChildDevice("AverageTemp_${app.id}")
	Float avg = averageTemp()
	
	offlineCheck()
	
	if (useRun > 1) {
		state.run = state.run.drop(1) + avg
		avg = averageTemp(useRun)
	}
	if (avg) {
		if (avg != averageDev.latestValue("temperature") ) {
			averageDev.setTemperature(avg)
			//log.info "Average sensor temperature = ${averageTemp()}°" + (useRun > 1 ? "    Running average is $avg°" : "")
		}
	} else {
		log.warn "No devices have recent temps"
	}
}
