/**
 *  Weather Lights
 *	Set light color based on current temperature
 * 
 *  Copyright 2020 Peter Miller
 */

definition(
	name: "Weather Lights",
	namespace: "hyposphere.net",
	author: "Peter Miller",
	description: "Set light color based on current temperature",
	iconUrl: "",
	iconX2Url: "",
	importUrl: "https://raw.githubusercontent.com/pfmiller0/Hubitat/main/Weather%20Lights.groovy"
)

preferences {
	section() {
		input "isPaused", "bool", title: "Pause app", defaultValue: false
	}
	section("Devices") {
		input "thermoOut", "capability.temperatureMeasurement", title: "Thermometer", required: true, multiple: false
		input "myLights", "capability.colorControl", title: "Lights", required: true, multiple: true
	}
	section("Settings") {
		input "colorOption", "enum", title: "Color scale", options: ["Pete's", "Weather Channel", "Spectrum", "Spectrum 2", "Smartthings"]
		input "saturationOption", "decimal", title: "Saturation (0..1) ", required: true, defaultValue: 0.95, range: "0..1"
	}
	section("Debug") {
		input "debugMode", "bool", title: "Debug Mode", submitOnChange: true
		if (debugMode) {
			input "debugLevel", "enum", title: "Debug level", options: [0, 1, 2], required: true, defaultValue: 0
			input "tempDebug", "number", title: "Temp override"
		}
	}
}

List<Map> tempScaleSmartthings() { /* Standard SmartThings temperature colors */
	return [
		[temp: 31, hsv: [62, 86, 0], name: "indigo"],
		[temp: 44, hsv: [53, 84, 0], name: "turquoise"],
		[temp: 59, hsv: [39, 31, 0], name: "mint"],
		[temp: 74, hsv: [29, 82, 0], name: "green"],
		[temp: 84, hsv: [15, 100, 0], name: "yellow"],
		[temp: 95, hsv: [6, 100, 0], name: "orange"],
		[temp: 96, hsv: [0, 81, 0], name: "red"]
	]
}

List<Map> tempScaleWeatherchannel() { /* Source: https://s.w-x.co/staticmaps/acttemp_1280x720.jpg */
	return [
		[temp: -40, hsv: [74, 44, 0], name: "lavender"],
		[temp: -30, hsv: [75, 30, 0], name: "gray-purple"],
		[temp: -20, hsv: [76, 23, 0], name: "light purple"],
		[temp: -10, hsv: [89, 94, 0], name: "burgundy"],
		[temp: 0, hsv: [85, 65, 0], name: "dark purple"],
		[temp: 10, hsv: [82, 46, 0], name: "purple"],
		[temp: 20, hsv: [55, 33, 0], name: "light blue"],
		[temp: 30, hsv: [60, 45, 0], name: "blue"],
		[temp: 40, hsv: [66, 84, 0], name: "dark blue"],
		[temp: 50, hsv: [70, 34, 0], name: "dark gray"],
		[temp: 60, hsv: [16, 76, 0], name: "yellow"],
		[temp: 70, hsv: [10, 100, 0], name: "orange"],
		[temp: 80, hsv: [2, 98, 0], name: "red-orange"],
		[temp: 90, hsv: [0, 100, 0], name: "dark red"],
		[temp: 100, hsv: [91, 53, 0], name: "pink"],
		[temp: 110, hsv: [88, 5, 0], name: "eggshell"],
		[temp: 120, hsv: [17, 24, 0], name: "sand"]
	]
}

List<Map> tempScalePetes() {
	return [
		[temp: -40, hsv: [74, 75, 0], name: "lavender"],
		[temp: -30, hsv: [75, 75, 0], name: "gray-purple"],
		[temp: -20, hsv: [76, 75, 0], name: "light purple"],
		[temp: -10, hsv: [89, 75, 0], name: "burgundy"],
		[temp: 0, hsv: [85, 80, 0], name: "dark purple"],
		[temp: 10, hsv: [82, 80, 0], name: "purple"],
		[temp: 20, hsv: [66, 90, 0], name: "dark blue"],
		[temp: 32, hsv: [52, 100, 0], name: "ice blue"],
		[temp: 56, hsv: [55, 100, 0], name: "light blue"],
		[temp: 68, hsv: [33, 100, 0], name: "green"],
		[temp: 80, hsv: [16, 100, 0], name: "yellow"],
		[temp: 90, hsv: [10, 100, 0], name: "orange"],
		[temp: 100, hsv: [0, 90, 0], name: "dark red"],
		[temp: 110, hsv: [91, 80, 0], name: "pink"],
		[temp: 120, hsv: [91, 75, 0], name: "pink"]
	]
}

List<Map> tempScaleSpectrum() {
	return [
		[temp: 0, hsv: [83, 75, 0], name: "purple"],
		[temp: 32, hsv: [83, 100, 0], name: "purple"],
		[temp: 100, hsv: [0, 100, 0], name: "red"],
		[temp: 120, hsv: [0, 75, 0], name: "red"]
	]
}

