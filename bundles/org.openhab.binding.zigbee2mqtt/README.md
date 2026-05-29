# Zigbee2MQTT Binding

This binding integrates Zigbee devices with openHAB via a [Zigbee2MQTT](https://www.zigbee2mqtt.io/) instance.
It connects directly to your MQTT broker, monitors the Zigbee2MQTT bridge, and dynamically creates Things and channels from the Zigbee2MQTT device metadata — no manual channel configuration needed.

## Supported Things

| Thing Type        | Description                                                  |
|-------------------|--------------------------------------------------------------|
| `bridge`          | The Zigbee2MQTT bridge (connection to the MQTT broker)       |
| `light`           | A Zigbee light or bulb                                       |
| `plug`            | A Zigbee smart plug with power monitoring                    |
| `switch`          | A Zigbee switch or relay                                     |
| `climate-sensor`  | A temperature and/or humidity sensor                         |
| `motion-sensor`   | An occupancy or motion sensor                                |
| `contact-sensor`  | A door or window contact sensor                              |
| `water-sensor`    | A water leak / flood sensor                                  |
| `air-sensor`      | An air quality sensor (PM2.5, VOC, CO2, etc.)                |
| `thermostat`      | A thermostat or TRV (thermostatic radiator valve)            |
| `device`          | A generic Zigbee device (fallback for unclassified devices)  |

Device thing types are inferred automatically from the device's feature set during discovery.

## Discovery

Once the bridge is online, scanning the inbox will request the device list from Zigbee2MQTT and create one Thing per device.
For each device the binding walks the Zigbee2MQTT feature tree (including container features like `light`, `switch`, `climate`, `fan`, `lock`) and creates the matching openHAB channels with proper item types, labels, and read-only flags.

Multi-endpoint devices (e.g. dual-channel lights) get unique channel IDs with endpoint suffixes such as `brightness-white` and `brightness-rgb`.

## Bridge Configuration

| Parameter   | Type    | Required | Default       | Description                                            |
|-------------|---------|----------|---------------|--------------------------------------------------------|
| `host`      | text    | yes      | —             | MQTT broker hostname or IP address                     |
| `port`      | int     | no       | `1883`        | MQTT broker TCP port                                   |
| `username`  | text    | no       | —             | MQTT broker username (leave empty if not required)     |
| `password`  | text    | no       | —             | MQTT broker password                                   |
| `baseTopic` | text    | no       | `zigbee2mqtt` | The MQTT base topic Zigbee2MQTT publishes to           |
| `secure`    | boolean | no       | `false`       | Enable TLS for the MQTT connection                     |
| `keepAlive` | int     | no       | `60`          | MQTT keep-alive interval in seconds                    |

## Device Configuration

| Parameter    | Type | Required | Description                                                              |
|--------------|------|----------|--------------------------------------------------------------------------|
| `deviceName` | text | yes      | The device's `friendly_name` from Zigbee2MQTT (used as the MQTT topic)   |

## Bridge Channels

| Channel       | Type     | Description                                                                              |
|---------------|----------|------------------------------------------------------------------------------------------|
| `permit-join` | Switch   | Allow new Zigbee devices to join the network                                             |
| `log-level`   | String   | Zigbee2MQTT log level — options: `debug`, `info`, `warn`, `error`                        |

## Device Channels

Device channels are created automatically based on the Zigbee2MQTT feature definitions. The most common ones:

### Control

| Channel ID          | Item Type | Description                                       |
|---------------------|-----------|---------------------------------------------------|
| `state`             | Switch    | On/off state                                      |
| `brightness`        | Dimmer    | Brightness (0–100 %)                              |
| `color-temperature` | Dimmer    | Color temperature (warm to cool, 0–100 %)         |
| `color`             | Color     | RGB color (hue, saturation, brightness)           |

### Climate Sensors

| Channel ID    | Item Type           | Description              |
|---------------|---------------------|--------------------------|
| `temperature` | Number:Temperature  | Measured temperature     |
| `humidity`    | Number:Dimensionless| Measured humidity (%)    |
| `pressure`    | Number:Pressure     | Atmospheric pressure     |

### Climate Control (Thermostats / TRVs)

| Channel ID                  | Item Type           | Description                              |
|-----------------------------|---------------------|------------------------------------------|
| `local-temperature`         | Number:Temperature  | Thermostat's measured temperature        |
| `occupied-heating-setpoint` | Number:Temperature  | Heating setpoint                         |
| `occupied-cooling-setpoint` | Number:Temperature  | Cooling setpoint                         |
| `current-heating-setpoint`  | Number:Temperature  | Current heating target                   |
| `system-mode`               | String              | `off` / `auto` / `heat` / `cool` / …     |
| `running-state`             | String              | `idle` / `heat` / `cool`                 |
| `preset`                    | String              | `manual` / `schedule` / `boost` / …      |
| `pi-heating-demand`         | Number:Dimensionless| Valve heating demand (%)                 |

### Binary Sensors

| Channel ID    | Item Type | Description              |
|---------------|-----------|--------------------------|
| `contact`     | Contact   | Door/window contact      |
| `occupancy`   | Switch    | Occupancy detection      |
| `motion`      | Switch    | Motion detection         |
| `water-leak`  | Switch    | Water leak detection     |
| `smoke`       | Switch    | Smoke detection          |
| `tamper`      | Switch    | Tamper detection         |
| `illuminance` | Number:Illuminance | Light level (lux)|

### Air Quality

| Channel ID  | Item Type        | Description                          |
|-------------|------------------|--------------------------------------|
| `pm25`      | Number:Density   | PM2.5 particulate matter (µg/m³)     |
| `voc-index` | Number:Dimensionless | Volatile organic compound index  |

### Power Monitoring

| Channel ID | Item Type                | Description     |
|------------|--------------------------|-----------------|
| `power`    | Number:Power             | Power draw (W)  |
| `energy`   | Number:Energy            | Energy (kWh)    |
| `voltage`  | Number:ElectricPotential | Voltage         |
| `current`  | Number:ElectricCurrent   | Current         |

### Device Metadata

| Channel ID    | Item Type             | Description                  |
|---------------|-----------------------|------------------------------|
| `battery`     | Number:Dimensionless  | Battery level (%)            |
| `battery-low` | Switch                | Battery low warning          |
| `linkquality` | Number:Dimensionless  | Zigbee signal quality (0–255)|

### Trigger Channels

| Channel ID | Kind    | Description                                                       |
|------------|---------|-------------------------------------------------------------------|
| `action`   | trigger | Events from buttons and remotes (`single`, `double`, `long`, …)   |

Trigger channels fire openHAB events that can be used in rules — they don't hold state.

## Device Availability

The binding subscribes to `<baseTopic>/+/availability` and automatically updates each device's thing status to `ONLINE` or `OFFLINE` when Zigbee2MQTT reports an availability change.

Battery-powered devices may show as `UNKNOWN` (`Waiting for device to report…`) until they wake up and send their first message — this is normal Zigbee behaviour for sleeping end devices and can take several hours for very low-duty sensors.

## Example

### Thing file (`zigbee2mqtt.things`)

```java
Bridge zigbee2mqtt:bridge:home "Zigbee2MQTT" [
    host="192.168.1.10",
    baseTopic="zigbee2mqtt"
] {
    Thing climate-sensor bedroom "Bedroom Sensor"   [ deviceName="bedroom_sensor" ]
    Thing light          ceiling "Ceiling Light"    [ deviceName="living_room_ceiling" ]
    Thing plug           tv      "TV Plug"          [ deviceName="tv_plug" ]
    Thing motion-sensor  hallway "Hallway Motion"   [ deviceName="hallway_motion" ]
}
```

### Items file (`zigbee2mqtt.items`)

```java
Number:Temperature  Bedroom_Temperature  "Bedroom [%.1f °C]"  <temperature>  { channel="zigbee2mqtt:climate-sensor:home:bedroom:temperature" }
Number:Dimensionless Bedroom_Humidity    "Bedroom [%.0f %%]"  <humidity>     { channel="zigbee2mqtt:climate-sensor:home:bedroom:humidity" }
Switch              Ceiling_Light        "Ceiling Light"      <light>        { channel="zigbee2mqtt:light:home:ceiling:state" }
Dimmer              Ceiling_Brightness   "Ceiling Brightness" <slider>       { channel="zigbee2mqtt:light:home:ceiling:brightness" }
Switch              Permit_Join          "Permit Join"        <network>      { channel="zigbee2mqtt:bridge:home:permit-join" }
```

### Rule using a trigger channel

```java
rule "Button press"
when
    Channel "zigbee2mqtt:device:home:remote:action" triggered
then
    val event = receivedEvent.event
    logInfo("remote", "Action: " + event)
    if (event == "single") Ceiling_Light.sendCommand(ON)
end
```

## Prerequisites

- A running [Zigbee2MQTT](https://www.zigbee2mqtt.io/) instance (1.x or 2.x).
- An MQTT broker (e.g. Mosquitto) reachable from openHAB.
- Zigbee2MQTT must be publishing to the configured `baseTopic` (default `zigbee2mqtt`).
