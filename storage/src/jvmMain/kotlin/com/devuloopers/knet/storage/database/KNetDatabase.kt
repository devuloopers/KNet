package com.devuloopers.knet.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.devuloopers.knet.storage.apistudio.dao.CollectionDao
import com.devuloopers.knet.storage.apistudio.entity.CollectionEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity
import com.devuloopers.knet.storage.apistudio.entity.SavedRequestEntity
import com.devuloopers.knet.storage.traffic.dao.HttpTransactionDao
import com.devuloopers.knet.storage.traffic.entity.HttpTransactionEntity

/**
 * Room Database contract definition for KNet JVM Desktop persistence.
 */
@Database(
    entities = [
        HttpTransactionEntity::class,
        CollectionEntity::class,
        CollectionFolderEntity::class,
        SavedRequestEntity::class
    ],
    version = 5
)
public abstract class KNetDatabase : RoomDatabase() {

    public abstract fun httpTransactionDao(): HttpTransactionDao

    public abstract fun collectionDao(): CollectionDao
}
