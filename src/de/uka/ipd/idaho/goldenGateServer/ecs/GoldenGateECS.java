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

package de.uka.ipd.idaho.goldenGateServer.ecs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.uka.ipd.idaho.goldenGate.GoldenGateConfiguration;
import de.uka.ipd.idaho.goldenGate.GoldenGateConstants;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Configuration;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.DataItem;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Lib;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Plugin;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Resource;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentRegistry;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthority;
import de.uka.ipd.idaho.goldenGateServer.util.Base64InputStream;
import de.uka.ipd.idaho.goldenGateServer.util.Base64OutputStream;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * The GoldenGATE Editor Configuration Server (ECS) provides specialized
 * configurations for the GoldenGATE Editor when the latter is used as a front
 * end to a GoldenGATE CMS or other document storage server. In particular, the
 * configurations are sub sets of the master configuration, i.e. all plugins and
 * resources available to the ECS. The configurations provided depend on (a) the
 * permissions of the user logging in, and optionally (b) on the markup progress
 * of a given document. For determining the former, ECS uses GoldenGATE UPS,
 * while the latter is obtained from the backing GoldenGATE CMS (if present).
 * 
 * @author sautter
 */
public class GoldenGateECS extends AbstractGoldenGateServerComponent implements GoldenGateConstants, GoldenGateEcsConstants {
	
	private static final String CONFIGURATION_PERMISSION_PREFIX = "ECS.Configuration.";
	private static final String CONFIGURATION_ALL_PERMISSION_SUFFIX = ".All";
	private static final String GROUP_PERMISSION_PREFIX = "ECS.Group.";
	
	private static final String CONFIGURATION_FILE_PREFIX = "Configuration.";
	private static final String GROUP_FILE_PREFIX = "Group.";
	private static final String TXT_FILE_SUFFIX = ".txt";
	
	private static final String PLUGIN_PREFIX = "P:";
	private static final String RESOURCE_PREFIX = "R:";
	
	private UserAccessAuthority uaa = null;
	
