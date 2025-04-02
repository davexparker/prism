//==============================================================================
//
//	Copyright (c) 2024-
//	Authors:
//	* Christoph Weinhuber <christoph.weinhuber@trinity.ox.ac.uk> (University of Oxford)
//	* Dave Parker <david.parker@cs.ox.ac.uk> (University of Oxford)
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

package grpc.server;

import grpc.server.services.PrismGrpcLogger;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import prism.PrismException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC-based PRISM server.
 */
public class PrismServer
{
    /** Logger */
	private static final PrismGrpcLogger logger = PrismGrpcLogger.getLogger();

    /** Default port for the server */
    private static final int DEFAULT_PORT = 50051;

    /** gRPC server */
	private Server server;

	private void start() throws IOException
	{
		// Start server
        int port = DEFAULT_PORT;
		server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
				.addService(new PrismServerService())
				.build()
				.start();
		logger.info("Server started, listening on " + port);

		// Graceful shutdown
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
			@Override
			public void run()
			{
				logger.info("Shutting down gRPC server since JVM is shutting down");
				try {
					PrismServer.this.stop();
				} catch (InterruptedException e) {
					e.printStackTrace(System.err);
				}
				logger.info("Server shut down");
			}
		});
	}

	private void stop() throws InterruptedException
	{
		if (server != null) {
			server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
		}
	}

	private void blockUntilShutdown() throws InterruptedException
	{
        // Await termination on the main thread (gRPC library uses daemon threads)
		if (server != null) {
			server.awaitTermination();
		}
	}

    /**
     * Launch the server
     */
    public static void main(String[] args) throws PrismException
    {
        final PrismServer server = new PrismServer();
        try {
            server.start();
            server.blockUntilShutdown();
        } catch (IOException | InterruptedException e) {
            throw new PrismException("gRPC error: " + e.getMessage());
        }
    }
}
