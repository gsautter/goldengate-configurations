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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Configuration;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.SpecialDataHandler;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.ecs.GoldenGateEcsConstants;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.util.Base64InputStream;
import de.uka.ipd.idaho.goldenGateServer.util.Base64OutputStream;
import de.uka.ipd.idaho.stringUtils.StringVector;
//import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineInputStream;
//import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineOutputStream;

/**
 * A client for remotely accessing the editor configuration provided by a
 * GoldenGATE ECS.
 * 
 * @author sautter
 */
public class GoldenGateEcsClient implements GoldenGateEcsConstants {
	
	//	TODO switch to binary transfer once ECS update deployed to server
	
	private AuthenticatedClient authClient;
	
	/** Constructor
	 * @param	ac	the authenticated client to use for authentication and connection 
	 */
	public GoldenGateEcsClient(AuthenticatedClient ac) {
		this.authClient = ac;
	}
	
	/**
	 * Retrieve the names of the configurations available to the user currently
	 * logged in
	 * @return an array holding the names of the configurations available to the
	 *         user currently logged in
	 * @throws IOException
	 */
	public String[] getConfigurationNames() throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_CONFIGURATION_NAMES);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_CONFIGURATION_NAMES.equals(error)) {
				StringVector configurations = new StringVector();
				String configuration;
				while ((configuration = br.readLine()) != null)
					configurations.addElementIgnoreDuplicates(configuration);
				return configurations.toStringArray();
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
	
	/**
	 * Retrieve the description of the default configuration for the GoldenGATE Editor
	 * from the backing server. The description returned by this method has to
	 * be wrapped in an EcsConfiguration according to the local environment
	 * before the GoldenGATE Editor can be started with it.
	 * @return the descriptor of the default configuration for the user logged
	 *         in on this GoldenGATE ECS client
	 * @throws IOException
	 */
	public Configuration getConfiguration() throws IOException {
		return this.getConfiguration(null);
	}
	
	/**
	 * Retrieve the descriptor of the configuration with a specific name from
	 * the backing server. The description returned by this method has to be
	 * wrapped in an EcsConfiguration according to the local environment before
	 * the GoldenGATE Editor can be started with it.
	 * @param name the name of the configuration to load (specifying null will
	 *            result in the default configuration being returned)
	 * @return the descriptor of the configuration with the specified name for
	 *         the user logged in on this GoldenGATE ECS client
	 * @throws IOException
	 */
	public Configuration getConfiguration(String name) throws IOException {
		return this.getConfiguration(name, null);
	}
	
	/**
	 * Retrieve the descriptor of the default configuration for editing the
	 * document with the specified ID in the GoldenGATE Editor from the backing
	 * server. A configuration for a specific document will be a part of the
	 * general configuration for the user logged in on this client.
	 * Specifically, the configuration depends on the markup status (as computed
	 * by a CMS) of the document identified by the argument ID and will consist
	 * only of the resources necessary to mark up this document from its current
	 * status to the end of the markup process. This results in a slimmer
	 * configuration that takes less memory to load. It is recommended in
	 * circumstances where the GoldenGATE editor is opened especially for
	 * editing a specific document, for instance in a web portal. The
	 * description returned by this method has to be wrapped in an
	 * EcsConfiguration according to the local environment before the GoldenGATE
	 * Editor can be started with it.
	 * @param documentId the ID of the document to obtain a configuration for
	 *            (specifying null will result in the general configuration
	 *            being returned)
	 * @return the descriptor for the document specific configuration for the
	 *         user logged in on this GoldenGATE ECS client
	 * @throws IOException
	 */
	public Configuration getDocumentConfiguration(String documentId) throws IOException {
		return this.getDocumentConfiguration(null, documentId);
	}
	
	/**
	 * Retrieve the descriptor of the configuration with a specific name for
	 * editing the document with the specified ID in the GoldenGATE Editor from
	 * the backing server. The description returned by this method has to be
	 * wrapped in an EcsConfiguration according to the local environment before
	 * the GoldenGATE Editor can be started with it.
	 * @param documentId the ID of the document to obtain a configuration for
	 *            (specifying null will result in the general configuration
	 *            being returned)
	 * @return the descriptor for the document specific configuration with the
	 *         specified name for the user logged in on this GoldenGATE ECS
	 *         client
	 * @throws IOException
	 */
	public Configuration getDocumentConfiguration(String name, String documentId) throws IOException {
		return this.getConfiguration(name, documentId);
	}
	
	private Configuration getConfiguration(String name, String documentId) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_CONFIGURATION);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write((name == null) ? "" : name);
			bw.newLine();
			bw.write((documentId == null) ? "" : documentId);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_CONFIGURATION.equals(error))
				return Configuration.readConfiguration(br);
			
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
	
	/**
	 * Retrieve a data item from the GoldenGATE ECS, useful for loading the
	 * actual data of configurations in circumstances where URLs cannot be used.
	 * In particular, this is necessary when using a dirct host/port connection
	 * to the server, without HTTP.
	 * @param dataName the path (prefixed with the configuration's base path) and
	 *            name of the data item to fetch
	 * @return an InputStream for reading the data item with the specified name
	 *         from, or null, if the data item does not exist
	 * @throws IOException
	 */
