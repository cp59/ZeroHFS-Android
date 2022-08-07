package com.zeroapp.zerohfs;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class SQLDataBaseHelper extends SQLiteOpenHelper {

    private static final String DataBaseName = "AppDataBase";
    private static final int DataBaseVersion = 1;

    public SQLDataBaseHelper(@Nullable Context context, @Nullable String name, @Nullable SQLiteDatabase.CursorFactory factory, int version,String TableName) {
        super(context, DataBaseName, null, DataBaseVersion);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String SqlTable = "CREATE TABLE IF NOT EXISTS Servers (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "serverUrl text not null," +
                "account TEXT," +
                "password TEXT" +
                ")";
        sqLiteDatabase.execSQL(SqlTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        final String SQL = "DROP TABLE Servers";
        sqLiteDatabase.execSQL(SQL);
    }
}