package de.uka.ipd.idaho.goldenGateServer.acp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.uka.ipd.idaho.easyIO.streams.CharSequenceReader;
import de.uka.ipd.idaho.easyIO.streams.DataHashOutputStream;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.goldenGate.GoldenGateConfiguration;
import de.uka.ipd.idaho.goldenGate.GoldenGateConstants;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfigGroupDescriptor;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfigVersionDescriptor;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfiguration;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfigurationDataFile;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentRegistry;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthority;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineInputStream;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineOutputStream;

/**
 * GoldenGATE Application Configuration Provider stores GoldenGATE application
 * configurations and provides them to clients via a servlet based web API and
 * interface.
 * 
 * @author sautter
 */
public class GoldenGateACP extends AbstractGoldenGateServerComponent implements GoldenGateAcpConstants {
	private File configDataRoot;
	private Map appConfigVersionsByName = Collections.synchronizedMap(new TreeMap(String.CASE_INSENSITIVE_ORDER));
	private Map appConfigsByName = Collections.synchronizedMap(new TreeMap(String.CASE_INSENSITIVE_ORDER));
	
	private UserAccessAuthority uaa;
	
	/** usual zero-argument constructor for class loading */
	public GoldenGateACP() {
		super("ACP");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#initComponent()
	 */
	protected void initComponent() {
		
		//	make sure config data folder exists
		String configDataRoot = this.configuration.getSetting("configDataRoot");
		if (configDataRoot == null)
			this.configDataRoot = new File(this.dataPath, GoldenGateConstants.CONFIG_FOLDER_NAME);
		else if ((configDataRoot.indexOf(":/") != -1) || (configDataRoot.indexOf(":\\") != -1) || configDataRoot.startsWith("/"))
			this.configDataRoot = new File(configDataRoot);
		else this.configDataRoot = new File(this.dataPath, configDataRoot);
		this.configDataRoot.mkdirs();
		
		//	cache app configuration versions
		File[] appConfigFiles = this.configDataRoot.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return (file.isFile() && file.getName().endsWith(GoldenGateConfiguration.DESCRIPTOR_FILE_NAME_SUFFIX));
			}
		});
		ArrayList appConfigVersions = new ArrayList();
		for (int f = 0; f < appConfigFiles.length; f++) {
			String appConfigFileName = appConfigFiles[f].getName();
			appConfigFileName = appConfigFileName.substring(0, (appConfigFileName.length() - GoldenGateConfiguration.DESCRIPTOR_FILE_NAME_SUFFIX.length()));
			String appConfigName = appConfigFileName.substring(0, appConfigFileName.lastIndexOf("."));
			String appConfigVersionDate = appConfigFileName.substring(appConfigFileName.lastIndexOf(".") + ".".length());
			appConfigVersions.add(new AppConfigVersionDescriptor(appConfigName, appConfigVersionDate, null));
		}
		AppConfigGroupDescriptor[] appConfigs = AppConfigGroupDescriptor.groupAppConfigVersionDescriptors((AppConfigVersionDescriptor[]) appConfigVersions.toArray(new AppConfigVersionDescriptor[appConfigVersions.size()]));
		for (int c = 0; c < appConfigs.length; c++) {
			this.appConfigVersionsByName.put(appConfigs[c].name, appConfigs[c]);
			try {
				AppConfiguration appConfig = this.loadAppConfiguration(appConfigs[c].latestLocal);
				this.appConfigsByName.put(appConfigs[c].name, appConfig);
			}
			catch (IOException ioe) {
				System.out.println("GoldenGateACP: failed to load descriptor of application configuration '" + appConfigs[c].name + "': " + ioe.getMessage());
				ioe.printStackTrace(System.out);
			}
		}
		
		//	TODO MAYBE also cache available data names
	}
	
	AppConfiguration getAppConfiguration(String name) {
		return this.getAppConfiguration(name, null /* getting most recent */);
	}
	AppConfiguration getAppConfiguration(String name, String versionDate) {
		if (versionDate == null) // get most recent version, we got that cached
			return ((AppConfiguration) this.appConfigsByName.get(name));
		AppConfigGroupDescriptor acgd = ((AppConfigGroupDescriptor) this.appConfigVersionsByName.get(name));
		if (acgd == null)
			return null;
		if (acgd.latestLocal.versionDate.equals(versionDate)) // get most recent version, if with explicit date
			return ((AppConfiguration) this.appConfigsByName.get(name));
		for (int v = 0; v < acgd.versions.length; v++)
			if (acgd.versions[v].versionDate.equals(versionDate)) try {
				return this.loadAppConfiguration(acgd.versions[v]);
			}
			catch (IOException ioe) {
				this.logError("GoldenGateACP: failed to load descriptor of application configuration '" + acgd.versions[v].name + "' with version date '" + acgd.versions[v].versionDate + "': " + ioe.getMessage());
				this.logError(ioe);
			}
		return null;
	}
	
	AppConfiguration loadAppConfiguration(AppConfigVersionDescriptor acvd) throws IOException {
		File acdFile = new File(this.configDataRoot, acvd.getDescriptorFileName());
		BufferedReader acdBr = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(acdFile)), "UTF-8"));
		AppConfiguration appConfig = AppConfiguration.readDescriptor(acdBr);
		acdBr.close();
		return appConfig;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#link()
	 */
	public void link() {
		
		//	link to UAA
		this.uaa = ((UserAccessAuthority) GoldenGateServerComponentRegistry.getServerComponent(UserAccessAuthority.class.getName()));
		if (this.uaa == null)
			throw new RuntimeException(UserAccessAuthority.class.getName());
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#linkInit()
	 */
	public void linkInit() {
		
		//	register permissions to facilitate granting them to users
		this.uaa.registerPermission(UPDATE_APPLICATION_CONFIGURATION_PERMISSION);
		this.uaa.registerPermission(DELETE_APPLICATION_CONFIGURATION_PERMISSION);
		
		//	TODO consider dynamically adding permissions for individual configurations
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	send list of versions of all application configurations (GET_APPLICATION_CONFIGURATION_VERSIONS)
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_APPLICATION_CONFIGURATION_VERSIONS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	get groups local
				ArrayList appConfigs = new ArrayList(appConfigVersionsByName.values());
				
				//	indicate data coming
				output.write(GET_APPLICATION_CONFIGURATION_VERSIONS);
				output.newLine();
				
				//	send groups
				for (int c = 0; c < appConfigs.size(); c++) {
					AppConfigGroupDescriptor appConfig = ((AppConfigGroupDescriptor) appConfigs.get(c));
					for (int v = 0; v < appConfig.versions.length; v++) {
						output.write(appConfig.versions[v].toTsvString(false));
						output.newLine();
					}
					output.newLine(); // add blank line for grouping
				}
				output.flush();
			}
		};
		cal.add(ca);
		
		//	send configuration descriptor GET_APPLICATION_CONFIGURATION
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_APPLICATION_CONFIGURATION;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	get configuration name and version date
				String appConfigName = input.readLine();
				String appConfigVersionDate = input.readLine();
				if (appConfigVersionDate.length() == 0)
					appConfigVersionDate = null;
				
				//	get descriptor
				AppConfiguration appConfig = getAppConfiguration(appConfigName, appConfigVersionDate);
				if (appConfig == null) {
					if (appConfigVersionDate == null)
						output.write("Application configuration '" + appConfigName + "' does not exist");
					else output.write("Application configuration '" + appConfigName + "' does not exist with version date '" + appConfigVersionDate + "'");
					output.newLine();
					return;
				}
				
				//	indicate data coming
				output.write(GET_APPLICATION_CONFIGURATION);
				output.newLine();
				
				//	send descriptor
				appConfig.writeDescriptor(output);
				output.flush();
			}
		};
		cal.add(ca);
		
		//	send zipped stream of one or more data files, including timestamps (GET_APPLICATION_CONFIGURATION_DATA)
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_APPLICATION_CONFIGURATION_DATA;
			}
			public void performActionNetwork(BufferedLineInputStream input, BufferedLineOutputStream output) throws IOException {
				
				//	read list of data file names
				LinkedHashSet dataPathsAndNames = new LinkedHashSet();
				for (String dpan; (dpan = input.readLine()) != null;) {
					if (dpan.length() == 0)
						break;
					dataPathsAndNames.add(dpan);
				}
				
				//	indicate data coming
				output.write(GET_APPLICATION_CONFIGURATION_DATA);
				output.newLine();
				
				//	send zipped stream
				ZipOutputStream zip = new ZipOutputStream(output);
				for (Iterator dpanit = dataPathsAndNames.iterator(); dpanit.hasNext();) {
					String dpan = ((String) dpanit.next());
					File configDataFile = new File(configDataRoot, dpan);
					if (!configDataFile.exists())
						continue;
					ZipEntry ze = new ZipEntry(dpan);
					ze.setTime(configDataFile.lastModified());
					zip.putNextEntry(ze);
					BufferedInputStream cdfIn = new BufferedInputStream(new FileInputStream(configDataFile));
					byte[] buffer = new byte[1024];
					for (int r; (r = cdfIn.read(buffer, 0, buffer.length)) != -1;)
						zip.write(buffer, 0, r);
					cdfIn.close();
					zip.flush();
					zip.closeEntry();
				}
			}
		};
		cal.add(ca);
		
		//	receive updated descriptor of application configuration
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return UPDATE_APPLICATION_CONFIGURATION;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {

				// check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					logWarning("Request for invalid session - " + sessionId);
					return;
				}
				
				//	check permission
				if (!uaa.hasSessionPermission(sessionId, UPDATE_APPLICATION_CONFIGURATION_PERMISSION, true)) {
					output.write("Insufficient permissions for updating an application configuration");
					output.newLine();
					return;
				}
				
				//	get session user name
				String userName = uaa.getUserNameForSession(sessionId);
				
				//	read remote application configuration descriptor (need to buffer whole descriptor first, so reading it won't block at end)
