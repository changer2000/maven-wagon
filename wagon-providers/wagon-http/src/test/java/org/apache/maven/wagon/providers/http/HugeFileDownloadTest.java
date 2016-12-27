package org.apache.maven.wagon.providers.http;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import junit.framework.Assert;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.observers.Debug;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.PlexusTestCase;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author Olivier Lamy
 */
public class HugeFileDownloadTest
    extends PlexusTestCase
{

    private static long HUGE_FILE_SIZE =
        Integer.valueOf( Integer.MAX_VALUE ).longValue() + Integer.valueOf( Integer.MAX_VALUE ).longValue();

    private Server server;

    public void testDownloadHugeFileWithContentLength()
        throws Exception
    {
        File hugeFile = new File( getBasedir(), "target/hugefile.txt" );
        if ( !hugeFile.exists() || hugeFile.length() < HUGE_FILE_SIZE )
        {
            makeHugeFile( hugeFile );
        }

        server = new Server( 0 );

        ServletContextHandler root = new ServletContextHandler( ServletContextHandler.SESSIONS );
        root.setResourceBase( new File( getBasedir(), "target" ).getAbsolutePath() );
        ServletHolder servletHolder = new ServletHolder( new DefaultServlet() );
        root.addServlet( servletHolder, "/*" );
        server.setHandler( root );

        server.start();

        File dest = null;
        try
        {
            Wagon wagon = getWagon();
            wagon.connect( new Repository( "id", "http://localhost:" + server.getConnectors()[0].getLocalPort() ) );

            dest = File.createTempFile( "huge", "txt" );

            wagon.get( "hugefile.txt", dest );

            Assert.assertTrue( dest.length() >= HUGE_FILE_SIZE );

            wagon.disconnect();
        }
        finally
        {
            server.start();
            dest.delete();
            hugeFile.delete();
        }


    }

    public void testDownloadHugeFileWithChunked()
        throws Exception
    {
        final File hugeFile = new File( getBasedir(), "target/hugefile.txt" );
        if ( !hugeFile.exists() || hugeFile.length() < HUGE_FILE_SIZE )
        {
            makeHugeFile( hugeFile );
        }

        server = new Server( 0 );

        ServletContextHandler root = new ServletContextHandler( ServletContextHandler.SESSIONS );
        root.setResourceBase( new File( getBasedir(), "target" ).getAbsolutePath() );
        ServletHolder servletHolder = new ServletHolder( new HttpServlet()
        {
            @Override
            protected void doGet( HttpServletRequest req, HttpServletResponse resp )
                throws ServletException, IOException
            {
                FileInputStream fis = new FileInputStream( hugeFile );

                byte[] buffer = new byte[8192];
                int len = 0;
                while ( ( len = fis.read( buffer ) ) != -1 )
                {
                    resp.getOutputStream().write( buffer, 0, len );
                }
                fis.close();
            }
        } );
        root.addServlet( servletHolder, "/*" );
        server.setHandler( root );

        server.start();

        File dest = null;
        try
        {
            Wagon wagon = getWagon();
            wagon.connect( new Repository( "id", "http://localhost:" + server.getConnectors()[0].getLocalPort() ) );

            dest = File.createTempFile( "huge", "txt" );

            wagon.get( "hugefile.txt", dest );

            Assert.assertTrue( dest.length() >= HUGE_FILE_SIZE );

            wagon.disconnect();
        }
        finally
        {
            server.start();
            dest.delete();
            hugeFile.delete();
        }


    }


    protected Wagon getWagon()
        throws Exception
    {
        Wagon wagon = (Wagon) lookup( Wagon.ROLE, "http" );

        Debug debug = new Debug();

        wagon.addSessionListener( debug );

        return wagon;
    }

    private void makeHugeFile( File hugeFile )
        throws Exception
    {
        RandomAccessFile ra = new RandomAccessFile( hugeFile.getPath(), "rw" );
        ra.setLength( HUGE_FILE_SIZE + 1 );
        ra.seek( HUGE_FILE_SIZE );
        ra.write( 1 );

    }

}
