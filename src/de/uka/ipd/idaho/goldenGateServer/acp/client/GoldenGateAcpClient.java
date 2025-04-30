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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.uka.ipd.idaho.gamta.util.ControllingProgressMonitor;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfigGroupDescriptor;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfigVersionDescriptor;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfiguration;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils.AppConfigurationDataFile;
import de.uka.ipd.idaho.goldenGateServer.acp.GoldenGateAcpConstants;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineOutputStream;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Client object for sending updates to GoldenGATE Application Configuration
 * Provider (ACP).
 * 
 * @author sautter
 */
public class GoldenGateAcpClient implements GoldenGateAcpConstants {
	private AuthenticatedClient authClient;
	
	/** Constructor
	 * @param authClient the authenticated client to use
	 */
	public GoldenGateAcpClient(AuthenticatedClient authClient) {
		this.authClient = authClient;
	}
	
	//	TODO wrap this bugger into dedicated ACP uploader or manager plug-in (NO fully blown exporter)
	
	/**
	 * Get a list of the application configurations currently available from
	 * the backing provider, grouped by name.
	 * @return an array holding descriptors of the available application
	 *            configurations
	 */
	public AppConfigGroupDescriptor[] getAppConfigGroups() throws IOException {
		AppConfigVersionDescriptor[] acvds = this.getAppConfigVersions();
		return AppConfigGroupDescriptor.groupAppConfigVersionDescriptors(acvds);
	}
	
