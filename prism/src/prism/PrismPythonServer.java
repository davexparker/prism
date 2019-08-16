//==============================================================================
//
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//
//------------------------------------------------------------------------------
//
//	This file is part of PRISM.
//
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//==============================================================================

package prism;

import java.io.*;
import java.net.*;
import java.util.*;

import parser.ast.*;

/**
* Example class demonstrating how to control PRISM programmatically,
* i.e. through the "API" exposed by the class prism.Prism.
* (this now uses the newer version of the API, released after PRISM 4.0.3)
* Test like this:
* PRISM_MAINCLASS=prism.PrismTest bin/prism ../prism-examples/polling/poll2.sm ../prism-examples/polling/poll3.sm
*/
public class PrismPythonServer
{
	private Prism prism;
	private ModulesFile currentModel;
	private ServerSocket server;
	String directory;
	String fileName;
	int socketPort;

	public PrismPythonServer(int port, String workDir, String prismFile){
		try{
			PrismLog mainLog;

			//init socket
			socketPort=port;
			server = new ServerSocket(socketPort);
			System.out.println("PRISM server running on port " + socketPort);


			fileName=prismFile;
			directory=workDir;

			// Init PRISM
			//mainLog = new PrismDevNullLog();
			mainLog = new PrismFileLog("stdout");
			prism = new Prism( mainLog);
			prism.initialise();
      prism.getSettings().set(PrismSettings.PRISM_PATH_VIA_AUTOMATA, true);
			setExports();
			prism.setEngine(Prism.EXPLICIT);
		} catch (PrismException | IOException e) {
			System.out.println("Error: " + e.getMessage());
		}
	}

	public Prism getPrism(){
		return prism;
	}

	public ServerSocket getServer(){
		return server;
	}

	public int getSocketPort(){
		return socketPort;
	}

	public void setExports(){
		try {
			prism.setGenStrat(true);
			prism.getSettings().set(PrismSettings.PRISM_EXPORT_ADV, "DTMC");
			prism.getSettings().set(PrismSettings.PRISM_EXPORT_ADV_FILENAME,directory + "/adv.tra");
			prism.setExportProductStates(true);
			prism.setExportProductStatesFilename(directory  + "/prod.sta");
			prism.setExportProductTrans(true);
			prism.setExportProductTransFilename(directory + "/prod.tra");
			prism.setExportTarget(true);
			prism.setExportTargetFilename(directory +  "/prod.lab");
			prism.getSettings().setExportPropAut(true);
			prism.getSettings().setExportPropAutFilename(directory + "/prod.aut");
			prism.setExportProductVector(true);
			prism.setExportProductVectorFilename(directory + "/guarantees.vect");
		} catch (PrismException e) {
			System.out.println("File not found Error: " + e.getMessage());
		}
	}

	public boolean loadPrismModelFile(){
		try{
			currentModel = prism.parseModelFile(new File(directory+fileName));
			prism.loadPRISMModel(currentModel);
			return true;
		} catch (FileNotFoundException | PrismException e) {
			System.out.println("Error: " + e.getMessage());
			return false;
		}
	}

	public boolean callPrismPartial(String ltlString) {
		try {
			Result result;
			PropertiesFile prismSpec;
			if (loadPrismModelFile()) {
				String ltl = ltlString.split(",")[0];
				String ltlForHistogram = ltlString.split(",")[1];
				prismSpec=prism.parsePropertiesString(currentModel, ltl);
				result = prism.modelCheck(prismSpec, prismSpec.getPropertyObject(0));
				System.out.println(result.getStrategy());
//				prism.applyStrategy(result.getStrategy());
//				Result dtmcResult = prism.modelCheck(ltlForHistogram);
//				Expression exprEnd = prism.parsePropertiesString(currentModel, "time").getProperty(0);
//				Distribution dist = prism.computeInstantaneousExpressionDistribution(20, exprEnd);
//				String distInfo = "mean = " + dist.mean() + ", s.d. = " + dist.standardDeviation() + ",  s.d.% = " + dist.standardDeviationRelative() + "%";
//				System.out.println(distInfo);
				return true;
			}
			else {
				return false;
			}
		}
		catch (PrismException e) {
			System.out.println("Error: " + e.getMessage());
			return false;
		}
	}

	public static void main(String args[]) throws Exception {
		String command;
		List<String> commands=Arrays.asList("sat_guarantees", "shutdown");
		String ltlString;
		Socket client;
		boolean success;

		PrismPythonServer talker=new PrismPythonServer(Integer.parseInt(args[0]), args[1], args[2]);

		if(Boolean.parseBoolean(args[3])){
			talker.callPrismPartial("Pmax=?[ ( F \"WayPoint3\" & time < 100) ], R{\"time\"}=?F \"WayPoint3\" & time < 100");
		}
		else {
			client = talker.server.accept();
			System.out.println("got connection on port" + talker.getSocketPort());
			BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
			PrintWriter out = new PrintWriter(client.getOutputStream(), true);
			boolean run = true;

			while (run) {
				command = in.readLine();
				System.out.println("received: " + command);
				if (command == null) {
					client = talker.server.accept();
					System.out.println("got connection on port" + talker.getSocketPort());
				} else {
					if (!commands.contains(command)) {
						System.out.println("Socket comm is unsynchronised! Trying to recover...");
						continue;
					}
					if (command.equals("sat_guarantees")) {
						ltlString = in.readLine();
						success = talker.callPrismPartial(ltlString);
						if (success) {
							out.println("success");
						} else {
							out.println("failure");
						}
						continue;
					}
					if (command.equals("shutdown")) {
						run = false;
						client.close();
						talker.server.close();
						talker.prism.closeDown();
					}
				}
			}
		}
		System.exit(0);
	}
}
