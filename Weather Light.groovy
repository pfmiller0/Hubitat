/**
 *  Weather Lights
 *	Set light color based on current temperature
 * 
 *  Copyright 2020 Peter Miller
 */

definition(
	name: "Weather Light",
	namespace: "hyposphere.net",
	parent: "hyposphere.net:P's Light Controls",
	author: "Peter Miller",
	description: "Set light color based on current temperature",
	iconUrl: "",
	iconX2Url: "",
	importUrl: "https://raw.githubusercontent.com/pfmiller0/Hubitat/main/Weather%20Light.groovy"
)

import groovy.transform.Field

@Field final static List<Map> tempScaleSmartthings = [ /* Standard SmartThings temperature colors */
	[temp: 31, hsv: [62, 86, 100], name: "indigo"],
	[temp: 44, hsv: [53, 84, 100], name: "turquoise"],
	[temp: 59, hsv: [39, 31, 100], name: "mint"],
	[temp: 74, hsv: [29, 82, 100], name: "green"],
	[temp: 84, hsv: [15, 100, 100], name: "yellow"],
	[temp: 95, hsv: [6, 100, 100], name: "orange"],
	[temp: 96, hsv: [0, 81, 100], name: "red"]
]

@Field final static List<Map> tempScaleWeatherchannel = [ /* Source: https://s.w-x.co/staticmaps/acttemp_1280x720.jpg */
	[temp: -40, hsv: [74, 44, 100], name: "lavender"],
	[temp: -30, hsv: [75, 30, 100], name: "gray-purple"],
	[temp: -20, hsv: [76, 23, 100], name: "light purple"],
	[temp: -10, hsv: [89, 94, 100], name: "burgundy"],
	[temp: 0, hsv: [85, 65, 100], name: "dark purple"],
	[temp: 10, hsv: [82, 46, 100], name: "purple"],
	[temp: 20, hsv: [55, 33, 100], name: "light blue"],
	[temp: 30, hsv: [60, 45, 100], name: "blue"],
	[temp: 40, hsv: [66, 84, 100], name: "dark blue"],
	[temp: 50, hsv: [70, 34, 100], name: "dark gray"],
	[temp: 60, hsv: [16, 76, 100], name: "yellow"],
	[temp: 70, hsv: [10, 100, 100], name: "orange"],
	[temp: 80, hsv: [2, 98, 100], name: "red-orange"],
	[temp: 90, hsv: [0, 100, 100], name: "dark red"],
	[temp: 100, hsv: [91, 53, 100], name: "pink"],
	[temp: 110, hsv: [88, 5, 100], name: "eggshell"],
	[temp: 120, hsv: [17, 24, 100], name: "sand"]
]

@Field final static List<Map> tempScalePetes = [
	[temp: -40, hsv: [74, 75, 100], name: "lavender"],
	[temp: -30, hsv: [75, 75, 100], name: "gray-purple"],
	[temp: -20, hsv: [76, 75, 100], name: "light purple"],
	[temp: -10, hsv: [89, 75, 100], name: "burgundy"],
	[temp: 0, hsv: [85, 80, 100], name: "dark purple"],
	[temp: 10, hsv: [82, 80, 100], name: "purple"],
	[temp: 20, hsv: [66, 90, 100], name: "dark blue"],
	[temp: 32, hsv: [52, 100, 100], name: "ice blue"],
	[temp: 56, hsv: [55, 100, 100], name: "light blue"],
	[temp: 68, hsv: [33, 100, 100], name: "green"],
	[temp: 80, hsv: [16, 100, 100], name: "yellow"],
	[temp: 90, hsv: [10, 100, 100], name: "orange"],
	[temp: 100, hsv: [0, 90, 100], name: "dark red"],
	[temp: 110, hsv: [91, 80, 100], name: "pink"],
	[temp: 120, hsv: [91, 75, 100], name: "pink"]
]

@Field final static List<Map> tempScaleSpectrum = [
	[temp: 0, hsv: [83, 75, 100], name: "purple"],
	[temp: 32, hsv: [83, 100, 100], name: "purple"],
//	[temp: 65, hsv: [43, 100, 100], name: ""],
//	[temp: 70, hsv: [37, 100, 100], name: ""],
//	[temp: 75, hsv: [31, 100, 100], name: ""],
	[temp: 100, hsv: [0, 100, 100], name: "red"],
	[temp: 120, hsv: [0, 75, 100], name: "red"]
]

@Field final static List<Map> tempScaleSpectrum2 = [
	[temp: 0, hsv: [83, 75, 100], name: "purple"],
	[temp: 32, hsv: [83, 100, 100], name: "purple"],
	[temp: 65, hsv: [49, 100, 100], name: ""],
	[temp: 70, hsv: [37, 100, 100], name: ""],
	[temp: 75, hsv: [25, 100, 100], name: ""],
	[temp: 100, hsv: [0, 100, 100], name: "red"],
	[temp: 120, hsv: [0, 75, 100], name: "red"]
]
	
