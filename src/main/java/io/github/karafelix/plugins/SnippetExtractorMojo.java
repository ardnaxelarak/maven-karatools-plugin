package io.github.karafelix.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;

/**
 * Goal which extracts snippets of code.
 *
 * @goal extract
 * @requiresProject true
 */
public class SnippetExtractorMojo extends AbstractMojo
{
    /**
     * Directory in which to search for snippets.
     * @parameter
     *      alias="src"
     *      default-value="${project.build.sourceDirectory}"
     */
    private File srcDirectory;

    /**
     * Directory in which to place generated snippets.
     * @parameter
     *      alias="dest"
     *      default-value="${project.build.directory}"
     */
    private File destDirectory;

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        /*
        File dest = destDirectory;

        if (!f.exists())
        {
            f.mkdirs();
        }
        */

        File src = srcDirectory;
        LinkedList<File> fileStack = new LinkedList<File>();
        fileStack.addFirst(src);
        File cur;
        while (!fileStack.isEmpty())
        {
            cur = fileStack.pollFirst();
            for (File file : cur.listFiles())
            {
                if (file.isDirectory())
                    fileStack.add(file);
                else
                    getLog().info(file.toString());
            }
        }

        /*
        File touch = new File( f, "touch.txt" );

        FileWriter w = null;
        try
        {
            w = new FileWriter( touch );

            w.write( "touch.txt" );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error creating file " + touch, e );
        }
        finally
        {
            if ( w != null )
            {
                try
                {
                    w.close();
                }
                catch ( IOException e )
                {
                    // ignore
                }
            }
        }
        */
    }
}
