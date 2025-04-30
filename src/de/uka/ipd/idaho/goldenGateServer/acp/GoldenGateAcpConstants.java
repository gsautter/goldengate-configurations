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
package de.uka.ipd.idaho.goldenGateServer.acp;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;

/**
 * Constant bearer interface for GoldenGATE Application Configuration Provider (ACP)
 * 
 * @author sautter
 */
public interface GoldenGateAcpConstants extends GoldenGateServerConstants {
//	
//	/** the dummy session ID for the configuration servlet to retrieve configuration descriptors from a backing ACP */
//	public static final String CONFIG_SERVLET_SESSION_ID = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC";
	
	/** the command for retrieving all versions off of all available GoldenGATE application configurations from the backing server */
	public static final String GET_APPLICATION_CONFIGURATION_VERSIONS = "ACP_GET_APPLICATION_CONFIGURATION_VERSIONS";
	
	/** the command for retrieving the full descriptor of a specific version of a GoldenGATE application configurations from the backing server */
	public static final String GET_APPLICATION_CONFIGURATION = "ACP_GET_APPLICATION_CONFIGURATION";
	
	/** the command for obtaining a series of data elements of a GoldenGATE application configuration from the backing server */
	public static final String GET_APPLICATION_CONFIGURATION_DATA = "ACP_GET_APPLICATION_CONFIGURATION_DATA";
//	
//	
//	/** the command for retrieving the names of the available GoldenGATE application configurations from the backing server */
//	public static final String GET_CONFIGURATION_NAMES = "ACP_GET_CONFIGURATION_NAMES";
//	
//	/** the command for retrieving the TSV descriptors of the available GoldenGATE application configurations from the backing server */
//	public static final String GET_CONFIGURATION_DESCRIPTORS = "ACP_GET_CONFIGURATION_DESCRIPTORS";
//	
//	/** the command for obtaining the TSV descriptor a specific GoldenGATE application configuration from the backing server */
//	public static final String GET_CONFIGURATION = "ACP_GET_CONFIGUTATION";
//	
//	/** the command for obtaining a series of data elements of a GoldenGATE application configuration from the backing server */
//	public static final String GET_DATA_ELEMENTS = "ACP_GET_DATA_ELEMENTS";
	
	
	/** the initial command for creating or updating a GoldenGATE application configuration */
	public static final String UPDATE_APPLICATION_CONFIGURATION = "ACP_UPDATE_APPLICATION_CONFIGURATION";
	
	/** the command for uploading/updating a series of data elements of a GoldenGATE application configuration on the backing server */
	public static final String UPDATE_APPLICATION_CONFIGURATION_DATA = "ACP_UPDATE_APPLICATION_CONFIGURATION_DATA";
	
	/** the command for marking the end of a partial upload of application configuration data files */
	public static final String MORE_APPLICATION_CONFIGURATION_DATA = "ACP_MORE_APPLICATION_CONFIGURATION_DATA";
//	
//	/** the command for finishing a running creation of or update to a GoldenGATE application configuration */
//	public static final String FINISH_APPLICATION_CONFIGURATION = "ACP_FINISH_APPLICATION_CONFIGURATION";
	
	/** the command for deleting a GoldenGATE application configuration */
	public static final String DELETE_APPLICATION_CONFIGURATION = "ACP_DELETE_APPLICATION_CONFIGURATION";
//	
//	
//	/** the command for getting the list of GoldenGATE application configurations available online through a configuration servlet */
//	public static final String GET_ONLINE_CONFIGURATIONS = "ACP_GET_ONLINE_CONFIGURATIONS";
//	
//	/** the command for setting the list of GoldenGATE application configurations available online through a configuration servlet */
//	public static final String SET_ONLINE_CONFIGURATIONS = "ACP_SET_ONLINE_CONFIGURATIONS";
	
	/** the permission for uploading new or updating existing application configurations in the ACP */
	public static final String UPDATE_APPLICATION_CONFIGURATION_PERMISSION = "ACP.UpdateAppConfiguration";
	
	/** the permission for deleting application configurations from the ACP */
	public static final String DELETE_APPLICATION_CONFIGURATION_PERMISSION = "ACP.DeleteAppConfiguration";
}
