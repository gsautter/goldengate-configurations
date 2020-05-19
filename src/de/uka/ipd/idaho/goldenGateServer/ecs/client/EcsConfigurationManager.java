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
 *     * Neither the name of the Universitaet Karlsruhe (TH) nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITAET KARLSRUHE (TH) AND CONTRIBUTORS 
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

import java.io.File;
import java.io.IOException;

import de.uka.ipd.idaho.goldenGate.configuration.AbstractConfigurationManager;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Configuration;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.SpecialDataHandler;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticationManagerPlugin;

/**
 * Manager for the configurations hosted in an ECS, in particular for creating
 * and updating ECS based configurations using GoldenGATE Editor.
 * 
 * @author sautter
 */
public class EcsConfigurationManager extends AbstractConfigurationManager {
	
	private AuthenticationManagerPlugin authManager = null;
	private AuthenticatedClient authClient = null;
	private GoldenGateEcsClient ecsClient = null;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#init()
	 */
	public void init() {
		
		//	get authentication manager
		this.authManager = ((AuthenticationManagerPlugin) this.parent.getPlugin(AuthenticationManagerPlugin.class.getName()));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#isOperational()
	 */
	public boolean isOperational() {
		return (super.isOperational() && (this.authManager != null));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getPluginName()
	 */
	public String getPluginName() {
		return "ECS Configuration Manager";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getMainMenuTitle()
	 */
	public String getMainMenuTitle() {
		return "ECS Configurations";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.configuration.AbstractConfigurationManager#doExport(java.lang.String, de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.SpecialDataHandler, de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Configuration, java.io.File, de.uka.ipd.idaho.goldenGate.configuration.AbstractConfigurationManager.ExportStatusDialog)
	 */
	protected boolean doExport(String exportName, SpecialDataHandler specialData, Configuration config, File rootPath, ExportStatusDialog statusDialog) throws IOException {
		
		//	connected to server, upload configuration
		if (this.ensureLoggedIn()) {
			this.ecsClient.uploadConfiguration(rootPath, specialData, config, statusDialog);
			return true;
		}
		
		//	not connected to server
		else return false;
	}
	
	private boolean ensureLoggedIn() {
		
		//	test if connection alive
		if (this.authClient != null) {
			try {
				//	test if connection alive
				if (this.authClient.ensureLoggedIn())
					return true;
				
				//	connection dead (eg a session timeout), make way for re-getting from auth manager
				else {
					this.ecsClient = null;
					this.authClient = null;
				}
			}
			
			//	server temporarily unreachable, re-login will be done by auth manager
			catch (IOException ioe) {
				this.ecsClient = null;
				this.authClient = null;
				return false;
			}
		}
		
		//	got no valid connection at the moment
		if (this.authClient == null)
			this.authClient = this.authManager.getAuthenticatedClient();
		
		//	authentication failed
		if (this.authClient == null) return false;
		
		//	got valid connection
		else {
			this.ecsClient = new GoldenGateEcsClient(this.authClient);
			return true;
		}
	}
}