preferences {
	section() {
		input "isPaused", "bool", title: "Pause app", defaultValue: false
	}
	section("<b>Devices</b>") {
		input "thermoOut", "capability.temperatureMeasurement", title: "Thermometer", required: true, multiple: false
		input "lights", "capability.colorControl", title: "Lights", required: true, multiple: true
	}
	section("<b>Settings</b>") {
		input "colorOption", "enum", title: "Color scale", required: true, options: ["Pete's", "Weather Channel", "Spectrum", "Spectrum 2", "Smartthings"]
		input "saturationOption", "decimal", title: "Saturation (0..1) ", required: true, defaultValue: 0.95, range: "0..1"
		if (colorOption && thermoOut) {
			paragraph "Current temperature: ${getTemp()} ${colorSquare(getHex(getColor(getTemp(), getScale())), 15)}"
		}
	}
	section("<b>Debug</b>") {
		input "debugMode", "bool", title: "Debug Mode", submitOnChange: true
		if (debugMode) {
			input "debugLevel", "enum", title: "Debug level", options: [0, 1, 2], required: true, defaultValue: 0
			input "tempDebug", "number", title: "Temp override"
		}
	}
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
		subscribe(thermoOut, 'temperature', 'updateLight')
		subscribe(lights, 'switch', 'switchHandler')

		updateLight()
	}
}

String colorSquare(String hex, Integer size=20) {
	return "<svg width='${size}' height='${size}'><rect width='${size}' height='${size}' style='fill:${hex};stroke-width:3;stroke:rgb(0,0,0)' /></svg>"
}

void switchHandler(evt) {
	if (lights.any {it.latestValue("switch") == "on"}) {
		updateLight(evt)
		subscribe(thermoOut, 'temperature', 'updateLight')
	} else {
		unsubscribe(thermoOut)
	}
}

void updateLight(evt) {
	Float tempOut
	List<Integer> hsvTempColor = null

	tempOut = getTemp()
	if (tempOut) {
		hsvTempColor = getColor(tempOut, getScale())
		
		for (light in lights) {
			if (light.latestValue("switch") == "on") {
				if (debugMode) log.debug "Changing light $light"
				light.setColor(["hue": hsvTempColor[0], "saturation": hsvTempColor[1], "level": light.latestValue("level")])
			}
		}
		
		if (debugMode) log.debug "Set color ${getHex(hsvTempColor)} ${colorSquare(getHex(hsvTempColor), 18)}"
	}
}

List<Map> getScale() {
	if (colorOption == "Pete's") {
		if (debugMode) log.debug "Using Pete's colors"
		return tempScalePetes
	} else if (colorOption == "Weather Channel") {
		if (debugMode) log.debug "Using Weather Channel colors"
		return tempScaleWeatherchannel
	} else if (colorOption == "Spectrum") {
		if (debugMode) log.debug "Using Spectrum colors"
		return tempScaleSpectrum
	} else if (colorOption == "Spectrum 2") {
		if (debugMode) log.debug "Using Spectrum2 colors"
		return tempScaleSpectrum2
	} else if (colorOption == "Smartthings") {
		if (debugMode) log.debug "Using Smartthings colors"
		return tempScaleSmartthings
	} else {
		if (debugMode) log.debug "Defaulting to Pete's colors: colorOption = $colorOption"	
		return tempScalePetes
	}
}

Float getTemp() {
	Float temp = null

	if (thermoOut?.getStatus() != "ACTIVE") {
		log.warn "Temp sensor is not online, do nothing"	
	} else {
		if (!debugMode || tempDebug == null) {
			temp = thermoOut.latestValue("temperature")
			if (debugMode) log.debug "temp: $temp"
		} else {
			temp = tempDebug ?: thermoOut.latestValue("temperature")
			if (debugMode) log.debug "DEBUG temp: $temp"
		}
	}
	
	return temp
}

List<Integer> getColor(Float temp, List<Map> colorScale) {
	Integer tempLow = null
	Integer tempHigh = null
	Integer tempLast = null
	List<Integer> colorLow = null
	List<Integer> colorHigh = null
	List<Integer> colorLast = null
	List<Integer> colorReturn = null
	Float rangePercent
	Float satLevel = saturationOption
	
	if (satLevel > 1) {
		satLevel = 1.0
	} else if (satLevel < 0) {
		satLevel = 0
	}
	
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
	
	colorReturn = getColorOnRange(colorLow, colorHigh, rangePercent)

	// Adjust saturation to configured level
	colorReturn[1] = (colorReturn[1] * satLevel) as Integer
	return colorReturn
}

List<Integer> getColorOnRange(List<Integer> colorOne, List<Integer> colorTwo, Float percent) {
	List<Integer> hsvColorNew = [100, 100, 100]

	for (i in 0..1) { // for H and S (we don't need V)
		hsvColorNew[i] = Math.round(((colorTwo[i] - colorOne[i]) * percent) + colorOne[i])
	}
    
	if (debugMode) log.debug "colorOne = $colorOne; hsvColorNew = $hsvColorNew; colorTwo = $colorTwo"

	return hsvColorNew
}

String getHex(List<Integer> hsvColor) {
	if (debugMode) log.debug "getHex in: ${hsvColor}"
	return hubitat.helper.ColorUtils.rgbToHEX(hubitat.helper.ColorUtils.hsvToRGB(hsvColor))
}
