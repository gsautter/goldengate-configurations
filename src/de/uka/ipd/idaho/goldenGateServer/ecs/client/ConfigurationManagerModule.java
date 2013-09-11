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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;

import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Configuration;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Plugin;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Resource;
import de.uka.ipd.idaho.goldenGateServer.ecs.GoldenGateEcsConstants;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Module for managing configurations
 * 
 * @author sautter
 */
public class ConfigurationManagerModule extends AuthenticatedWebClientModul implements GoldenGateEcsConstants {

	private static final String MODE_PARAMETER = "mode";
	
	private static final String EDIT_USER_CONFIGURATIONS = "ECS_EDIT_USER_CONFIGURATIONS";
	private static final String EDIT_ONLINE_CONFIGURATIONS = "ECS_ONLINE_USER_CONFIGURATIONS";
	private static final String CREATE_CONFIGURATION = "ECS_CREATE_CONFIGURATION";
	private static final String EDIT_CONFIGURATION = "ECS_EDIT_CONFIGURATION";
	private static final String EDIT_GROUP = "ECS_EDIT_ROLE";
	
	private static final String ONLINE_CONFIGURATION_PARAMETER = "onlineConfig";
	
	private static final String GROUP_NAME_PARAMETER = "groupName";
	
	private static final String PLUGIN_PARAMETER = "plugin";
	private static final String RESOURCE_PARAMETER = "resource";
	
	private static final String CONFIGURATION_NAME_PARAMETER = "configName";
	private static final String CONFIGURATION_BASE_PARAMETER = "configBase";
	
	private static final String USER_NAME_PARAMETER = "userName";
	private static final String USER_COUNT_PARAMETER = "userCount";
	
