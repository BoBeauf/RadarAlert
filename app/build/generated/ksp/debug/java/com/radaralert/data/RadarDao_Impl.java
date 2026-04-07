package com.radaralert.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class RadarDao_Impl implements RadarDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<RadarEntity> __insertionAdapterOfRadarEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  private final SharedSQLiteStatement __preparedStmtOfDeleteBySource;

  public RadarDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfRadarEntity = new EntityInsertionAdapter<RadarEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `radars` (`id`,`latitude`,`longitude`,`type`,`speedLimit`,`speedLimitHgv`,`department`,`city`,`route`,`direction`,`equipment`,`installDate`,`sectionLengthKm`,`gridLat`,`gridLng`,`source`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RadarEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindDouble(2, entity.getLatitude());
        statement.bindDouble(3, entity.getLongitude());
        statement.bindString(4, entity.getType());
        if (entity.getSpeedLimit() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getSpeedLimit());
        }
        if (entity.getSpeedLimitHgv() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getSpeedLimitHgv());
        }
        statement.bindString(7, entity.getDepartment());
        statement.bindString(8, entity.getCity());
        statement.bindString(9, entity.getRoute());
        statement.bindString(10, entity.getDirection());
        statement.bindString(11, entity.getEquipment());
        statement.bindString(12, entity.getInstallDate());
        statement.bindString(13, entity.getSectionLengthKm());
        statement.bindLong(14, entity.getGridLat());
        statement.bindLong(15, entity.getGridLng());
        statement.bindString(16, entity.getSource());
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM radars";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteBySource = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM radars WHERE source = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertAll(final List<RadarEntity> radars,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfRadarEntity.insert(radars);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteBySource(final String source, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteBySource.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, source);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteBySource.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getRadarsInGridRange(final int minGridLat, final int maxGridLat,
      final int minGridLng, final int maxGridLng,
      final Continuation<? super List<RadarEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM radars\n"
            + "        WHERE gridLat BETWEEN ? AND ?\n"
            + "        AND gridLng BETWEEN ? AND ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 4);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, minGridLat);
    _argIndex = 2;
    _statement.bindLong(_argIndex, maxGridLat);
    _argIndex = 3;
    _statement.bindLong(_argIndex, minGridLng);
    _argIndex = 4;
    _statement.bindLong(_argIndex, maxGridLng);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<RadarEntity>>() {
      @Override
      @NonNull
      public List<RadarEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfSpeedLimit = CursorUtil.getColumnIndexOrThrow(_cursor, "speedLimit");
          final int _cursorIndexOfSpeedLimitHgv = CursorUtil.getColumnIndexOrThrow(_cursor, "speedLimitHgv");
          final int _cursorIndexOfDepartment = CursorUtil.getColumnIndexOrThrow(_cursor, "department");
          final int _cursorIndexOfCity = CursorUtil.getColumnIndexOrThrow(_cursor, "city");
          final int _cursorIndexOfRoute = CursorUtil.getColumnIndexOrThrow(_cursor, "route");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfEquipment = CursorUtil.getColumnIndexOrThrow(_cursor, "equipment");
          final int _cursorIndexOfInstallDate = CursorUtil.getColumnIndexOrThrow(_cursor, "installDate");
          final int _cursorIndexOfSectionLengthKm = CursorUtil.getColumnIndexOrThrow(_cursor, "sectionLengthKm");
          final int _cursorIndexOfGridLat = CursorUtil.getColumnIndexOrThrow(_cursor, "gridLat");
          final int _cursorIndexOfGridLng = CursorUtil.getColumnIndexOrThrow(_cursor, "gridLng");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final List<RadarEntity> _result = new ArrayList<RadarEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RadarEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final double _tmpLatitude;
            _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            final double _tmpLongitude;
            _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final Integer _tmpSpeedLimit;
            if (_cursor.isNull(_cursorIndexOfSpeedLimit)) {
              _tmpSpeedLimit = null;
            } else {
              _tmpSpeedLimit = _cursor.getInt(_cursorIndexOfSpeedLimit);
            }
            final Integer _tmpSpeedLimitHgv;
            if (_cursor.isNull(_cursorIndexOfSpeedLimitHgv)) {
              _tmpSpeedLimitHgv = null;
            } else {
              _tmpSpeedLimitHgv = _cursor.getInt(_cursorIndexOfSpeedLimitHgv);
            }
            final String _tmpDepartment;
            _tmpDepartment = _cursor.getString(_cursorIndexOfDepartment);
            final String _tmpCity;
            _tmpCity = _cursor.getString(_cursorIndexOfCity);
            final String _tmpRoute;
            _tmpRoute = _cursor.getString(_cursorIndexOfRoute);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpEquipment;
            _tmpEquipment = _cursor.getString(_cursorIndexOfEquipment);
            final String _tmpInstallDate;
            _tmpInstallDate = _cursor.getString(_cursorIndexOfInstallDate);
            final String _tmpSectionLengthKm;
            _tmpSectionLengthKm = _cursor.getString(_cursorIndexOfSectionLengthKm);
            final int _tmpGridLat;
            _tmpGridLat = _cursor.getInt(_cursorIndexOfGridLat);
            final int _tmpGridLng;
            _tmpGridLng = _cursor.getInt(_cursorIndexOfGridLng);
            final String _tmpSource;
            _tmpSource = _cursor.getString(_cursorIndexOfSource);
            _item = new RadarEntity(_tmpId,_tmpLatitude,_tmpLongitude,_tmpType,_tmpSpeedLimit,_tmpSpeedLimitHgv,_tmpDepartment,_tmpCity,_tmpRoute,_tmpDirection,_tmpEquipment,_tmpInstallDate,_tmpSectionLengthKm,_tmpGridLat,_tmpGridLng,_tmpSource);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM radars";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getFirstRadar(final Continuation<? super RadarEntity> $completion) {
    final String _sql = "SELECT * FROM radars LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<RadarEntity>() {
      @Override
      @Nullable
      public RadarEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfSpeedLimit = CursorUtil.getColumnIndexOrThrow(_cursor, "speedLimit");
          final int _cursorIndexOfSpeedLimitHgv = CursorUtil.getColumnIndexOrThrow(_cursor, "speedLimitHgv");
          final int _cursorIndexOfDepartment = CursorUtil.getColumnIndexOrThrow(_cursor, "department");
          final int _cursorIndexOfCity = CursorUtil.getColumnIndexOrThrow(_cursor, "city");
          final int _cursorIndexOfRoute = CursorUtil.getColumnIndexOrThrow(_cursor, "route");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfEquipment = CursorUtil.getColumnIndexOrThrow(_cursor, "equipment");
          final int _cursorIndexOfInstallDate = CursorUtil.getColumnIndexOrThrow(_cursor, "installDate");
          final int _cursorIndexOfSectionLengthKm = CursorUtil.getColumnIndexOrThrow(_cursor, "sectionLengthKm");
          final int _cursorIndexOfGridLat = CursorUtil.getColumnIndexOrThrow(_cursor, "gridLat");
          final int _cursorIndexOfGridLng = CursorUtil.getColumnIndexOrThrow(_cursor, "gridLng");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final RadarEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final double _tmpLatitude;
            _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            final double _tmpLongitude;
            _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final Integer _tmpSpeedLimit;
            if (_cursor.isNull(_cursorIndexOfSpeedLimit)) {
              _tmpSpeedLimit = null;
            } else {
              _tmpSpeedLimit = _cursor.getInt(_cursorIndexOfSpeedLimit);
            }
            final Integer _tmpSpeedLimitHgv;
            if (_cursor.isNull(_cursorIndexOfSpeedLimitHgv)) {
              _tmpSpeedLimitHgv = null;
            } else {
              _tmpSpeedLimitHgv = _cursor.getInt(_cursorIndexOfSpeedLimitHgv);
            }
            final String _tmpDepartment;
            _tmpDepartment = _cursor.getString(_cursorIndexOfDepartment);
            final String _tmpCity;
            _tmpCity = _cursor.getString(_cursorIndexOfCity);
            final String _tmpRoute;
            _tmpRoute = _cursor.getString(_cursorIndexOfRoute);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpEquipment;
            _tmpEquipment = _cursor.getString(_cursorIndexOfEquipment);
            final String _tmpInstallDate;
            _tmpInstallDate = _cursor.getString(_cursorIndexOfInstallDate);
            final String _tmpSectionLengthKm;
            _tmpSectionLengthKm = _cursor.getString(_cursorIndexOfSectionLengthKm);
            final int _tmpGridLat;
            _tmpGridLat = _cursor.getInt(_cursorIndexOfGridLat);
            final int _tmpGridLng;
            _tmpGridLng = _cursor.getInt(_cursorIndexOfGridLng);
            final String _tmpSource;
            _tmpSource = _cursor.getString(_cursorIndexOfSource);
            _result = new RadarEntity(_tmpId,_tmpLatitude,_tmpLongitude,_tmpType,_tmpSpeedLimit,_tmpSpeedLimitHgv,_tmpDepartment,_tmpCity,_tmpRoute,_tmpDirection,_tmpEquipment,_tmpInstallDate,_tmpSectionLengthKm,_tmpGridLat,_tmpGridLng,_tmpSource);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCountBySource(final String source,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM radars WHERE source = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, source);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
