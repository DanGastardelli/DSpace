/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.SQLException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.HttpHeadersInitializer;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * This is a specialized controller to provide access to the sitemap files, generated by
 * {@link org.dspace.app.sitemap.GenerateSitemaps}
 *
 * The mapping for requested endpoint try to resolve a valid sitemap file name, for example
 * <pre>
 * {@code
 * https://<dspace.server.url>/sitemaps/26453b4d-e513-44e8-8d5b-395f62972eff/sitemap0.html
 * }
 * </pre>
 *
 * @author Maria Verdonck (Atmire) on 08/07/2020
 */
@Controller
@RequestMapping("/${sitemap.path:sitemaps}")
public class SitemapRestController {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(SitemapRestController.class);

    @Autowired
    ConfigurationService configurationService;

    // Most file systems are configured to use block sizes of 4096 or 8192 and our buffer should be a multiple of that.
    private static final int BUFFER_SIZE = 4096 * 10;

    /**
     * Tries to retrieve a matching sitemap file in configured location
     *
     * @param name     the name of the requested sitemap file
     * @param response the HTTP response
     * @param request  the HTTP request
     * @throws SQLException if db error while completing DSpace context
     * @throws IOException  if IO error surrounding sitemap file
     * @return
     */
    @GetMapping("/{name}")
    public ResponseEntity retrieve(@PathVariable String name, HttpServletResponse response,
                                                       HttpServletRequest request) throws IOException, SQLException {
        // Find sitemap with given name in dspace/sitemaps
        File foundSitemapFile = null;
        File sitemapOutputDir = new File(configurationService.getProperty("sitemap.dir"));
        if (sitemapOutputDir.exists() && sitemapOutputDir.isDirectory()) {
            // List of all files and directories inside sitemapOutputDir
            File sitemapFilesList[] = sitemapOutputDir.listFiles();
            for (File sitemapFile : sitemapFilesList) {
                if (name.equalsIgnoreCase(sitemapFile.getName())) {
                    if (sitemapFile.isFile()) {
                        foundSitemapFile = sitemapFile;
                    } else {
                        throw new ResourceNotFoundException(
                            "Directory with name " + name + " in " + sitemapOutputDir.getAbsolutePath() +
                            " found, but no file.");
                    }
                }
            }
        } else {
            throw new ResourceNotFoundException(
                "Sitemap directory in " + sitemapOutputDir.getAbsolutePath() + " does not " +
                "exist, either sitemaps have not been generated (./dspace generate-sitemaps)," +
                " or are located elsewhere (config used: sitemap.dir).");
        }
        if (foundSitemapFile == null) {
            throw new ResourceNotFoundException(
                "Could not find sitemap file with name " + name + " in " + sitemapOutputDir.getAbsolutePath());
        } else {
            // return found sitemap file
            return this.returnSitemapFile(foundSitemapFile, response, request);
        }
    }

    /**
     * Sends back the matching sitemap file as a MultipartFile, with the headers set with details of the file
     * (content, size, name, last modified)
     *
     * @param foundSitemapFile the found sitemap file, with matching name as in request path
     * @param response         the HTTP response
     * @param request          the HTTP request
     * @throws SQLException if db error while completing DSpace context
     * @throws IOException  if IO error surrounding sitemap file
     * @return
     */
    private ResponseEntity returnSitemapFile(File foundSitemapFile, HttpServletResponse response,
                                             HttpServletRequest request) throws SQLException, IOException {
        // Pipe the bits
        try (InputStream is = new FileInputStream(foundSitemapFile)) {
            HttpHeadersInitializer sender = new HttpHeadersInitializer()
                .withBufferSize(BUFFER_SIZE)
                .withFileName(foundSitemapFile.getName())
                .withLength(foundSitemapFile.length())
                .withMimetype(Files.probeContentType(foundSitemapFile.toPath()))
                .with(request)
                .with(response);

            sender.withLastModified(foundSitemapFile.lastModified());

            // Determine if we need to send the file as a download or if the browser can open it inline
            long dispositionThreshold = configurationService.getLongProperty("webui.content_disposition_threshold");
            if (dispositionThreshold >= 0 && foundSitemapFile.length() > dispositionThreshold) {
                sender.withDisposition(HttpHeadersInitializer.CONTENT_DISPOSITION_ATTACHMENT);
            }

            Context context = ContextUtil.obtainContext(request);

            // We have all the data we need, close the connection to the database so that it doesn't stay open during
            // download/streaming
            context.complete();

            // Send the data
            if (sender.isValid()) {
                HttpHeaders httpHeaders = sender.initialiseHeaders();
                return ResponseEntity.ok().headers(httpHeaders).body(new FileSystemResource(foundSitemapFile));

            }

        } catch (ClientAbortException e) {
            log.debug("Client aborted the request before the download was completed. " +
                      "Client is probably switching to a Range request.", e);
        }
        return null;
    }
}