	private Map ecsClientCache = Collections.synchronizedMap(new HashMap());
	private GoldenGateEcsClient getEcsClient(AuthenticatedClient authClient) {
		GoldenGateEcsClient ecsc = ((GoldenGateEcsClient) this.ecsClientCache.get(authClient.getSessionID()));
		if (ecsc == null) {
			ecsc = new GoldenGateEcsClient(authClient);
			this.ecsClientCache.put(authClient.getSessionID(), ecsc);
		}
		return ecsc;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateScf.uaa.webClient.AuthenticatedWebClientModul#getModulLabel()
	 */
	public String getModulLabel() {
		return "Configurations";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#displayFor(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient)
	 */
	public boolean displayFor(AuthenticatedClient authClient) {
		return authClient.isAdmin(); // managing configurations is admin business
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateScf.uaa.webClient.AuthenticatedWebClientModul#handleRequest(de.uka.ipd.idaho.goldenGateScf.uaa.client.AuthenticatedClient, javax.servlet.http.HttpServletRequest)
	 */
	public String[] handleRequest(AuthenticatedClient authClient, HttpServletRequest request) throws IOException {
		GoldenGateEcsClient ecsc = this.getEcsClient(authClient);
		StringVector messageCollector = new StringVector();
		
		String command = request.getParameter(COMMAND_PARAMETER);
		
		//	create group
		if (CREATE_GROUP.equals(command)) {
			
			//	get parameters
			String groupName = request.getParameter(GROUP_NAME_PARAMETER);
			
			//	create group
			ecsc.createGroup(groupName);
			messageCollector.addElement("Group '" + groupName + "' created successfully.");
		}
		
		//	delete group
		else if (DELETE_GROUP.equals(command)) {
			
			//	get parameters
			String groupName = request.getParameter(GROUP_NAME_PARAMETER);
			
			//	delete group
			ecsc.deleteGroup(groupName);
			messageCollector.addElement("Group '" + groupName + "' deleted successfully.");
		}
		
		//	edit a group's groups and permissions
		else if (EDIT_GROUP.equals(command)) {
			
			//	get parameters
			String groupName = request.getParameter(GROUP_NAME_PARAMETER);
			String[] plugins = request.getParameterValues(PLUGIN_PARAMETER);
			String[] resources = request.getParameterValues(RESOURCE_PARAMETER);
			
			//	set plugins
			ecsc.setGroupPlugins(groupName, plugins);
			messageCollector.addElement("Plugins of group '" + groupName + "' changed successfully.");
			
			//	set resources
			ecsc.setGroupResources(groupName, resources);
			messageCollector.addElement("Resources of group '" + groupName + "' changed successfully.");
		}
		
		//	edit a configuration
		else if (EDIT_CONFIGURATION.equals(command)) {
			
			//	get parameters
			String configName = request.getParameter(CONFIGURATION_NAME_PARAMETER);
			String baseConfigName = request.getParameter(CONFIGURATION_BASE_PARAMETER);
			String[] plugins = request.getParameterValues(PLUGIN_PARAMETER);
			String[] resources = request.getParameterValues(RESOURCE_PARAMETER);
			
			//	update configuration
			ecsc.updateConfiguration(configName, baseConfigName, ((plugins == null) ? new String[0] : plugins), ((resources == null) ? new String[0] : resources));
			messageCollector.addElement("Configuration '" + configName + "' updated successfully.");
		}
		
		//	delete a configuration
		else if (DELETE_CONFIGURATION.equals(command)) {
			
			//	get parameters
			String configName = request.getParameter(CONFIGURATION_NAME_PARAMETER);
			
			//	update configuration
			ecsc.deleteConfiguration(configName);
			messageCollector.addElement("Configuration '" + configName + "' deleted successfully.");
		}
		
		//	update user's default configurations
		else if (EDIT_USER_CONFIGURATIONS.equals(command)) {
			Map userConfigs = new HashMap();
			
			//	read data
			int userCount = 0;
			try {
				String userCountString = request.getParameter(USER_COUNT_PARAMETER);
				userCount = Integer.parseInt(userCountString);
			}
			catch (Exception e) {
				messageCollector.addElement("Could not update default configurations of users, count missing.");
				return messageCollector.toStringArray();
			}
			for (int u = 0; u < userCount; u++) {
				String userName = request.getParameter(USER_NAME_PARAMETER + u);
				String configName = request.getParameter(CONFIGURATION_NAME_PARAMETER + u);
				if ((userName != null) && (configName != null))
					userConfigs.put(userName, configName);
			}
			
			//	update configuration
			ecsc.setUserDefaultConfigurations(userConfigs);
			messageCollector.addElement("Default configurations of users updated successfully.");
		}
		
		//	update online configurations
		else if (EDIT_ONLINE_CONFIGURATIONS.equals(command)) {
			String[] onlineConfigurations = request.getParameterValues(ONLINE_CONFIGURATION_PARAMETER);
			ecsc.setOnlineConfigurations(onlineConfigurations);
			messageCollector.addElement("Online configurations changed successfully.");
		}
		
		return messageCollector.toStringArray();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageContent(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageContent(AuthenticatedClient authClient, HtmlPageBuilder pageBuilder) throws IOException {
		GoldenGateEcsClient ecsc = this.getEcsClient(authClient);
		String mode = pageBuilder.request.getParameter(MODE_PARAMETER);
		
		//	edit a group's groups and plugins
		if (EDIT_GROUP.equals(mode)) {
			String groupName = pageBuilder.request.getParameter(GROUP_NAME_PARAMETER);
			
			String[] plugins = ecsc.getPlugins();
			String[] groupPlugins = ecsc.getGroupPlugins(groupName);
			Set groupPluginSet = new HashSet(Arrays.asList(groupPlugins));
			
			String[] resources = ecsc.getResources();
			String[] groupResources = ecsc.getGroupResources(groupName);
			Set groupResourceSet = new HashSet(Arrays.asList(groupResources));
			
			//	open form and add command
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + EDIT_GROUP + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + GROUP_NAME_PARAMETER + "\" value=\"" + groupName + "\">");
			
			//	build label row
			pageBuilder.writeLine("<table class=\"mainTable\">");
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableHeader\">");
			pageBuilder.writeLine("Manage the groups, plugins and resources of group '" + groupName + "'");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	open level table
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
			pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
			
			//	build label row
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Groups / Plugins");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Resources");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add actual group data
			for (int pr = 0; pr < Math.max(plugins.length, resources.length); pr++) {
				String plugin = ((pr < plugins.length) ? plugins[pr] : null);
				String resource = ((pr < resources.length) ? resources[pr] : null);
				
				//	open table row
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				if (plugin == null)
					pageBuilder.writeLine("&nbsp;");
				else {
					pageBuilder.writeLine("<input type=\"checkbox\" name=\"" + PLUGIN_PARAMETER + "\" value=\"" + plugin + "\"" + (groupPluginSet.contains(plugin) ? " checked" : "") + ">");
					pageBuilder.writeLine("&nbsp;");
					pageBuilder.writeLine(plugin);
				}
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				if (resource == null)
					pageBuilder.writeLine("&nbsp;");
				else {
					pageBuilder.writeLine("<input type=\"checkbox\" name=\"" + RESOURCE_PARAMETER + "\" value=\"" + resource + "\"" + (groupResourceSet.contains(resource) ? " checked" : "") + ">");
					pageBuilder.writeLine("&nbsp;");
					pageBuilder.writeLine(resource);
				}
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("</tr>");
			}
			
			//	add button row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"formTableBody\">");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Edit Group\" class=\"submitButton\">");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close level table
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close master table and form
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</form>");
		}
		
		//	edit a configuration's plugins and resources
		else if (EDIT_CONFIGURATION.equals(mode)) {
			String configName = pageBuilder.request.getParameter(CONFIGURATION_NAME_PARAMETER);
			Configuration config = null;
			try {
				config = ecsc.getConfigurationDescriptor(configName);
			}
			catch (Exception e) {
				/*
				 * this exception can happen if trying to fetch the descriptor
				 * for a configuration being newly created
				 */
				System.out.println("Configuration '" + configName + "' does not exist so far.");
			}
			
			String baseConfigName = pageBuilder.request.getParameter(CONFIGURATION_BASE_PARAMETER);
			Configuration baseConfig = ecsc.getConfigurationDescriptor(baseConfigName);
			
			String[] basePlugins = ((String[]) baseConfig.pluginsByName.keySet().toArray(new String[baseConfig.pluginsByName.size()]));
			Set pluginSet = new HashSet();
			if (config != null) pluginSet.addAll(config.pluginsByName.keySet());
			
			TreeMap resourceNamesByNiceName = new TreeMap();
			for (Iterator rit = baseConfig.resources.iterator(); rit.hasNext();) {
				Resource resource = ((Resource) rit.next());
				Plugin plugin = ((Plugin) baseConfig.pluginsByClassName.get(resource.managerClassName));
				resourceNamesByNiceName.put((((plugin == null) ? "" : (plugin.name + ".")) + resource.name), resource.name);
			}
			String[] baseResources = ((String[]) resourceNamesByNiceName.keySet().toArray(new String[resourceNamesByNiceName.size()]));
			Set resourceSet = new HashSet();
			if (config != null) resourceSet.addAll(config.resourcesByName.keySet());
			//	TODO: generate resource nice names for display, use plain name in parameter value
			
			//	open form and add command
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + EDIT_CONFIGURATION + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + CONFIGURATION_NAME_PARAMETER + "\" value=\"" + configName + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + CONFIGURATION_BASE_PARAMETER + "\" value=\"" + baseConfigName + "\">");
			
			//	build label row
			pageBuilder.writeLine("<table class=\"mainTable\">");
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableHeader\">");
			pageBuilder.writeLine("Manage the plugins and resources of configuration '" + configName + "' (base is '" + baseConfigName + "')");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	open level table
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
			pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
			
			//	build label row
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Plugins");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Resources");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add actual group data
			for (int pr = 0; pr < Math.max(basePlugins.length, baseResources.length); pr++) {
				String plugin = ((pr < basePlugins.length) ? basePlugins[pr] : null);
				String resource = ((pr < baseResources.length) ? baseResources[pr] : null);
				
				//	open table row
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				if (plugin == null)
					pageBuilder.writeLine("&nbsp;");
				
				else {
					pageBuilder.writeLine("<input type=\"checkbox\" name=\"" + PLUGIN_PARAMETER + "\" value=\"" + plugin + "\"" + (pluginSet.contains(plugin) ? " checked" : "") + ">");
					pageBuilder.writeLine("&nbsp;");
					pageBuilder.writeLine(plugin);
				}
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				if (resource == null)
					pageBuilder.writeLine("&nbsp;");
				else {
					String resourceName = ((String) resourceNamesByNiceName.get(resource));
					pageBuilder.writeLine("<input type=\"checkbox\" name=\"" + RESOURCE_PARAMETER + "\" value=\"" + resourceName + "\"" + (resourceSet.contains(resourceName) ? " checked" : "") + ">");
					pageBuilder.writeLine("&nbsp;");
					pageBuilder.writeLine(resource);
				}
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("</tr>");
			}
			
			//	add button row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"formTableBody\">");
			pageBuilder.writeLine(("<input type=\"submit\" value=\"" + ((config == null) ? "Create" : "Edit") + " Configuration\" class=\"submitButton\">"));
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close level table
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close master table and form
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</form>");
		}
		