//				AppConfiguration remoteAppConfig = AppConfiguration.readDescriptor(input);
				StringBuffer appConfigDesc = new StringBuffer();
				for (String acdLine; (acdLine = input.readLine()) != null;) {
					if (acdLine.length() == 0)
						break; // descriptor terminated by blank line
					appConfigDesc.append(acdLine);
					appConfigDesc.append("\r\n");
				}
				AppConfiguration remoteAppConfig = AppConfiguration.readDescriptor(new BufferedReader(new CharSequenceReader(appConfigDesc)));
				
				//	get local application configuration descriptor (latest version)
				AppConfiguration localAppConfig = getAppConfiguration(remoteAppConfig.name);
				
				//	check if we actually need the update (we always need the README even if all data files are present)
//				if ((localAppConfig != null) && localAppConfig.versionDate.equals(remoteAppConfig.versionDate)) {
				File appConfigDescriptorFile = new File(configDataRoot, remoteAppConfig.getDescriptorFileName());
				if (appConfigDescriptorFile.exists()) {
					
					//	send back empty update key to indicate protocol is coming right away
					output.write(UPDATE_APPLICATION_CONFIGURATION);
					output.newLine();
					output.write("");
					output.newLine();
					output.write("Application configuration '" + remoteAppConfig.name + "' already available with version date " + remoteAppConfig.versionDate);
					output.newLine();
					output.flush();
					
					//	we're done here
					return;
				}
				
				//	diff local data files with received entry list
				ArrayList toUpdateDataFiles = new ArrayList();
				HashMap toUploadDataFilesByName = new HashMap();
				AppConfigurationDataFile[] remoteDataFiles = remoteAppConfig.listDataFiles();
				for (int f = 0; f < remoteDataFiles.length; f++) {
					AppConfigurationDataFile localDataFile = ((localAppConfig == null) ? null : localAppConfig.getDataFile(remoteDataFiles[f].fullName));
					if (localDataFile == null) {}
					else if (localDataFile.size != remoteDataFiles[f].size)
						localDataFile = null;
					else if ((localDataFile.hash == null) && (remoteDataFiles[f].hash == null)) {}
					else if ((localDataFile.hash == null) || (remoteDataFiles[f].hash == null))
						localDataFile = null;
					else if (!remoteDataFiles[f].hash.equals(localDataFile.hash))
						localDataFile = null;
					if (localDataFile != null)
						continue; // we have this one from a previous version
					File configDataFile = new File(configDataRoot, remoteDataFiles[f].getDataFilePathAndName());
					if (configDataFile.exists())
						continue; // we already have this one from a different configuration
					toUpdateDataFiles.add(remoteDataFiles[f]);
					toUploadDataFilesByName.put(remoteDataFiles[f].fullName, remoteDataFiles[f]);
				}
				
				//	unless we already have the actual version (descriptor and all), we definitely need the REEDME
				File configReadmeFile = new File(configDataRoot, remoteAppConfig.readmeFile.getDataFilePathAndName());
				if (!configReadmeFile.exists()) {
					toUpdateDataFiles.add(remoteAppConfig.readmeFile);
					toUploadDataFilesByName.put(remoteAppConfig.readmeFile.fullName, remoteAppConfig.readmeFile);
				}
				
				//	we already have all the data files, including the README (update must have broken just before finalization)
				if (toUpdateDataFiles.isEmpty()) {
					
					//	finalize update right away
					finalizeAppConfigurationUpdate(new AppConfigurationUpdate(remoteAppConfig, toUploadDataFilesByName));
					
					//	send back empty update key to indicate protocol is coming right away
					output.write(UPDATE_APPLICATION_CONFIGURATION);
					output.newLine();
					output.write("");
					output.newLine();
					output.write("Application configuration '" + remoteAppConfig.name + "' stored with version date " + remoteAppConfig.versionDate);
					output.newLine();
					output.write("No data files transferred");
					output.newLine();
					output.flush();
					
					//	we're done here
					return;
				}
				
				//	generate update key and cache data
				String updateKey = Gamta.getAnnotationID();
				AppConfigurationUpdate update = new AppConfigurationUpdate(remoteAppConfig, toUploadDataFilesByName);
				updatesByKey.put(updateKey, update);
				
				//	send back entire entry list
				output.write(UPDATE_APPLICATION_CONFIGURATION);
				output.newLine();
				output.write(updateKey);
				output.newLine();
				for (int f = 0; f < toUpdateDataFiles.size(); f++) {
					output.write(((AppConfigurationDataFile) toUpdateDataFiles.get(f)).toTsvString());
					output.newLine();
				}
				output.write(""); // terminate list with blank line
				output.newLine();
				output.flush();
			}
		};
		cal.add(ca);
		
		//	receive data files of application configuration
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return UPDATE_APPLICATION_CONFIGURATION_DATA;
			}
			public void performActionNetwork(BufferedLineInputStream input, BufferedLineOutputStream output) throws IOException {
				
				// check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					logWarning("Request for invalid session - " + sessionId);
					return;
				}
				
				//	get update key and update data
				String updateKey = input.readLine();
				AppConfigurationUpdate acu = ((AppConfigurationUpdate) updatesByKey.remove(updateKey));
				if (acu == null) {
					output.write("Invalid update key (" + updateKey + ")");
					output.newLine();
					return;
				}
				acu.dataFileUpdateRounds++;
				
				//	receive requested entries
				ZipInputStream zin = new ZipInputStream(input);
				byte[] buffer = new byte[1024];
				boolean updateComplete = false;
				for (ZipEntry ze; (ze = zin.getNextEntry()) != null;) {
					if (updateKey.equals(ze.getName())) {
						updateComplete = true;
						break;
					}
					if (MORE_APPLICATION_CONFIGURATION_DATA.equals(ze.getName()))
						break;
					
					//	handle README separately (has no hash or size, and no record in TSV descriptor)
					if (acu.appConfig.readmeFile.fullName.equals(ze.getName())) {
						File configReadmeFile = new File(configDataRoot, acu.appConfig.readmeFile.getDataFilePathAndName());
						if (configReadmeFile.exists())
							continue;
						File configReadmeFileWriting = new File(configDataRoot, acu.appConfig.readmeFile.getDataFilePathAndName() + ".writing");
						configReadmeFileWriting.getParentFile().mkdirs();
						BufferedOutputStream configReadmeOut = new BufferedOutputStream(new FileOutputStream(configReadmeFileWriting));
						for (int r; (r = zin.read(buffer, 0, buffer.length)) != -1;)
							configReadmeOut.write(buffer, 0, r);
						configReadmeOut.flush();
						configReadmeOut.close();
						configReadmeFileWriting.setLastModified(acu.appConfig.readmeFile.lastMod);
						configReadmeFileWriting.renameTo(configReadmeFile);
						acu.missingDataFilesByName.remove(acu.appConfig.readmeFile.fullName);
						continue;
					}
					
					//	handle regular data file, verifying both size and hash
					AppConfigurationDataFile dataFile = ((AppConfigurationDataFile) acu.missingDataFilesByName.get(ze.getName()));
					if (dataFile == null)
						continue;
					ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
					DataHashOutputStream dataHasher = new DataHashOutputStream(dataBuffer);
					for (int r; (r = zin.read(buffer, 0, buffer.length)) != -1;)
						dataHasher.write(buffer, 0, r);
					dataHasher.flush();
					dataHasher.close();
					int dataSize = dataBuffer.size();
					if (dataFile.size != dataSize)
						throw new IOException("Expected " + dataFile.size + " bytes in '" + dataFile.fullName + "', but found " + dataSize);
					String dataHash = dataHasher.getDataHashString();
					if (!dataFile.hash.equals(dataHash))
						throw new IOException("Expected " + dataFile.hash + " as hash of '" + dataFile.fullName + "', but found " + dataHash);
					File configDataFile = new File(configDataRoot, dataFile.getDataFilePathAndName());
					if (configDataFile.exists())
						continue;
					File configDataFileWriting = new File(configDataRoot, dataFile.getDataFilePathAndName() + ".writing");
					configDataFileWriting.getParentFile().mkdirs();
					BufferedOutputStream configDataOut = new BufferedOutputStream(new FileOutputStream(configDataFileWriting));
					dataBuffer.writeTo(configDataOut);
					configDataOut.flush();
					configDataOut.close();
					configDataFileWriting.setLastModified(dataFile.lastMod);
					configDataFileWriting.renameTo(configDataFile);
					acu.missingDataFilesByName.remove(dataFile.fullName);
				}
				
				//	update complete, finalize it and send update log
				if (updateComplete) {
					finalizeAppConfigurationUpdate(acu);
					
					output.write(UPDATE_APPLICATION_CONFIGURATION_DATA);
					output.newLine();
					output.write("");
					output.newLine();
					output.write("Application configuration '" + acu.appConfig.name + "' stored with version date " + acu.appConfig.versionDate);
					output.newLine();
					output.write(acu.missingDataFileCount + " data files transferred in " + acu.dataFileUpdateRounds + " chunks");
					output.newLine();
					output.flush();
				}
				
				//	more to come, make update key valid again and acknowledge received part
				else {
					updatesByKey.put(updateKey, acu);
					
					output.write(UPDATE_APPLICATION_CONFIGURATION_DATA);
					output.newLine();
					output.write(updateKey);
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	delete an application configuration or a specific version of one
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return DELETE_APPLICATION_CONFIGURATION;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {

				// check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					logWarning("Request for invalid session - " + sessionId);
					return;
				}
				
				//	check permission
				if (!uaa.hasSessionPermission(sessionId, DELETE_APPLICATION_CONFIGURATION_PERMISSION, true)) {
					output.write("Insufficient permissions for deleting an application configuration");
					output.newLine();
					return;
				}
				
				//	get configuration name and version date
				String appConfigName = input.readLine();
				String appConfigVersionDate = input.readLine();
				if (appConfigVersionDate.length() == 0)
					appConfigVersionDate = null;
				
				//	perform deletion
				try {
					int deleted = deleteAppConfiguration(appConfigName, appConfigVersionDate);
					if (deleted < 0) {
						output.write("Application configuration '" + appConfigName + "' does not exist");
						output.newLine();
					}
					else if (deleted == 0) {
						output.write("Version date '" + appConfigVersionDate + "' does not exist for application configuration '" + appConfigName + "' does not exist");
						output.newLine();
					}
					else {
						output.write(DELETE_APPLICATION_CONFIGURATION);
						output.newLine();
						output.write(deleted + " versions of application configuration '" + appConfigName + "' deleted");
						output.newLine();
					}
				}
				catch (IOException ioe) {
					output.write(ioe.getMessage());
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	TODO maybe add console function cleaning up spurious data ???
		
		//	finally ...
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	private Map updatesByKey = Collections.synchronizedMap(new LinkedHashMap(16, 0.9f, false) {
		protected boolean removeEldestEntry(Entry eldest) {
			return (this.size() > 128); // should be OK for starters
		}
	});
	private static class AppConfigurationUpdate {
		final AppConfiguration appConfig;
		final Map missingDataFilesByName;
		final int missingDataFileCount;
		int dataFileUpdateRounds = 0;
//		final String userName;
//		AppConfigurationUpdate(AppConfiguration appConfig, Map missingDataFilesByName, String userName) {
		AppConfigurationUpdate(AppConfiguration appConfig, Map missingDataFilesByName) {
			this.appConfig = appConfig;
			this.missingDataFilesByName = missingDataFilesByName;
			this.missingDataFileCount = this.missingDataFilesByName.size();
//			this.userName = userName;
		}
	}
	private void finalizeAppConfigurationUpdate(AppConfigurationUpdate appConfigUpdate) throws IOException {
		
		//	do we have all we need?
		if (appConfigUpdate.missingDataFilesByName.size() != 0)
			throw new IOException("Missing entries: " + appConfigUpdate.missingDataFilesByName.keySet());
		
		//	store configuration descriptor
		File acdFileWriting = new File(this.configDataRoot, (appConfigUpdate.appConfig.getDescriptorFileName() + ".writing"));
		acdFileWriting.getParentFile().mkdirs();
		BufferedWriter acdBr = new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(acdFileWriting)), "UTF-8"));
		appConfigUpdate.appConfig.writeDescriptor(acdBr);
		acdBr.flush();
		acdBr.close();
		acdFileWriting.setLastModified(appConfigUpdate.appConfig.readmeFile.lastMod); // as close as it gets ...
		File acdFile = new File(this.configDataRoot, appConfigUpdate.appConfig.getDescriptorFileName());
		acdFileWriting.renameTo(acdFile);
		
		//	update local data structures and version list
		AppConfigVersionDescriptor acvd = new AppConfigVersionDescriptor(appConfigUpdate.appConfig.configName, appConfigUpdate.appConfig.versionDate, null);
		AppConfigGroupDescriptor acgd = ((AppConfigGroupDescriptor) this.appConfigVersionsByName.get(acvd.name));
		if (acgd == null) {
			AppConfigVersionDescriptor[] acvds = {acvd};
			acgd = new AppConfigGroupDescriptor(acvd.name, acvds);
		}
		else {
			AppConfigVersionDescriptor[] acvds = new AppConfigVersionDescriptor[acgd.versions.length + 1];
			acvds[0] = acvd; // add (presumably) latest version at start to speed up sorting
			System.arraycopy(acgd.versions, 0, acvds, 1, acgd.versions.length);
			Arrays.sort(acvds, AppConfigVersionDescriptor.nameOrder);
			acgd = new AppConfigGroupDescriptor(acgd.name, acvds);
		}
		this.appConfigVersionsByName.put(acgd.name, acgd);
		if (acvd == acgd.latestLocal)
			this.appConfigsByName.put(acgd.name, appConfigUpdate.appConfig);
	}
	
	int deleteAppConfiguration(String configName, String versionDate) throws IOException {
		
		//	get existing versions
		AppConfigGroupDescriptor acgd = ((AppConfigGroupDescriptor) this.appConfigVersionsByName.get(configName));
		if (acgd == null)
			return -1; // invalid name
		
		//	clean up descriptor and README files matching arguments
		long deleteTime = System.currentTimeMillis();
		int deleted = 0;
		boolean deletedLatest = false;
		for (int v = 0; v < acgd.versions.length; v++) {
			if ((versionDate != null) && !acgd.versions[v].versionDate.equals(versionDate)) {
				if (deleted != 0)
					acgd.versions[v - deleted] = acgd.versions[v];
				continue;
			}
			if (this.deleteAppConfigVersion(acgd.versions[v], deleteTime)) {
				if (acgd.versions[v] == acgd.latestLocal)
					deletedLatest = true;
				deleted++;
			}
			else if (deleted != 0)
				acgd.versions[v - deleted] = acgd.versions[v];
		}
		if (deleted == 0)
			return 0; // invalid version date
		
		//	cleaned up completely
		if (deleted == acgd.versions.length) {
			this.appConfigVersionsByName.remove(acgd.name);
			this.appConfigsByName.remove(acgd.name);
			return deleted;
		}
		
		//	replace version list
		AppConfigVersionDescriptor[] acvds = new AppConfigVersionDescriptor[acgd.versions.length - deleted];
		System.arraycopy(acgd.versions, 0, acvds, 0, acvds.length);
		acgd = new AppConfigGroupDescriptor(acgd.name, acvds);
		this.appConfigVersionsByName.put(acgd.name, acgd);
		
		//	cleaned up latest version, need to reload cached descriptor
		if (deletedLatest) {
			this.appConfigsByName.remove(acgd.name);
			AppConfiguration appConfig = this.loadAppConfiguration(acgd.latestLocal);
			this.appConfigsByName.put(acgd.name, appConfig);
		}
		
		//	finally ...
		return deleted;
	}
	private boolean deleteAppConfigVersion(AppConfigVersionDescriptor acvd, long time) throws IOException {
		File descriptorFile = new File(this.configDataRoot, acvd.getDescriptorFileName());
		if (descriptorFile.exists())
			descriptorFile.renameTo(new File(this.configDataRoot, (acvd.getDescriptorFileName() + "." + time + ".old")));
		else return false;
		File readmeFile = new File(this.configDataRoot, (acvd.name + "." + acvd.versionDate + "." + GoldenGateConstants.README_FILE_NAME));
		if (readmeFile.exists())
			readmeFile.renameTo(new File(this.configDataRoot, (acvd.name + "." + acvd.versionDate + "." + GoldenGateConstants.README_FILE_NAME + "." + time + ".old")));
		return true;
	}
	
	/*
TODO ALSO, replace ECS:
- dub whole thing GCS (GoldenGATE application Configuration Server)
  ==> maybe better dub ACP (Application Configuration Provider)
- update configurations via generic upload tool ...
- ... transferring delta just like IMF updates between IMS client and IMS proper
- make configurations top level data objects ...
- ... issuing update events, etc.
  ==> also provide replicator (GCR, or ACR). working just like IMR and upcoming XMR
  ==> maybe use events to trigger configuration updates in IMP, DPR, and upcoming XMP
    ==> most likely make that behavior configurable
- forget about ECS _client_, new system always goes local-first ...
- ... and use simple upload tool offering local configurations for upload
  ==> however, still provide uploader GG plug-in (more comfortable to use right after export)
- build all of above by copying and stripping down IMS and its client, as well as IMR ...
- ... also removing all the authentication hassle, as configurations are free
  ==> and then, maybe do keep pass phrase mechanism ...
  ==> ... and simply don't use it for now (someone else might be more possessive somewhere)
- on web frontend, list all latest as "<configName><tab><versionDate>" ...
- ... showing all available version dates if "showVersions" parameter set to "all" (defaulting to "latest")
- add respective list getter to GCS client
- use the latter for getting remote configurations ...
- ... requesting all versions if specific version date specified
	 */
	
}
