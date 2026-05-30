package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "launcher_settings")
data class LauncherSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "pinned_apps", primaryKeys = ["packageName", "isDock"])
data class PinnedApp(
    val packageName: String,
    val label: String,
    val position: Int,
    val isDock: Boolean
)

@Entity(tableName = "hidden_apps")
data class HiddenApp(
    @PrimaryKey val packageName: String
)

@Dao
interface LauncherDao {
    @Query("SELECT * FROM launcher_settings")
    fun getAllSettings(): Flow<List<LauncherSetting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: LauncherSetting)

    @Query("DELETE FROM launcher_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)

    @Query("SELECT * FROM pinned_apps ORDER BY position ASC")
    fun getPinnedApps(): Flow<List<PinnedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedApp(app: PinnedApp)

    @Delete
    suspend fun deletePinnedApp(app: PinnedApp)

    @Query("DELETE FROM pinned_apps WHERE packageName = :packageName")
    suspend fun deletePinnedAppByPackage(packageName: String)

    @Query("DELETE FROM pinned_apps WHERE packageName = :packageName AND isDock = :isDock")
    suspend fun deletePinnedAppByPackageAndDock(packageName: String, isDock: Boolean)

    @Query("SELECT * FROM hidden_apps")
    fun getHiddenApps(): Flow<List<HiddenApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiddenApp(app: HiddenApp)

    @Query("DELETE FROM hidden_apps WHERE packageName = :packageName")
    suspend fun deleteHiddenApp(packageName: String)
}

@Database(entities = [LauncherSetting::class, PinnedApp::class, HiddenApp::class], version = 1, exportSchema = false)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun launcherDao(): LauncherDao

    companion object {
        @Volatile
        private var INSTANCE: LauncherDatabase? = null

        fun getDatabase(context: Context): LauncherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LauncherDatabase::class.java,
                    "launcher_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class LauncherRepository(private val launcherDao: LauncherDao) {
    val settings: Flow<List<LauncherSetting>> = launcherDao.getAllSettings()
    val pinnedApps: Flow<List<PinnedApp>> = launcherDao.getPinnedApps()
    val hiddenApps: Flow<List<HiddenApp>> = launcherDao.getHiddenApps()

    suspend fun setSetting(key: String, value: String) {
        launcherDao.insertSetting(LauncherSetting(key, value))
    }

    suspend fun deleteSetting(key: String) {
        launcherDao.deleteSetting(key)
    }

    suspend fun pinApp(packageName: String, label: String, position: Int, isDock: Boolean) {
        launcherDao.insertPinnedApp(PinnedApp(packageName, label, position, isDock))
    }

    suspend fun unpinApp(app: PinnedApp) {
        launcherDao.deletePinnedApp(app)
    }

    suspend fun unpinAppByPackage(packageName: String) {
        launcherDao.deletePinnedAppByPackage(packageName)
    }

    suspend fun unpinAppByPackage(packageName: String, isDock: Boolean) {
        launcherDao.deletePinnedAppByPackageAndDock(packageName, isDock)
    }

    suspend fun hideApp(packageName: String) {
        launcherDao.insertHiddenApp(HiddenApp(packageName))
    }

    suspend fun unhideApp(packageName: String) {
        launcherDao.deleteHiddenApp(packageName)
    }
}