//	public InputStream getData(String dataName) throws IOException {
//		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
//		
//		final Connection con;
//		try {
//			con = this.authClient.getConnection();
//			BufferedLineOutputStream blos = con.getOutputStream();
//			
//			blos.writeLine(GET_DATA);
//			blos.writeLine(this.authClient.getSessionID());
//			blos.writeLine(dataName);
//			blos.flush();
//			
//			BufferedLineInputStream blis = con.getInputStream();
//			String error = blis.readLine();
//			if (GET_DATA.equals(error))
//				return new FilterInputStream(blis) {
//					private Connection connection = con;
//					public void close() throws IOException {
//						this.connection.close();
//						this.connection = null;
//					}
//					protected void finalize() throws Throwable {
//						if (this.connection != null)
//							this.connection.close();
//					}
//				};
//			
//			else {
//				con.close();
//				throw new IOException(error);
//			}
//		}
//		catch (Exception e) {
//			throw new IOException(e.getMessage());
//		}
//	}
	public InputStream getData(String dataName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		final Connection con;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_DATA);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(dataName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_DATA.equals(error))
				return new FilterInputStream(new Base64InputStream(br)) {
					private Connection connection = con;
					public void close() throws IOException {
						this.connection.close();
						this.connection = null;
					}
					protected void finalize() throws Throwable {
						if (this.connection != null)
							this.connection.close();
					}
				};
			
			else {
				con.close();
				throw new IOException(error);
			}
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}
	
	/**
	 * Upload a data item to the GoldenGATE ECS, useful for uploading the actual
	 * data of configurations after client side modifications. This requires
	 * administrative privileges and is intended for updating the configuration
	 * hosted by the ECS.
	 * @param dataName the path (prefixed with the configuration's base path) and
	 *            name of the data item to fetch
	 * @return an InputStream for writing the data item with the specified name
	 *         to
	 * @throws IOException
	 */
