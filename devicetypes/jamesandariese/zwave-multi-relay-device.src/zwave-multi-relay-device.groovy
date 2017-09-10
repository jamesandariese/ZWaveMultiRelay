/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {

    definition (name: "Z-Wave Multi Relay Device", namespace: "jamesandariese", author: "James Andariese") {
        capability "Switch"
        capability "Refresh"
	}

	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "off", label: '${currentValue}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
			state "on", label: '${currentValue}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC"
		}
        
		childDeviceTile("outlet1Toggle", "outlet1", height: 1, width: 1, childTileName: "switch")
		childDeviceTile("outlet2Toggle", "outlet2", height: 1, width: 1, childTileName: "switch")
        
        standardTile("refresh", "device.switch", width: 1, height: 1, inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

        main "switch"
    }
}

def updated() {
    installed()
}

def installed() {
    if (!childDevices) {
        for (i in 1..2) {
        addChildDevice(
            "Multi Channel Device Relay",
            "${device.deviceNetworkId}.$i",
            null,
            [completedSetup: true, label: "${device.label} $i", componentName: "outlet$i", componentLabel: "$i"]).displayName = "$device.displayName $i"
        }
    }
}

def on() {
    childDevices.each{it.on()}
}

def off() {
    childDevices.each{it.off()}
}

def refresh() {
   	def cmds = (1..2).collect{new physicalgraph.device.HubAction(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:it).encapsulate(zwave.switchBinaryV1.switchBinaryGet()).format())}
	delayBetween(cmds, 100)
}

/*
off parsing zw device: 08, command: 600D, payload: 01 01 25 03 00
on  parsing zw device: 08, command: 600D, payload: 01 01 25 03 FF
off parsing zw device: 08, command: 600D, payload: 01 01 25 03 00
off parsing zw device: 08, command: 600D, payload: 02 01 25 03 00
on  parsing zw device: 08, command: 600D, payload: 02 01 25 03 FF

092c4b31-8058-4d9f-8bf1-4d3f6639acc3  8:20:44 PM: debug Parsed MultiChannelCmdEncap(bitAddress: false, command: 3, commandClass: 37, destinationEndPoint: 1, parameter: [0], sourceEndPoint: 2) to []
092c4b31-8058-4d9f-8bf1-4d3f6639acc3  8:20:44 PM: debug MultiChannelCmdEncap: MultiChannelCmdEncap(bitAddress: false, command: 3, commandClass: 37, destinationEndPoint: 1, parameter: [0], sourceEndPoint: 2)
092c4b31-8058-4d9f-8bf1-4d3f6639acc3  8:20:44 PM: debug zwave parsed command: MultiChannelCmdEncap(bitAddress: false, command: 3, commandClass: 37, destinationEndPoint: 1, parameter: [0], sourceEndPoint: 2)
092c4b31-8058-4d9f-8bf1-4d3f6639acc3  8:20:44 PM: debug null parsing zw device: 08, command: 600D, payload: 02 01 25 03 00
092c4b31-8058-4d9f-8bf1-4d3f6639acc3  8:20:43 PM: debug Parsed MultiChannelCmdEncap(bitAddress: false, command: 3, commandClass: 37, destinationEndPoint: 1, parameter: [255], sourceEndPoint: 2) to []
092c4b31-8058-4d9f-8bf1-4d3f6639acc3  8:20:43 PM: debug MultiChannelCmdEncap: MultiChannelCmdEncap(bitAddress: false, command: 3, commandClass: 37, destinationEndPoint: 1, parameter: [255], sourceEndPoint: 2)
092c4b31-8058-4d9f-8bf1-4d3f6639acc3  8:20:43 PM: debug zwave parsed command: MultiChannelCmdEncap(bitAddress: false, command: 3, commandClass: 37, destinationEndPoint: 1, parameter: [255], sourceEndPoint: 2)
092c4b31-8058-4d9f-8bf1-4d3f6639acc3  8:20:43 PM: debug null parsing zw device: 08, command: 600D, payload: 02 01 25 03 FF

*/

def logStates() {
    childDevices.each{
        def st = it.currentState("switch").value
        log.debug "$it state: $st"
    }
}

def refreshStates() {
     def off = true
     childDevices.each{
         if (it.currentState("switch").value == "on") {
             off = false
         }
     }
     sendEvent(name: "switch", value: off? "off":"on")
}

def parse(description) {
    log.debug "parsing $description"
    
    def cmd = zwave.parse(description, [0x60: 3])
    log.debug "zwave parsed command: ${cmd}"
    if (cmd) {
        zwaveEvent(cmd)
        log.debug "Parsed ${cmd} to ${result.inspect()}"
    } else {
        log.debug "Non-parsed event: ${description}"
    }
    refreshStates()
    null
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    if (cmd.commandClass == 37 || cmd.commandClass == 32) {
        def child = childDevices.find{it.deviceNetworkId.endsWith(".${cmd.sourceEndPoint}")}
        if (!child) {
            log.debug "Couldn't find a child device matching DNI ending with .${cmd.sourceEndPoint}"
            return
        }
        def value = cmd.parameter[0] ? "on" : "off"
        log.debug "Sending $value on switch $child"
        child.sendEvent(name: "switch", value: cmd.parameter[0] ? "on" : "off")
    }
    null
}

def childOnOff(dni, value) {
    log.debug "childOnOff($dni, $value)"

    def splitted = dni.split("\\.")
    log.debug "Splitted into ${splitted}"
    def endpoint = dni.split("\\.").last() as Integer
    
   	def actions = [
			new physicalgraph.device.HubAction(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:endpoint).encapsulate(zwave.basicV1.basicSet(value: value)).format()),
			new physicalgraph.device.HubAction(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:endpoint).encapsulate(zwave.switchBinaryV1.switchBinaryGet()).format()),
	]
	sendHubCommand(actions, 500)

}

def childOn(dni) {
    log.debug "childOn($dni)"
    childOnOff(dni, 0xFF)
}

def childOff(dni) {
    log.debug "childOn($dni)"
    childOnOff(dni, 0)
}
