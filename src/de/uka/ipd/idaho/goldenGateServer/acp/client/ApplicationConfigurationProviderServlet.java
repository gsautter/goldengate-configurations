/*
 * Copyright (c) 2006-, IPD Boehm, Universitaet Karlsruhe (TH) / KIT, by Guido Sautter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Universitaet Karlsruhe (TH) / KIT nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITAET KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.uka.ipd.idaho.goldenGateServer.acp.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.goldenGate.GoldenGateConfiguration;
import de.uka.ipd.idaho.goldenGate.GoldenGateConstants;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfigGroupDescriptor;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfigVersionDescriptor;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfiguration;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfigurationDataFile;
import de.uka.ipd.idaho.goldenGateServer.acp.GoldenGateAcpConstants;
import de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineInputStream;

/**
 * GoldenGATE ACP backed servlet providing access to GoldenGATE application
 * configurations.
 * 
 * @author sautter
 */
public class ApplicationConfigurationProviderServlet extends GgServerClientServlet implements GoldenGateAcpConstants, GoldenGateConstants {
	private File configDataRoot;
	
	/** usual zero-argument constructor for class loading */
	public ApplicationConfigurationProviderServlet() {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get configuration data location
		String configDataRoot = this.getSetting("configDataRoot");
		if (configDataRoot == null)
			this.configDataRoot = new File(this.cacheRootFolder, GoldenGateConstants.CONFIG_FOLDER_NAME);
		else this.configDataRoot = new File(configDataRoot);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#reInit()
	 */
	protected void reInit() throws ServletException {
		super.reInit();
		this.appConfigTrays.clear();
	}
	
	private synchronized AppConfigTray getAppConfig(String configName, boolean reload) throws IOException {
		if (reload || this.appConfigTrays.isEmpty())
			this.getAppConfigs(true);
		return ((AppConfigTray) this.appConfigTrays.get(configName));
	}
	
	private synchronized AppConfigTray[] getAppConfigs(boolean reload) throws IOException {
		if (this.appConfigTrays.isEmpty())
			reload = true;
		else if ((this.appConfigsLastFetched + (1000 * 60 * 10)) < System.currentTimeMillis()) // refresh every 10 minutes for now TODO make this configurable
			reload = true;
		if (reload) {
			this.appConfigTrays.clear();
			AppConfigGroupDescriptor[] acgds = AppConfigGroupDescriptor.groupAppConfigVersionDescriptors(this.loadAppConfigVersions());
			AppConfigTray[] acts = new AppConfigTray[acgds.length];
			for (int g = 0; g < acgds.length; g++) {
				acts[g] = new AppConfigTray(acgds[g]);
				this.appConfigTrays.put(acgds[g].name, acts[g]);
			}
			this.appConfigsLastFetched = System.currentTimeMillis();
			return acts;
		}
		else {
			AppConfigTray[] acts = new AppConfigTray[this.appConfigTrays.size()];
			int actIndex = 0;
			for (Iterator acnit = this.appConfigTrays.keySet().iterator(); acnit.hasNext();) {
				String acn = ((String) acnit.next());
				acts[actIndex++] = ((AppConfigTray) this.appConfigTrays.get(acn));
			}
			return acts;
		}
	}
	
	private AppConfigVersionDescriptor[] loadAppConfigVersions() throws IOException {
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_APPLICATION_CONFIGURATION_VERSIONS);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_APPLICATION_CONFIGURATION_VERSIONS.equals(error)) {
				AppConfigVersionDescriptor[] acvds = AppConfigVersionDescriptor.readAppConfigVersionDescriptors(br, "ACP");
				Arrays.sort(acvds, AppConfigVersionDescriptor.nameOrder);
				return acvds;
			}
			else throw new IOException(error);
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	private AppConfigVersionDescriptor findAppConfigVersion(String appConfigNameAndDate, boolean reload) throws IOException {
		if (reload || this.appConfigTrays.isEmpty())
			this.getAppConfigs(true);
		String appConfigName;
		String appConfigVersionDate;
		if (appConfigNameAndDate.matches(".+\\." + "2[0-9]{3}" + "[0-1][0-9]" + "[0-3][0-9]" + "-" + "[0-2][0-9]" + "[0-5][0-9]")) {
			appConfigName = appConfigNameAndDate.substring(0, appConfigNameAndDate.lastIndexOf("."));
			appConfigVersionDate = appConfigNameAndDate.substring(appConfigNameAndDate.lastIndexOf(".") + ".".length());
		}
		else {
			appConfigName = appConfigNameAndDate;
			appConfigVersionDate = null;
		}
		AppConfigTray act = ((AppConfigTray) this.appConfigTrays.get(appConfigName));
		if (act == null)
			return null;
		if (appConfigVersionDate == null)
			return act.config.latestRemote;
		for (int v = 0; v < act.config.versions.length; v++) {
			if (act.config.versions[v].versionDate.equals(appConfigVersionDate))
				return act.config.versions[v];
		}
		return null;
	}
	
