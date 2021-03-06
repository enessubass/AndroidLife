/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package alpha1.android.arch.persistence.room;

import alpha1.android.arch.core.executor.AppToolkitTaskExecutor;
import alpha1.android.arch.persistence.db.SupportSQLiteDatabase;
import alpha1.android.arch.persistence.db.SupportSQLiteOpenHelper;
import alpha1.android.arch.persistence.db.SupportSQLiteQuery;
import alpha1.android.arch.persistence.db.SupportSQLiteStatement;
import alpha1.android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.v4.util.SparseArrayCompat;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all Room databases. All classes that are annotated with {link Database} must
 * extend this class.
 * <p>
 * RoomDatabase provides direct access to the underlying database implementation but you should
 * prefer using {link Dao} classes.
 *
 * see Database
 */
@SuppressWarnings({ "unused", "WeakerAccess" })
public abstract class RoomDatabase {
    private static final String DB_IMPL_SUFFIX = "_Impl";
    private final InvalidationTracker mInvalidationTracker;
    // set by the generated open helper.
    protected volatile SupportSQLiteDatabase mDatabase;
    private SupportSQLiteOpenHelper mOpenHelper;
    private boolean mAllowMainThreadQueries;


    /**
     * Creates a RoomDatabase.
     * <p>
     * You cannot create an instance of a database, instead, you should acquire it via
     * {@link Room#databaseBuilder(Context, Class, String)} or
     * {@link Room#inMemoryDatabaseBuilder(Context, Class)}.
     */
    public RoomDatabase() {
        mInvalidationTracker = createInvalidationTracker();
    }


    /**
     * Called by {@link Room} when it is initialized.
     *
     * @param configuration The database configuration.
     */
    @CallSuper
    public void init(DatabaseConfiguration configuration) {
        mOpenHelper = createOpenHelper(configuration);
        mAllowMainThreadQueries = configuration.allowMainThreadQueries;
    }


    /**
     * Returns the SQLite open helper used by this database.
     *
     * @return The SQLite open helper used by this database.
     */
    public SupportSQLiteOpenHelper getOpenHelper() {
        return mOpenHelper;
    }


    /**
     * Creates the open helper to access the database. Generated class already implements this
     * method.
     * Note that this method is called when the RoomDatabase is initialized.
     *
     * @param config The configuration of the Room database.
     * @return A new SupportSQLiteOpenHelper to be used while connecting to the database.
     */
    protected abstract SupportSQLiteOpenHelper createOpenHelper(DatabaseConfiguration config);

    /**
     * Called when the RoomDatabase is created.
     * <p>
     * This is already implemented by the generated code.
     *
     * @return Creates a new InvalidationTracker.
     */
    protected abstract InvalidationTracker createInvalidationTracker();


    /**
     * Returns true if database connection is open and initialized.
     *
     * @return true if the database connection is open, false otherwise.
     */
    public boolean isOpen() {
        final SupportSQLiteDatabase db = mDatabase;
        return db != null && db.isOpen();
    }


    /**
     * Closes the database if it is already open.
     */
    public void close() {
        if (isOpen()) {
            mOpenHelper.close();
        }
    }


    /**
     * Asserts that we are not on the main thread.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void assertNotMainThread() {
        if (mAllowMainThreadQueries) {
            return;
        }
        if (AppToolkitTaskExecutor.getInstance().isMainThread()) {
            throw new IllegalStateException("Cannot access database on the main thread since"
                + " it may potentially lock the UI for a long periods of time.");
        }
    }

    // Below, there are wrapper methods for SupportSQLiteDatabase. This helps us track which
    // methods we are using and also helps unit tests to mock this class without mocking
    // all SQLite database methods.


    /**
     * Wrapper for {@link SupportSQLiteDatabase#rawQuery(String, String[])}.
     *
     * @param sql Sql query to run.
     * @param selectionArgs Selection arguments.
     * @return Result of the query.
     */
    public Cursor query(String sql, String[] selectionArgs) {
        assertNotMainThread();
        return mOpenHelper.getWritableDatabase().rawQuery(sql, selectionArgs);
    }


    /**
     * Wrapper for {@link SupportSQLiteDatabase#rawQuery(SupportSQLiteQuery)}.
     *
     * @param query The Query which includes the SQL and a bind callback for bind arguments.
     * @return Result of the query.
     */
    public Cursor query(SupportSQLiteQuery query) {
        assertNotMainThread();
        return mOpenHelper.getWritableDatabase().rawQuery(query);
    }