//	public OutputStream updateData(String dataName) throws IOException {
//		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
//		
//		final Connection con;
//		try {
//			con = this.authClient.getConnection();
//			final BufferedLineOutputStream blos = con.getOutputStream();
//			
//			blos.writeLine(UPDATE_DATA);
//			blos.writeLine(this.authClient.getSessionID());
//			blos.writeLine(dataName);
//			
//			return new FilterOutputStream(blos) {
//				private Connection connection = con;
//				private boolean unwritten = true;
//				public void write(int b) throws IOException {
//					this.unwritten = false;
//					super.write(b);
//				}
//				public void close() throws IOException {
//					if (this.unwritten) {
//						blos.write((int) '\r');
//						blos.write((int) '\n');
//						System.out.println("Padded empty file upload");
//					}
//					blos.newLine();
//					blos.flush();
//					BufferedLineInputStream blis = con.getInputStream();
//					String error = blis.readLine();
//					if (!UPDATE_DATA.equals(error))
//						throw new IOException(error);
//					this.connection.close();
//					this.connection = null;
//				}
//				protected void finalize() throws Throwable {
//					if (this.connection != null)
//						this.connection.close();
//				}
//			};
//		}
//		catch (Exception e) {
//			throw new IOException(e.getMessage());
//		}
//	}
	public OutputStream updateData(String dataName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		final Connection con;
		try {
			con = this.authClient.getConnection();
			final BufferedWriter bw = con.getWriter();
			
			bw.write(UPDATE_DATA);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(dataName);
			bw.newLine();
			
			final Base64OutputStream bos = new Base64OutputStream(bw);
			return new FilterOutputStream(bos) {
				private Connection connection = con;
				private boolean unwritten = true;
				public void write(int b) throws IOException {
					this.unwritten = false;
					super.write(b);
				}
				public void close() throws IOException {
					bos.close(false);
					if (this.unwritten) {
						bw.write(0);
						System.out.println("Padded empty file upload");
					}
					bw.newLine();
					bw.flush();
					BufferedReader br = con.getReader();
					String error = br.readLine();
					if (!UPDATE_DATA.equals(error))
						throw new IOException(error);
					this.connection.close();
					this.connection = null;
				}
				protected void finalize() throws Throwable {
					if (this.connection != null)
						this.connection.close();
				}
			};
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}
	
	/**
	 * Delete a data item in the GoldenGATE ECS.
	 * @param dataName the path (prefixed with the configuration's base path) and
	 *            name of the data item to fetch
	 * @throws IOException
	 */
	public void deleteData(String dataName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(DELETE_DATA);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(dataName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!DELETE_DATA.equals(error))
				throw new IOException(error);
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Retrieve detail information about all configurations existing in the
	 * backing ECS. The configuration objects returned by this method will not
	 * contain any plugins or resources, only the mere main data. (requires
	 * administrative privileges)
	 * @return an array of descriptors for all the configurations existing in
	 *         the backing ECS
	 * @throws IOException
	 */
	public Configuration[] getConfigurationDescriptors() throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			final BufferedWriter bw = con.getWriter();
			
			bw.write(GET_CONFIGURATION_DESCRIPTORS);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
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
					System.out.println(configData);
					if (("</" + ConfigurationUtils.configuration_NODE_TYPE + ">").equals(configData.trim())) {// end of configuration
						configList.add(Configuration.readConfiguration(new StringReader(configDataCollector.toString())));
						configDataCollector = new StringBuffer();
					}
				}
				return ((Configuration[]) configList.toArray(new Configuration[configList.size()]));
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
	
	/**
	 * Retrieve the descriptor of a specific configuration, ignoring
	 * permissions, levels, resource, groups, etc. (requires administrative
	 * privileges)
	 * @param configName the name of the configuration to retrieve the
	 *            descriptor for
	 * @return an unfiltered descriptor for the configuration with the specified
	 *         name
	 * @throws IOException
	 */
	public Configuration getConfigurationDescriptor(String configName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			final BufferedWriter bw = con.getWriter();
			
			bw.write(GET_CONFIGURATION_DESCRIPTOR);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(configName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_CONFIGURATION_DESCRIPTOR.equals(error))
				return Configuration.readConfiguration(br);
			
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
	
	/**
	 * Update a configuration. If a configuration with the specified name does
	 * not exist in the backing ECS, it is created. The updated or newly created
	 * configuration will be a projection of the specified base configuration,
	 * inheriting all updates of the latter, but having no option of updating
	 * its backing data itself. For creating or updating materialized
	 * configurations, use uploadConfiguration(). (requires administrative
	 * privileges)
	 * @param configName the name of the configuration.
	 * @param baseConfigName the name of the configuration that forms the basis.
	 *            On updates of existing configurations, this parameter can be
	 *            null. It can also be used for changing the base configuration
	 *            of a projected configuration.
	 * @param plugins the names of the plugins in the base configuration to be
	 *            included in the configuration
	 * @param resources the names of the resources in the base configuration to
	 *            be included in the configuration
	 * @throws IOException
	 */
	public void updateConfiguration(String configName, String baseConfigName, String[] plugins, String[] resources) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(UPDATE_CONFIGURATION);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(configName);
			bw.newLine();
			bw.write(baseConfigName);
			bw.newLine();
			for (int p = 0; p < plugins.length; p++) {
				bw.write("P:" + plugins[p]);
				bw.newLine();
			}
			for (int r = 0; r < resources.length; r++) {
				bw.write("R:" + resources[r]);
				bw.newLine();
			}
			bw.newLine(); // terminal newline to prevent server from blocking
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!UPDATE_CONFIGURATION.equals(error))
				throw new IOException(error);
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Upload a new configuration from a GoldenGATE Editor to ECS. This will
	 * create a new materialized configuration on ECS, using the data items from
	 * the local GoldenGATE installation. If the specified descriptor point to a
	 * configuration that already exists, this configuration will be updated.
	 * (requires administrative privileges)
	 * @param basePath the local base path of the configuration to upload
	 * @param specialData the handler for special data
	 * @param config the descriptor of the configuration to upload
	 * @param pm a progress monitor for monitoring the upload process (may be
	 *            null)
	 * @throws IOException
	 */
	public void uploadConfiguration(File basePath, SpecialDataHandler specialData, Configuration config, ProgressMonitor pm) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			final BufferedWriter bw = con.getWriter();
			
			bw.write(UPLOAD_CONFIGURATION);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			config.writeXml(bw);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (UPLOAD_CONFIGURATION.equals(error)) {
				long sConfigAge = Long.parseLong(br.readLine());
				System.out.println("GgEcsClient: receiving server file list");
				Set sFiles = new HashSet();
				String sFile;
				while (((sFile = br.readLine()) != null) && (sFile.length() != 0)) {
					sFiles.add(sFile);
					System.out.println(" - " + sFile);
				}
				con.close();
				
				long lConfigTime = (config.configTimestamp - sConfigAge);
				
				String[] files = ConfigurationUtils.getDataNameList(basePath, config);
				Arrays.sort(files);
				
				for (int f = 0; f < files.length; f++) {
					File file = new File(basePath, files[f]);
					InputStream specialSource = ((specialData == null) ? null : specialData.getInputStream(files[f]));
					
					if (specialSource == null) {
						if (sFiles.contains(files[f]) && (file.lastModified() < lConfigTime)) {
							System.out.println("GgEcsClient: skipping up-to-date file " + files[f]);
							if (pm != null) {
								pm.setInfo("GgEcsClient: skipping up-to-date file " + files[f]);
								pm.setProgress(((f+1) * 100) / files.length);
							}
							continue;
						}
						if (!sFiles.contains(files[f]) && (file.length() == 0)) {
							System.out.println("GgEcsClient: skipping empty file " + files[f]);
							if (pm != null) {
								pm.setInfo("GgEcsClient: skipping empty file " + files[f]);
								pm.setProgress(((f+1) * 100) / files.length);
							}
							continue;
						}
					}
					
					System.out.println("GgEcsClient: updating " + files[f]);
					if (pm != null)
						pm.setInfo("GgEcsClient: updating " + files[f]);
					
					InputStream in = ((specialSource == null) ? new FileInputStream(file) : specialSource);
					System.out.println("- got source");
					OutputStream out = this.updateData(config.name + "/" + files[f]);
					System.out.println("- got sink");
					byte[] buffer = new byte[1024];
					int read;
					System.out.print("- sending ");
					while ((read = in.read(buffer)) != -1) {
						out.write(buffer, 0, read);
						System.out.print(".");
					}
					System.out.println();
					System.out.println("- data sent");
					out.flush();
					out.close();
					in.close();
					System.out.println("- streams closed");
					
					if (pm != null)
						pm.setProgress(((f+1) * 100) / files.length);
				}
			}
			else throw new IOException(error);
		}
		catch (Exception e) {
			System.out.println("Error on export: " + e.getMessage());
			e.printStackTrace(System.out);
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Delete a configuration. If the configuration with the specified name is
	 * materialized, it cannot be deleted. (requires administrative privileges)
	 * @param configName the name of the configuration to delete.
	 * @throws IOException
	 */
	public void deleteConfiguration(String configName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			final BufferedWriter bw = con.getWriter();
			
			bw.write(DELETE_CONFIGURATION);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(configName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!DELETE_CONFIGURATION.equals(error))
				throw new IOException(error);
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Retrieve the mapping of user names to default configuration names. Users
	 * not mapped to any configuration explicitly use the ECS's default
	 * configuration. This configuration is marked by its name enclosed in angle
	 * brackets. (requires administrative privileges)
	 * @return a map containing the user names and corresponding default
	 *         configuration names, user names in alphabetical order
	 * @throws IOException
	 */
	public SortedMap getUserDefaultConfigurations() throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			final BufferedWriter bw = con.getWriter();
			
			bw.write(GET_USER_CONFIGURATIONS);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_USER_CONFIGURATIONS.equals(error)) {
				SortedMap userConfigs = new TreeMap();
				String userConfig;
				while ((userConfig = br.readLine()) != null) {
					String[] userConfigParts = userConfig.split("\\:");
					if (userConfigParts.length == 2)
						userConfigs.put(userConfigParts[0], userConfigParts[1]);
				}
				return userConfigs;
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
	
	/**
	 * Set the mapping of user names to default configuration names. Users not
	 * mapped to any configuration explicitly are set to use the ECS's default
	 * configuration. (requires administrative privileges)
	 * @param userConfigs a map containing the user names and corresponding
	 *            default configuration names to set
	 * @throws IOException
	 */
	public void setUserDefaultConfigurations(Map userConfigs) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			final BufferedWriter bw = con.getWriter();
			
			bw.write(SET_USER_CONFIGURATIONS);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			for (Iterator uit = userConfigs.keySet().iterator(); uit.hasNext();) {
				String userName = ((String) uit.next());
				String userConfigName = ((String) userConfigs.get(userName));
				if (userConfigName != null) {
					bw.write(userName + ":" + userConfigName);
					bw.newLine();
				}
			}
			bw.newLine(); // terminal newline to prevent server from blocking
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!SET_USER_CONFIGURATIONS.equals(error))
				throw new IOException(error);
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	
	/**
	 * Retrieve the list of configurations available online, i.e., through a
	 * configuration servlet. (requires administrative privileges)
	 * @return the list of configurations available online
	 * @throws IOException
	 */
	public String[] getOnlineConfigurations() throws IOException {
		return this.retrieveList(GET_ONLINE_CONFIGURATIONS);
	}
	
	/**
	 * Set the configurations available online. (requires administrative privileges)
	 * @param onlineConfigs the list of configurations available online
	 * @throws IOException
	 */
	public void setOnlineConfigurations(String[] onlineConfigs) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			final BufferedWriter bw = con.getWriter();
			
			bw.write(SET_ONLINE_CONFIGURATIONS);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			for (int oc = 0; oc < onlineConfigs.length; oc++) {
				bw.write(onlineConfigs[oc]);
				bw.newLine();
			}
			bw.newLine(); // terminal newline to prevent server from blocking
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!SET_ONLINE_CONFIGURATIONS.equals(error))
				throw new IOException(error);
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	
	/**
	 * Retrieve all available groups (requires administrative privileges)
	 * @return an array holding all groups available in the ECS
	 * @throws IOException
	 */
	public String[] getGroups() throws IOException {
		return this.retrieveList(GET_GROUPS);
	}
	
	/**
	 * Retrieve all available plugins (requires administrative privileges)
	 * @return an array holding all plugins available in the ECS
	 * @throws IOException
	 */
	public String[] getPlugins() throws IOException {
		return this.retrieveList(GET_PLUGINS);
	}
	
	/**
	 * Retrieve all available resources (requires administrative privileges)
	 * @return an array holding all resources available in the ECS
	 * @throws IOException
	 */
	public String[] getResources() throws IOException {
		return this.retrieveList(GET_RESOURCES);
	}
	
	private String[] retrieveList(String command) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(command);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (command.equals(error)) {
				StringVector list = new StringVector();
				String lisEntry;
				while ((lisEntry = br.readLine()) != null)
					list.addElementIgnoreDuplicates(lisEntry);
				return list.toStringArray();
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
	
	
	/**
	 * create a new group for the ECS (requires administrative privileges)
	 * @param groupName the name for the new group
	 * @throws IOException
	 */
	public void createGroup(String groupName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		//	check parameter
		if (groupName == null)
			throw new IOException("Invalid arguments for creating a group.");
		
		//	create user
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(CREATE_GROUP);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(groupName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!CREATE_GROUP.equals(error))
				throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * delete a group (requires administrative privileges)
	 * @param groupName the name of the group to delete
	 * @throws IOException
	 */
	public void deleteGroup(String groupName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		//	check login parameters
		if (groupName == null)
			throw new IOException("Invalid arguments for deleting a group.");
		
		//	delete user
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(DELETE_GROUP);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(groupName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!DELETE_GROUP.equals(error))
				throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Retrieve the plugins explicitly granted to a specific group, excluding
	 * the ones obtained from other groups (requires administrative privileges)
	 * @param groupName the group to retrieve the plugins for
	 * @return an array holding the plugins explicitly granted to the
	 *         specified group
	 * @throws IOException
	 */
	public String[] getGroupPlugins(String groupName) throws IOException {
		return this.getGroupsOrPluginsOrResources(GET_GROUP_PLUGINS, groupName, false);
	}
	
	/**
	 * Set the plugins of a group (requires administrative privileges). This
	 * operation will not change plugins obtained through other groups.
	 * @param groupName the group to set the plugins for
	 * @param plugins the plugins the specified group shall have from now
	 *            on
	 * @throws IOException
	 */
	public void setGroupPlugins(String groupName, String[] plugins) throws IOException {
		this.modifyGroupsOrPluginsOrResources(SET_GROUP_PLUGINS, groupName, plugins);
	}
	
	/**
	 * Retrieve the resources explicitly granted to a specific group, excluding
	 * the ones obtained from other groups (requires administrative privileges)
	 * @param groupName the group to retrieve the resources for
	 * @return an array holding the resources explicitly granted to the
	 *         specified group
	 * @throws IOException
	 */
	public String[] getGroupResources(String groupName) throws IOException {
		return this.getGroupsOrPluginsOrResources(GET_GROUP_RESOURCES, groupName, false);
	}
	
	/**
	 * Set the resources of a group (requires administrative privileges). This
	 * operation will not change resources obtained through other groups.
	 * @param groupName the group to set the resources for
	 * @param resources the resources the specified group shall have from now
	 *            on
	 * @throws IOException
	 */
	public void setGroupResources(String groupName, String[] resources) throws IOException {
		this.modifyGroupsOrPluginsOrResources(SET_GROUP_RESOURCES, groupName, resources);
	}
	
	private String[] getGroupsOrPluginsOrResources(String command, String levelOrGroupName, boolean allowNullName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(command);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(((levelOrGroupName == null) && allowNullName) ? "" : levelOrGroupName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (command.equals(error)) {
				StringVector groupsOrPluginsOrResources = new StringVector();
				String groupOrPluginOrResource;
				while ((groupOrPluginOrResource = br.readLine()) != null)
					groupsOrPluginsOrResources.addElementIgnoreDuplicates(groupOrPluginOrResource);
				return groupsOrPluginsOrResources.toStringArray();
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
	
	private void modifyGroupsOrPluginsOrResources(String command, String levelOrGroupName, String[] groupsOrPluginsOrResources) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(command);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(levelOrGroupName);
			bw.newLine();
			if (groupsOrPluginsOrResources != null)
				for (int rp = 0; rp < groupsOrPluginsOrResources.length; rp++) {
					bw.write(groupsOrPluginsOrResources[rp]);
					bw.newLine();
				}
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!command.equals(error))
				throw new IOException(error);
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
//	
//	public static void main(String[] args) throws Exception {
//		AuthenticatedClient ac = AuthenticatedClient.getAuthenticatedClient(ServerConnection.getServerConnection("http://plazi.cs.umb.edu/GgServer/proxy"));
//		ac.login("", ""); // TODO add credentials for tests
//		GoldenGateEcsClient ecsc = new GoldenGateEcsClient(ac);
//		Configuration conf = ecsc.getConfiguration("ServerBatch.imagine");
//		BufferedWriter cbw = new BufferedWriter(new OutputStreamWriter(System.out));
//		conf.writeXml(cbw);
//		cbw.flush();
//	}
}