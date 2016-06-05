/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import org.geowebcache.storage.StorageException;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.geowebcache.sqlite.Utils.Tuple;
import static org.geowebcache.sqlite.Utils.Tuple.tuple;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public final class SqliteConnectionManagerTest extends TestSupport {

    @Test
    public void testGetConnection() throws StorageException {
        SqliteConnectionManager connectionManager = new SqliteConnectionManager(Integer.MAX_VALUE, 1000);
        connectionManager.doWork(buildRootFile("tiles", "data_base.sqlite"), false, new SqliteConnectionManager.Work() {
            @Override
            public void doWork(Connection connection) {
                insertInTestTable(connection, "name", "europe");
            }
        });
        connectionManager.reapAllConnections();
        assertThat(connectionManager.getPool().size(), is(0));
        connectionManager.doWork(buildRootFile("tiles", "data_base.sqlite"), true, new SqliteConnectionManager.Work() {
            @Override
            public void doWork(Connection connection) {
                String value = getFromTestTable(connection, "name");
                assertThat(value, notNullValue());
                assertThat(value, is("europe"));
                closeConnectionQuietly(connection);
            }
        });
    }

    @Test
    public void testMultiThreadsWithSingleFile() throws Exception {
        genericMultiThreadsTest(10, 500, Integer.MAX_VALUE, buildRootFile("data_base_a.sqlite"));
    }

    @Test
    public void testMultiThreadsWithMultipleFiles() throws Exception {
        genericMultiThreadsTest(10, 500, 10,
                buildRootFile("data_base_a.sqlite"),
                buildRootFile("data_base_b.sqlite"),
                buildRootFile("data_base_c.sqlite"),
                buildRootFile("data_base_d.sqlite"),
                buildRootFile("data_base_e.sqlite")
        );
    }

    @Test
    public void testMultiThreadsWithMultipleFilesWithCacheLimit() throws Exception {
        genericMultiThreadsTest(10, 500, 1,
                buildRootFile("data_base_a.sqlite"),
                buildRootFile("data_base_b.sqlite"),
                buildRootFile("data_base_c.sqlite"),
                buildRootFile("data_base_d.sqlite"),
                buildRootFile("data_base_e.sqlite")
        );
    }

    @Test
    public void testReplaceOperation() throws Exception {
        SqliteConnectionManager connectionManager = new SqliteConnectionManager(Integer.MAX_VALUE, 1000);
        File file1 = buildRootFile("tiles", "data_base_1.sqlite");
        Utils.createFileParents(file1);
        File file2 = buildRootFile("tiles", "data_base_2.sqlite");
        Utils.createFileParents(file2);
        connectionManager.doWork(file1, false, new SqliteConnectionManager.Work() {
            @Override
            public void doWork(Connection connection) {
                insertInTestTable(connection, "name", "europe");
                closeConnectionQuietly(connection);
            }
        });
        file2.createNewFile();
        connectionManager.replace(file1, file2);
        connectionManager.doWork(file1, false, new SqliteConnectionManager.Work() {
            @Override
            public void doWork(Connection connection) {
                createTestTable(connection);
                String value = getFromTestTable(connection, "name");
                assertThat(value, nullValue());
                closeConnectionQuietly(connection);
            }
        });
    }

    private static void genericMultiThreadsTest(int threadsNumber, int workersNumber,
                                                long poolSize, final File... files) throws Exception {
        final SqliteConnectionManager connectionManager = new SqliteConnectionManager(poolSize, 10);
        ExecutorService executor = Executors.newFixedThreadPool(threadsNumber);
        final Random random = new Random();
        List<Callable<Tuple<File, String>>> workers = new ArrayList<>();
        for (int i = 0; i < workersNumber; i++) {
            workers.add(new Callable<Tuple<File, String>>() {
                @Override
                public Tuple<File, String> call() throws Exception {
                    final File file = files[random.nextInt(files.length)];
                    final String key = UUID.randomUUID().toString();
                    Utils.updateLogId(file.getName());
                    return connectionManager.doWork(file, false, new SqliteConnectionManager.WorkWithResult<Tuple<File, String>>() {
                        @Override
                        public Tuple<File, String> doWork(Connection connection) {
                            insertInTestTable(connection, key, "value-" + key);
                            closeConnectionQuietly(connection);
                            return tuple(file, key);
                        }
                    });
                }
            });
        }
        List<Future<Tuple<File, String>>> results = executor.invokeAll(workers);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        connectionManager.reapAllConnections();
        assertThat(connectionManager.getPool().size(), is(0));
        for (Future<Tuple<File, String>> result : results) {
            File file = result.get().first;
            final String key = result.get().second;
            Utils.updateLogId(file.getName());
            connectionManager.doWork(file, true, new SqliteConnectionManager.Work() {
                @Override
                public void doWork(Connection connection) {
                    String value = getFromTestTable(connection, key);
                    assertThat(value, notNullValue());
                    assertThat(value, is("value-" + key));
                    closeConnectionQuietly(connection);
                }
            });
        }
    }

    private static void closeConnectionQuietly(Connection connection) {
        try {
            connection.close();
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error closing connection.");
        }
    }

    private static void createTestTable(Connection connection) {
        execute(connection, "CREATE TABLE IF NOT EXISTS test " +
                "(key text, value text, CONSTRAINT pk_metadata PRIMARY KEY(key));");
    }

    private static void insertInTestTable(Connection connection, String key, String value) {
        createTestTable(connection);
        execute(connection, "INSERT INTO test VALUES ('%s', '%s');", key, value);
    }

    private static String getFromTestTable(Connection connection, String key) {
        return new ExecuteQuery(connection, "SELECT value FROM test WHERE key = '%s' ORDER BY key;", key) {

            String result;

            @Override
            public void extract(ResultSet resultSet) throws Exception {
                if (resultSet.next()) {
                    result = resultSet.getString(1);
                }
            }
        }.result;
    }

    private static void execute(Connection connection, String sql, Object... arguments) {
        String finalSql = String.format(sql, arguments);
        try (PreparedStatement statement = connection.prepareStatement(finalSql)) {
            statement.execute();
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error executing SQL '%s'.", finalSql);
        }
    }

    private static abstract class ExecuteQuery {

        public abstract void extract(ResultSet resultSet) throws Exception;

        public ExecuteQuery(Connection connection, String query, Object... arguments) {
            String finalQuery = String.format(query, arguments);
            try (PreparedStatement statement = connection.prepareStatement(finalQuery)) {
                extract(statement.executeQuery());
            } catch (Exception exception) {
                throw Utils.exception(exception, "Error executing query '%s'.", finalQuery);
            }
        }
    }
}