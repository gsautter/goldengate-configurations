/*
 * Copyright (c) 2006-2008, IPD Boehm, Universitaet Karlsruhe (TH)
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
 *     * Neither the name of the Universität Karlsruhe (TH) nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITÄT KARLSRUHE (TH) AND CONTRIBUTORS 
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

package de.uka.ipd.idaho.goldenGateServer.ecs.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.goldenGate.GoldenGateConfiguration;
import de.uka.ipd.idaho.goldenGate.GoldenGateConstants;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Configuration;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.DataItem;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Lib;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Plugin;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Resource;
import de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.ecs.GoldenGateEcsConstants;
import de.uka.ipd.idaho.goldenGateServer.util.Base64InputStream;

/**
 * This servlet is intended to serve configuration data from a backing
 * GoldenGATE ECS to UrlConfiguration objects. It requires the mapping
 * '/Configurations/*' within the webapp it is running. This is for serving
 * GoldenGATE configuration data to the web by means of URLs. The configuration
 * data has to be on the same machine as this servlet, either replicated to the
 * context of this servlet, in the sub folder 'Configurations' (in this case,
 * the 'Configurations' mapping is superfluous, since no file paths have to be
 * mapped), or in some other location specified by the 'configDataLocation'
 * parameter in the form of an absolute file path.<br>
 * <br>
 * This servlet uses a configuration file for obtaining the connection
 * parameters of the backing GoldenGATE Server. By default, this file is
 * 'config.cnfg' in the surrounding web-app's context path. It can be changed to
 * another file somewhere below the web-app's context path through specifying
 * the alternative file path and name in the value of the servlet's 'configFile'
 * parameter in the web.xml. From the configuration file, the servlet reads
 * three parametes:
 * <ul>
 * <li><b>configDataLocation</b>: the absolute path of the backing ECS's
 * configuration data (see above)</li>
 * <li><b>serverAddress</b>: the host name of the backing GoldenGATE Server
 * (for retrieving the configuration descriptors, which are required for serving
 * projected configurations)</li>
 * <li><b>serverPort</b>: the port the backing GoldenGATE Server listens on
 * (for retrieving the configuration descriptors, which are required for serving
 * projected configurations)</li>
 * </ul>
 * @author sautter
 */
public class GoldenGateConfigurationServlet extends GgServerClientServlet implements GoldenGateEcsConstants {
	
	private File configDataRoot;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get configuration data location
		String configDataLocation = this.getSetting("configDataLocation");
		if (configDataLocation == null)
			this.configDataRoot = new File(new File(this.webInfFolder, "caches"), GoldenGateConstants.CONFIG_FOLDER_NAME);
		else this.configDataRoot = new File(configDataLocation);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#reInit()
	 */
	protected void reInit() throws ServletException {
		super.reInit();
		this.configurationTrayCache.clear();
	}
	
