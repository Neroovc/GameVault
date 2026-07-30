package com.gamevault.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gamevault.app.data.local.converter.Converters
import com.gamevault.app.data.local.dao.CollectionDao
import com.gamevault.app.data.local.dao.GameCollectionDao
import com.gamevault.app.data.local.dao.GameDao
import com.gamevault.app.data.local.dao.GameRouteDao
import com.gamevault.app.data.local.dao.GameTagDao
import com.gamevault.app.data.local.dao.PlaySessionDao
import com.gamevault.app.data.local.dao.TagDao
import com.gamevault.app.data.local.entity.CollectionEntity
import com.gamevault.app.data.local.entity.GameCollectionCrossRef
import com.gamevault.app.data.local.entity.GameEntity
import com.gamevault.app.data.local.entity.GameRouteEntity
import com.gamevault.app.data.local.entity.GameTagCrossRef
import com.gamevault.app.data.local.entity.PlaySessionEntity
import com.gamevault.app.data.local.entity.TagEntity

@Database(
    entities = [
        GameEntity::class,
        GameRouteEntity::class,
        PlaySessionEntity::class,
        TagEntity::class,
        CollectionEntity::class,
        GameTagCrossRef::class,
        GameCollectionCrossRef::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class GameVaultDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao
    abstract fun gameRouteDao(): GameRouteDao
    abstract fun playSessionDao(): PlaySessionDao
    abstract fun tagDao(): TagDao
    abstract fun collectionDao(): CollectionDao
    abstract fun gameCollectionDao(): GameCollectionDao
    abstract fun gameTagDao(): GameTagDao

    companion object {
        private const val DB_NAME = "gamevault.db"

        fun create(context: Context): GameVaultDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                GameVaultDatabase::class.java,
                DB_NAME,
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