	/** Constructor passing 'ECS' as the letter code to super constructor
	 */
	public GoldenGateECS() {
		super("ECS");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateScf.AbstractServerComponent#initComponent()
	 */
	protected void initComponent() {
		
		//	load configurations
		File[] configurationFiles = this.dataPath.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return (file.isFile() && file.getName().startsWith(CONFIGURATION_FILE_PREFIX) && file.getName().endsWith(".xml"));
			}
		});
		for (int c = 0; c < configurationFiles.length; c++) {
			String configurationName = configurationFiles[c].getName();
			configurationName = configurationName.substring(CONFIGURATION_FILE_PREFIX.length());
			configurationName = configurationName.substring(0, (configurationName.length() - TXT_FILE_SUFFIX.length()));
			this.getConfiguration(configurationName);
		}
		
		//	get master configuration
		String masterConfigurationName = this.configuration.getSetting("MasterConfigurationName", "Master");
		this.masterConfiguration = ((Configuration) this.configurationsByName.get(masterConfigurationName));
		if (this.masterConfiguration == null)
			this.masterConfiguration = this.getConfiguration(masterConfigurationName);
		if (this.masterConfiguration == null)
			this.masterConfiguration = new Configuration(masterConfigurationName, masterConfigurationName, System.currentTimeMillis(), DOCUMENTATION_FOLDER_NAME, GoldenGateConstants.CONFIG_FILE_NAME, DATA_FOLDER_NAME);
		
		//	load groups
		File[] groupFiles = this.dataPath.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return (file.isFile() && file.getName().startsWith(GROUP_FILE_PREFIX) && file.getName().endsWith(TXT_FILE_SUFFIX));
			}
		});
		for (int g = 0; g < groupFiles.length; g++) {
			String groupName = groupFiles[g].getName();
			groupName = groupName.substring(GROUP_FILE_PREFIX.length());
			groupName = groupName.substring(0, (groupName.length() - TXT_FILE_SUFFIX.length()));
			this.getGroup(groupName);
		}
		
		//	generate nice names for resources
		for (Iterator cit = this.configurationsByName.keySet().iterator(); cit.hasNext();) {
			Configuration config = this.getConfiguration((String) cit.next());
			for (Iterator pit = config.plugins.iterator(); pit.hasNext();) {
				Plugin plugin = ((Plugin) pit.next());
				for (Iterator rit = plugin.resources.iterator(); rit.hasNext();) {
					Resource resource = ((Resource) rit.next());
					this.resourceNameMappings.put((plugin.name + "." + resource.name), resource.name);
				}
			}
		}
		
		//	load user -> default configuration mapping
		try {
			StringVector userConfigs = StringVector.loadList(new File(this.dataPath, "UserConfigurations.txt"));
			for (int uc = 0; uc < userConfigs.size(); uc++) {
				String[] userConfigParts = userConfigs.get(uc).split("\\:");
				if (userConfigParts.length == 2)
					this.userDefaultConfigurations.put(userConfigParts[0], userConfigParts[1]);
			}
		}
		catch (IOException ioe) {
			System.out.println(ioe.getMessage() + " while loading user default configurations file.");
			ioe.printStackTrace(System.out);
		}
		
		//	load online configurations
		try {
			StringVector onlineConfigs = StringVector.loadList(new File(this.dataPath, "OnlineConfigurations.txt"));
			for (int oc = 0; oc < onlineConfigs.size(); oc++)
				this.onlineConfigurations.add(onlineConfigs.get(oc));
		}
		catch (IOException ioe) {
			System.out.println(ioe.getMessage() + " while loading online configurations file.");
			ioe.printStackTrace(System.out);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#link()
	 */
	public void link() {
		
		//	get access authority
		this.uaa = ((UserAccessAuthority) GoldenGateServerComponentRegistry.getServerComponent(UserAccessAuthority.class.getName()));
		
		//	check success
		if (this.uaa == null) throw new RuntimeException(UserAccessAuthority.class.getName());
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#linkInit()
	 */
	public void linkInit() {
		
		//	create permissions for groups
		for (Iterator git = this.groupsByName.keySet().iterator(); git.hasNext();)
			this.uaa.registerPermission(GROUP_PERMISSION_PREFIX + ((String) git.next()));
		
		//	create permissions for configurations
		for (Iterator cit = this.configurationsByName.keySet().iterator(); cit.hasNext();) {
			String configName = ((String) cit.next());
			this.uaa.registerPermission(CONFIGURATION_PERMISSION_PREFIX + configName);
			this.uaa.registerPermission(CONFIGURATION_PERMISSION_PREFIX + configName + CONFIGURATION_ALL_PERMISSION_SUFFIX);
		}
	}
	
	private ArrayList documentResourceFilterers = new ArrayList(); 
	
	/**
	 * Register a DocumentResourceFilterer to provide the resource groups to use
	 * in a document specific fashion
	 * @param drf the DocumentResourceFilterer to add
	 */
	public void addDocumentResourceFilterer(DocumentResourceFilterer drf) {
		if (drf != null)
			this.documentResourceFilterers.add(drf);
	}
	
	/**
	 * Remove a DocumentResourceFilterer
	 * @param drf the DocumentResourceFilterer to remove
	 */
	public void removeDocumentResourceFilterer(DocumentResourceFilterer drf) {
		if (drf != null)
			this.documentResourceFilterers.remove(drf);
	}
	
	//	mapping of resource nice names to permission Strings
	private TreeMap resourceNameMappings = new TreeMap();
	
	private Configuration getConfiguration(String configurationName) {
		if (configurationName == null)
			return null;
		Configuration configuration = ((Configuration) this.configurationsByName.get(configurationName));
		if (configuration == null) {
			configuration = this.loadConfiguration(configurationName);
			if (configuration != null)
				this.configurationsByName.put(configurationName, configuration);
		}
		return configuration;
	}
	
	private void updateConfiguration(String configName, Configuration model, Set pluginNames, Set resourceNames) throws IOException {
		this.configurationsByName.remove(configName);
		
		File configFile = new File(this.dataPath, (CONFIGURATION_FILE_PREFIX + configName + ".xml"));
		this.invalidate(configFile);
		
		Configuration config = this.projectConfiguration(configName, model, pluginNames, resourceNames);
		
		configFile.createNewFile();
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile)));
		config.writeXml(bw);
		bw.flush();
		bw.close();
		
		this.configurationsByName.put(configName, config);
		this.uaa.registerPermission(CONFIGURATION_PERMISSION_PREFIX + configName);
		this.uaa.registerPermission(CONFIGURATION_PERMISSION_PREFIX + configName + CONFIGURATION_ALL_PERMISSION_SUFFIX);
	}
	
	private void deleteConfiguration(String configName) throws IOException {
		this.configurationsByName.remove(configName);
		this.deleteGroup(CONFIGURATION_FILE_PREFIX + configName);
		this.invalidate(new File(this.dataPath, (CONFIGURATION_FILE_PREFIX + configName + ".xml")));
	}
	
	private Configuration loadConfiguration(String configName) {
		File configFile = new File(this.dataPath, (CONFIGURATION_FILE_PREFIX + configName + ".xml"));
		try {
			Reader configReader = new FileReader(configFile);
			Configuration config = Configuration.readConfiguration(configReader);
			configReader.close();
			return config;
		}
		catch (FileNotFoundException fnfe) {
			System.out.println(fnfe.getMessage() + " while loading configuration '" + configName + "'");
			fnfe.printStackTrace(System.out);
		}
		catch (IOException ioe) {
			System.out.println(ioe.getMessage() + " while loading configuration '" + configName + "'");
			ioe.printStackTrace(System.out);
		}
		return null;
	}
	
	/*
	 * Store a new configuration descriptor, and compute the time since the last
	 * update of this descriptor.
	 * @param config the configuration descriptor representing the
	 *            configuration's most recent status
	 * @param receiveTime the time (in milliseconds) when the descriptor was
	 *            received
	 * @return the time (in milliseconds) since the last update of the specified
	 *         configuration
	 * @throws IOException
	 */
	private long uploadConfiguration(Configuration config, long receiveTime) throws IOException {
		
		//	compute time delta
		long timeDelta = config.configTimestamp - receiveTime;
		
		//	localize timestamps
		Configuration localConfig = new Configuration(config.name, config.name, (config.configTimestamp - timeDelta), config.helpBasePath, config.settingsPath, config.iconImagePath);
		
		//	copy data items
		for (Iterator dit = config.dataItems.iterator(); dit.hasNext();) {
			DataItem di = ((DataItem) dit.next());
			localConfig.addDataItem(new DataItem(di.path, (di.timestamp - timeDelta)));
		}
		
		//	go through plugins
		for (Iterator pit = config.plugins.iterator(); pit.hasNext();) {
			Plugin plugin = ((Plugin) pit.next());
			
			//	copy plugin
			Plugin localPlugin = new Plugin(plugin.name, plugin.className, plugin.classPath, (plugin.timestamp - timeDelta), plugin.dataPath);
			localConfig.addPlugin(localPlugin);
			
			//	copy dependencies
			localPlugin.requiredPluginClassNames.addAll(plugin.requiredPluginClassNames);
			
			//	copy libs
			for (Iterator lit = plugin.libs.iterator(); lit.hasNext();) {
				Lib lib = ((Lib) lit.next());
				localPlugin.libs.add(new Lib(lib.path, (lib.timestamp - timeDelta)));
			}
			
			//	copy data items
			for (Iterator dit = plugin.dataItems.iterator(); dit.hasNext();) {
				DataItem di = ((DataItem) dit.next());
				localPlugin.dataItems.add(new DataItem(di.path, (di.timestamp - timeDelta)));
			}
			
			//	copy resources & data items
			for (Iterator rit = plugin.resources.iterator(); rit.hasNext();) {
				Resource res = ((Resource) rit.next());
				
				//	copy resource
				Resource localRes = new Resource(res.name, res.path, (res.timestamp - timeDelta), res.managerClassName);
				localConfig.addResource(localRes);
				localPlugin.resources.add(localRes);
				
				//	copy dependencies
				localRes.requiredResourceNames.addAll(res.requiredResourceNames);
				
				//	copy data items
				for (Iterator dit = res.dataItems.iterator(); dit.hasNext();) {
					DataItem di = ((DataItem) dit.next());
					localRes.dataItems.add(new DataItem(di.path, (di.timestamp - timeDelta)));
				}
			}
		}
		
		File configFile = new File(this.dataPath, (CONFIGURATION_FILE_PREFIX + localConfig.name + ".xml"));
		long configTime = configFile.lastModified();
		
		this.invalidate(configFile);
		this.configurationsByName.remove(localConfig.name);
		
		configFile.createNewFile();
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile)));
		localConfig.writeXml(bw);
		bw.flush();
		bw.close();
		
		this.configurationsByName.put(localConfig.name, localConfig);
		
		this.uaa.registerPermission(CONFIGURATION_PERMISSION_PREFIX + localConfig.name);
		this.uaa.registerPermission(CONFIGURATION_PERMISSION_PREFIX + localConfig.name + CONFIGURATION_ALL_PERMISSION_SUFFIX);
		
		for (Iterator pit = localConfig.plugins.iterator(); pit.hasNext();) {
			Plugin plugin = ((Plugin) pit.next());
			for (Iterator rit = plugin.resources.iterator(); rit.hasNext();) {
				Resource resource = ((Resource) rit.next());
				this.resourceNameMappings.put((plugin.name + "." + resource.name), resource.name);
			}
		}
		
		return (receiveTime - configTime);
	}
	
	private Group getGroup(String groupName) {
		Group group = ((Group) this.groupsByName.get(groupName));
		if (group == null)
			group = this.loadGroup(groupName);
		return group;
	}
	
	private synchronized Group loadGroup(String groupName) {
		File groupFile = new File(this.dataPath, (GROUP_FILE_PREFIX + groupName + TXT_FILE_SUFFIX));
		Group group = new Group(groupName);
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(groupFile)));
			String pluginOrResource;
			while ((pluginOrResource = br.readLine()) != null) {
				if (pluginOrResource.startsWith(PLUGIN_PREFIX))
					group.plugins.add(pluginOrResource.substring(PLUGIN_PREFIX.length()).trim());
				else if (pluginOrResource.startsWith(RESOURCE_PREFIX))
					group.resources.add(pluginOrResource.substring(RESOURCE_PREFIX.length()).trim());
			}
			br.close();
		}
		catch (IOException ioe) {
			System.out.println(ioe.getClass().getName() + " (" + ioe.getMessage() + ") while loading group '" + groupName + "'.");
			ioe.printStackTrace(System.out);
		}
		return group;
	}
	
	private synchronized void saveGroup(Group group) throws IOException {
		File groupFile = new File(this.dataPath, (GROUP_FILE_PREFIX + group.name + TXT_FILE_SUFFIX));
		this.invalidate(groupFile);
		groupFile.createNewFile();
		
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(groupFile)));
		
		for (Iterator pit = group.plugins.iterator(); pit.hasNext();) {
			bw.write(PLUGIN_PREFIX + pit.next().toString());
			bw.newLine();
		}
		
		for (Iterator rit = group.resources.iterator(); rit.hasNext();) {
			bw.write(RESOURCE_PREFIX + rit.next().toString());
			bw.newLine();
		}
		
		bw.flush();
		bw.close();
	}
	
	private void invalidate(File file) {
		if (file.exists())
			file.renameTo(new File(file.getAbsolutePath() + "." + System.currentTimeMillis() + ".old"));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateScf.ServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	get names of available configurations
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_CONFIGURATION_NAMES;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				
				//	get user name
				String userName = uaa.getUserNameForSession(sessionId);
				
				//	get configuration names
				String[] configs = getConfigurationNames(userName);
				
				//	indicate configuration coming
				output.write(GET_CONFIGURATION_NAMES);
				output.newLine();
				
				//	send configurations
				for (int c = 0; c < configs.length; c++) {
					output.write(configs[c]);
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	get configuration
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_CONFIGURATION;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				
				//	get user name
				String userName = uaa.getUserNameForSession(sessionId);
				
				//	get configuration name
				String configName = input.readLine();
				if ("".equals(configName)) configName = null;
				
				//	get document ID
				String documentId = input.readLine();
				if ("".equals(documentId)) documentId = null;
				
				//	get resources required for document
				Set docResGroupSet = new HashSet();
				if (documentId != null)
					for (int f = 0; f < documentResourceFilterers.size(); f++) {
						String[] docResGroups = ((DocumentResourceFilterer) documentResourceFilterers.get(f)).getGroupsForDocument(documentId);
						if (docResGroups != null)
							docResGroupSet.addAll(Arrays.asList(docResGroups));
					}
				if (docResGroupSet.isEmpty())
					docResGroupSet = null;
				
				//	get base configuration for projection
				Configuration baseConfig = ((configName == null) ? null : getConfiguration(configName));
				if (baseConfig == null) baseConfig = getUserConfiguration(userName);
				
				//	get configuration
				/*
				 * if user is admin, do NOT project configuration, but deliver
				 * "as is", with plain config name ==> makes Master
				 * configurations recognizable and thus editable in client
				 */
				Configuration config = (uaa.isAdmin(userName) ? baseConfig : projectConfiguration(userName, docResGroupSet, baseConfig));
				
				//	indicate configuration coming
				output.write(GET_CONFIGURATION);
				output.newLine();
				
				//	send configuration
				config.writeXml(output);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	get data
		ca = new ComponentActionNetwork() {
			private static final boolean DEBUG = true;
			public String getActionCommand() {
				return GET_DATA;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId) && !CONFIG_SERVLET_SESSION_ID.equals(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				
				//	read data name
				String dataName = input.readLine();
				if (DEBUG) System.out.println("Data name is " + dataName);
				
				//	create and check data file
				File dataFile = new File(dataPath, dataName);
				if (dataFile.exists() && dataFile.isFile()) {
					if (DEBUG) System.out.println("File is " + dataFile.getAbsolutePath());
					
					//	indicate configuration coming
					output.write(GET_DATA);
					output.newLine();
					
					FileInputStream fis = new FileInputStream(dataFile);
					byte[] buffer = new byte[1024];
					int read;
					Base64OutputStream bos = new Base64OutputStream(output);
					if (DEBUG) System.out.println("Got streams, start sending");
					while ((read = fis.read(buffer)) != -1)
						bos.write(buffer, 0, read);
					if (DEBUG) System.out.println("Data sent");
					fis.close();
					bos.close(false);
					if (DEBUG) System.out.println("Streams closed");
				}
				
				//	indicate failure
				else {
					output.write("Data not found, or it's a directory");
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	update data
		ca = new ComponentActionNetwork() {
			private static final boolean DEBUG = true;
			public String getActionCommand() {
				return UPDATE_DATA;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				
				//	read data name
				String dataName = input.readLine();
				if (DEBUG) System.out.println("Data name is " + dataName);
				
				//	create and check data file
				File dataFile = new File(dataPath, dataName);
				
				//	data file exists, make way
				if (dataFile.exists()) {
					if (dataFile.isFile())
						dataFile.renameTo(new File(dataFile.getAbsolutePath() + "." + System.currentTimeMillis() + ".old"));
					else {
						output.write("Cannot write to directory");
						output.newLine();
						return;
					}
				}
				if (dataFile.getParentFile() != null)
					dataFile.getParentFile().mkdirs();
				dataFile.createNewFile();
				if (DEBUG) System.out.println("File created");
				
				//	create streams
				Base64InputStream bis = new Base64InputStream(new FilterReader(input) {
					boolean gotFirst = false;
					public int read() throws IOException {
						int i = super.read();
						if (i == 0) return -1;
						if (this.gotFirst) return ((i < 33) ? -1 : i);
						else if (i < 33) return this.read();
						else {
							this.gotFirst = true;
							return i;
						}
					}
				});
				FileOutputStream fos = new FileOutputStream(dataFile, true);
				if (DEBUG) System.out.println("Got streams");
				byte[] buffer = new byte[1024];
				for (int read; (read = bis.read(buffer)) != -1;)
					fos.write(buffer, 0, read);
				if (DEBUG) System.out.println("Data read");
				fos.flush();
				fos.close();
				
				if (DEBUG) System.out.println("Done");
				output.write(UPDATE_DATA);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	delete data
		ca = new ComponentActionNetwork() {
			private static final boolean DEBUG = false;
			public String getActionCommand() {
				return DELETE_DATA;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				
				//	read data name
				String dataName = input.readLine();
				if (DEBUG) System.out.println("Data name is " + dataName);
				
				//	create and check data file
				File dataFile = new File(dataPath, dataName);
				if (dataFile.exists() && dataFile.isFile()) {
					if (DEBUG) System.out.println("File is " + dataFile.getAbsolutePath());
					
					dataFile.renameTo(new File(dataFile.getAbsolutePath() + "." + System.currentTimeMillis() + ".old"));
					if (DEBUG) System.out.println("File deleted");
					
					//	indicate configuration coming
					output.write(DELETE_DATA);
					output.newLine();
				}
				
				//	indicate failure
				else {
					output.write("Data not found, or it's a directory");
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		
		//	get configuration stubs
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_CONFIGURATION_DESCRIPTORS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication (bypassing UAA for descriptor requests from config servlet)
				String sessionId = input.readLine();
				if (CONFIG_SERVLET_SESSION_ID.equals(sessionId)) {}
				else if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				
				//	get configurations and create stubs
				Configuration[] configs = ((Configuration[]) configurationsByName.values().toArray(new Configuration[configurationsByName.size()]));
				for (int c = 0; c < configs.length; c++)
					configs[c] = new Configuration(configs[c].name, configs[c]);
				
				//	indicate configurations coming
				output.write(GET_CONFIGURATION_DESCRIPTORS);
				output.newLine();
				
				//	send configurations
				for (int c = 0; c < configs.length; c++) {
					if (CONFIG_SERVLET_SESSION_ID.equals(sessionId) && !onlineConfigurations.contains(configs[c].name))
						continue;
					configs[c].writeXml(output);
					output.newLine(); // additional newline for separation
				}
			}
		};
		cal.add(ca);
		
		//	get configuration stubs
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_CONFIGURATION_DESCRIPTOR;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication (bypassing UAA for descriptor requests from config servlet)
				String sessionId = input.readLine();
				if (CONFIG_SERVLET_SESSION_ID.equals(sessionId)) {}
				else if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				
				//	get configuration name
				String configName = input.readLine();
				if ("".equals(configName)) configName = null;
				System.out.println("Config name is '" + configName + "'");
				
				if (CONFIG_SERVLET_SESSION_ID.equals(sessionId) && !onlineConfigurations.contains(configName)) {
					output.write("Configuration '" + configName + "' is not an online configuration");
					output.newLine();
					return;
				}
				
				//	get configuration
				Configuration config = ((configName == null) ? masterConfiguration : getConfiguration(configName));
				System.out.println("  config fetched");
				
				//	indicate missing configuration
				if (config == null) {
					System.out.println("  config not found");
					output.write("Configuration not found.");
					output.newLine();
				}
				
				else {
					System.out.println("  config found");
					
					//	indicate configuration coming
					output.write(GET_CONFIGURATION_DESCRIPTOR);
					output.newLine();
					
					System.out.println("  sending descriptor");
					
					//	send configuration
					config.writeXml(output);
					output.newLine();
					System.out.println("  descriptor sent");
				}
			}
		};
		cal.add(ca);
		
		//	update configuration
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return UPDATE_CONFIGURATION;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				System.out.println("Updating configuration ...");
				
				//	get configuration name
				String configName = input.readLine();
				System.out.println("  config name is " + configName);
				Configuration config = getConfiguration(configName);
				if ((config != null) && (config.name.equals(config.basePath))) {
					output.write("Cannot update materialized configuration.");
					output.newLine();
					return;
				}
				
				//	get base configuration
				String baseConfigName = input.readLine();
				System.out.println("  base config name is " + baseConfigName);
				Configuration baseConfig = getConfiguration(baseConfigName);
				if (baseConfig == null) {
					output.write("The specified base configuration does not exist.");
					output.newLine();
					return;
				}
				
				//	read plugins and resources
				HashSet pluginNameSet = new HashSet();
				HashSet resourceNameSet = new HashSet();
				String pluginOrResourceName;
				while (((pluginOrResourceName = input.readLine()) != null) && (pluginOrResourceName.length() != 0)) {
					if (pluginOrResourceName.startsWith("P:"))
						pluginNameSet.add(pluginOrResourceName.substring(2));
					else if (pluginOrResourceName.startsWith("R:"))
						resourceNameSet.add(pluginOrResourceName.substring(2));
				}
				System.out.println("  got resources and plugins");
				
				//	create or update projected configuration
				updateConfiguration(configName, baseConfig, pluginNameSet, resourceNameSet);
				System.out.println("  update done");
				
				//	indicate success
				output.write(UPDATE_CONFIGURATION);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	delete configuration
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return DELETE_CONFIGURATION;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				
				//	get configuration name
				String configName = input.readLine();
				Configuration config = getConfiguration(configName);
				if (config == null) {
					output.write("The specified configuration does not exist.");
					output.newLine();
					return;
				}
				else if (config.name.equals(config.basePath)) {
					output.write("Cannot delete materialized configuration.");
					output.newLine();
					return;
				}
				else {
					deleteConfiguration(configName);
					
					//	indicate success
					output.write(DELETE_CONFIGURATION);
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	upload a new configuration
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return UPLOAD_CONFIGURATION;
			}
			public void performActionNetwork(final BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				System.out.println("Receiving configuration ...");
				
				//	remember receive time
				long receiveTime = System.currentTimeMillis();
				
				//	read configuration descriptor (up to the next blank line)
				Configuration config = Configuration.readConfiguration(new Reader() {
					StringReader sr = null;
					public void close() throws IOException {
						input.close();
					}
					public int read(char[] cbuf, int off, int len) throws IOException {
						if (this.sr == null) {
							String line = input.readLine();
							if ((line != null) && (line.length() != 0))
								this.sr = new StringReader(line + "\n");
							else return -1;
						}
						int read = this.sr.read(cbuf, off, len);
						if (read == -1) {
							this.sr = null;
							return this.read(cbuf, off, len);
						}
						else return read;
					}
				});
				
				//	get last update time of local files
				long configAge = uploadConfiguration(config, receiveTime);
				
				//	get file list
				StringVector files = ConfigurationUtils.listFilesRelative(new File(dataPath, config.name));
				
				//	indicate success
				output.write(UPLOAD_CONFIGURATION);
				output.newLine();
				
				//	send timestamps
				output.write("" + configAge);
				output.newLine();
				
				//	send file list
				for (int f = 0; f < files.size(); f++) {
					output.write(files.get(f));
					output.newLine();
				}
				output.newLine();
			}
		};
		cal.add(ca);
		
		
		//	get default configurations for users
		ca = new ListAction(GET_USER_CONFIGURATIONS) {
			String[] getList() throws IOException {
				String[] userNames = uaa.getUserNames();
				for (int u = 0; u < userNames.length; u++) {
					String configName = ((String) userDefaultConfigurations.get(userNames[u]));
					userNames[u] = (userNames[u] + ":" + ((configName == null) ? ("<" + masterConfiguration.name + ">") : configName));
				}
				return userNames;
			}
		};
		cal.add(ca);
		
		//	set default configurations for users
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return SET_USER_CONFIGURATIONS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				
				//	create backup
				HashMap backup = new HashMap();
				backup.putAll(userDefaultConfigurations);
				
				//	process data
				try {
					userDefaultConfigurations.clear();
					String userConfig;
					while (((userConfig = input.readLine()) != null) && (userConfig.length() != 0)) {
						String[] userConfigParts = userConfig.split("\\:");
						if ((userConfigParts.length == 2) && configurationsByName.containsKey(userConfigParts[1]))
							userDefaultConfigurations.put(userConfigParts[0], userConfigParts[1]);
					}
					saveUserConfigurations();
					
					//	indicate success
					output.write(SET_USER_CONFIGURATIONS);
					output.newLine();
				}
				catch (Exception e) {
					
					//	restore data
					userDefaultConfigurations.clear();
					userDefaultConfigurations.putAll(backup);
					
					//	indicate failure
					output.write("Could not update user default configurations: " + e.getMessage());
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	get online configurations
		ca = new ListAction(GET_ONLINE_CONFIGURATIONS) {
			String[] getList() throws IOException {
				return ((String[]) onlineConfigurations.toArray(new String[onlineConfigurations.size()]));
			}
		};
		cal.add(ca);
		
		//	set online configurations
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return SET_ONLINE_CONFIGURATIONS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				
				//	process data
				try {
					onlineConfigurations.clear();
					String onlineConfig;
					while (((onlineConfig = input.readLine()) != null) && (onlineConfig.length() != 0))
						onlineConfigurations.add(onlineConfig);
					saveOnlineConfigurations();
					
					//	indicate success
					output.write(SET_ONLINE_CONFIGURATIONS);
					output.newLine();
				}
				catch (Exception e) {
					
					//	indicate failure
					output.write("Could not update online configurations: " + e.getMessage());
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		
		ca = new ListAction(GET_GROUPS) {
			String[] getList() throws IOException {
				return ((String[]) groupsByName.keySet().toArray(new String[groupsByName.size()]));
			}
		};
		cal.add(ca);
		
		ca = new ListAction(GET_PLUGINS) {
			String[] getList() throws IOException {
				return getGroupPlugins(null);
			}
		};
		cal.add(ca);
		
		ca = new ListAction(GET_RESOURCES) {
			String[] getList() throws IOException {
				return getGroupResources(null);
			}
		};
		cal.add(ca);
		
		
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return CREATE_GROUP;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				
				//	get group name
				String groupName = input.readLine();
				
				//	create group
				String error = createGroup(groupName);
				
				//	send response
				output.write((error == null) ? CREATE_GROUP : error);
				output.newLine();
			}
		};
		cal.add(ca);
		
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return DELETE_GROUP;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				
				//	get group name
				String groupName = input.readLine();
				
				//	delete group
				String error = deleteGroup(groupName);
				
				//	send response
				output.write((error == null) ? DELETE_GROUP : error);
				output.newLine();
			}
		};
		cal.add(ca);
		
		
		ca = new GetGroupsOrPluginsOrResourcesAction(GET_GROUP_PLUGINS) {
			String[] getGroupsOrPluginsOrResources(String groupName) throws IOException {
				return getGroupPlugins(groupName);
			}
		};
		cal.add(ca);
		
		ca = new ModifyGroupsOrPluginsOrResourcesAction(SET_GROUP_PLUGINS) {
			String performAction(String groupName, String[] groupsOrPluginsOrResources) {
				return setGroupPlugins(groupName, groupsOrPluginsOrResources);
			}
		};
		cal.add(ca);
		
		ca = new GetGroupsOrPluginsOrResourcesAction(GET_GROUP_RESOURCES) {
			String[] getGroupsOrPluginsOrResources(String groupName) throws IOException {
				return getGroupResources(groupName);
			}
		};
		cal.add(ca);
		
		ca = new ModifyGroupsOrPluginsOrResourcesAction(SET_GROUP_RESOURCES) {
			String performAction(String groupName, String[] groupsOrPluginsOrResources) {
				return setGroupResources(groupName, groupsOrPluginsOrResources);
			}
		};
		cal.add(ca);
		
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	private abstract class ListAction implements ComponentActionNetwork {
		private String actionCommand;
		ListAction(String actionCommand) {
			this.actionCommand = actionCommand;
		}
		public String getActionCommand() {
			return this.actionCommand;
		}
		public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
			
			//	check authentication
			String sessionId = input.readLine();
			if (!uaa.isValidSession(sessionId)) {
				output.write("Invalid session (" + sessionId + ")");
				output.newLine();
				return;
			}
			else if (!uaa.isAdminSession(sessionId)) {
				output.write("Administrative priviledges required");
				output.newLine();
				return;
			}
			
			//	get list
			String[] list;
			try {
				list = getList();
			}
			catch (IOException ioe) {
				output.write(ioe.getMessage());
				output.newLine();
				return;
			}
			
			//	indicate success
			output.write(this.actionCommand);
			output.newLine();
			
			//	send list
			for (int l = 0; l < list.length; l++) {
				output.write(list[l]);
				output.newLine();
			}
		}
		abstract String[] getList() throws IOException;
	}
	
	private abstract class GetGroupsOrPluginsOrResourcesAction implements ComponentActionNetwork {
		private String actionCommand;
		GetGroupsOrPluginsOrResourcesAction(String actionCommand) {
			this.actionCommand = actionCommand;
		}
		public String getActionCommand() {
			return this.actionCommand;
		}
		public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
			
			//	check authentication
			String sessionId = input.readLine();
			if (!uaa.isValidSession(sessionId)) {
				output.write("Invalid session (" + sessionId + ")");
				output.newLine();
				return;
			}
			else if (!uaa.isAdminSession(sessionId)) {
				output.write("Administrative priviledges required");
				output.newLine();
				return;
			}
			
			//	get action target
			String groupName = input.readLine();
			
			//	get data
			String[] groupsOrPluginsOrResources;
			try {
				groupsOrPluginsOrResources = getGroupsOrPluginsOrResources(groupName);
			}
			catch (IOException ioe) {
				output.write(ioe.getMessage());
				output.newLine();
				return;
			}
			
			//	indicate success
			output.write(this.actionCommand);
			output.newLine();
			
			//	send data
			for (int gpr = 0; gpr < groupsOrPluginsOrResources.length; gpr++) {
				output.write(groupsOrPluginsOrResources[gpr]);
				output.newLine();
			}
		}
		abstract String[] getGroupsOrPluginsOrResources(String groupName) throws IOException;
	}
	
	private abstract class ModifyGroupsOrPluginsOrResourcesAction implements ComponentActionNetwork {
		private String actionCommand;
		ModifyGroupsOrPluginsOrResourcesAction(String actionCommand) {
			this.actionCommand = actionCommand;
		}
		public String getActionCommand() {
			return this.actionCommand;
		}
		public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
			
			//	check authentication
			String sessionId = input.readLine();
			if (!uaa.isValidSession(sessionId)) {
				output.write("Invalid session (" + sessionId + ")");
				output.newLine();
				return;
			}
			else if (!uaa.isAdminSession(sessionId)) {
				output.write("Administrative priviledges required");
				output.newLine();
				return;
			}
			
			//	get action target
			String groupName = input.readLine();
			
			//	get groups or resources
			StringVector groupsOrPluginsOrResources = new StringVector();
			String groupOrPluginOrResource;
			while (((groupOrPluginOrResource = input.readLine()) != null) && (groupOrPluginOrResource.length() != 0))
				groupsOrPluginsOrResources.addElementIgnoreDuplicates(groupOrPluginOrResource);
			
			//	perform actual action
			String error = this.performAction(groupName, groupsOrPluginsOrResources.toStringArray());
			
			//	indicate success
			if (error == null) {
				output.write(this.actionCommand);
				output.newLine();
			}
			
			//	indicate error
			else {
				output.write(error);
				output.newLine();
			}
		}
		abstract String performAction(String levelOrGroupName, String[] groupsOrPluginsOrResources);
	}
	
	private String[] getConfigurationNames(String userName) {
		
		//	get all configuration names
		TreeSet userConfigNames = new TreeSet();
		
		//	collect config names user has permission to see
		for (Iterator cit = this.configurationsByName.keySet().iterator(); cit.hasNext();) {
			String configName = ((String) cit.next());
			if (this.uaa.hasPermission(userName, (CONFIGURATION_PERMISSION_PREFIX + configName), true))
				userConfigNames.add(configName);
		}
		
		//	add default configuration
		String userDefaultConfig = ((String) this.userDefaultConfigurations.get(userName));
		if (userDefaultConfig != null) userConfigNames.add(userDefaultConfig);
		
		//	return config names
		return ((String[]) userConfigNames.toArray(new String[userConfigNames.size()]));
	}
	
	/*
	 * This method facilitates using different master configurations as the
	 * basis for permission and level based filtering, all adding an additional
	 * filter to the actual master configuration. This will facilitate, for
	 * instance, omitting memory intensive plugins in applet environments. And
	 * it will facilitate changing components very quickly and clandestinely for
	 * user experiments ...
	 */
	private Configuration getUserConfiguration(String userName) {
		String configName = ((String) this.userDefaultConfigurations.get(userName));
		if (configName == null) return this.masterConfiguration;
		else {
			Configuration config = this.getConfiguration(configName);
			return ((config == null) ? this.masterConfiguration : config);
		}
	}
	
	private Configuration projectConfiguration(String userName, Set docResGroups, Configuration model) {
		
		//	collect required plugins and resources
		HashSet pluginNameSet = new HashSet();
		HashSet resourceNameSet = new HashSet();
		
		//	request for full configuration, copy data from model
		if (docResGroups == null) {
			pluginNameSet.addAll(model.pluginsByName.keySet());
			resourceNameSet.addAll(model.resourcesByName.keySet());
		}
		
		//	request for document specific configuration
		else {
			
			//	read required groups
			for (Iterator git = docResGroups.iterator(); git.hasNext();) {
				Group group = this.getGroup((String) git.next());
				if (group != null) {
					pluginNameSet.addAll(group.plugins);
					for (Iterator rit = group.resources.iterator(); rit.hasNext();) {
						String resourceName = ((String) rit.next());
						String plainResourceName = ((String) this.resourceNameMappings.get(resourceName));
						if (plainResourceName != null) resourceName = plainResourceName;
						resourceNameSet.add(resourceName);
					}
				}
			}
		}
		
		//	check global permission for configuration
		if (this.uaa.hasPermission(userName, (CONFIGURATION_PERMISSION_PREFIX + model.name + CONFIGURATION_ALL_PERMISSION_SUFFIX)))
			return this.projectConfiguration(userName, model, pluginNameSet, resourceNameSet);
		
		//	check user permissions on group level
		HashSet permissionSet = new HashSet();
		for (Iterator git = this.groupsByName.keySet().iterator(); git.hasNext();) {
			String groupName = ((String) git.next());
			if (this.uaa.hasPermission(userName, (GROUP_PERMISSION_PREFIX + groupName), true)) {
				Group group = this.getGroup(groupName);
				if (group != null) {
					permissionSet.addAll(group.plugins);
					for (Iterator rit = group.resources.iterator(); rit.hasNext();) {
						String resourceName = ((String) rit.next());
						String plainResourceName = ((String) this.resourceNameMappings.get(resourceName));
						if (plainResourceName != null)
							resourceName = plainResourceName;
						permissionSet.add(resourceName);
					}
				}
			}
		}
		
		//	apply permissions
		pluginNameSet.retainAll(permissionSet);
		resourceNameSet.retainAll(permissionSet);
		
		//	project and return configuration
		return this.projectConfiguration(userName, model, pluginNameSet, resourceNameSet);
	}
	
	private Configuration projectConfiguration(String configName, Configuration model, Set pluginNameSet, Set resourceNameSet) {
		
		//	assemble configuration
		Configuration config = new Configuration(configName, model);
		config.dataItems.addAll(model.dataItems);
		
		//	apply permissions
		String[] pluginNames = ((String[]) pluginNameSet.toArray(new String[pluginNameSet.size()]));
		for (int p = 0; p < pluginNames.length; p++) {
			Plugin plugin = ((Plugin) model.pluginsByName.get(pluginNames[p]));
			if (plugin != null)
				config.addPlugin(plugin);
		}
		
		String[] resourceNames = ((String[]) resourceNameSet.toArray(new String[resourceNameSet.size()]));
		for (int r = 0; r < resourceNames.length; r++) {
			Resource resource = ((Resource) model.resourcesByName.get(resourceNames[r]));
			if (resource != null)
				config.addResource(resource);
		}
		
		//	collect required resources recursively
		int resourceCount;
		do {
			Resource[] resources = ((Resource[]) config.resources.toArray(new Resource[config.resources.size()]));
			resourceCount = resources.length;
			for (int r = 0; r < resources.length; r++)
				for (Iterator rrit = resources[r].requiredResourceNames.iterator(); rrit.hasNext();) {
					Resource reqRes = ((Resource) model.resourcesByName.get(rrit.next()));
					if (reqRes != null)
						config.addResource(reqRes);
				}
		} while (resourceCount < config.resources.size());
		
		//	collect required plugins, and resources to plugins
		Resource[] resources = ((Resource[]) config.resources.toArray(new Resource[config.resources.size()]));
		for (int r = 0; r < resources.length; r++) {
			Plugin managerPlugin = ((Plugin) model.pluginsByClassName.get(resources[r].managerClassName));
			if (managerPlugin != null) {
				config.addPlugin(managerPlugin);
				managerPlugin.resources.add(resources[r]);
			}
		}
		
		//	collect plugins required by other plugins
		int pluginCount;
		do {
			Plugin[] plugins = ((Plugin[]) config.plugins.toArray(new Plugin[config.plugins.size()]));
			pluginCount = plugins.length;
			for (int p = 0; p < plugins.length; p++)
				for (Iterator rpit = plugins[p].requiredPluginClassNames.iterator(); rpit.hasNext();) {
					Plugin reqPlugin = ((Plugin) model.pluginsByClassName.get(rpit.next()));
					if (reqPlugin != null)
						config.addPlugin(reqPlugin);
				}
		} while (pluginCount < config.plugins.size());
		
		//	add data items
		for (Iterator dit = model.dataItems.iterator(); dit.hasNext();)
			config.addDataItem((DataItem) dit.next());
		
		//	return configuration
		return config;
	}
	
	private TreeMap userDefaultConfigurations = new TreeMap();
	private void saveUserConfigurations() throws IOException {
		File ucFile = new File(this.dataPath, "UserConfigurations.txt");
		this.invalidate(ucFile);
		ucFile.createNewFile();
		
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ucFile)));
		
		for (Iterator uit = this.userDefaultConfigurations.keySet().iterator(); uit.hasNext();) {
			String userName = ((String) uit.next());
			String userConfigName = ((String) this.userDefaultConfigurations.get(userName));
			if (userConfigName != null) {
				bw.write(userName + ":" + userConfigName);
				bw.newLine();
			}
		}
		
		bw.flush();
		bw.close();
	}
	
	private TreeSet onlineConfigurations = new TreeSet();
	private void saveOnlineConfigurations() throws IOException {
		File ucFile = new File(this.dataPath, "OnlineConfigurations.txt");
		this.invalidate(ucFile);
		ucFile.createNewFile();
		
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ucFile)));
		
		for (Iterator uit = this.onlineConfigurations.iterator(); uit.hasNext();) {
			String configName = ((String) uit.next());
			bw.write(configName);
			bw.newLine();
		}
		
		bw.flush();
		bw.close();
	}
	
	private TreeMap configurationsByName = new TreeMap();
	private Configuration masterConfiguration;
	
	private TreeMap groupsByName = new TreeMap();
	
	private class Group implements Comparable {
		final String name;
		final TreeSet plugins = new TreeSet();
		final TreeSet resources = new TreeSet();
		Group(String name) {
			this.name = name;
			groupsByName.put(this.name, this);
		}
		public boolean equals(Object obj) {
			return ((obj instanceof Group) && this.name.equals(((Group) obj).name));
		}
		public int hashCode() {
			return this.name.hashCode();
		}
		public int compareTo(Object obj) {
			return ((obj instanceof Group) ? this.name.compareTo(((Group) obj).name) : -1);
		}
	}
	
	/**
	 * Retrieve a configuration for creating a GoldenGATE Editor instance.
	 * @param configName the name of the configuration
	 * @return the GoldenGATE configuration with the specified name, or null, if
	 *         there is no such configuration
	 */
	public GoldenGateConfiguration getGgConfiguration(String configName) {
		Configuration config = this.getConfiguration(configName);
		if (config == null) return null;
		else return new EcsLocalConfiguration(config, new File(this.dataPath, config.basePath));
	}
	
	/**
	 * Create a group to be administered by the ECS. This will also register a
	 * permission for the group with UPS, names 'ECS.Group.&lt;groupName&gt;',
	 * where &lt;groupName&gt; is the argument group name.
	 * @param groupName the group to create
	 * @return an error message, or null if the group was created successfully
	 */
	public String createGroup(String groupName) {
		if (groupName == null) return "Group names must not be null.";
		else if (this.groupsByName.containsKey(groupName))
			return ("Group '" + groupName + "' already exists.");
		else {
			Group group = this.getGroup(groupName);
			try {
				this.saveGroup(group);
				String permissionName = (GROUP_PERMISSION_PREFIX + groupName);
				this.uaa.registerPermission(permissionName);
				return null;
			}
			catch (IOException ioe) {
				return ioe.getMessage();
			}
		}
	}
	
	/**
	 * Delete a group administered by the ECS.
	 * @param groupName the group to delete
	 * @return an error message, or null if the group was deleted successfully
	 */
	public String deleteGroup(String groupName) {
		if (groupName == null) return "Group names must not be null.";
		else if (!this.groupsByName.containsKey(groupName))
			return ("Group '" + groupName + "' does not exists.");
		else {
			File groupFile = new File(this.dataPath, (GROUP_FILE_PREFIX + groupName + TXT_FILE_SUFFIX));
			this.invalidate(groupFile);
			this.groupsByName.remove(groupName);
			return null;
		}
	}
	
	/**
	 * Retrieve the plugins explicitly granted to a specific group, excluding
	 * the ones obtained from other groups. If group name is null, returns all
	 * plugins available.
	 * @param groupName the group to retrieve the plugins for
	 * @return an array holding the plugins explicitly granted to the
	 *         specified group
	 */
	public String[] getGroupPlugins(String groupName) {
		TreeSet plugins = new TreeSet();
		if (groupName == null)
			plugins.addAll(this.masterConfiguration.pluginsByName.keySet());
		
		else {
			Group group = this.getGroup(groupName);
			if (group != null)
				plugins.addAll(group.plugins);
		}
		return ((String[]) plugins.toArray(new String[plugins.size()]));
	}
	
	/**
	 * Set the plugins of a group. This operation will not change plugins
	 * obtained through other groups.
	 * @param groupName the group to set the plugins for
	 * @param plugins the plugins the specified group shall have from now on
	 * @return an error message, or null if the plugins were set successfully
	 */
	public String setGroupPlugins(String groupName, String[] plugins) {
		Group group = this.getGroup(groupName);
		if (group == null) return ("Group '" + groupName + "' does not exist");
		else try {
			if ((group.plugins.size() != plugins.length) || !group.plugins.containsAll(Arrays.asList(plugins))) {
				group.plugins.clear();
				group.plugins.addAll(Arrays.asList(plugins));
				this.saveGroup(group);
			}
			return null;
		}
		catch (IOException ioe) {
			return ioe.getMessage();
		}
	}
	
	/**
	 * Retrieve the resources explicitly granted to a specific group, excluding
	 * the ones obtained from other groups. If group name is null, returns all
	 * resources available.
	 * @param groupName the group to retrieve the resources for
	 * @return an array holding the resources explicitly granted to the
	 *         specified group
	 */
	public String[] getGroupResources(String groupName) {
		TreeSet resources = new TreeSet();
		if (groupName == null)
			resources.addAll(this.resourceNameMappings.keySet());
		else {
			Group group = this.getGroup(groupName);
			if (group != null)
				resources.addAll(group.resources);
		}
		return ((String[]) resources.toArray(new String[resources.size()]));
	}
	
	/**
	 * Set the resources of a group. This operation will not change resources
	 * obtained through other groups.
	 * @param groupName the group to set the resources for
	 * @param resources the resources the specified group shall have from now on
	 * @return an error message, or null if the resources were set successfully
	 */
	public String setGroupResources(String groupName, String[] resources) {
		Group group = this.getGroup(groupName);
		if (group == null) return ("Group '" + groupName + "' does not exist");
		else try {
			if ((group.resources.size() != resources.length) || !group.resources.containsAll(Arrays.asList(resources))) {
				group.resources.clear();
				group.resources.addAll(Arrays.asList(resources));
				this.saveGroup(group);
			}
			return null;
		}
		catch (IOException ioe) {
			return ioe.getMessage();
		}
	}
	
