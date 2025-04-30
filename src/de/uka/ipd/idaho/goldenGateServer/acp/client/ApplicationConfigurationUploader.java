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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;
import de.uka.ipd.idaho.gamta.util.swing.ProgressMonitorDialog;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfigGroupDescriptor;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfigVersionDescriptor;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfiguration;
import de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin;
import de.uka.ipd.idaho.goldenGate.ui.GoldenGateUI;
import de.uka.ipd.idaho.goldenGate.ui.GoldenGateUI.DocumentDisplay;
import de.uka.ipd.idaho.goldenGate.ui.WindowMenuBar;
import de.uka.ipd.idaho.goldenGate.ui.WindowMenuElement;
import de.uka.ipd.idaho.goldenGate.ui.WindowMenuFunction;
import de.uka.ipd.idaho.goldenGate.util.DialogPanel;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticationManagerPlugin;

/**
 * Uploader plug-in from GoldenGATE Application Configuration Provider (ACP).
 * 
 * @author sautter
 */
public class ApplicationConfigurationUploader extends AbstractGoldenGatePlugin {
	private AuthenticationManagerPlugin authManager = null;
	private AuthenticatedClient authClient = null;
	
	private GoldenGateAcpClient acpClient;
	
	/** usual zero-argument constructor for class loading */
	public ApplicationConfigurationUploader() {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#requiresMasterConfiguration()
	 */
	public boolean requiresMasterConfiguration() {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getPluginName()
	 */
	public String getPluginName() {
		return "ACP Configuration Uploader";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getMainMenuTitle()
	 */
	public String getMainMenuTitle() {
		return "GoldenGATE ACP Configurations";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getRequiredPluginClasses()
	 */
	public Class[] getRequiredPluginClasses() {
		Class[] rpcs = { AuthenticationManagerPlugin.class };
		return rpcs;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#init()
	 */
	public void init() {
		
		//	get authentication manager
		this.authManager = ((AuthenticationManagerPlugin) this.parent.getPlugin(AuthenticationManagerPlugin.class.getName()));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getWindowMenuElements()
	 */
	public WindowMenuElement[] getWindowMenuElements() {
		int flags = 0;
		flags |= WindowMenuElement.PROPERTY_AVAILABLE_DESKTOP;
		flags |= WindowMenuElement.PROPERTY_REQUIRES_MAIN_WINDOW;
		flags |= WindowMenuElement.PROPERTY_REQUIRES_MASTER_DATA;
		flags |= WindowMenuElement.PROPERTY_REQUIRES_MASTER_MODE;
		flags |= WindowMenuElement.PROPERTY_RESOURCE_MANAGEMENT;
		flags = WindowMenuElement.encodePreferredMenuName(WindowMenuBar.EXPORT_MENU_NAME, flags);
		WindowMenuElement[] wmes = {
			new WindowMenuFunction(ApplicationConfigurationUploader.class.getName(), "main.uploadConfigs", "Upload Configurations to ACP", "Upload exported configurations to GoldenGATE Application Configuration Provider for others to use", flags) {
				public boolean checkAvailable(GoldenGateUI ggui, DocumentDisplay display) {
					return (ggui != null); // exporting configurations only needs to be available in main menu
				}
				public void execute(GoldenGateUI ggui, DocumentDisplay display) {
					uploadConfigurations();
				}
			}
		};
		return wmes;
	}
	
	private boolean ensureLoggedIn() {
		
		//	test if connection alive
		if (this.authClient != null) try {
			
			//	test if connection alive
			if (this.authClient.ensureLoggedIn())
				return true;
			
			//	connection dead (e.g. a session timeout), make way for re-getting from auth manager
			else {
				this.acpClient = null;
				this.authClient = null;
			}
		}
		
		//	server temporarily unreachable, re-login will be done by auth manager
		catch (IOException ioe) {
			this.acpClient = null;
			this.authClient = null;
			return false;
		}
		
		//	got no valid connection at the moment, try and get one
		if (this.authClient == null)
			this.authClient = this.authManager.getAuthenticatedClient();
		
		//	authentication failed
		if (this.authClient == null)
			return false;
		
		//	got valid connection, flush cache if we got one
		else {
			this.acpClient = new GoldenGateAcpClient(this.authClient);
			return true;
		}
	}
	
	void uploadConfigurations() {
		
		//	get root folder
		File rootFolder = this.getRootFolder();
		if (rootFolder == null)
			return;
		
		//	get list of user configurations from '_Configurations' folder
		File configSourceFolder = new File(rootFolder, ("_" + CONFIG_FOLDER_NAME));
		AppConfigVersionDescriptor[] localConfigVersions = ConfigurationUtils.listLocalConfigurations(configSourceFolder);
		if ((localConfigVersions == null) || (localConfigVersions.length == 0)) {
			DialogFactory.alert(("There are no locally exported configurations to upload to GoldenGATE Server."), "Local Configurations Not Found", JOptionPane.ERROR_MESSAGE);
			return;
		}
		AppConfigGroupDescriptor[] localConfigs = AppConfigGroupDescriptor.groupAppConfigVersionDescriptors(localConfigVersions);
		Arrays.sort(localConfigs, appConfigGroupOrder);
//		System.out.println("AcpConfigurationUploader: got " + localConfigs.length + " local configurations with total of " + localConfigVersions.length + " versions");
		
		//	check connection
		this.ensureLoggedIn();
		if (this.acpClient == null) {
			DialogFactory.alert(("Cannot upload configurations to GoldenGATE Server without authentication."), "Cannot Upload Configurations", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		//	get list of user configurations from ACP
		AppConfigVersionDescriptor[] acpConfigVersions;
		try {
			acpConfigVersions = this.acpClient.getAppConfigVersions();
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
			DialogFactory.alert(("An error occurred while fetching excisting configurations from GoldenGATE Server:\r\n  " + ioe.getMessage()), "Error Fetching Existing Configurations", JOptionPane.ERROR_MESSAGE);
			return;
		}
		AppConfigGroupDescriptor[] acpConfigs = AppConfigGroupDescriptor.groupAppConfigVersionDescriptors(acpConfigVersions);
		Arrays.sort(acpConfigs, appConfigGroupOrder);
//		System.out.println("AcpConfigurationUploader: got " + acpConfigs.length + " remote configurations with total of " + acpConfigVersions.length + " versions");
		
		//	merge configurations and create selectors, with ones requiring update being bold and pre-selected
		final ArrayList configSelectors = new ArrayList();
		for (int l = 0, a = 0; l < localConfigs.length; l++) {
			while ((a < acpConfigs.length) && (0 < appConfigGroupOrder.compare(localConfigs[l], acpConfigs[a])))
				a++; // find matching or closest successor config name from ACP
			if (a == acpConfigs.length)
				configSelectors.add(new AppConfigurationGroupUploadSelector(localConfigs[l], null));
			else if (localConfigs[l].name.equals(acpConfigs[a].name))
				configSelectors.add(new AppConfigurationGroupUploadSelector(localConfigs[l], acpConfigs[a++]));
			else configSelectors.add(new AppConfigurationGroupUploadSelector(localConfigs[l], null));
		}
		
		//	assemble selector panel and check if we have anything to update
		int updateConfigCount = 0;
		for (int s = 0; s < configSelectors.size(); s++) {
			AppConfigurationGroupUploadSelector configSelector = ((AppConfigurationGroupUploadSelector) configSelectors.get(s));
			if (configSelector.isSelected())
				updateConfigCount++;
		}
		if (updateConfigCount == 0) {
			DialogFactory.alert(("All local configurations are up to date on GoldenGATE Server."), "No Configurations To Upload", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		
		//	offer latest version of each for selection
		ConfigurationUploadDialog cud = new ConfigurationUploadDialog(configSourceFolder, ((AppConfigurationGroupUploadSelector[]) configSelectors.toArray(new AppConfigurationGroupUploadSelector[configSelectors.size()])));
		cud.setVisible(true);
	}
	
	int uploadConfigurations(final File configSourceFolder, final AppConfigGroupDescriptor[] configs) {
		
		//	open progress monitor
		final ProgressMonitorDialog pmd = new ProgressMonitorDialog(true, true, DialogFactory.getTopWindow(), ("Uploading " + configs.length + " Configurations"));
		pmd.setSize(400, 150);
		pmd.setLocationRelativeTo(DialogFactory.getTopWindow());
		pmd.setAbortExceptionMessage("ABORTED_BY_USER");
		
		//	upload in separate thread to support pausing and aborting
		final int[] uploaded = {0};
		Thread configUploader = new Thread("AcpConfigUploadThread") {
			public void run() {
				try {
					
					//	wait for progress monitor to pop up
					while (!pmd.isVisible()) try {
						Thread.sleep(10);
					} catch (InterruptedException ie) {}
					
					//	perform upload
					for (int s = 0; s < configs.length; s++) {
						
						//	adjust progress monitor
						pmd.setStep("Uploading configuration '" + configs[s].name);
						pmd.setBaseProgress((s * 100) / configs.length);
						pmd.setProgress(0);
						pmd.setMaxProgress(((s + 1) * 100) / configs.length);
						pmd.setPauseResumeEnabled(true); // need to re-enable this, as ACP disables it when done uploading and awaiting result
						pmd.setAbortEnabled(true); // need to re-enable this, as ACP disables it when done uploading and awaiting result
						
						//	perform upload
						try {
							uploadConfiguration(configSourceFolder, configs[s].latestLocal, pmd);
							uploaded[0]++;
						}
						catch (Exception e) {
							if ("ABORTED_BY_USER".equals(e.getMessage()))
								return;
							e.printStackTrace(System.out);
							int choice = DialogFactory.confirm(("An error occurred while uploading configuration '" + configs[s].name + "' to upload to GoldenGATE Server:\r\n  " + e.getMessage() + "\r\nContinue with uploads?"), "Error Uploading Configuration", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
							if (choice != JOptionPane.YES_OPTION)
								return;
						}
					}
				}
				finally {
					pmd.close();
				}
			}
		};
		configUploader.start();
		
		//	make progress monitor show (blocks until upload thread finished)
		pmd.popUp(true);
		return uploaded[0];
	}
	
	void uploadConfiguration(File configSourceFolder, AppConfigVersionDescriptor latestLocal, ProgressMonitor pm) throws Exception {
		
		//	load application configuration proper
		File localConfigFile = new File(configSourceFolder, latestLocal.getDescriptorFileName());
		BufferedReader localConfigBr = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(localConfigFile)), "UTF-8"));
		AppConfiguration localConfig = AppConfiguration.readDescriptor(localConfigBr);
		
		//	(delta) upload local configuration to aCP
		this.acpClient.updateAppConfiguration(localConfig, configSourceFolder, pm);
	}
	
	private File getRootFolder() {
		
		//	get root path
		File ggRoot;
		try {
			ggRoot = new File(".");
			
			//	check if we got the root path
			File ggJar = new File(ggRoot, "GoldenGATE.jar");
			if (ggJar.exists())
				return ggRoot;
			else {
				DialogFactory.alert("Cannot upload GoldenGATE configurations without GoldenGATE root folder.", "GoldenGATE Root Folder Not Found", JOptionPane.ERROR_MESSAGE);
				return null;
			}
		}
		
		//	we may not be allowed to access the file system ...
		catch (SecurityException se) {
			DialogFactory.alert("Cannot upload GoldenGATE configurations without access to GoldenGATE root folder.", "GoldenGATE Root Folder Not Accessible", JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}
	
	private static Comparator appConfigGroupOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			AppConfigGroupDescriptor acgd1 = ((AppConfigGroupDescriptor) obj1);
			AppConfigGroupDescriptor acgd2 = ((AppConfigGroupDescriptor) obj2);
			return (acgd1.name.compareTo(acgd2.name));
		}
	};
	
	private static class AppConfigurationGroupUploadSelector extends JCheckBox {
		AppConfigGroupDescriptor config;
		AppConfigurationGroupUploadSelector(AppConfigGroupDescriptor localConfig, AppConfigGroupDescriptor acpConfig) {
			this.config = localConfig;
			
			boolean offerUpload;
			if (acpConfig == null)
				offerUpload = true;
			else if ((acpConfig.latestRemote.versionTimestamp + (1000 * 60) /* version date only goes down to minutes */) < localConfig.latestLocal.versionTimestamp)
				offerUpload = true;
			else offerUpload = false;
			
			StringBuffer label = new StringBuffer();
			if (offerUpload)
				label.append("<HTML><B>");
			label.append(localConfig.name);
			label.append(" (last exported ");
			label.append(localConfig.latestLocal.versionDate);
			if (acpConfig == null)
				label.append(", never uploaded");
			else {
				label.append(", last uploaded ");
				label.append(acpConfig.latestRemote.versionDate);
			}
			label.append(")");
			if (offerUpload)
				label.append("</B></HTML>");
			
			this.setText(label.toString());
			if (offerUpload)
				this.setSelected(true);
			else this.setEnabled(false); // TODO really ??? might be hard(er) to read ...
		}
	}
	
	private class ConfigurationUploadDialog extends DialogPanel {
		private File configSourceFolder;
		private AppConfigurationGroupUploadSelector[] configSelectors;
		ConfigurationUploadDialog(File configSourceFolder, AppConfigurationGroupUploadSelector[] configSelectors) {
			super("GoldenGATE Configuration Exporter", true);
			this.configSourceFolder = configSourceFolder;
			this.configSelectors = configSelectors;
			
			this.setLayout(new BorderLayout());
			
			JLabel label = new JLabel("Select the configurations to upload to GoldenGATE Server", JLabel.CENTER);
			this.add(label, BorderLayout.NORTH);
			
			JPanel configSelectorPanel = new JPanel(new GridLayout(0, 1, 3, 3), true);
			for (int s = 0; s < this.configSelectors.length; s++)
				configSelectorPanel.add(this.configSelectors[s]);
			JPanel configSelectorPanelTray = new JPanel(new BorderLayout(), true);
			configSelectorPanelTray.add(configSelectorPanel, BorderLayout.NORTH);
			JScrollPane configSelectorPanelBox = new JScrollPane(configSelectorPanelTray);
			this.add(configSelectorPanelBox, BorderLayout.CENTER);
			
			JButton uploadButton = new JButton("Upload Selected");
			uploadButton.setBorder(BorderFactory.createRaisedBevelBorder());
			uploadButton.setPreferredSize(new Dimension(120, 21));
			uploadButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					exportSelectedConfigurations();
				}
			});
			JButton cancelButton = new JButton("Cancel");
			cancelButton.setBorder(BorderFactory.createRaisedBevelBorder());
			cancelButton.setPreferredSize(new Dimension(120, 21));
			cancelButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					dispose();
				}
			});
			JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
			buttonPanel.add(uploadButton);
			buttonPanel.add(cancelButton);
			this.add(buttonPanel, BorderLayout.SOUTH);
			
			this.setSize(400, 400);
			this.setLocationRelativeTo(this.getOwner());
		}
		void exportSelectedConfigurations() {
			ArrayList uploadConfigs = new ArrayList();
			for (int s = 0; s < this.configSelectors.length; s++) {
				if (this.configSelectors[s].isSelected())
					uploadConfigs.add(this.configSelectors[s].config);
			}
			if (uploadConfigs.isEmpty()) {
				DialogFactory.alert(("No local configurations are selected for upload to GoldenGATE Server."), "No Configurations Selected For Upload", JOptionPane.ERROR_MESSAGE);
				return;
			}
			else {
				int uploaded = uploadConfigurations(this.configSourceFolder, ((AppConfigGroupDescriptor[]) uploadConfigs.toArray(new AppConfigGroupDescriptor[uploadConfigs.size()])));
				int failed = (uploadConfigs.size() - uploaded);
				DialogFactory.alert((uploaded + " local configurations uploaded to upload to GoldenGATE Server" + ((failed == 0) ? "" : (", got errors on " + failed)) + "."), "Configurations Upload Complete", JOptionPane.INFORMATION_MESSAGE);
				this.dispose();
			}
		}
	}
	
	//	TEST ONLY !!!
	public static void main(String[] args) throws Exception {
		//	TODO test whole upload procedure ... most likely locally
	}
}
