definition(
	name: "Average Temperature",
	namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Average Temperatures",
	author: "Peter Miller",
	description: "Average some temperature/humidity sensors",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: ""
)

// Originally based on the "Average Temperature" sample app by Bruce Ravenel

preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		def averageTempDev = getChildDevice("AverageTemp_${app.id}")
		def averageHumDev = getChildDevice("AverageHum_${app.id}")
		def averageHeatIndexDev = getChildDevice("AverageHeatIndex_${app.id}")
		section {
			input "thisName", "text", title: "Name this temperature averager", submitOnChange: true
			if (thisName) app.updateLabel("$thisName")
			input "useHum", "bool", title: "Use Humidity", defaultValue: false, width: 3, submitOnChange: true
		}
		section("<b>Temperature</b>") {
			if (averageTempDev) paragraph "Average temperature device name: <b>${averageTempDev.label?:averageTempDev.name}</b>"
			input "tempSensors", "capability.temperatureMeasurement", title: "Select Temperature Sensors", submitOnChange: true, required: true, multiple: true
			tempSensors.each {
				if (it.getStatus() == "ACTIVE") {
					input "weight_temperature$it.id", "decimal", title: "$it ($it.currentTemperature)", defaultValue: 1.0, submitOnChange: true, width: 3
				} else {
					input "weight_temperature$it.id", "decimal", title: "$it (❌)", defaultValue: 1.0, submitOnChange: true, width: 3
				}
			}
		}
		if (useHum) {
			section("<b>Humidity</b>") {
				if (averageHumDev) paragraph "Average humidity device name: <b>${averageHumDev.label?:averageHumDev.name}</b>"
				input "humSensors", "capability.relativeHumidityMeasurement", title: "Select Humidity Sensors", submitOnChange: true, required: true, multiple: true
				paragraph "Sensor weights"
				humSensors.each {
					if (it.getStatus() == "ACTIVE") {
						input "weight_humidity$it.id", "decimal", title: "$it ($it.currentHumidity)", defaultValue: 1.0, submitOnChange: true, width: 3
					} else {
						input "weight_humidity$it.id", "decimal", title: "$it (❌)", defaultValue: 1.0, submitOnChange: true, width: 3
					}
				}
			}
		}
		section() {
			input "useRun", "number", title: "Compute running average over this many sensor events:", defaultValue: 1, submitOnChange: true, width: 3
			if (! (useRun > 1)) {
				if (tempSensors) paragraph "Current temperature average is ${averageDevs(tempSensors, "temperature")}°"
				if (humSensors) paragraph "Current humidity average is ${averageDevs(humSensors, "humidity")}%"
				if (averageHeatIndexDev) paragraph "Current heat index is ${getHeatindex(averageDevs(tempSensors, "temperature"), averageDevs(humSensors, "humidity"))}°"
			} else {
				initRun()
				if (tempSensors) paragraph "Current running average is ${averageDevs(tempSensors, "temperature", useRun)}°"
				if (humSensors) paragraph "Current humidity average is ${averageDevs(tempSensors, "temperature", useRun)}%"
				if (averageHeatIndexDev) paragraph "Current heat index is ${getHeatindex(averageDevs(tempSensors, "temperature", useRun), averageDevs(humSensors, "humidity", useRun))}°"
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

def initialize() {
	def averageTempDev = getChildDevice("AverageTemp_${app.id}")
	if (!averageTempDev) {
		averageTempDev = addChildDevice("hubitat", "Virtual Temperature Sensor", "AverageTemp_${app.id}", null, [label: "$thisName temp", name: "$thisName temp"])
		averageTempDev.updateSetting("txtEnable", [value:false,type:"bool"])
		//averageTempDev.setTemperature(averageTemp())
	}
	subscribe(tempSensors, "temperature", 'tempHandler')
	
	if (useHum) {
		def averageHumDev = getChildDevice("AverageHum_${app.id}")
		if (!averageHumDev) {
			averageHumDev = addChildDevice("hubitat", "Virtual Humidity Sensor", "AverageHum_${app.id}", null, [label: "$thisName humidity", name: "$thisName humidity"])
			averageHumDev.updateSetting("txtEnable", [value:false,type:"bool"])
			//averageHumDev.setHumidity(averageHum())
		}
		subscribe(humSensors, "humidity", 'humHandler')

		def averageHeatIndexDev = getChildDevice("AverageHeatIndex_${app.id}")
		if (!averageHeatIndexDev) {
			averageTempDev = addChildDevice("hubitat", "Virtual Temperature Sensor", "AverageHeatIndex_${app.id}", null, [label: "$thisName heat index", name: "$thisName heat index"])
			averageHeatIndexDev.updateSetting("txtEnable", [value:false,type:"bool"])
			//averageHeatIndexDev.setTemperature(averageTemp())
		}
	}
}

def initRun() {
	def temp = averageTemp()
	def hum = averageTemp()
	if (!state.run) {
		state.run = []
		for(int i = 0; i < useRun; i++) state.runTemp += temp
		for(int i = 0; i < useRun; i++) state.runHum += hum
	}
}

void offlineCheck() {
	if (tempSensors.any { it.getStatus() != "ACTIVE" } || humSensors.any { it.getStatus() != "ACTIVE" }) {
		setOfflineLabel()
	} else {
		unsetOfflineLabel()
	}
}

void setOfflineLabel() {
	String label = app.getLabel()
	
	if (label.substring(label.length()-2) != " ❌" ) {
		app.updateLabel(label + " ❌")
	}
}

void unsetOfflineLabel() {
	String label = app.getLabel()
	
	if (label.substring(label.length()-2) == " ❌" ) {
		app.updateLabel(label.substring(0, label.length()-2))
	}
}

Float averageDevs(List devs, String attr, Integer run = 1) {
	Float total = 0
	Float n = 0
	Float weight
	Float timeWeightAdjust
	Float hrSinceLastUpdate

	Float exp = 1.5
	Float div = 12**exp / 10

	devs.each {
		if (it.getStatus() == "ACTIVE") { // Device goes INACTIVE after 24 hrs w/out events
			weight = settings["weight_${attr}${it.id}"] ?: 1
			/****
			hrSinceLastUpdate = (now() - it.currentState(attr).date.time) / (1000 * 60 * 60)
			timeWeightAdjust = hrSinceLastUpdate**exp / div + 1
			/****/
			timeWeightAdjust = timeWeightAdjust > 1.0 ? timeWeightAdjust : 1.0
			if (timeWeightAdjust > 1.01) {
				//log.debug sprintf("%s: time: %.2f, weight: %.2f, adj: %.2f, adj weight: %.2f (^%.4s/%.4s)", it, hrSinceLastUpdate, weight, timeWeightAdjust, weight / timeWeightAdjust, exp, div)
			}
			total += Float.parseFloat(it.currentState(attr).value) * weight / timeWeightAdjust
			n += weight / timeWeightAdjust
		}
	}
	
	if (n > 0) {
		def result = total / n
		if (run > 1) {
			total = 0
			if (attr == "temperature") {
				state.runTemp.each {total += it}
			} else {
				state.runHum.each {total += it}
			}
			result = total / run
		}
		return result.toFloat().round(1)
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
	
	def eventsPastHour = averageTempDev.eventsSince(timePrevHour)
	
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

Float getHeatindex(Float tempF, Float humidity) {
	// Simple heat index equation, good for temps below 80F
	Float hi = 0.5 * (tempF + 61.0 + ((tempF-68.0)*1.2) + (humidity*0.094))
	return hi.round(1)
}

void tempHandler(evt) {
	Float avg
	def averageTempDev = getChildDevice("AverageTemp_${app.id}")
	def averageHumDev
	def averageHeatIndexDev
	if (useHum) {
		averageHumDev = getChildDevice("AverageHum_${app.id}")
		averageHeatIndexDev = getChildDevice("AverageHeatIndex_${app.id}")
	}
	
	offlineCheck()
	
	if (useRun > 1) {
		state.runTemp = state.runTemp.drop(1) + avg
		avg = averageDevs(tempSensors, "temperature", useRun)
	} else {
		avg = averageDevs(tempSensors, "temperature")
	}
		
	if (avg) {
		if (avg != averageTempDev.latestValue("temperature") ) {
			averageTempDev.setTemperature(avg)
			if (useHum) {
				averageHeatIndexDev.setTemperature(getHeatindex(avg, averageHumDev.currentHumidity))
			}
			//log.info "Average sensor temperature = ${averageDevs(tempSensors, "temperature")}°" + (useRun > 1 ? "    Running average is $avg°" : "")
		//else {
		//	log.debug "no change"
		}
	} else {
		log.warn "No devices have recent temps"
	}
}

void humHandler(evt) {
	def averageTempDev = getChildDevice("AverageTemp_${app.id}")
	def averageHumDev = getChildDevice("AverageHum_${app.id}")
	def averageHeatIndexDev = getChildDevice("AverageHeatIndex_${app.id}")
	Float avg
	
	offlineCheck()
		
	if (useRun > 1) {
		state.runHum = state.runHum.drop(1) + avg
		avg = averageDevs(humSensors, "humidity", useRun)
	} else {
		avg = averageDevs(humSensors, "humidity")
	}
	
	if (avg) {
		if (avg != averageHumDev.latestValue("humidity") ) {
			averageHumDev.setHumidity(avg)
			averageHeatIndexDev.setTemperature(getHeatindex(averageTempDev.currentTemperature, avg))
			//log.info "Average sensor humidity = ${averageDevs(humSensors, "humidity")}°" + (useRun > 1 ? "    Running average is $avg%" : "")
		}
	} else {
		log.warn "No devices have recent humidities"
	}
}
