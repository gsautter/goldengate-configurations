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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import de.uka.ipd.idaho.goldenGate.configuration.XmlConfiguration;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.Configuration;

/**
 * GoldenGateConfiguration to wrap around a configuration descriptor, to use
 * inside the GoldenGATE Server instance ECS runs in.
 * 
 * @author sautter
 */
public class EcsLocalConfiguration extends XmlConfiguration implements GoldenGateEcsConstants {
	
	private File basePath; // the base path of the actual configuration (below the root path)
	
	/**
	 * Constructor
	 * @param descriptor the configuration descriptor to back this configuration
	 * @param basePath the base path of the GoldenGATE configuration in ECS's
	 *            configuration store
	 */
	public EcsLocalConfiguration(Configuration descriptor, File basePath) {
		super(descriptor, null);
		this.basePath = basePath;
		this.basePath.mkdirs();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.GoldenGateConfiguration#isEditable()
	 */
	public boolean isEditable() {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.GoldenGateConfiguration#getHelpBaseURL()
	 */
	public String getHelpBaseURL() {
		return (new File(this.basePath, DOCUMENTATION_FOLDER_NAME)).getAbsolutePath();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.configuration.XmlConfiguration#getInputStream(java.lang.String, long)
	 */
	protected InputStream getInputStream(String dataName, long timestamp) throws IOException {
		return new FileInputStream(new File(this.basePath, dataName));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.configuration.XmlConfiguration#doGetOutputStream(java.lang.String)
	 */
	protected OutputStream doGetOutputStream(String dataName) throws IOException {
		File dataFile = new File(this.basePath, dataName);
		if (dataFile.exists())
			dataFile.renameTo(new File(this.basePath, (dataName + "." + System.currentTimeMillis() + ".old")));
		if (dataFile.getParentFile() != null)
			dataFile.getParentFile().mkdirs();
		dataFile.createNewFile();
		
		return new FileOutputStream(new File(this.basePath, dataName));
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.configuration.XmlConfiguration#doDeleteData(java.lang.String)
	 */
	protected boolean doDeleteData(String dataName) throws IOException {
		File file = new File(this.basePath, dataName);
		return (!file.exists() || file.delete());
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.configuration.XmlConfiguration#getURL(java.lang.String, long)
	 */
	protected URL getURL(String dataName, long timestamp) throws IOException {
		if ((dataName.indexOf("://") == -1) && !dataName.toLowerCase().startsWith("file:/"))
			return (new File(this.basePath, dataName)).toURI().toURL();
		else return new URL(dataName);
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.GoldenGateConfiguration#getAbsolutePath()
	 */
	public String getAbsolutePath() {
		if (this.absolutePath == null) {
			String path = this.basePath.getAbsolutePath().replaceAll("\\\\", "/");
			StringBuffer cleanPath = new StringBuffer();
			for (int c = 0; c < path.length(); c++) {
				char ch = path.charAt(c);
				if (ch == '/') {
					if (path.startsWith("./", (c+1)))
						c++; // ignore current slash and jump dot
					else cleanPath.append(ch);
				} 
				else if (ch == '\\') {
					if (path.startsWith("./", (c+1)) || path.startsWith(".\\", (c+1)))
						c++; // ignore current slash and jump dot
					else cleanPath.append('/');
				}
				else cleanPath.append(ch);
			}
			this.absolutePath = cleanPath.toString();
		}
		return this.absolutePath;
	}
	private String absolutePath = null;
}