List<Map> tempScaleSpectrum2() {
	return [
		[temp: 0, hsv: [83, 75, 0], name: "purple"],
		[temp: 32, hsv: [83, 100, 0], name: "purple"],
		[temp: 65, hsv: [49, 100, 0], name: ""],
		[temp: 70, hsv: [37, 100, 0], name: ""],
		[temp: 75, hsv: [25, 100, 0], name: " "],
		[temp: 100, hsv: [0, 100, 0], name: "red"],
		[temp: 120, hsv: [0, 75, 0], name: "red"]
	]
}

void installed() {
	if (debugMode) log.debug "Installed: $settings"
	initialize()
}

void updated() {
	if (debugMode) log.debug "Updated: $settings"
	unsubscribe()
	initialize()
}

void initialize() {
	if (isPaused == false) {
		subscribe(thermoOut, "temperature", updateLight)
		subscribe(myLights, "switch.on", updateLight)

		if (colorOption == "Pete's") {
			if (debugMode) log.debug "Using Pete's colors"
			state.tempScale = tempScalePetes()
		} else if (colorOption == "Weather Channel") {
			if (debugMode) log.debug "Using Weather Channel colors"
			state.tempScale = tempScaleWeatherchannel()
		} else if (colorOption == "Spectrum") {
			if (debugMode) log.debug "Using Spectrum colors"
			state.tempScale = tempScaleSpectrum()
		} else if (colorOption == "Spectrum 2") {
			if (debugMode) log.debug "Using Spectrum2 colors"
			state.tempScale = tempScaleSpectrum2()
		} else if (colorOption == "Smartthings") {
			if (debugMode) log.debug "Using Smartthings colors"
			state.tempScale = tempScaleSmartthings()
		} else {
			if (debugMode) log.debug "Defaulting to Pete's colors: colorOption = $colorOption"	
			state.tempScale = tempScalePetes()
		}
		updateLight()
	}
}

void updateLight(evt) {
	Float tempOut = null
    List<Integer> hsvTempColor = null
	Float satLevel = saturationOption
	
	if (satLevel > 1) {
		satLevel = 1.0
	} else if (satLevel < 0) {
		satLevel = 0
	} 

	if (thermoOut.getStatus() != "ACTIVE") {
		log.warn "Temp sensor is not online, do nothing"	
	} else {
		if (!debugMode || tempDebug == null) {
			tempOut = thermoOut.latestValue("temperature")
			if (debugMode) log.debug "temp: $tempOut"
		} else {
			tempOut = tempDebug ? tempDebug : thermoOut.latestValue("temperature")
			if (debugMode) log.debug "DEBUG temp: $tempOut"
		}

		hsvTempColor = getColor(tempOut, state.tempScale)
		
		// Adjust saturation to configured level
		hsvTempColor[1] = (hsvTempColor[1] * satLevel) as Integer

		for (light in myLights) {
			if (light.latestValue("switch") == "on") {
				if (debugMode) log.debug "Changing light $light"
				light.setColor(["hue": hsvTempColor[0], "saturation": hsvTempColor[1]])
			}
		}
	}
}

List<Integer> getColor(Float temp, List<Map> colorScale) {
	Integer tempLow = null
	Integer tempHigh = null
	Integer tempLast = null
	List<Integer> colorLow = null
	List<Integer> colorHigh = null
	List<Integer> colorLast = null
	Float rangePercent = null

	for (i in colorScale) {
		//if (debugMode) log.debug "i = $i; tempLow = $tempLow; tempHigh = $tempHigh; tempLast = $tempLast"
		if (temp < i.temp && tempLast == null) {
			return i.hsv
		} else if (temp == i.temp) {
			return i.hsv
		} else if (temp > tempLast && temp < i.temp) {
			tempLow = tempLast
			colorLow = colorLast
			tempHigh = i.temp
			colorHigh = i.hsv
			break
		} else if (temp > i.temp) {
			tempLast = i.temp
			colorLast = i.hsv
		}
	}
    
	if (tempLow == null && tempHigh == null) {
		// Passed the end of the temp scale, use last color
		return colorLast
	} else {
		// Get percent in temp range between colors
		rangePercent = (temp - tempLow)/(tempHigh - tempLow)
	}
	if (debugMode) log.debug "tempLow: $tempLow, temp = $temp ($rangePercent); tempHigh: $tempHigh"
    
	return getColorOnRange(colorLow, colorHigh, rangePercent)
}

List<Integer> getColorOnRange(List<Integer> colorOne, List<Integer> colorTwo, Float percent) {
	List<Integer> hsvColorNew = [0, 0, 0]

	for (i in 0..1) { // for H and S (we don't need V)
		hsvColorNew[i] = Math.round(((colorTwo[i] - colorOne[i]) * percent) + colorOne[i])
	}
    
	if (debugMode) log.debug "colorOne = $colorOne; hsvColorNew = $hsvColorNew; colorTwo = $colorTwo"

	return hsvColorNew
}
