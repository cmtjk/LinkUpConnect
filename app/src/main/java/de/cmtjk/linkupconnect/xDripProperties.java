package de.cmtjk.linkupconnect;

public enum xDripProperties {
    ACTION("com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING"),
    GLUCOSE("glucose"),
    TIMESTAMP("timestamp"),
    SENSOR_SERIAL("sensorSerial"),
    BLE_MANAGER("bleManager");

    final String value;

    xDripProperties(String value) {
        this.value = value;
    }
}
