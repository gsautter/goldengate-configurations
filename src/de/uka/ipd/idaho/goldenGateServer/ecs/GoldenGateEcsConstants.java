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

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;


/**
 * Constant bearer interface for GoldenGATE Editor Configuration Server (ECS)
 * 
 * @author sautter
 */
public interface GoldenGateEcsConstants extends GoldenGateServerConstants {
	
	/** the dummy session ID for the configuration servlet to retrieve configuration descriptors from a backing ECS */
	public static final String CONFIG_SERVLET_SESSION_ID = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC";
	
	
	/** the default path (mapping) of the servlet acting as a web gateway for GoldenGATE instances running in restricted environments, e.g. applets */
	public static final String WEB_GATEWAY_SERVLET_PATH = "WebGateway";
	
	
	/** the command for obtaining the names of the available configurations from the backing server */
	public static final String GET_CONFIGURATION_NAMES = "ECS_GET_CONFIGURATION_NAMES";
	
	/** the command for obtaining the GoldenGATE Editor configuration from the backing server */
	public static final String GET_CONFIGURATION = "ECS_GET_CONFIGUTATION";
	
	
	/** the command for obtaining a specific data element of a GoldenGATE Editor configuration from the backing server */
	public static final String GET_DATA = "ECS_GET_DATA";
	
	/** the command for updating a specific data element of a GoldenGATE Editor configuration on the backing server */
	public static final String UPDATE_DATA = "ECS_UPDATE_DATA";
	
	/** the command for deleting a specific data element of a GoldenGATE Editor configuration on the backing server */
	public static final String DELETE_DATA = "ECS_DELETE_DATA";
	
	
	/** the command for retrieving stubs of all configurations */
	public static final String GET_CONFIGURATION_DESCRIPTORS = "ECS_GET_CONFIGURATION_DESCRIPTORS";
	
	/** the command for retrieving the full descriptor of a configuration, ignoring all filters */
	public static final String GET_CONFIGURATION_DESCRIPTOR = "ECS_GET_CONFIGURATION_DESCRIPTOR";
	
	/** the command for creating/updating a projected configuration */
	public static final String UPDATE_CONFIGURATION = "ECS_UPDATE_CONFIGURATION";
	
	/** the command for uploading a new base configuration */
	public static final String UPLOAD_CONFIGURATION = "ECS_UPLOAD_CONFIGURATION";
	
	/** the command for deleting a configuration */
	public static final String DELETE_CONFIGURATION = "ECS_DELETE_CONFIGURATION";
	
	
	/** the command for getting the mapping of user names to default configurations */
	public static final String GET_USER_CONFIGURATIONS = "ECS_GET_USER_CONFIGURATIONS";
	
	/** the command for setting the mapping of user names to default configurations */
	public static final String SET_USER_CONFIGURATIONS = "ECS_SET_USER_CONFIGURATIONS";
	
	
	/** the command for getting the list of configurations available online through a configuration servlet */
	public static final String GET_ONLINE_CONFIGURATIONS = "ECS_GET_ONLINE_CONFIGURATIONS";
	
	/** the command for setting the list of configurations available online through a configuration servlet */
	public static final String SET_ONLINE_CONFIGURATIONS = "ECS_SET_ONLINE_CONFIGURATIONS";
	
	
	/** command for retrieving all groups available */
	public static final String GET_GROUPS = "ECS_GET_GROUPS";
	
	/** command for retrieving all plugins available */
	public static final String GET_PLUGINS = "ECS_GET_PLUGINS";
	
	/** command for retrieving all resources available */
	public static final String GET_RESOURCES = "ECS_GET_RESOURCES";
	
	
	/** the command for creating a group of plugins and resources */
	public static final String CREATE_GROUP = "ECS_CREATE_GROUP";
	
	/** the command for deleting a group of plugins and resources */
	public static final String DELETE_GROUP = "ECS_DELETE_GROUP";
	
	
	/** command for retrieving the plugins of a group */
	public static final String GET_GROUP_PLUGINS = "ECS_GET_GROUP_PLUGINS";
	
	/** command for setting the plugins of a group */
	public static final String SET_GROUP_PLUGINS = "ECS_SET_GROUP_PLUGINS";
	
	/** command for retrieving the resources of a group */
	public static final String GET_GROUP_RESOURCES = "ECS_GET_GROUP_RESOURCES";
	
	/** command for setting the resources of a group */
	public static final String SET_GROUP_RESOURCES = "ECS_SET_GROUP_RESOURCES";
	
	
	/**
	 * A DocumentResourceFilterer provides ECS with a document specific set of
	 * resource groups to include in a configuration for editing a specific
	 * document.
	 * 
	 * @author sautter
	 */
	public static interface DocumentResourceFilterer {
		
		/**
		 * This method returns the names of resource groups required for editing
		 * the document with the specified ID. If more than one
		 * DocumentResourceFilterer is registered with ECS, ECS will add up the
		 * group names returned by the individual ones. If this method returns
		 * null for all registered DocumentResourceFilterer, ECS will ignore the
		 * filter and use all resource groups available.
		 * @param documentId the ID of the document to edit
		 * @return the names of the resource groups required for editing the
		 *         document with the specified ID
		 */
		public abstract String[] getGroupsForDocument(String documentId);
	}
}
