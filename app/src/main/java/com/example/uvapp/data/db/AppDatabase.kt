package com.example.uvapp.data.db

/** Database boundary. This becomes a RoomDatabase when Room is configured. */
interface AppDatabase {
    fun uvReadingDao(): UvReadingDao
}
