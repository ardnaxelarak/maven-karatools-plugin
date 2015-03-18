package io.github.karafelix.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

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
     *      expression="${extract.source}"
     *      default-value="${project.build.sourceDirectory}"
     */
    private File srcDirectory;

    /**
     * Directory in which to place generated snippets.
     * @parameter
     *      alias="dest"
     *      expression="${extract.destination}"
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
        LinkedList<File> dirList = new LinkedList<File>();
        LinkedList<File> fileList = new LinkedList<File>();
        dirList.addFirst(src);
        File cur;

        while (!dirList.isEmpty())
        {
            cur = dirList.pollFirst();
            for (File file : cur.listFiles())
            {
                if (file.isDirectory())
                    dirList.add(file);
                else
                {
                    getLog().debug("Adding " + file.toString());
                    fileList.addLast(file);
                }
            }
        }

        Pattern matchPat = Pattern.compile("<<<\\s*((?:BEGIN)|(?:END)):\\s*(.*?)(?:\\s*\\{(-?[0-9]+)\\})?\\s*>>>");
        Scanner sc;
        boolean ignore = false;
        int priority;
        String line;

        for (File file : fileList)
        {
            try
            {
                sc = new Scanner(file);
                while (sc.hasNextLine())
                {
                    ignore = false;
                    while (sc.findInLine(matchPat) != null)
                    {
                        ignore = true;
                        MatchResult mr = sc.match();

                        if (mr.group(3) == null)
                            priority = 0;
                        else
                            priority = Integer.parseInt(mr.group(3));

                        if ("BEGIN".equals(mr.group(1)))
                        {
                            getLog().info(String.format("Starting capture to %s, priority %d", mr.group(2), priority));
                        }
                        else if ("END".equals(mr.group(1)))
                        {
                            getLog().info(String.format("Ending capture to %s, priority %d", mr.group(2), priority));
                        }
                        else
                        {
                            getLog().warn("Unknown token \"" + mr.group(1) + "\"");
                        }
                    }
                    line = sc.nextLine();
                }
                sc.close();
            }
            catch (FileNotFoundException e)
            {
                getLog().warn(e);
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