		//	show master list
		else {
			String[] groups = ecsc.getGroups();
			
			//	build label row
			pageBuilder.writeLine("<table class=\"mainTable\">");
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableHeader\">");
			pageBuilder.writeLine("Manage the groups, plugins and resources of configurations, levels and groups");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	open level table
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
			pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
			
			//	build label row
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Groups");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add row with form for creating group
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"dataTableBody\">");
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + CREATE_GROUP + "\">");
			pageBuilder.writeLine("Group Name&nbsp;");
			pageBuilder.writeLine("<input type=\"text\" name=\"" + GROUP_NAME_PARAMETER + "\">");
			pageBuilder.writeLine("&nbsp;");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Create Group\" class=\"submitButton\">");
			pageBuilder.writeLine("</form>");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			
			//	add actual group data
			for (int lg = 0; lg < groups.length; lg++) {
				String group = ((lg < groups.length) ? groups[lg] : null);
				
				//	open table row
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				if (group == null)
					pageBuilder.writeLine("&nbsp;");
				else {
					pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + DELETE_GROUP + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + GROUP_NAME_PARAMETER + "\" value=\"" + group + "\">");
					pageBuilder.writeLine(("<a" +
							" title=\"" + ("Edit plugins and resources of group '" + group + "'") + "\"" +
							" href=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "?" + 
										  MODE_PARAMETER + "=" + EDIT_GROUP + 
									"&" + GROUP_NAME_PARAMETER + "=" + group + 
							"\">"));
					pageBuilder.writeLine(group);
					pageBuilder.writeLine("</a>");
					pageBuilder.writeLine("&nbsp;");
					pageBuilder.writeLine("<input type=\"submit\" value=\"Delete Group\" class=\"submitButton\">");
					pageBuilder.writeLine("</form>");
				}
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("</tr>");
			}
			
			//	close level table
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			
			
			//	get configuration data
			Configuration[] configs = ecsc.getConfigurationDescriptors();
			SortedSet baseConfigSet = new TreeSet(new Comparator() {
				public int compare(Object o1, Object o2) {
					return ((Configuration) o1).name.compareToIgnoreCase(((Configuration) o2).name);
				}
			});
			for (int c = 0; c < configs.length; c++)
				if (configs[c].name.equals(configs[c].basePath))
					baseConfigSet.add(configs[c]);
			Configuration[] baseConfigs = ((Configuration[]) baseConfigSet.toArray(new Configuration[baseConfigSet.size()]));
			
			//	open configurations table
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
			pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
			
			//	build label row
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Configurations");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add row with form for creating configuration
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td colspan=\"2\" class=\"dataTableBody\">");
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + CREATE_CONFIGURATION + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + MODE_PARAMETER + "\" value=\"" + EDIT_CONFIGURATION + "\">");
			pageBuilder.writeLine("Configuration Name&nbsp;");
			pageBuilder.writeLine("<input type=\"text\" name=\"" + CONFIGURATION_NAME_PARAMETER + "\">");
			pageBuilder.writeLine("&nbsp;Base Configuration&nbsp;");
			pageBuilder.writeLine("<select name=\"" + CONFIGURATION_BASE_PARAMETER + "\">");
			for (int c = 0; c < baseConfigs.length; c++) {
				pageBuilder.writeLine("<option>");
				pageBuilder.writeLine(baseConfigs[c].name);
				pageBuilder.writeLine("</option>");
			}
			pageBuilder.writeLine("</select>");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Create Configuration\" class=\"submitButton\">");
			pageBuilder.writeLine("</form>");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add actual configuration data
			for (int c = 0; c < configs.length; c++) {
				
				//	projected configuration, add editing options
				if (!baseConfigSet.contains(configs[c])) {
					
					//	open table row
					pageBuilder.writeLine("<tr>");
					pageBuilder.writeLine("<td width=\"80%\" class=\"dataTableBody\">");
					
					pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
					
					pageBuilder.writeLine(("<a" +
							" title=\"" + ("Edit plugins and resources of configuration '" + configs[c].name + "'") + "\"" +
							" href=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "?" + 
										  MODE_PARAMETER + "=" + EDIT_CONFIGURATION + 
									"&" + CONFIGURATION_NAME_PARAMETER + "=" + configs[c].name + 
									"&" + CONFIGURATION_BASE_PARAMETER + "=" + configs[c].basePath + 
							"\">"));
					pageBuilder.writeLine(configs[c].name);
					pageBuilder.writeLine("</a>");
					
					pageBuilder.writeLine("&nbsp;");
					
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + CREATE_CONFIGURATION + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + MODE_PARAMETER + "\" value=\"" + EDIT_CONFIGURATION + "\">");
					pageBuilder.writeLine("&nbsp;Base Configuration&nbsp;");
					pageBuilder.writeLine("<select name=\"" + CONFIGURATION_BASE_PARAMETER + "\">");
					for (int b = 0; b < baseConfigs.length; b++) {
						pageBuilder.writeLine("<option>");
						pageBuilder.writeLine(baseConfigs[b].name);
						pageBuilder.writeLine("</option>");
					}
					pageBuilder.writeLine("</select>");
					pageBuilder.writeLine("<input type=\"submit\" value=\"Change Configuration\" class=\"submitButton\">");
					pageBuilder.writeLine("</form>");
					
					pageBuilder.writeLine("</td>");
					pageBuilder.writeLine("<td class=\"dataTableBody\">");
					
					pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + DELETE_CONFIGURATION + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + CONFIGURATION_NAME_PARAMETER + "\" value=\"" + configs[c].name + "\">");
					pageBuilder.writeLine("<input type=\"submit\" value=\"Delete Configuration\" class=\"submitButton\">");
					pageBuilder.writeLine("</form>");
					
					pageBuilder.writeLine("</td>");
					pageBuilder.writeLine("</tr>");
				}
			}
			
			//	close configuration table
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			
			
			//	get user/configuration data
			Map userConfigs = ecsc.getUserDefaultConfigurations();
			
			//	open configurations table
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
			pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
			
			//	build label row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"dataTableHeader\">");
			pageBuilder.writeLine("User's default configurations");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + EDIT_USER_CONFIGURATIONS + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + USER_COUNT_PARAMETER + "\" value=\"" + userConfigs.size() + "\">");
			
			//	add actual user/configuration data
			int userNumber = 0;
			for (Iterator uit = userConfigs.keySet().iterator(); uit.hasNext();) {
				String userName = ((String) uit.next());
				String configName = ((String) userConfigs.get(userName));
				
				//	open table row
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				pageBuilder.writeLine(userName);
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("<td width=\"80%\" class=\"dataTableBody\">");
				
				pageBuilder.writeLine("<input type=\"hidden\" name=\"" + (USER_NAME_PARAMETER + userNumber) + "\" value=\"" + userName + "\">");
				pageBuilder.writeLine("&nbsp;Default Configuration&nbsp;");
				pageBuilder.writeLine("<select name=\"" + (CONFIGURATION_NAME_PARAMETER + userNumber) + "\">");
				pageBuilder.writeLine(("<option" + (((configName == null) || configName.startsWith("<")) ? " selected" : "") + ">"));
				pageBuilder.writeLine("&lt;Default&gt;");
				pageBuilder.writeLine("</option>");
				for (int c = 0; c < configs.length; c++) {
					pageBuilder.writeLine(("<option" + (configs[c].name.equals(configName) ? " selected" : "") + ">"));
					pageBuilder.writeLine(configs[c].name);
					pageBuilder.writeLine("</option>");
				}
				pageBuilder.writeLine("</select>");
				
				pageBuilder.writeLine("</td>");
				pageBuilder.writeLine("</tr>");
				
				userNumber++;
			}
			
			//	add button row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"dataTableBody\">");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Set Users' Default Configurations\" class=\"submitButton\">");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close form
			pageBuilder.writeLine("</form>");
			
			//	close user/configuration table
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			
			
			//	get online configurations
			Set onlineConfigSet = new HashSet(Arrays.asList(ecsc.getOnlineConfigurations()));
			
			//	open configurations table
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
			pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
			
			//	build label row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"dataTableHeader\">");
			pageBuilder.writeLine("Online configurations");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + EDIT_ONLINE_CONFIGURATIONS + "\">");
			
			//	add actual user/configuration data
			for (int c = 0; c < configs.length; c++) {
				
				//	open table row
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				pageBuilder.writeLine(configs[c].name);
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				
				pageBuilder.writeLine("&nbsp;Online&nbsp;");
				pageBuilder.writeLine("<input type=\"checkbox\" name=\"" + ONLINE_CONFIGURATION_PARAMETER + "\" value=\"" + configs[c].name + "\"" + (onlineConfigSet.contains(configs[c].name) ? " checked" : "") + ">");
				
				pageBuilder.writeLine("</td>");
				pageBuilder.writeLine("</tr>");
				
				userNumber++;
			}
			
			//	add button row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"dataTableBody\">");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Set Online Configurations\" class=\"submitButton\">");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close form
			pageBuilder.writeLine("</form>");
			
			//	close user/configuration table
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close master table
			pageBuilder.writeLine("</table>");
		}
	}
}