	private synchronized Configuration[] getConfigurationDescriptors() throws IOException {
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			final BufferedWriter bw = con.getWriter();
			
			bw.write(GET_CONFIGURATION_DESCRIPTORS);
			bw.newLine();
			bw.write(CONFIG_SERVLET_SESSION_ID);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_CONFIGURATION_DESCRIPTORS.equals(error)) {
				ArrayList configList = new ArrayList();
				String configData;
				StringBuffer configDataCollector = new StringBuffer();
				while((configData = br.readLine()) != null) {
					configDataCollector.append(configData);
					if (("</" + ConfigurationUtils.configuration_NODE_TYPE + ">").equals(configData.trim())) {// end of configuration
						configList.add(Configuration.readConfiguration(new StringReader(configDataCollector.toString())));
						configDataCollector = new StringBuffer();
					}
				}
				Configuration[] configs = ((Configuration[]) configList.toArray(new Configuration[configList.size()]));
				
				//	remove outdated full descriptors from cache
				for (int c = 0; c < configs.length; c++) {
					ConfigurationTray configTray = ((ConfigurationTray) this.configurationTrayCache.get(configs[c].name));
					if ((configTray != null) && (configTray.config.configTimestamp < configs[c].configTimestamp))
						this.configurationTrayCache.remove(configs[c].name);
				}
				
				//	return descriptor stubs
				return configs;
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
	
	private class ConfigurationTray {
		final Configuration config;
		final int configDescriptorLength;
		private Map dataItemTimestampCache = new HashMap();
		ConfigurationTray(Configuration config, int configDescriptorLength) {
			this.config = config;
			this.configDescriptorLength = configDescriptorLength;
			
			//	index fixed data items
			this.dataItemTimestampCache.put(this.config.settingsPath, new Long(this.config.configTimestamp));
			this.dataItemTimestampCache.put(this.config.iconImagePath, new Long(this.config.configTimestamp));
			
			//	index data items
			for (Iterator dit = this.config.dataItems.iterator(); dit.hasNext();) {
				DataItem di = ((DataItem) dit.next());
				this.dataItemTimestampCache.put(di.path, new Long(di.timestamp));
			}
			
			//	go through plugins
			for (Iterator pit = this.config.plugins.iterator(); pit.hasNext();) {
				Plugin plugin = ((Plugin) pit.next());
				
				//	index plugin class path
				this.dataItemTimestampCache.put(plugin.classPath, new Long(plugin.timestamp));
				
				//	index libs
				String libPath = ((plugin.classPath.lastIndexOf('/') == -1) ? "" : plugin.classPath.substring(0, plugin.classPath.lastIndexOf('/') + 1));
				for (Iterator lit = plugin.libs.iterator(); lit.hasNext();) {
					Lib lib = ((Lib) lit.next());
					this.dataItemTimestampCache.put((libPath + lib.path), new Long(lib.timestamp));
				}
				
				//	index resources & data items
				String dataPath = (plugin.dataPath.endsWith("/") ? plugin.dataPath : (plugin.dataPath + "/"));
				
				for (Iterator dit = plugin.dataItems.iterator(); dit.hasNext();) {
					DataItem di = ((DataItem) dit.next());
					this.dataItemTimestampCache.put((dataPath + di.path), new Long(di.timestamp));
				}
				
				for (Iterator rit = plugin.resources.iterator(); rit.hasNext();) {
					Resource res = ((Resource) rit.next());
					this.dataItemTimestampCache.put((dataPath + res.path), new Long(res.timestamp));
					
					for (Iterator dit = res.dataItems.iterator(); dit.hasNext();) {
						DataItem di = ((DataItem) dit.next());
						this.dataItemTimestampCache.put((dataPath + di.path), new Long(di.timestamp));
					}
				}
			}
		}
		long getDataItemTimestamp(String dataName) {
			Long timestamp = ((Long) this.dataItemTimestampCache.get(dataName));
			return ((timestamp == null) ? (dataName.startsWith(this.config.helpBasePath) ? this.config.configTimestamp : -1) : timestamp.longValue());
		}
	}
	
	private Map configurationTrayCache = Collections.synchronizedMap(new HashMap());
	
	private synchronized ConfigurationTray getConfigurationDescriptor(String configName) throws IOException {
		
		//	do cache lookup
		ConfigurationTray configTray = ((ConfigurationTray) this.configurationTrayCache.get(configName));
		if (configTray != null)
			return configTray;
		
		//	cache miss, get descriptor from server
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_CONFIGURATION_DESCRIPTOR);
			bw.newLine();
			bw.write(CONFIG_SERVLET_SESSION_ID);
			bw.newLine();
			bw.write(configName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_CONFIGURATION_DESCRIPTOR.equals(error)) {
				final int[] configDescriptorLength = {0};
				Configuration config = Configuration.readConfiguration(new FilterReader(br) {
					public int read() throws IOException {
						int read = super.read();
						if (read != -1)
							configDescriptorLength[0]++;
						return read;
					}
					public int read(char[] cbuf, int off, int len) throws IOException {
						int read = super.read(cbuf, off, len);
						if (read != -1)
							configDescriptorLength[0] += read;
						return read;
					}
				});
				
				//	create & cache tray
				configTray = new ConfigurationTray(config, configDescriptorLength[0]);
				this.configurationTrayCache.put(configName, configTray);
				
				//	return descriptor
				return configTray;
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
	
	private long getTimestamp(String dataName) throws IOException {
		
		//	get config decriptor
		String configName = dataName.substring(0, dataName.indexOf('/'));
		ConfigurationTray configTray = this.getConfigurationDescriptor(configName);
		if (configTray == null)
			return -1;
		
		//	extract config-local data name
		dataName = dataName.substring(dataName.indexOf('/') + 1);
		
		//	use cached timestamps
		return configTray.getDataItemTimestamp(dataName);
	}
	
	private DataInputStream getInputStream(String dataName, final long dataTimestamp) throws IOException {
		
		//	data item does not exist
		if (dataTimestamp == -1)
			return null;
		
		//	get configuration descriptor
		String configName = dataName.substring(0, dataName.indexOf('/'));
		ConfigurationTray configTray = this.getConfigurationDescriptor(configName);
		if (configTray == null)
			return null;
		
		//	extract config-local data name
		dataName = dataName.substring(dataName.indexOf('/') + 1);
		
		//	add config path (differs from name for projected configurations)
		dataName = (configTray.config.basePath + "/" + dataName);
		
		//	create data file
		File cacheDataFile = new File(this.configDataRoot, dataName);
		
		/*
		 * file not in cache or outdated, fetch from server (add a second of
		 * tolerance for Linux file systems, though, cause some of them store
		 * only seconds instead of milliseconds)
		 */
		if ((cacheDataFile.lastModified() + 999) < dataTimestamp) try {
			Connection con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_DATA);
			bw.newLine();
			bw.write(CONFIG_SERVLET_SESSION_ID);
			bw.newLine();
			bw.write(dataName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_DATA.equals(error)) {
				File cachingDataFile = new File(this.configDataRoot, (dataName + ".caching"));
				cachingDataFile.getParentFile().mkdirs();
				cachingDataFile.createNewFile();
				
				InputStream is = new BufferedInputStream(new Base64InputStream(br));
				OutputStream os = new BufferedOutputStream(new FileOutputStream(cachingDataFile));
				byte[] inBuf = new byte[1024];
				int inLen = -1;
				while ((inLen = is.read(inBuf)) != -1)
					os.write(inBuf, 0, inLen);
				os.flush();
				os.close();
				is.close();
				
				if (cacheDataFile.exists()) {
					cacheDataFile.renameTo(new File(this.configDataRoot, (dataName + "." + dataTimestamp + ".old")));
					cacheDataFile = new File(this.configDataRoot, dataName);
				}
				
				cachingDataFile.renameTo(cacheDataFile);
				cacheDataFile.setLastModified(dataTimestamp);
			}
			else {
				con.close();
				throw new IOException(error);
			}
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		
		//	return cached file
		return new DataInputStream(cacheDataFile);
	}
	
	private class DataInputStream extends BufferedInputStream {
		final int length;
		DataInputStream(File file) throws FileNotFoundException {
			super(new FileInputStream(file));
			this.length = ((int) file.length());
		}
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	get invokaltion path and data name
		String servletPath = request.getServletPath();
		if (servletPath.startsWith("/"))
			servletPath = servletPath.substring(1);
		String dataName = request.getPathInfo();
		
		//	HTTP 404 forward
		if ("404.html".equals(servletPath))
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		
		//	invalid data name
		else if (dataName == null)
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		
		//	serve configuration data from the backing ECS
		else if (GoldenGateConstants.CONFIG_FOLDER_NAME.equals(servletPath)) {
			
			//	clean data name
			if (dataName.startsWith("/"))
				dataName = dataName.substring(1);
			
			//	request for configuration list
			if ((dataName.length() == 0) || GoldenGateConfiguration.FILE_INDEX_NAME.equals(dataName)) {
				Configuration[] configs = this.getConfigurationDescriptors();
				
				response.setContentType("text/plain");
				response.setHeader("Cache-Control", "no-cache");
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
				
				for (int c = 0; c < configs.length; c++) {
					bw.write(configs[c].name);
					bw.newLine();
				}
				bw.flush();
			}
			
			//	request for server's local time
			else if (GoldenGateConfiguration.TIMESTAMP_NAME.equals(dataName)) {
				response.setContentType("text/plain");
				response.setHeader("Cache-Control", "no-cache");
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
				bw.write("" + System.currentTimeMillis());
				bw.newLine();
				bw.flush();
			}
			
			//	request for XML descriptor of specific configuration
			else if (dataName.endsWith("/" + GoldenGateConfiguration.DESCRIPTOR_FILE_NAME)) {
				String configName = dataName.substring(0, dataName.indexOf('/'));
				ConfigurationTray configTray = this.getConfigurationDescriptor(configName);
				
				response.setContentType("text/xml");
				response.setHeader("Cache-Control", "no-cache");
				response.setHeader("Last-Modified", lastModifiedFormat.format(new Date(configTray.config.configTimestamp)));
				response.setContentLength(configTray.configDescriptorLength);
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
				configTray.config.writeXml(bw);
				bw.flush();
			}
			
			//	request for file list of specific configuration
			else if (dataName.endsWith("/" + GoldenGateConfiguration.FILE_INDEX_NAME)) {
				String configName = dataName.substring(0, dataName.indexOf('/'));
				ConfigurationTray configTray = this.getConfigurationDescriptor(configName);
				
				TreeSet fileList = new TreeSet(Arrays.asList(ConfigurationUtils.getDataNameList(null, configTray.config)));
				
				int contentLength = 0;
				int newLineLength = 1;
				String newLine = System.getProperty("line.separator");
				if (newLine != null)
					newLineLength = newLine.length();
				for (Iterator fit = fileList.iterator(); fit.hasNext();) {
					contentLength += ((String) fit.next()).length();
					contentLength += newLineLength;
				}
				
				response.setContentType("text/plain");
				response.setHeader("Cache-Control", "no-cache");
				response.setHeader("Last-Modified", lastModifiedFormat.format(new Date(configTray.config.configTimestamp)));
				response.setContentLength(contentLength);
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
				for (Iterator fit = fileList.iterator(); fit.hasNext();) {
					bw.write(fit.next().toString());
					bw.newLine();
				}
				bw.flush();
			}
			
			//	request for timestamp of specific configuration
			else if (dataName.endsWith("/" + GoldenGateConfiguration.TIMESTAMP_NAME)) {
				String configName = dataName.substring(0, dataName.indexOf('/'));
				ConfigurationTray configTray = this.getConfigurationDescriptor(configName);
				if (configTray == null)
					response.sendError(HttpServletResponse.SC_NOT_FOUND, dataName);
				
				else {
					response.setContentType("text/plain");
					response.setHeader("Cache-Control", "no-cache");
					response.setHeader("Last-Modified", lastModifiedFormat.format(new Date(configTray.config.configTimestamp)));
					BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
					bw.write("" + configTray.config.configTimestamp);
					bw.newLine();
					bw.flush();
				}
			}
			
			//	request for other data item
			else {
				
				//	get data timestamp
				long dataTimestamp = this.getTimestamp(dataName);
				
				//	data item does not exist or is not accessible
				if (dataTimestamp == -1)
					response.sendError(HttpServletResponse.SC_NOT_FOUND, dataName);
				
				//	send data to requester
				else try {
					//	get data input stream
					DataInputStream dis = this.getInputStream(dataName, dataTimestamp);
					
					//	indicate last modification (necessary in "classical" UrlConfiguration)
					response.setHeader("Last-Modified", lastModifiedFormat.format(new Date(dataTimestamp)));
					response.setHeader("Cache-Control", "no-cache");
					response.setContentLength(dis.length);
					
					//	loop data through to requester
					OutputStream os = new BufferedOutputStream(response.getOutputStream());
					byte[] inBuf = new byte[1024];
					int inLen = -1;
					while ((inLen = dis.read(inBuf)) != -1)
						os.write(inBuf, 0, inLen);
					os.flush();
					
					//	close streams
					dis.close();
				}
				catch (FileNotFoundException e) {
					response.sendError(HttpServletResponse.SC_NOT_FOUND, dataName);
				}
			}
		}
		
		//	indicate data not found
		else response.sendError(HttpServletResponse.SC_NOT_FOUND);
	}
	
	private static final SimpleDateFormat lastModifiedFormat = new SimpleDateFormat("EE, dd MMM yyyy HH:mm:ss 'GMT'Z", Locale.US);
}