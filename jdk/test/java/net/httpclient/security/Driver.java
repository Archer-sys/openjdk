/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 8087112
 * @library /lib/testlibrary/
 * @build jdk.testlibrary.SimpleSSLContext jdk.testlibrary.Utils
 * @compile ../../../../com/sun/net/httpserver/LogFilter.java
 * @compile ../../../../com/sun/net/httpserver/FileServerHandler.java
 * @compile ../ProxyServer.java
 * @build Security
 *
 * @run driver/timeout=60 Driver
 */

/**
 * driver required for allocating free portnumbers and putting this number
 * into security policy file used in some tests.
 *
 * The tests are in Security.java and port number supplied in -Dport.number
 * and -Dport.number1 for tests that require a second free port
 */
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.io.*;
import java.net.*;

import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.Utils;

/**
 * Driver for tests
 */
public class Driver {

    public static void main(String[] args) throws Throwable {
        System.out.println("Starting Driver");
        runtest("1.policy", "1");
        runtest("10.policy", "10");
        runtest("11.policy", "11");
        runtest("12.policy", "12");
        System.out.println("DONE");
    }

    static class Logger extends Thread {
        private final OutputStream ps;
        private final InputStream stdout;

        Logger(String cmdLine, Process p, String dir) throws IOException {
            super();
            setDaemon(true);
            cmdLine = "Command line = [" + cmdLine + "]";
            stdout = p.getInputStream();
            File f = File.createTempFile("debug", ".txt", new File(dir));
            ps = new FileOutputStream(f);
            ps.write(cmdLine.getBytes());
            ps.flush();
        }

        public void run() {
            try {
                byte[] buf = new byte[128];
                int c;
                while ((c = stdout.read(buf)) != -1) {
                    ps.write(buf, 0, c);
                    ps.flush();
                }
                ps.close();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public static void runtest(String policy, String testnum) throws Throwable {

        String testJdk = System.getProperty("test.jdk", "?");
        String testSrc = System.getProperty("test.src", "?");
        String testClassPath = System.getProperty("test.class.path", "?");
        String testClasses = System.getProperty("test.classes", "?");
        String sep = System.getProperty("file.separator", "?");
        String javaCmd = testJdk + sep + "bin" + sep + "java";
        int retval = 10; // 10 is special exit code denoting a bind error
                         // in which case, we retry
        while (retval == 10) {
            List<String> cmd = new ArrayList<>();
            cmd.add(javaCmd);
            cmd.add("-Dtest.jdk=" + testJdk);
            cmd.add("-Dtest.src=" + testSrc);
            cmd.add("-Dtest.classes=" + testClasses);
            cmd.add("-Djava.security.manager");
            cmd.add("-Djava.security.policy=" + testSrc + sep + policy);
            cmd.add("-Dport.number=" + Integer.toString(Utils.getFreePort()));
            cmd.add("-Dport.number1=" + Integer.toString(Utils.getFreePort()));
            cmd.add("-cp");
            cmd.add(testClassPath);
            cmd.add("Security");
            cmd.add(testnum);

            ProcessBuilder processBuilder = new ProcessBuilder(cmd)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectErrorStream(true);

            String cmdLine = cmd.stream().collect(Collectors.joining(" "));
            Process child = processBuilder.start();
            Logger log = new Logger(cmdLine, child, testClasses);
            log.start();
            retval = child.waitFor();
            System.out.println("retval = " + retval);
        }
        if (retval != 0) {
            Thread.sleep(2000);
            throw new RuntimeException("Non zero return value");
        }
    }
}
