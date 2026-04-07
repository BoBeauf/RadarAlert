package com.radaralert.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class RadarDatabase_Impl extends RadarDatabase {
  private volatile RadarDao _radarDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(3) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `radars` (`id` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `type` TEXT NOT NULL, `speedLimit` INTEGER, `speedLimitHgv` INTEGER, `department` TEXT NOT NULL, `city` TEXT NOT NULL, `route` TEXT NOT NULL, `direction` TEXT NOT NULL, `equipment` TEXT NOT NULL, `installDate` TEXT NOT NULL, `sectionLengthKm` TEXT NOT NULL, `gridLat` INTEGER NOT NULL, `gridLng` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_radars_gridLat_gridLng` ON `radars` (`gridLat`, `gridLng`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '445e5aadfe77b74f1106c5e34bf2427a')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `radars`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsRadars = new HashMap<String, TableInfo.Column>(16);
        _columnsRadars.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("latitude", new TableInfo.Column("latitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("longitude", new TableInfo.Column("longitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("speedLimit", new TableInfo.Column("speedLimit", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("speedLimitHgv", new TableInfo.Column("speedLimitHgv", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("department", new TableInfo.Column("department", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("city", new TableInfo.Column("city", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("route", new TableInfo.Column("route", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("direction", new TableInfo.Column("direction", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("equipment", new TableInfo.Column("equipment", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("installDate", new TableInfo.Column("installDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("sectionLengthKm", new TableInfo.Column("sectionLengthKm", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("gridLat", new TableInfo.Column("gridLat", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("gridLng", new TableInfo.Column("gridLng", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRadars.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRadars = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRadars = new HashSet<TableInfo.Index>(1);
        _indicesRadars.add(new TableInfo.Index("index_radars_gridLat_gridLng", false, Arrays.asList("gridLat", "gridLng"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoRadars = new TableInfo("radars", _columnsRadars, _foreignKeysRadars, _indicesRadars);
        final TableInfo _existingRadars = TableInfo.read(db, "radars");
        if (!_infoRadars.equals(_existingRadars)) {
          return new RoomOpenHelper.ValidationResult(false, "radars(com.radaralert.data.RadarEntity).\n"
                  + " Expected:\n" + _infoRadars + "\n"
                  + " Found:\n" + _existingRadars);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "445e5aadfe77b74f1106c5e34bf2427a", "ed47d250dd972ffdb53ea99b48a7b037");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "radars");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `radars`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(RadarDao.class, RadarDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public RadarDao radarDao() {
    if (_radarDao != null) {
      return _radarDao;
    } else {
      synchronized(this) {
        if(_radarDao == null) {
          _radarDao = new RadarDao_Impl(this);
        }
        return _radarDao;
      }
    }
  }
}