    /**
     * Wrapper for {@link SupportSQLiteDatabase#compileStatement(String)}.
     *
     * @param sql The query to compile.
     * @return The compiled query.
     */
    public SupportSQLiteStatement compileStatement(String sql) {
        assertNotMainThread();
        return mOpenHelper.getWritableDatabase().compileStatement(sql);
    }


    /**
     * Wrapper for {@link SupportSQLiteDatabase#beginTransaction()}.
     */
    public void beginTransaction() {
        assertNotMainThread();
        mInvalidationTracker.syncTriggers();
        mOpenHelper.getWritableDatabase().beginTransaction();
    }


    /**
     * Wrapper for {@link SupportSQLiteDatabase#endTransaction()}.
     */
    public void endTransaction() {
        mOpenHelper.getWritableDatabase().endTransaction();
        mInvalidationTracker.refreshVersionsAsync();
    }


    /**
     * Wrapper for {@link SupportSQLiteDatabase#setTransactionSuccessful()}.
     */
    public void setTransactionSuccessful() {
        mOpenHelper.getWritableDatabase().setTransactionSuccessful();
    }


    /**
     * Called by the generated code when database is open.
     * <p>
     * You should never call this method manually.
     *
     * @param db The database instance.
     */
    protected void internalInitInvalidationTracker(SupportSQLiteDatabase db) {
        mInvalidationTracker.internalInit(db);
    }


    /**
     * Returns the invalidation tracker for this database.
     * <p>
     * You can use the invalidation tracker to get notified when certain tables in the database
     * are modified.
     *
     * @return The invalidation tracker for the database.
     */
    public InvalidationTracker getInvalidationTracker() {
        return mInvalidationTracker;
    }


    /**
     * Returns true if current thread is in a transaction.
     *
     * @return True if there is an active transaction in current thread, false otherwise.
     * @see SupportSQLiteDatabase#inTransaction()
     */
    public boolean inTransaction() {
        return mOpenHelper.getWritableDatabase().inTransaction();
    }


    /**
     * Builder for RoomDatabase.
     *
     * @param <T> The type of the abstract database class.
     */
    @SuppressWarnings("unused")
    public static class Builder<T extends RoomDatabase> {
        private final Class<T> mDatabaseClass;
        private final String mName;
        private final Context mContext;

        private SupportSQLiteOpenHelper.Factory mFactory;
        private boolean mInMemory;
        private boolean mAllowMainThreadQueries;
        /**
         * Migrations, mapped by from-to pairs.
         */
        private MigrationContainer mMigrationContainer;


        Builder(@NonNull Context context, @NonNull Class<T> klass, @Nullable String name) {
            mContext = context;
            mDatabaseClass = klass;
            mName = name;
            mMigrationContainer = new MigrationContainer();
        }


        /**
         * Sets the database factory. If not set, it defaults to
         * {@link FrameworkSQLiteOpenHelperFactory}.
         *
         * @param factory The factory to use to access the database.
         * @return this
         */
        public Builder<T> openHelperFactory(SupportSQLiteOpenHelper.Factory factory) {
            mFactory = factory;
            return this;
        }


        /**
         * Adds a migration to the builder.
         * <p>
         * Each Migration has a start and end versions and Room runs these migrations to bring the
         * database to the latest version.
         * <p>
         * If a migration item is missing between current version and the latest version, Room
         * will clear the database and recreate so even if you have no changes between 2 versions,
         * you should still provide a Migration object to the builder.
         * <p>
         * A migration can handle more than 1 version (e.g. if you have a faster path to choose when
         * going version 3 to 5 without going to version 4). If Room opens a database at version
         * 3 and latest version is &gt;= 5, Room will use the migration object that can migrate from
         * 3 to 5 instead of 3 to 4 and 4 to 5.
         *
         * @param migrations The migration object that can modify the database and to the necessary
         * changes.
         * @return this
         */
        public Builder<T> addMigrations(alpha1.android.arch.persistence.room.migration.Migration... migrations) {
            mMigrationContainer.addMigrations(migrations);
            return this;
        }


        /**
         * Disables the main thread query check for Room.
         * <p>
         * Room ensures that Database is never accessed on the main thread because it may lock the
         * main thread and trigger an ANR. If you need to access the database from the main thread,
         * you should always use async alternatives or manually move the call to a background
         * thread.
         * <p>
         * You may want to turn this check off for testing.
         *
         * @return this
         */
        public Builder<T> allowMainThreadQueries() {
            mAllowMainThreadQueries = true;
            return this;
        }


