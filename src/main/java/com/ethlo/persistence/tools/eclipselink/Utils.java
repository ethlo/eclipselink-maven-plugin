package com.ethlo.persistence.tools.eclipselink;

/*-
 * #%L
 * Eclipselink Maven Plugin
 * %%
 * Copyright (C) 2013 - 2021 Morten Haraldsen (ethlo)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;

public class Utils
{
    private Utils()
    {

    }
    
    public static File[] getClassPathFiles(MavenProject project)
    {
        final List<File> files = new ArrayList<>();
        List<?> classpathElements;
        try
        {
            classpathElements = project.getTestClasspathElements();
        }
        catch (DependencyResolutionRequiredException e)
        {
            throw new RuntimeException(e.getMessage(), e);
        }

        for (final Object o : classpathElements)
        {
            if (o != null)
            {
                final File file = new File(o.toString());
                if (file.canRead())
                {
                    files.add(file);
                }
            }
        }
        return files.toArray(new File[0]);
    }
}
