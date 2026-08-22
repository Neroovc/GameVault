package com.gamevault.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 9,
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN in_library INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN local_cover_path TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN changelog TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN dev_links TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN download_links TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN publisher TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN last_checked INTEGER")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN update_available INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): GameVaultDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                GameVaultDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