//	public static void main(String[] args) throws Exception {
//		File basePath = new File("E:/GoldenGATEv2.Server/Components/");
//		File cBasePath;
//		
//		UserAccessAuthority uaa = new UserAccessAuthority();
//		cBasePath = new File(basePath, "GgServerUAAData");
//		cBasePath.mkdirs();
//		uaa.setDataPath(cBasePath);
//		GoldenGateServerComponentRegistry.registerServerComponent(uaa);
//		
//		GoldenGateUPS ups = new GoldenGateUPS();
//		cBasePath = new File(basePath, "GgServerUPSData");
//		cBasePath.mkdirs();
//		ups.setDataPath(cBasePath);
//		GoldenGateServerComponentRegistry.registerServerComponent(ups);
//		
//		GoldenGateECS ecs = new GoldenGateECS();
//		cBasePath = new File(basePath, "GgServerECSData");
//		cBasePath.mkdirs();
//		ecs.setDataPath(cBasePath);
//		GoldenGateServerComponentRegistry.registerServerComponent(ecs);
//		
//		GoldenGateDIO dio = new GoldenGateDIO();
//		cBasePath = new File(basePath, "GgServerDIOData");
//		cBasePath.mkdirs();
//		dio.setDataPath(cBasePath);
//		GoldenGateServerComponentRegistry.registerServerComponent(dio);
//		
//		uaa.init();
//		System.out.println("UAA initialized");
//		ups.init();
//		System.out.println("UPS initialized");
//		ecs.init();
//		System.out.println("ECS initialized");
//		dio.init();
//		System.out.println("DIO initialized");
//		
////		System.out.println("Initialized, got levels:");
////		int levelCount = ((GoldenGateCMS) ecs.cms).getLevelCount();
////		for (int l = 0; l < levelCount; l++)
////			ecs.getLevel(l);
////		String[] levels = ((String[]) ecs.levelsByName.keySet().toArray(new String[ecs.levelsByName.size()]));
////		for (int l = 0; l < levels.length; l++)
////			System.out.println("  " + levels[l]);
//	}
}