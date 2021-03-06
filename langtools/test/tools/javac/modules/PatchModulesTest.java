/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 8160489
 * @summary tests for --patch-modules
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.file
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.ModuleBuilder ModuleTestBase
 * @run main PatchModulesTest
 */

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.BaseFileManager;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.file.Locations;

import static java.util.Arrays.asList;


public class PatchModulesTest extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        PatchModulesTest t = new PatchModulesTest();
        t.init();
        t.runTests();
    }

    private static String PS = File.pathSeparator;

    void init() throws IOException {
        tb.createDirectories("a", "b", "c", "d", "e");
        tb.writeJavaFiles(Paths.get("."), "class C { }");
    }

    @Test
    public void testSimple(Path base) throws Exception {
        test(asList("java.base=a"),
            "{java.base=[a]}");
    }

    @Test
    public void testPair(Path base) throws Exception {
        test(asList("java.base=a", "java.compiler=b"),
            "{java.base=[a], java.compiler=[b]}");
    }

    @Test
    public void testMultiple(Path base) throws Exception {
        test(asList("java.base=a:b"),
            "{java.base=[a, b]}");
    }

    @Test
    public void testLastOneWins(Path base) throws Exception {
        test(asList("java.base=a", "java.compiler=b", "java.base=c"),
            "{java.base=[c], java.compiler=[b]}");
    }

    void test(List<String> patches, String expect) throws Exception {
        JavacTool tool = (JavacTool) ToolProvider.getSystemJavaCompiler();
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            JavacFileManager fm = tool.getStandardFileManager(null, null, null);
            List<String> opts = patches.stream()
                .map(p -> "--patch-module=" + p.replace(":", PS))
                .collect(Collectors.toList());
            Iterable<? extends JavaFileObject> files = fm.getJavaFileObjects("C.java");
            JavacTask task = tool.getTask(pw, fm, null, opts, null, files);

            Field locationsField = BaseFileManager.class.getDeclaredField("locations");
            locationsField.setAccessible(true);
            Object locations = locationsField.get(fm);

            Field patchMapField = Locations.class.getDeclaredField("patchMap");
            patchMapField.setAccessible(true);
            Map<?,?> patchMap = (Map<?,?>) patchMapField.get(locations);
            String found = patchMap.toString();

            if (!found.equals(expect)) {
                tb.out.println("Expect: " + expect);
                tb.out.println("Found:  " + found);
                error("output not as expected");
            }
        }
    }
}

