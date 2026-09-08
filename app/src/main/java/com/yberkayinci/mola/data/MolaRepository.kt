package com.yberkayinci.mola.data

interface MolaRepository {
    fun loadProfile(): UserProfile?
    fun saveProfile(profile: UserProfile)
    fun loadRecords(): List<InterventionRecord>
    fun appendRecord(record: InterventionRecord)
    fun replaceRecords(records: List<InterventionRecord>)
    fun clearAll()
}
