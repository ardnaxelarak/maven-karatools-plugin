package io.github.karafelix.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
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
        OutputTracker tracker = new OutputTracker();

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

                        tracker.processToken(mr);
                    }

                    line = sc.nextLine();

                    if (!ignore)
                        tracker.addLine(line);
                }
                sc.close();
                tracker.reset();
            }
            catch (FileNotFoundException e)
            {
                getLog().warn(e);
            }
        }

        File dest = destDirectory;

        if (!dest.exists())
        {
            getLog().debug(String.format("Creating directory %s", dest));
            dest.mkdirs();
        }

        tracker.writeOutputs(dest);
    }

    private class OutputTracker
    {
        private HashMap<String, LinkedList<String>> files;
        private LinkedList<String> curfiles;

        public OutputTracker()
        {
            files = new HashMap<String, LinkedList<String>>();
            curfiles = new LinkedList<String>();
        }

        public void addLine(String line)
        {
            for (String file : curfiles)
            {
                files.get(file).add(line);
            }
        }

        public void startCapture(String filename, int priority)
        {
            if (curfiles.contains(filename))
            {
                getLog().warn(String.format("already capturing to %s", filename));
            }
            else
            {
                getLog().debug(String.format("starting capture to %s, priority %d", filename, priority));
                curfiles.add(filename);
                if (!files.containsKey(filename))
                    files.put(filename, new LinkedList<String>());
            }
        }

        public void startCapture(String filename, String priority)
        {
            if (priority == null)
                startCapture(filename, 0);
            else
                startCapture(filename, Integer.parseInt(priority));
        }

        public void endCapture(String filename)
        {
            if (curfiles.remove(filename))
            {
                getLog().debug(String.format("ended capture to %s", filename));
            }
            else
            {
                getLog().warn(String.format("invalid end token: not capturing to %s", filename));
            }
        }

        public void endCapture(String filename, String priority)
        {
            if (priority == null)
                endCapture(filename);
            else
            {
                endCapture(filename);
                getLog().warn("extraneous priority on END token");
            }
        }

        public void processToken(MatchResult mr)
        {
            if ("BEGIN".equals(mr.group(1)))
            {
                startCapture(mr.group(2), mr.group(3));
            }
            else if ("END".equals(mr.group(1)))
            {
                endCapture(mr.group(2), mr.group(3));
            }
            else
            {
                getLog().warn("Unknown token \"" + mr.group(1) + "\"");
            }
        }

        public void reset()
        {
            if (!curfiles.isEmpty())
            {
                for (String file : curfiles)
                    getLog().warn(String.format("unclosed capture: %s", file));
                curfiles.clear();
            }
        }

        public void writeOutputs(File dir)
        {
            for (String filename : files.keySet())
            {
                File file = new File(dir, filename);
                try (PrintWriter pw = new PrintWriter(file))
                {
                    getLog().debug(String.format("writing output to %s", filename));
                    for (String line : files.get(filename))
                        pw.println(line);
                }
                catch (IOException e)
                {
                    getLog().error(e);
                }
            }
        }
    }
}
