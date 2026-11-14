package io.github.seektop.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

fun createNodeSeekDatabase(directory: File): NodeSeekDatabase =
    Room.databaseBuilder<NodeSeekDatabase>(name = File(directory, "nodeseek.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*NODESEEK_MIGRATIONS)
        .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()
