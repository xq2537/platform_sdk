/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.adt.internal.refactorings.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.AdtProjectTest;
import com.android.ide.eclipse.adt.internal.refactorings.changes.AndroidDocumentChange;
import com.android.ide.eclipse.adt.internal.refactorings.changes.AndroidLayoutChange;
import com.android.ide.eclipse.adt.internal.refactorings.changes.AndroidPackageRenameChange;
import com.android.ide.eclipse.adt.internal.refactorings.changes.AndroidTypeRenameChange;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenamePackageChange;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.core.refactoring.resource.MoveResourceChange;
import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange;
import org.eclipse.text.edits.TextEdit;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings({"javadoc","restriction"})
public abstract class RefactoringTestBase extends AdtProjectTest {
    protected void checkRefactoring(Refactoring refactoring, String expected)
            throws Exception {
        RefactoringStatus status = refactoring.checkAllConditions(new NullProgressMonitor());
        assertNotNull(status);
        if (!status.isOK()) {
            assertEquals(status.toString(), expected);
            return;
        }
        assertTrue(status.toString(), status.isOK());
        Change change = refactoring.createChange(new NullProgressMonitor());
        assertNotNull(change);
        String explanation = "CHANGES:\n-------\n" + describe(change);
        if (!expected.trim().equals(explanation.trim())) { // allow trimming endlines in expected
            assertEquals(expected, explanation);
        }
    }

    protected IProject createProject(Object[] testData) throws Exception {
        String name = getName();
        IProject project = createProject(name);
        File projectDir = AdtUtils.getAbsolutePath(project).toFile();
        assertNotNull(projectDir);
        assertTrue(projectDir.getPath(), projectDir.exists());
        createTestDataDir(projectDir, testData);
        project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());

        for (int i = 0; i < testData.length; i+= 2) {
            assertTrue(testData[i].toString(), testData[i] instanceof String);
            String relative = (String) testData[i];
            IResource member = project.findMember(relative);
            assertNotNull(relative, member);
            assertTrue(member.getClass().getSimpleName(), member instanceof IFile);
        }