	/**
	 * Get a list of all versions of the application configurations currently
	 * available from the backing provider.
	 * @param includePastVersions include past versions?
	 * @return an array holding descriptors of the available application
	 *            configurations
	 */
	public AppConfigVersionDescriptor[] getAppConfigVersions() throws IOException {
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_APPLICATION_CONFIGURATION_VERSIONS);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_APPLICATION_CONFIGURATION_VERSIONS.equals(error)) {
				AppConfigVersionDescriptor[] acvds = AppConfigVersionDescriptor.readAppConfigVersionDescriptors(br, "ACP");
				Arrays.sort(acvds, AppConfigVersionDescriptor.nameOrder);
				return acvds;
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
	 * Get the full descriptor of a specific version of an application
	 * configuration from the backing provider. Setting the version date to
	 * null retrieves the latest version.
	 * @param appConfigName the name of of the application configuration whose
	 *            descriptor to get
	 * @param appConfigVersionDate the version date of of the specific
	 *            application configuration version whose descriptor to get
	 * @return the descriptors of the specified application configurations
	 */
	public AppConfiguration getAppConfigurationDescriptor(String appConfigName, String appConfigVersionDate) throws IOException {
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_APPLICATION_CONFIGURATION);
			bw.newLine();
			bw.write(appConfigName);
			bw.newLine();
			bw.write((appConfigVersionDate == null) ? "" : appConfigVersionDate);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_APPLICATION_CONFIGURATION.equals(error))
				return AppConfiguration.readDescriptor(br);
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
	 * Upload a new (version of an existing) GoldenGATE application
	 * configuration to the backing GoldenGATE ACP. The returned array
	 * contains some details about the update process, e.g. for displaying
	 * in a UI based tool.
	 * @param appConfig the descriptor of the application configuration to
	 *            upload
	 * @param appConfigFolder the root folder of the application configurations
	 *            in the GoldenGATE installation
	 * @param pm a progress monitor to provide progress and status information
	 *            through
	 * @return an array holding status report lines
	 * @throws IOException
	 */
	public String[] updateAppConfiguration(AppConfiguration appConfig, File appConfigFolder, ProgressMonitor pm) throws IOException {
		
		//	make sure we're logged in
		if (!this.authClient.isLoggedIn())
			throw new IOException("Not logged in.");
		
		//	obtain list of to-update entries
		Connection con = null;
		LinkedList toUpdateDataFiles = null;
		String updateKey = null;
		try {
			pm.setInfo("Connecting to server ...");
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			bw.write(UPDATE_APPLICATION_CONFIGURATION);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			pm.setInfo("Sending configuration descriptor ...");
			appConfig.writeDescriptor(bw);
			bw.write(""); // terminate descriptor with blank line
			bw.newLine();
			bw.flush();
			
			pm.setInfo("Receiving list of to-update data files ...");
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (UPDATE_APPLICATION_CONFIGURATION.equals(error)) {
				updateKey = br.readLine();
				
				//	empty update key indicates nothing to update
				if (updateKey.length() == 0) {
					pm.setInfo("No data files to update");
					if (pm instanceof ControllingProgressMonitor) {
						((ControllingProgressMonitor) pm).setPauseResumeEnabled(false);
						((ControllingProgressMonitor) pm).setAbortEnabled(false);
					}
					StringVector log = new StringVector();
					for (String logEntry; (logEntry = br.readLine()) != null;)
						log.addElement(logEntry);
					pm.setInfo("Update complete.");
					pm.setProgress(100);
					return log.toStringArray();
				}
				
				//	receive list of to-update entries
				else {
					toUpdateDataFiles = new LinkedList();
					for (String dataFileString; (dataFileString = br.readLine()) != null;) {
						if (dataFileString.length() == 0)
							break;
						AppConfigurationDataFile dataFile = AppConfigurationDataFile.readAppConfigurationDataFile(dataFileString);
						if (dataFile != null)
							toUpdateDataFiles.addLast(dataFile);
					}
					pm.setInfo("Received list of " + toUpdateDataFiles.size() + " to-update data files");
				}
			}
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
		
		//	nothing changed at all, we're done
		if (toUpdateDataFiles.isEmpty()) {
			String[] log = {"Configuration up to date on server"};
			return log;
		}
		
		//	send to-update entries (make sure to include name and timestamp)
		int toUpdateDocEntryCount = toUpdateDataFiles.size();
		while (toUpdateDataFiles.size() != 0) try {
			pm.setInfo("Sending " + toUpdateDataFiles.size() + ((toUpdateDataFiles.size() < toUpdateDocEntryCount) ? " remaning" : "") + " data files to server ...");
			con = this.authClient.getConnection();
			BufferedLineOutputStream out = con.getOutputStream();
			out.writeLine(UPDATE_APPLICATION_CONFIGURATION_DATA);
			out.writeLine(this.authClient.getSessionID());
			out.writeLine(updateKey);
			ByteCountingOutputStream bcout = new ByteCountingOutputStream(out);
			ZipOutputStream zout = new ZipOutputStream(bcout);
			byte[] buffer = new byte[1024];
			while (toUpdateDataFiles.size() != 0) {
				AppConfigurationDataFile dataFile = ((AppConfigurationDataFile) toUpdateDataFiles.removeFirst());
				pm.setInfo(" - " + dataFile.fullName);
				pm.setProgress(((toUpdateDocEntryCount - toUpdateDataFiles.size()) * 100) / toUpdateDocEntryCount);
				ZipEntry ze = new ZipEntry(dataFile.fullName);
				ze.setTime(dataFile.lastMod);
				zout.putNextEntry(ze);
//				File df = new File(appConfigFolder, dataFile.fullName);
				File df = new File(appConfigFolder, dataFile.getDataFilePathAndName());
				InputStream dfIn = new BufferedInputStream(new FileInputStream(df));
				for (int r; (r = dfIn.read(buffer, 0, buffer.length)) != -1;)
					zout.write(buffer, 0, r);
				dfIn.close();
				zout.closeEntry();
				if (bcout.bytesWritten > (1024 * 1024 * 128))
					break; // stop (partial) upload after 128MB to ease memory consumption due to HTTP request data buffering
			}
			
			//	we've sent everything, indicate so and expect update result
			if (toUpdateDataFiles.isEmpty()) {
				ZipEntry ze = new ZipEntry(updateKey);
				zout.putNextEntry(ze);
				zout.closeEntry();
				zout.flush();
				
				pm.setInfo("Receiving update result ...");
				if (pm instanceof ControllingProgressMonitor) {
					((ControllingProgressMonitor) pm).setPauseResumeEnabled(false);
					((ControllingProgressMonitor) pm).setAbortEnabled(false);
				}
				BufferedReader br = con.getReader();
				String error = br.readLine();
				if (UPDATE_APPLICATION_CONFIGURATION_DATA.equals(error)) {
					StringVector log = new StringVector();
					for (String logEntry; (logEntry = br.readLine()) != null;)
						log.addElement(logEntry);
					pm.setInfo("Update complete.");
					pm.setProgress(100);
					return log.toStringArray();
				}
				else throw new IOException(error);
			}
			
			//	more to send, indicate so and expect acknowledgment for last part
			else {
				ZipEntry ze = new ZipEntry(MORE_APPLICATION_CONFIGURATION_DATA);
				zout.putNextEntry(ze);
				zout.closeEntry();
				zout.flush();
				
				BufferedReader br = con.getReader();
				String error = br.readLine();
				if (!MORE_APPLICATION_CONFIGURATION_DATA.equals(error))
					throw new IOException(error);
			}
		}
		finally {
			if (con != null)
				con.close();
			con = null;
		}
		
		//	never gonna happen, but Java don't know
		String[] log = {"Strange outcome of upload to server ..."};
		return log;
	}
	private static class ByteCountingOutputStream extends OutputStream {
		private OutputStream out;
		int bytesWritten = 0;
		ByteCountingOutputStream(OutputStream out) {
			this.out = out;
		}
		public void write(int b) throws IOException {
			this.out.write(b);
			this.bytesWritten++;
		}
		public void write(byte[] b) throws IOException {
			this.out.write(b);
			this.bytesWritten += b.length;
		}

		public void write(byte[] b, int off, int len) throws IOException {
			this.out.write(b, off, len);
			this.bytesWritten += len;
		}
		public void flush() throws IOException {
			this.out.flush();
		}
		public void close() throws IOException {
			this.out.close();
		}
	}
	
	/**
	 * Delete a GoldenGATE application configuration, or an individual version
	 * thereof, from the backing GoldenGATE ACP. Specifically, if the argument
	 * version date is null, all versions will be deleted. This does not
	 * pertain to any of their actual data files, though, as the latter might
	 * well be shared with other hosted configurations. The returned array
	 * contains some details about the deletion process, e.g. for displaying
	 * in a UI based tool.
	 * @param appConfigName the name of of the application configuration to
	 *            delete
	 * @param appConfigVersionDate the version date of of the specific
	 *            application configuration to delete
	 * @param pm a progress monitor to provide progress and status information
	 *            through
	 * @return an array holding status report lines
	 * @throws IOException
	 */
	public String[] deleteAppConfiguration(String appConfigName, String appConfigVersionDate, ProgressMonitor pm) throws IOException {
		
		//	make sure we're logged in
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		//	send deletion command ...
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(DELETE_APPLICATION_CONFIGURATION);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(appConfigName);
			bw.newLine();
			bw.write((appConfigVersionDate == null) ? "" : appConfigVersionDate);
			bw.newLine();
			bw.flush();
			
			//	... and receive response
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (DELETE_APPLICATION_CONFIGURATION.equals(error)) {
				StringVector log = new StringVector();
				for (String logEntry; (logEntry = br.readLine()) != null;)
					log.addElement(logEntry);
				return log.toStringArray();
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
}
