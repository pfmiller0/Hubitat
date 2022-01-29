/*
 *  P's Light Controls Parent app
 *
 *  Based on "Shade Control" example app from Hubitat
 *  https://community.hubitat.com/t/help-combining-three-shades-as-one/57028/12
 *  Copyright 2021 Hubitat Inc.  All Rights Reserved
 *
 */
definition(
	name: "P's Light Controls",
	namespace: "hyposphere.net",
	author: "Peter Miller",
	description: "Light Controls",
	category: "Convenience",
	singleInstance: true,
	iconUrl: "",
	iconX2Url: "",
	installOnOpen: true
)

preferences {
	page(name: "mainPage")
  	page(name: "removePage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: false) {
		section {
			app(name: "childMultiLevel", appName: "Multi-level Light", namespace: "hyposphere.net", title: "Create New Multi-level Light", multiple: true, displayChildApps: true)
			paragraph " "
			href "removePage", title: "Remove all Multi-level Lights", description: ""
		}
		section {
			app(name: "childLightToggle", appName: "Two Light Toggle", namespace: "hyposphere.net", title: "Create New Two Light Toggle", multiple: true, displayChildApps: true)
			paragraph " "
			href "removePage", title: "Remove all Two Light Toggles", description: ""
		}
		section {
			app(name: "childWeatherLight", appName: "Weather Light", namespace: "hyposphere.net", title: "Create New Weather Light", multiple: true, displayChildApps: true)
			paragraph " "
			href "removePage", title: "Remove all Weather ", description: ""
		}
	}
}

def removePage() {
	dynamicPage(name: "removePage", title: "Remove all Light Controls", install: false, uninstall: true) {
		section ("WARNING!\n\nRemoving removes all Light Controls\n") {
		}
	}
}

def installed() {
}

def updated() {
}