        /**
         * Creates the databases and initializes it.
         * <p>
         * By default, all RoomDatabases use in memory storage for TEMP tables and enables recursive
         * triggers.
         *
         * @return A new database instance.
         */
        public T build() {
            //noinspection ConstantConditions
            if (mContext == null) {
                throw new IllegalArgumentException("Cannot provide null context for the database.");
            }
            //noinspection ConstantConditions
            if (mDatabaseClass == null) {
                throw new IllegalArgumentException("Must provide an abstract class that"
                    + " extends RoomDatabase");
            }
            if (mFactory == null) {
                mFactory = new FrameworkSQLiteOpenHelperFactory();
            }
            DatabaseConfiguration configuration =
                new DatabaseConfiguration(mContext, mName, mFactory, mMigrationContainer,
                    mAllowMainThreadQueries);
            T db = Room.getGeneratedImplementation(mDatabaseClass, DB_IMPL_SUFFIX);
            db.init(configuration);
            return db;
        }
    }


    /**
     * A container to hold migrations. It also allows querying its contents to find migrations
     * between two versions.
     */
    public static class MigrationContainer {
        private SparseArrayCompat<SparseArrayCompat<alpha1.android.arch.persistence.room.migration.Migration>>
            mMigrations =
            new SparseArrayCompat<>();


        /**
         * Adds the given migrations to the list of available migrations. If 2 migrations have the
         * same start-end versions, the latter migration overrides the previous one.
         *
         * @param migrations List of available migrations.
         */
        public void addMigrations(alpha1.android.arch.persistence.room.migration.Migration... migrations) {
            for (alpha1.android.arch.persistence.room.migration.Migration migration : migrations) {
                addMigration(migration);
            }
        }


        private void addMigration(alpha1.android.arch.persistence.room.migration.Migration migration) {
            final int start = migration.startVersion;
            final int end = migration.endVersion;
            SparseArrayCompat<alpha1.android.arch.persistence.room.migration.Migration> targetMap
                = mMigrations.get(start);
            if (targetMap == null) {
                targetMap = new SparseArrayCompat<>();
                mMigrations.put(start, targetMap);
            }
            alpha1.android.arch.persistence.room.migration.Migration existing = targetMap.get(end);
            if (existing != null) {
                Log.w(Room.LOG_TAG, "Overriding migration " + existing + " with " + migration);
            }
            targetMap.append(end, migration);
        }


        /**
         * Finds the list of migrations that should be run to move from {@code start} version to
         * {@code end} version.
         *
         * @param start The current database version
         * @param end The target database version
         * @return An ordered list of {@link alpha1.android.arch.persistence.room.migration.Migration}
         * objects that should be run to migrate
         * between the given versions. If a migration path cannot be found, returns {@code null}.
         */
        @Nullable
        public List<alpha1.android.arch.persistence.room.migration.Migration> findMigrationPath(int start, int end) {
            if (start == end) {
                return Collections.emptyList();
            }
            boolean migrateUp = end > start;
            List<alpha1.android.arch.persistence.room.migration.Migration> result
                = new ArrayList<>();
            return findUpMigrationPath(result, migrateUp, start, end);
        }


        private List<alpha1.android.arch.persistence.room.migration.Migration> findUpMigrationPath(List<alpha1.android.arch.persistence.room.migration.Migration> result, boolean upgrade,
                                                                                                   int start, int end) {
            final int searchDirection = upgrade ? -1 : 1;
            while (upgrade ? start < end : start > end) {
                SparseArrayCompat<alpha1.android.arch.persistence.room.migration.Migration>
                    targetNodes = mMigrations.get(start);
                if (targetNodes == null) {
                    return null;
                }
                // keys are ordered so we can start searching from one end of them.
                final int size = targetNodes.size();
                final int firstIndex;
                final int lastIndex;

                if (upgrade) {
                    firstIndex = size - 1;
                    lastIndex = -1;
                } else {
                    firstIndex = 0;
                    lastIndex = size;
                }
                boolean found = false;
                for (int i = firstIndex; i != lastIndex; i += searchDirection) {
                    int targetVersion = targetNodes.keyAt(i);
                    if (targetVersion <= end && targetVersion > start) {
                        result.add(targetNodes.valueAt(i));
                        start = targetVersion;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return null;
                }
            }
            return result;
        }
    }
}
