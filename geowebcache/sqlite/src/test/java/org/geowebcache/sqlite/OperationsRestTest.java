/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import org.apache.commons.io.FileUtils;
import org.geowebcache.sqlite.Utils.Tuple;
import org.geowebcache.storage.TileObject;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextLoader;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.server.MockMvc;
import org.springframework.test.web.server.request.MockMvcRequestBuilders;
import org.springframework.test.web.server.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.geowebcache.sqlite.Utils.Tuple.tuple;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.server.result.MockMvcResultMatchers.status;

@ContextConfiguration(loader = OperationsRestTest.CustomLoader.class, classes = OperationsRestWebConfig.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class OperationsRestTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @AfterClass
    public static void afterClass() throws Exception {
        FileUtils.deleteQuietly(OperationsRestWebConfig.ROOT_DIRECTORY);
    }

    @Test
    public void testMultipleFilesUploadReplace() throws Exception {
        // creates some database files for the tests
        Tuple<File, Tuple<String, String>> testFiles = createTestFiles();
        File rootDirectory = testFiles.first;
        FileInputStream fileA = new FileInputStream(new File(rootDirectory, testFiles.second.first));
        FileInputStream fileB = new FileInputStream(new File(rootDirectory, testFiles.second.second));
        // perform the rest request
        MockMultipartFile fileUploadA = new MockMultipartFile("file", fileA);
        MockMultipartFile fileUploadB = new MockMultipartFile("file", fileB);
        MockMvc mockMvc = MockMvcBuilders.webApplicationContextSetup(webApplicationContext).build();
        // first request
        mockMvc.perform(MockMvcRequestBuilders.fileUpload("/sqlite/replace")
                .file(fileUploadA)
                .param("layer", "europe")
                .param("destination", testFiles.second.first))
                .andExpect(status().is(200));
        // second request
        mockMvc.perform(MockMvcRequestBuilders.fileUpload("/sqlite/replace")
                .file(fileUploadB)
                .param("layer", "europe")
                .param("destination", testFiles.second.second))
                .andExpect(status().is(200));
        // check that files were replaced
        checkThatStoreContainsReplacedTiles(testFiles.second.first, testFiles.second.second);
        // clean up
        fileA.close();
        fileB.close();
        FileUtils.deleteQuietly(rootDirectory);
    }

    @Test
    public void testZipFileUploadReplace() throws Exception {
        // creates some database files for the tests
        Tuple<File, Tuple<String, String>> testFiles = createTestFiles();
        File rootDirectory = testFiles.first;
        // zip store files
        File zipFile = new File(rootDirectory, "replace.zip");
        zipDirectory(Paths.get(rootDirectory.getPath()), zipFile);
        FileInputStream zipFileInputStream = new FileInputStream(zipFile);
        // perform the rest request
        MockMultipartFile zipUpload = new MockMultipartFile("file", zipFileInputStream);
        MockMvc mockMvc = MockMvcBuilders.webApplicationContextSetup(webApplicationContext).build();
        // execute request
        mockMvc.perform(MockMvcRequestBuilders.fileUpload("/sqlite/replace")
                .file(zipUpload)
                .param("layer", "europe"))
                .andExpect(status().is(200));
        // check that files were replaced
        checkThatStoreContainsReplacedTiles(testFiles.second.first, testFiles.second.second);
        // clean up
        zipFileInputStream.close();
        FileUtils.deleteQuietly(rootDirectory);
    }

    @Test
    public void testLocalDirectoryReplace() throws Exception {
        // creates some database files for the tests
        Tuple<File, Tuple<String, String>> testFiles = createTestFiles();
        File rootDirectory = testFiles.first;
        // perform the rest request
        MockMvc mockMvc = MockMvcBuilders.webApplicationContextSetup(webApplicationContext).build();
        // execute request
        mockMvc.perform(MockMvcRequestBuilders.fileUpload("/sqlite/replace")
                .param("layer", "europe")
                .param("source", rootDirectory.getPath()))
                .andExpect(status().is(200));
        // check that files were replaced
        checkThatStoreContainsReplacedTiles(testFiles.second.first, testFiles.second.second);
        // clean up
        FileUtils.deleteQuietly(rootDirectory);
    }

    private Tuple<File, Tuple<String, String>> createTestFiles() throws Exception {
        // instantiating the stores
        File rootDirectoryA = OperationsRestWebConfig.ROOT_DIRECTORY;
        FileUtils.deleteQuietly(OperationsRestWebConfig.ROOT_DIRECTORY);
        OperationsRestWebConfig.ROOT_DIRECTORY.mkdirs();
        File rootDirectoryB = Files.createTempDirectory("gwc-").toFile();
        MbtilesConfiguration configurationA = new MbtilesConfiguration();
        configurationA.setRootDirectory(rootDirectoryA.getPath());
        configurationA.setTemplatePath(Utils.buildPath("{grid}", "{layer}", "{format}", "{z}", "tiles-{x}-{y}.sqlite"));
        MbtilesConfiguration configurationB = new MbtilesConfiguration();
        configurationB.setRootDirectory(rootDirectoryB.getPath());
        configurationB.setTemplatePath(Utils.buildPath("{grid}", "{layer}", "{format}", "{z}", "tiles-{x}-{y}.sqlite"));
        MbtilesBlobStore storeA = new MbtilesBlobStore(configurationA);
        MbtilesBlobStore storeB = new MbtilesBlobStore(configurationB);
        // create the tiles that will be stored
        TileObject putTileA = TileObject.createCompleteTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null, TestSupport.stringToResource("IMAGE-10-50-5-A"));
        TileObject putTileB = TileObject.createCompleteTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null, TestSupport.stringToResource("IMAGE-10-50-5-B"));
        TileObject putTileC = TileObject.createCompleteTileObject("africa",
                new long[]{10, 5050, 15}, "EPSG:4326", "image/png", null, TestSupport.stringToResource("IMAGE-15-5050-5-B"));
        // storing the tile
        storeA.put(putTileA);
        storeB.put(putTileB);
        storeB.put(putTileC);
        // return relative paths
        String relativePathA = Utils.buildPath("EPSG_4326", "africa", "image_png", "5", "tiles-0-0.sqlite");
        String relativePathB = Utils.buildPath("EPSG_4326", "africa", "image_png", "15", "tiles-0-5000.sqlite");
        return tuple(rootDirectoryB, tuple(relativePathA, relativePathB));
    }

    private void checkThatStoreContainsReplacedTiles(String relativePathA, String relativePathB) throws Exception {
        // instantiating the store
        File rootDirectory = OperationsRestWebConfig.ROOT_DIRECTORY;
        MbtilesConfiguration configuration = new MbtilesConfiguration();
        configuration.setRootDirectory(rootDirectory.getPath());
        configuration.setTemplatePath(Utils.buildPath("{grid}", "{layer}", "{format}", "{z}", "tiles-{x}-{y}.sqlite"));
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        // checking that all the files are present
        File fileA = new File(OperationsRestWebConfig.ROOT_DIRECTORY, relativePathA);
        File fileB = new File(OperationsRestWebConfig.ROOT_DIRECTORY, relativePathB);
        assertThat(fileA.exists(), is(true));
        assertThat(fileB.exists(), is(true));
        // let's query the store to see if we get the replaced tiles
        TileObject getTile = TileObject.createQueryTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null);
        assertThat(store.get(getTile), is(true));
        assertThat(getTile.getBlob(), notNullValue());
        assertThat(TestSupport.resourceToString(getTile.getBlob()), is("IMAGE-10-50-5-B"));
        // let's query the second tile
        getTile = TileObject.createQueryTileObject("africa",
                new long[]{10, 5050, 15}, "EPSG:4326", "image/png", null);
        assertThat(store.get(getTile), is(true));
        assertThat(getTile.getBlob(), notNullValue());
        assertThat(TestSupport.resourceToString(getTile.getBlob()), is("IMAGE-15-5050-5-B"));
    }

    private void zipDirectory(final Path directoryToZip, File outputZipFile) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputZipFile);
             ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
            Files.walkFileTree(directoryToZip, new SimpleFileVisitor<Path>() {

                public FileVisitResult visitFile(Path file, BasicFileAttributes fileAttributes) throws IOException {
                    zipOutputStream.putNextEntry(new ZipEntry(directoryToZip.relativize(file).toString()));
                    Files.copy(file, zipOutputStream);
                    zipOutputStream.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                    zipOutputStream.putNextEntry(new ZipEntry(directoryToZip.relativize(directory).toString() + File.separator));
                    zipOutputStream.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * This is necessary because of this https://jira.springsource.org/browse/SPR-5243
     */
    public static class CustomLoader implements ContextLoader {

        @Override
        public String[] processLocations(Class<?> clazz, String... locations) {
            return locations;
        }

        @Override
        public ApplicationContext loadContext(String... locations) throws Exception {
            AnnotationConfigWebApplicationContext result = new AnnotationConfigWebApplicationContext();
            MockServletContext servletContext = new MockServletContext();
            result.setServletContext(servletContext);
            result.setServletConfig(new MockServletConfig(servletContext));
            result.setConfigLocations(locations);
            result.scan("org.geowebcache.sqlite");
            result.refresh();
            return result;
        }
    }
}