	AppConfiguration loadAppConfiguration(AppConfigVersionDescriptor acvd) throws IOException {
		
		//	load directly from (potentially shared) data folder if possible
		File acdFile = new File(this.configDataRoot, acvd.getDescriptorFileName());
		BufferedReader acdBr = null;
		if (acdFile.exists()) try {
			acdBr = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(acdFile)), "UTF-8"));
			return AppConfiguration.readDescriptor(acdBr);
		}
		finally {
			if (acdBr != null)
				acdBr.close();
		}
		
		//	get from back-end otherwise
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_APPLICATION_CONFIGURATION);
			bw.newLine();
			bw.write(acvd.name);
			bw.newLine();
			bw.write(acvd.versionDate);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_APPLICATION_CONFIGURATION.equals(error))
				return AppConfiguration.readDescriptor(br);
			else throw new IOException(error);
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	private class AppConfigTray {
		final AppConfigGroupDescriptor config;
		AppConfigTray(AppConfigGroupDescriptor config) {
			this.config = config;
		}
		private AppConfiguration appConfig = null; // cache for descriptor of latest version
		AppConfiguration getAppConfiguration(String versionDate) throws IOException {
			AppConfigVersionDescriptor acvd = null; 
			if (versionDate == null)
				versionDate = this.config.latestRemote.versionDate;
			else for (int v = 0; v < this.config.versions.length; v++)
				if (this.config.versions[v].versionDate.equals(versionDate)) {
					acvd = this.config.versions[v];
					break;
				}
			if (acvd == null)
				return null;
			if ((acvd == this.config.latestRemote) && (this.appConfig != null))
				return this.appConfig;
			AppConfiguration appConfig = loadAppConfiguration(acvd);
			if (acvd == this.config.latestRemote)
				this.appConfig = appConfig; // only caching most recent version
			return appConfig;
		}
	}
	
	private Map appConfigTrays = Collections.synchronizedMap(new TreeMap());
	private long appConfigsLastFetched = System.currentTimeMillis();
	
	/* TODO POST (path info '', '/<configName>', or '/<configName>.<versionDate>'):
	 * - read list of physical data file names from request body
	 * - send back zipped stream of requested data files, with entries holding: full physical name and correct modification timestamp
	 */
	
	/* TODOne GET (dependent on path info):
	 * - '' or '/list' ==> TSV list of latest version of all configurations
	 * - '/listAll' ==> TSV list of all versions of all configurations
	 * - '/<configName>' ==> full TSV descriptor of latest version of named configuration
	 * - '/<configName>.<versionDate>' ==> full TSV descriptor of specific version of named configuration
	 * - '/<dataItemPathAndPhysicalFileName>' ==> input stream for single data item
	 * - '/<configName>/<dataItemPathAndPhysicalFileName>' ==> alias for previous, as hash based coexistence does away with need for keeping configurations in individual folders
	 *   ==> TODOne add special treatment for README, though, sending README of latest version
	 * - '/<configName>.<versionDate>/<dataItemPathAndPhysicalFileName>' ==> alias for previous, as hash based coexistence does away with need for keeping configurations in individual folders
	 *   ==> TODOne add special treatment for README, though, sending README of specific version
	 * 
	 * FILE NAMES EXPECTED BY ConfigurationUtils
	 * String remoteConfigDescriptorName = (remote.name + "." + remote.versionDate + GoldenGateConfiguration.DESCRIPTOR_FILE_NAME_SUFFIX);
	 * String readmeFileName = (remote.name + "." + remote.versionDate + "." + README_FILE_NAME);
	 * String remoteDataFileUrl = (remote.host + remoteDataFiles[f].getDataFilePathAndName());
	 */
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	get invocation path and data name
		String servletPath = request.getServletPath();
		if (servletPath.startsWith("/"))
			servletPath = servletPath.substring(1);
		
		//	HTTP 404 forward (catch it, most likely added for some reason)
		if ("404.html".equals(servletPath)) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		//	support old behavior under old mandated path (only need to map it)
		else if (GoldenGateConstants.CONFIG_FOLDER_NAME.equals(servletPath)) {
			this.doLegacyGet(request, response);
			return;
		}
		
		//	get request path
		String dataName = request.getPathInfo();
		if ((dataName == null) || "/".equals(dataName))
			dataName = "list";
		else /* normalize and scrutinize file name */ {
			dataName = dataName.trim();
			dataName = dataName.replace('\\', '/');
			dataName = dataName.replaceAll("[\\/]+", "/");
			dataName = dataName.replace("/./", "/");
			while (dataName.startsWith("/"))
				dataName = dataName.substring("/".length());
			if (dataName.indexOf("../") != -1) /* catch any parent steps */ {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			if (dataName.length() == 0)
				dataName = null;
		}
		
		//	request for TSV list of latest version or all versions of all configurations
		if ("list".equals(dataName) || "listAll".equals(dataName)) {
			AppConfigTray[] acts = this.getAppConfigs("force".equalsIgnoreCase(request.getParameter("cacheControl")));
			boolean latestOnly = "list".equals(dataName);
			response.setContentType("text/plain");
			response.setCharacterEncoding("UTF-8");
			response.setHeader("Cache-Control", "no-cache");
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), "UTF-8"));
			for (int c = 0; c < acts.length; c++) {
				if (latestOnly)
					bw.write(acts[c].config.latestRemote.toTsvString(false /* no use including host, fixed to 'ACP' for us, and we _are_ the public facing host */));
				else for (int v = 0; v < acts[c].config.versions.length; v++) {
					bw.write(acts[c].config.versions[v].toTsvString(false /* no use including host, fixed to 'ACP' for us, and we _are_ the public facing host */));
					bw.newLine();
				}
				bw.newLine(); // intentional double line break for full list, to effect grouping for improved legibility
			}
			bw.flush();
			return;
		}
		
		//	catch undetermined README.txt
		if (README_FILE_NAME.equals(dataName)) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		//	list versions of specific configuration
		if (dataName.endsWith("/versions") && (dataName.indexOf("/") == dataName.lastIndexOf("/"))) {
			String appConfigName = dataName.substring(0, (dataName.length() - "/versions".length()));
			if ("force".equalsIgnoreCase(request.getParameter("cacheControl")) || this.appConfigTrays.isEmpty())
				this.getAppConfigs(true);
			AppConfigTray act = ((AppConfigTray) this.appConfigTrays.get(appConfigName));
			if (act == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			else {
				response.setContentType("text/plain");
				response.setCharacterEncoding("UTF-8");
				response.setHeader("Cache-Control", "no-cache");
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), "UTF-8"));
				for (int v = 0; v < act.config.versions.length; v++) {
					bw.write(act.config.versions[v].toTsvString(false /* no use including host, fixed to 'ACP' for us, and we _are_ the public facing host */));
					bw.newLine();
				}
				bw.flush();
			}
			return;
		}
		
		//	README of specific configuration
		if ((dataName.endsWith("." + README_FILE_NAME) && (dataName.indexOf("/") == -1)) || (dataName.endsWith("/" + README_FILE_NAME) && (dataName.indexOf("/") == dataName.lastIndexOf("/")))) {
			String appConfigNameAndDate = dataName.substring(0, (dataName.length() - (".".length() + README_FILE_NAME.length())));
			AppConfigVersionDescriptor acvd = this.findAppConfigVersion(appConfigNameAndDate, "force".equalsIgnoreCase(request.getParameter("cacheControl")));
			if (acvd == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			else this.sendFile((acvd.name + "." + acvd.versionDate + "." + README_FILE_NAME), "text/plain", acvd.versionTimestamp, response);
			return;
		}
		
		//	full descriptor of specific configuration (with variations)
		if (dataName.endsWith(GoldenGateConfiguration.DESCRIPTOR_FILE_NAME_SUFFIX) && (dataName.indexOf("/") == -1)) {
			String appConfigNameAndDate = dataName.substring(0, (dataName.length() - GoldenGateConfiguration.DESCRIPTOR_FILE_NAME_SUFFIX.length()));
			AppConfigVersionDescriptor acvd = this.findAppConfigVersion(appConfigNameAndDate, "force".equalsIgnoreCase(request.getParameter("cacheControl")));
			if (acvd == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			else this.sendFile((acvd.name + "." + acvd.versionDate + GoldenGateConfiguration.DESCRIPTOR_FILE_NAME_SUFFIX), "text/plain", acvd.versionTimestamp, response);
			return;
		}
		
		//	API-like function style access
		if (dataName.endsWith("/descriptor") && (dataName.indexOf("/") == dataName.lastIndexOf("/"))) {
			String appConfigNameAndDate = dataName.substring(0, (dataName.length() - "/descriptor".length()));
			AppConfigVersionDescriptor acvd = this.findAppConfigVersion(appConfigNameAndDate, "force".equalsIgnoreCase(request.getParameter("cacheControl")));
			if (acvd == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			else this.sendFile((acvd.name + "." + acvd.versionDate + GoldenGateConfiguration.DESCRIPTOR_FILE_NAME_SUFFIX), "text/plain", acvd.versionTimestamp, response);
			return;
		}
		
		//	folder path style access TODO make sure this doesn't interfere with anything else
		if (dataName.endsWith("/") && (dataName.indexOf("/") == dataName.lastIndexOf("/"))) {
			String appConfigNameAndDate = dataName.substring(0, (dataName.length() - "/".length()));
			AppConfigVersionDescriptor acvd = this.findAppConfigVersion(appConfigNameAndDate, "force".equalsIgnoreCase(request.getParameter("cacheControl")));
			if (acvd == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			else this.sendFile((acvd.name + "." + acvd.versionDate + GoldenGateConfiguration.DESCRIPTOR_FILE_NAME_SUFFIX), "text/plain", acvd.versionTimestamp, response);
			return;
		}
		
		//	access via plain configuration name TODO make sure this doesn't interfere with anything else
		if (dataName.indexOf("/") == -1) {
			String appConfigNameAndDate = dataName;
			AppConfigVersionDescriptor acvd = this.findAppConfigVersion(appConfigNameAndDate, "force".equalsIgnoreCase(request.getParameter("cacheControl")));
			if (acvd == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			else this.sendFile((acvd.name + "." + acvd.versionDate + GoldenGateConfiguration.DESCRIPTOR_FILE_NAME_SUFFIX), "text/plain", acvd.versionTimestamp, response);
			return;
		}
		
		//	check for folder style access to data file
		String appConfigNameAndDate = dataName.substring(0, (dataName.length() - "/".length()));
		AppConfigVersionDescriptor acvd = this.findAppConfigVersion(appConfigNameAndDate, "force".equalsIgnoreCase(request.getParameter("cacheControl")));
		if (acvd != null)
			dataName = dataName.substring(dataName.indexOf("/") + "/".length());
		
		//	send any other file
		this.sendFile(dataName, "application/octet-stream", -1, response);
	}
	
	private void sendFile(String pathAndName, String mimeType, long lastModified, HttpServletResponse response) throws IOException {
		
		//	make sure file is available
		File configDataFile = new File(this.configDataRoot, pathAndName);
		if (!configDataFile.exists()) {
			configDataFile = this.getConfigDataFile(pathAndName);
			if (configDataFile == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
		}
		
		//	set headers
		response.setHeader("Cache-Control", "no-cache");
		response.setContentType(mimeType);
		if (mimeType.startsWith("text/"))
			response.setCharacterEncoding("UTF-8");
		response.setContentLength((int) + configDataFile.length());
		if (lastModified < 0)
			lastModified = configDataFile.lastModified();
		if (0 < lastModified)
			response.setHeader("Last-Modified", formatHttpTimestamp(lastModified));
		
		//	send actual file content
		InputStream is = new BufferedInputStream(new FileInputStream(configDataFile));
		OutputStream os = new BufferedOutputStream(response.getOutputStream());
		byte[] cdfBuffer = new byte[1024];
		for (int r; (r = is.read(cdfBuffer, 0, cdfBuffer.length)) != -1;)
			os.write(cdfBuffer, 0, r);
		os.flush();
		os.close();
		is.close();
	}
	
	private File getConfigDataFile(String pathAndName) throws IOException {
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_APPLICATION_CONFIGURATION_DATA);
			bw.newLine();
			bw.write(pathAndName);
			bw.newLine();
			bw.write(""); // terminate list with blank line
			bw.newLine();
			bw.flush();
			
			BufferedLineInputStream blis = con.getInputStream();
			String error = blis.readLine();
			if (GET_APPLICATION_CONFIGURATION_DATA.equals(error)) {
				ZipInputStream zis = new ZipInputStream(blis);
				ZipEntry ze = zis.getNextEntry();
				if (ze == null)
					return null; // sends 404 response above
				File cachingDataFile = new File(this.configDataRoot, (pathAndName + ".caching"));
				cachingDataFile.getParentFile().mkdirs();
				cachingDataFile.createNewFile();
				
				OutputStream os = new BufferedOutputStream(new FileOutputStream(cachingDataFile));
				byte[] cdfBuffer = new byte[1024];
				for (int r; (r = zis.read(cdfBuffer, 0, cdfBuffer.length)) != -1;)
					os.write(cdfBuffer, 0, r);
				os.flush();
				os.close();
				zis.closeEntry();
				cachingDataFile.setLastModified(ze.getTime());
				
				File configDataFile = new File(this.configDataRoot, pathAndName);
				cachingDataFile.renameTo(configDataFile);
				return configDataFile;
			}
			else throw new IOException(error);
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	private static final String LEGACY_DESCRIPTOR_FILE_NAME = "configuration.xml";
	private static final String LEGACY_FILE_INDEX_NAME = "files.txt";
	private static final String LEGACY_TIMESTAMP_NAME = "timestamp.txt";
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	private void doLegacyGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	get data name
		String dataName = request.getPathInfo();
		
		//	clean data name
		while (dataName.startsWith("/"))
			dataName = dataName.substring("/".length());
		
		//	request for configuration list
		if ((dataName.length() == 0) || LEGACY_FILE_INDEX_NAME.equals(dataName)) {
			AppConfigTray[] acts = this.getAppConfigs("force".equalsIgnoreCase(request.getParameter("cacheControl")));
			response.setContentType("text/plain");
			response.setCharacterEncoding("UTF-8");
			response.setHeader("Cache-Control", "no-cache");
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
			for (int c = 0; c < acts.length; c++) {
				bw.write(acts[c].config.name);
				bw.newLine();
			}
			bw.flush();
		}
		
		//	request for server's local time
		else if (LEGACY_TIMESTAMP_NAME.equals(dataName)) {
			response.setContentType("text/plain");
			response.setHeader("Cache-Control", "no-cache");
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
			bw.write("" + System.currentTimeMillis());
			bw.newLine();
			bw.flush();
		}
		
		//	request for XML descriptor of specific configuration (overly and unnecessarily complex, cannot support that anymore)
		else if (dataName.endsWith("/" + LEGACY_DESCRIPTOR_FILE_NAME)) {
			String configName = dataName.substring(0, dataName.indexOf('/'));
			AppConfigTray act = this.getAppConfig(configName, false);
			if (act == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			else response.sendError(HttpServletResponse.SC_MOVED_PERMANENTLY); // we don't have that old, overly complicated XML anymore
//			response.setContentType("text/xml");
//			response.setHeader("Cache-Control", "no-cache");
//			response.setHeader("Last-Modified", formatHttpTimestamp(act.config.latestRemote.versionTimestamp));
//			response.setContentLength(configTray.configDescriptorLength);
//			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
//			configTray.config.writeXml(bw);
//			bw.flush();
		}
		
		//	request for file list of specific configuration
		else if (dataName.endsWith("/" + LEGACY_FILE_INDEX_NAME)) {
			String configName = dataName.substring(0, dataName.indexOf('/'));
			AppConfigTray act = this.getAppConfig(configName, false);
			if (act == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			
			//	get full descriptor of latest version
			AppConfiguration appConfig = act.getAppConfiguration(null /* latest version */);
			AppConfigurationDataFile[] dataFiles = appConfig.listDataFiles();
			
			//	send list of full file names (without the hashes)
			response.setContentType("text/plain");
			response.setHeader("Cache-Control", "no-cache");
			response.setHeader("Last-Modified", formatHttpTimestamp(act.config.latestRemote.versionTimestamp));
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
			for (int f = 0; f < dataFiles.length; f++) {
				bw.write(dataFiles[f].fullName);
				bw.newLine();
			}
			bw.write(README_FILE_NAME);
			bw.newLine();
			bw.flush();
		}
		
		//	also handle README, needs special treatment
		else if (dataName.endsWith("/" + README_FILE_NAME)) {
			String configName = dataName.substring(0, dataName.indexOf('/'));
			AppConfigTray act = this.getAppConfig(configName, false);
			if (act == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND, dataName);
			AppConfiguration appConfig = act.getAppConfiguration(null /* latest version */);
			this.sendFile(appConfig.readmeFile.getDataFilePathAndName(), "text/plain", appConfig.readmeFile.lastMod, response);
		}
		
		//	request for timestamp of specific configuration
		else if (dataName.endsWith("/" + LEGACY_TIMESTAMP_NAME)) {
			String configName = dataName.substring(0, dataName.indexOf('/'));
			AppConfigTray act = this.getAppConfig(configName, false);
			if (act == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND, dataName);
			else {
				response.setContentType("text/plain");
				response.setHeader("Cache-Control", "no-cache");
				response.setHeader("Last-Modified", formatHttpTimestamp(act.config.latestRemote.versionTimestamp));
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
				bw.write("" + act.config.latestRemote.versionTimestamp);
				bw.newLine();
				bw.flush();
			}
		}
		
		//	request for other data item
		else {
			
			//	get full descriptor of latest version
			String configName = dataName.substring(0, dataName.indexOf('/'));
			AppConfigTray act = this.getAppConfig(configName, false);
			if (act == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			AppConfiguration appConfig = act.getAppConfiguration(null /* latest version */);
			
			//	retrieve data file object ...
			dataName = dataName.substring(dataName.indexOf("/") + "/".length());
			AppConfigurationDataFile dataFile = appConfig.getDataFile(dataName);
			if (dataFile == null)
				response.sendError(HttpServletResponse.SC_NOT_FOUND, dataName);
			
			//	send version with appropriate hash in physical name
			this.sendFile(dataFile.getDataFilePathAndName(), "application/octet-stream", dataFile.lastMod, response);
		}
	}
}
