package com.aladdin.geniebox.domain.model

data class ControllerProfile(
    val playerId: Int,                      // 1, 2, 3, 4
    val deviceDescriptor: String,           // 裝置硬體識別碼 (Descriptor / Product+Vendor)
    val deviceName: String,                 // 裝置顯示名稱 (例如 "Xbox Wireless Controller")
    val inputDeviceId: Int = -1,            // Android InputDevice.id (暫時性，重連會改變)
    val isConnected: Boolean = false,
    val deadzone: Float = 0.20f,            // 類比搖桿死區 (預設 20%)
    val buttonMapping: Map<Int, Int> = emptyMap(), // Android KeyCode -> RetroPad KeyCode
    val vibrationEnabled: Boolean = true
)