        return project;
    }

    public static String describe(Change change) throws Exception {
        StringBuilder sb = new StringBuilder(1000);
        describe(sb, change, 0);

        // Trim trailing space
        for (int i = sb.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(sb.charAt(i))) {
                sb.setLength(i + 1);
                break;
            }
        }

        return sb.toString();
    }

    protected static void describe(StringBuilder sb, Change change, int indent) throws Exception {
        if (change instanceof CompositeChange
                && ((CompositeChange) change).isSynthetic()) {
            // Don't display information about synthetic changes
        } else {
            String changeName = change.getName();

            if (changeName.contains("MoreUnit")) {
                // If MoreUnit plugin is installed, don't include in unit test results
                return;
            }

            // Describe this change
            indent(sb, indent);
            sb.append("* ");
            sb.append(changeName);

            IFile file = getFile(change);
            if (file != null) {
                sb.append(" - ");
                sb.append(file.getFullPath());
                sb.append('\n');
            } else {
                sb.append('\n');
            }

            if (change instanceof TextFileChange
                    || change instanceof AndroidPackageRenameChange
                    || change instanceof AndroidTypeRenameChange
                    || change instanceof AndroidLayoutChange) {
                assertNotNull(file);
                if (file != null) {
                    TextChange tc = (TextChange) change;
                    TextEdit edit = tc.getEdit();
                    byte[] bytes = ByteStreams.toByteArray(file.getContents());
                    String before = new String(bytes, Charsets.UTF_8);
                    IDocument document = new Document();
                    document.replace(0, 0, before);
                    // Make a copy: edits are sometimes destructive when run repeatedly!
                    edit.copy().apply(document);
                    String after = document.get();

                    String diff = getDiff(before, after);
                    for (String line : Splitter.on('\n').split(diff)) {
                        if (!line.trim().isEmpty()) {
                            indent(sb, indent + 1);
                            sb.append(line);
                        }
                        sb.append('\n');
                    }
                }
            } else if (change instanceof RenameCompilationUnitChange) {
                // Change name, appended above, is adequate
            } else if (change instanceof RenameResourceChange) {
                // Change name, appended above, is adequate
            } else if (change instanceof RenamePackageChange) {
                // Change name, appended above, is adequate
            } else if (change instanceof MoveResourceChange) {
                // Change name, appended above, is adequate
            } else if (change instanceof CompositeChange) {
                // Don't print details about children here; they'll be nested below
            } else {
                indent(sb, indent);
                sb.append("<UNKNOWN CHANGE TYPE " + change.getClass().getName() + ">");
            }
            sb.append('\n');
        }

        if (change instanceof CompositeChange) {
            CompositeChange composite = (CompositeChange) change;
            Change[] children = composite.getChildren();
            List<Change> sorted = Arrays.asList(children);
            // Process children in a fixed (output-alphabetical) order to ensure stable output
            Collections.sort(sorted, new Comparator<Change>() {
                @Override
                public int compare(Change change1, Change change2) {
                    try {
                        IFile file1 = getFile(change1);
                        IFile file2 = getFile(change2);
                        if (file1 != null && file2 != null) {
                            // Sort in decreasing order. This places the most interesting
                            // files first: res > src > gen
                            int fileDelta = file2.getFullPath().toOSString().compareToIgnoreCase(
                                    file1.getFullPath().toOSString());
                            if (fileDelta != 0) {
                                return fileDelta;
                            }
                        }

                        int nameDelta = change2.getName().compareTo(change1.getName());
                        if (nameDelta != 0) {
                            return nameDelta;
                        }

                        // This is pretty inefficient but ensures stable output
                        return describe(change2).compareTo(describe(change1));
                    } catch (Exception e) {
                        fail(e.getLocalizedMessage());
                        return 0;
                    }
                }

            });
            for (Change child : sorted) {
                describe(sb, child, indent + (composite.isSynthetic() ? 0 : 1));
            }
        }
    }

    @Nullable
    private static IFile getFile(@NonNull Change change) {
        if (change instanceof TextFileChange) {
            TextFileChange tfc = (TextFileChange) change;
            return tfc.getFile();
        } else if (change instanceof AndroidPackageRenameChange) {
            AndroidPackageRenameChange aprc = (AndroidPackageRenameChange) change;
            return aprc.getManifest();
        } else if (change instanceof AndroidTypeRenameChange) {
            AndroidTypeRenameChange aprc = (AndroidTypeRenameChange) change;
            return aprc.getManifest();
        } else if (change instanceof AndroidLayoutChange) {
            AndroidLayoutChange alc = (AndroidLayoutChange) change;
            return alc.getFile();
        } else if (change instanceof AndroidDocumentChange) {
            AndroidDocumentChange atmc = (AndroidDocumentChange) change;
            return atmc.getManifest();
        }

        return null;
    }

    protected static void indent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
    }

    protected void createTestDataDir(File dir, Object[] data) throws IOException {
        for (int i = 0, n = data.length; i < n; i += 2) {
            assertTrue("Must be a path: " + data[i], data[i] instanceof String);
            String relativePath = ((String) data[i]).replace('/', File.separatorChar);
            File to = new File(dir, relativePath);
            File parent = to.getParentFile();
            if (!parent.exists()) {
                boolean mkdirs = parent.mkdirs();
                assertTrue(to.getPath(), mkdirs);
            }

            Object o = data[i + 1];
            if (o instanceof String) {
                String contents = (String) o;
                Files.write(contents, to, Charsets.UTF_8);
            } else if (o instanceof byte[]) {
                Files.write((byte[]) o, to);
            } else {
                fail("Data must be a String or a byte[] for " + to);
            }
        }
    }

    // Test sources

    protected static final String SAMPLE_MANIFEST =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    package=\"com.example.refactoringtest\"\n" +
            "    android:versionCode=\"1\"\n" +
            "    android:versionName=\"1.0\" >\n" +
            "\n" +
            "    <uses-sdk\n" +
            "        android:minSdkVersion=\"8\"\n" +
            "        android:targetSdkVersion=\"17\" />\n" +
            "\n" +
            "    <application\n" +
            "        android:icon=\"@drawable/ic_launcher\"\n" +
            "        android:label=\"@string/app_name\"\n" +
            "        android:theme=\"@style/AppTheme\" >\n" +
            "        <activity\n" +
            "            android:name=\"com.example.refactoringtest.MainActivity\"\n" +
            "            android:label=\"@string/app_name\" >\n" +
            "            <intent-filter>\n" +
            "                <action android:name=\"android.intent.action.MAIN\" />\n" +
            "\n" +
            "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
            "            </intent-filter>\n" +
            "        </activity>\n" +
            "    </application>\n" +
            "\n" +
            "</manifest>";

    protected static final String SAMPLE_MAIN_ACTIVITY =
            "package com.example.refactoringtest;\n" +
            "\n" +
            "import android.os.Bundle;\n" +
            "import android.app.Activity;\n" +
            "import android.view.Menu;\n" +
            "import android.view.View;\n" +
            "\n" +
            "public class MainActivity extends Activity {\n" +
            "\n" +
            "    @Override\n" +
            "    protected void onCreate(Bundle savedInstanceState) {\n" +
            "        super.onCreate(savedInstanceState);\n" +
            "        setContentView(R.layout.activity_main);\n" +
            "        View view1 = findViewById(R.id.textView1);\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public boolean onCreateOptionsMenu(Menu menu) {\n" +
            "        // Inflate the menu; this adds items to the action bar if it is present.\n" +
            "        getMenuInflater().inflate(R.menu.activity_main, menu);\n" +
            "        return true;\n" +
            "    }\n" +
            "\n" +
            "}\n";

    protected static final String SAMPLE_LAYOUT =
            "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    tools:context=\".MainActivity\" >\n" +
            "\n" +
            "    <TextView\n" +
            "        android:id=\"@+id/textView1\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_centerVertical=\"true\"\n" +
            "        android:layout_toRightOf=\"@+id/button2\"\n" +
            "        android:text=\"@string/hello_world\" />\n" +
            "\n" +
            "    <Button\n" +
            "        android:id=\"@+id/button1\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_alignLeft=\"@+id/textView1\"\n" +
            "        android:layout_below=\"@+id/textView1\"\n" +
            "        android:layout_marginLeft=\"22dp\"\n" +
            "        android:layout_marginTop=\"24dp\"\n" +
            "        android:text=\"Button\" />\n" +
            "\n" +
            "    <Button\n" +
            "        android:id=\"@+id/button2\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_alignParentLeft=\"true\"\n" +
            "        android:layout_alignParentTop=\"true\"\n" +
            "        android:text=\"Button\" />\n" +
            "\n" +
            "</RelativeLayout>";

    protected static final String SAMPLE_LAYOUT_2 =
            "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    tools:context=\".MainActivity\" >\n" +
            "\n" +
            "\n" +
            "</RelativeLayout>";


    protected static final String SAMPLE_MENU =
            "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n" +
            "\n" +
            "    <item\n" +
            "        android:id=\"@+id/menu_settings\"\n" +
            "        android:orderInCategory=\"100\"\n" +
            "        android:showAsAction=\"never\"\n" +
            "        android:title=\"@string/menu_settings\"/>\n" +
            "\n" +
            "</menu>";

    protected static final String SAMPLE_STRINGS =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<resources>\n" +
            "\n" +
            "    <string name=\"app_name\">RefactoringTest</string>\n" +
            "    <string name=\"hello_world\">Hello world!</string>\n" +
            "    <string name=\"menu_settings\">Settings</string>\n" +
            "\n" +
            "</resources>";

    protected static final String SAMPLE_STYLES =
            "<resources>\n" +
            "\n" +
            "    <!--\n" +
            "        Base application theme, dependent on API level. This theme is replaced\n" +
            "        by AppBaseTheme from res/values-vXX/styles.xml on newer devices.\n" +
            "    -->\n" +
            "    <style name=\"AppBaseTheme\" parent=\"android:Theme.Light\">\n" +
            "        <!--\n" +
            "            Theme customizations available in newer API levels can go in\n" +
            "            res/values-vXX/styles.xml, while customizations related to\n" +
            "            backward-compatibility can go here.\n" +
            "        -->\n" +
            "    </style>\n" +
            "\n" +
            "    <!-- Application theme. -->\n" +
            "    <style name=\"AppTheme\" parent=\"AppBaseTheme\">\n" +
            "        <!-- All customizations that are NOT specific to a particular API-level can go here. -->\n" +
            "    </style>\n" +
            "\n" +
            "</resources>";

    protected static final String SAMPLE_R =
            "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n" +
            " *\n" +
            " * This class was automatically generated by the\n" +
            " * aapt tool from the resource data it found.  It\n" +
            " * should not be modified by hand.\n" +
            " */\n" +
            "\n" +
            "package com.example.refactoringtest;\n" +
            "\n" +
            "public final class R {\n" +
            "    public static final class attr {\n" +
            "    }\n" +
            "    public static final class drawable {\n" +
            "        public static final int ic_launcher=0x7f020000;\n" +
            "    }\n" +
            "    public static final class id {\n" +
            "        public static final int button1=0x7f070002;\n" +
            "        public static final int button2=0x7f070001;\n" +
            "        public static final int menu_settings=0x7f070003;\n" +
            "        public static final int textView1=0x7f070000;\n" +
            "    }\n" +
            "    public static final class layout {\n" +
            "        public static final int activity_main=0x7f030000;\n" +
            "    }\n" +
            "    public static final class menu {\n" +
            "        public static final int activity_main=0x7f060000;\n" +
            "    }\n" +
            "    public static final class string {\n" +
            "        public static final int app_name=0x7f040000;\n" +
            "        public static final int hello_world=0x7f040001;\n" +
            "        public static final int menu_settings=0x7f040002;\n" +
            "    }\n" +
            "    public static final class style {\n" +
            "        /** \n" +
            "        Base application theme, dependent on API level. This theme is replaced\n" +
            "        by AppBaseTheme from res/values-vXX/styles.xml on newer devices.\n" +
            "    \n" +
            "\n" +
            "            Theme customizations available in newer API levels can go in\n" +
            "            res/values-vXX/styles.xml, while customizations related to\n" +
            "            backward-compatibility can go here.\n" +
            "        \n" +
            "\n" +
            "        Base application theme for API 11+. This theme completely replaces\n" +
            "        AppBaseTheme from res/values/styles.xml on API 11+ devices.\n" +
            "    \n" +
            " API 11 theme customizations can go here. \n" +
            "\n" +
            "        Base application theme for API 14+. This theme completely replaces\n" +
            "        AppBaseTheme from BOTH res/values/styles.xml and\n" +
            "        res/values-v11/styles.xml on API 14+ devices.\n" +
            "    \n" +
            " API 14 theme customizations can go here. \n" +
            "         */\n" +
            "        public static final int AppBaseTheme=0x7f050000;\n" +
            "        /**  Application theme. \n" +
            " All customizations that are NOT specific to a particular API-level can go here. \n" +
            "         */\n" +
            "        public static final int AppTheme=0x7f050001;\n" +
            "    }\n" +
            "}\n";

    protected static final Object[] TEST_PROJECT = new Object[] {
        "AndroidManifest.xml",
        SAMPLE_MANIFEST,

        "src/com/example/refactoringtest/MainActivity.java",
        SAMPLE_MAIN_ACTIVITY,

        "gen/com/example/refactoringtest/R.java",
        SAMPLE_R,

        "res/drawable-xhdpi/ic_launcher.png",
        new byte[] { 0 },
        "res/drawable-hdpi/ic_launcher.png",
        new byte[] { 0 },
        "res/drawable-ldpi/ic_launcher.png",
        new byte[] { 0 },
        "res/drawable-mdpi/ic_launcher.png",
        new byte[] { 0 },

        "res/layout/activity_main.xml",
        SAMPLE_LAYOUT,

        "res/layout-land/activity_main.xml",
        SAMPLE_LAYOUT_2,

        "res/menu/activity_main.xml",
        SAMPLE_MENU,

        "res/values/strings.xml",   // file 3
        SAMPLE_STRINGS,

        "res/values/styles.xml",   // file 3
        SAMPLE_STYLES,
    };

    // More test data

    protected static final String CUSTOM_VIEW_1 =
            "package com.example.refactoringtest;\n" +
            "\n" +
            "import android.content.Context;\n" +
            "import android.widget.Button;\n" +
            "\n" +
            "public class CustomView1 extends Button {\n" +
            "    public CustomView1(Context context) {\n" +
            "        super(context);\n" +
            "    }\n" +
            "}\n";

    protected static final String CUSTOM_VIEW_2 =
            "package com.example.refactoringtest.subpackage;\n" +
            "\n" +
            "import android.content.Context;\n" +
            "import android.widget.Button;\n" +
            "\n" +
            "public class CustomView2 extends Button {\n" +
            "    public CustomView2(Context context) {\n" +
            "        super(context);\n" +
            "    }\n" +
            "}\n";

    protected static final String CUSTOM_VIEW_LAYOUT =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    android:orientation=\"vertical\"\n" +
            "    tools:ignore=\"HardcodedText\" >\n" +
            "\n" +
            "    <com.example.refactoringtest.CustomView1\n" +
            "        android:id=\"@+id/customView1\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"CustomView1\" />\n" +
            "\n" +
            "    <com.example.refactoringtest.subpackage.CustomView2\n" +
            "        android:id=\"@+id/customView2\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"CustomView2\" />\n" +
            "\n" +
            "</LinearLayout>";

    protected static final Object[] TEST_PROJECT2 = new Object[] {
        "AndroidManifest.xml",
        SAMPLE_MANIFEST,

        "src/com/example/refactoringtest/MainActivity.java",
        SAMPLE_MAIN_ACTIVITY,

        "src/com/example/refactoringtest/CustomView1.java",
        CUSTOM_VIEW_1,

        "src/com/example/refactoringtest/subpackage/CustomView2.java",
        CUSTOM_VIEW_2,

        "gen/com/example/refactoringtest/R.java",
        SAMPLE_R,

        "res/drawable-xhdpi/ic_launcher.png",
        new byte[] { 0 },
        "res/drawable-hdpi/ic_launcher.png",
        new byte[] { 0 },
        "res/drawable-ldpi/ic_launcher.png",
        new byte[] { 0 },
        "res/drawable-mdpi/ic_launcher.png",
        new byte[] { 0 },

        "res/layout/activity_main.xml",
        SAMPLE_LAYOUT,

        "res/layout-land/activity_main.xml",
        SAMPLE_LAYOUT_2,

        "res/layout/customviews.xml",
        CUSTOM_VIEW_LAYOUT,

        "res/layout-land/customviews.xml",
        CUSTOM_VIEW_LAYOUT,

        "res/menu/activity_main.xml",
        SAMPLE_MENU,

        "res/values/strings.xml",   // file 3
        SAMPLE_STRINGS,

        "res/values/styles.xml",   // file 3
        SAMPLE_STYLES,
    };
}
