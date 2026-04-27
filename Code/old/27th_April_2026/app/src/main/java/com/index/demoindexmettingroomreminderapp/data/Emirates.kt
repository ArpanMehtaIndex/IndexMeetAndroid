package com.index.demoindexmettingroomreminderapp.data

enum class Emirate(val displayName: String) {
    ABU_DHABI("Abu Dhabi Meeting Room"),
    DUBAI("Dubai Meeting Room"),
    SHARJAH("Sharjah Meeting Room"),
    RAS_AL_KHAIMAH("Ras Al Khaimah Meeting Room"),
    DESIGN("INDEX Design Meeting Room");
    // Optional: To easily get an Emirate from its display name
    companion object {
        fun fromDisplayName(name: String): Emirate? {
            return entries.find { it.displayName == name }
        }
    }
}