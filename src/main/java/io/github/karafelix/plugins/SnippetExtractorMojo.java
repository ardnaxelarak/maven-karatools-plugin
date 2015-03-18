package io.github.karafelix.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.TreeMap;
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

    /**
     * Number of spaces to replace tabs with.
     * @parameter
     *      experssion="${karatools.tabWidth}"
     *      default-value=4
     */
    private int tabWidth;

    /**
     * Remove maximal leading spaces?
     * @parameter
     *      experssion="${extract.trimSpaces}"
     *      default-value=true
     */
    private boolean trimSpaces;

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        File src = srcDirectory;
        LinkedList<File> dirList = new LinkedList<File>();
        LinkedList<File> fileList = new LinkedList<File>();
        dirList.addFirst(src);
        File cur;

        /* get list of files from directories */
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

        /* read in files */
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
        private final class PriorityFile
        {
            private String filename;
            private int priority;

            public PriorityFile(String filename, int priority)
            {
                this.filename = filename;
                this.priority = priority;
            }

            public PriorityFile(String filename)
            {
                this(filename, 0);
            }

            public String getFilename()
            {
                return filename;
            }

            public int getPriority()
            {
                return priority;
            }

            public boolean equals(Object o)
            {
                if (!(o instanceof PriorityFile))
                    return false;
                PriorityFile pf = (PriorityFile)o;
                if (filename == null)
                    return pf.filename == null;
                else
                    return filename.equals(pf.filename);
            }
        }

        private HashMap<String, TreeMap<Integer, LinkedList<String>>> files;
        private LinkedList<PriorityFile> curfiles;
        private HashMap<String, Integer> trimlens;
        private final Pattern tabPat = Pattern.compile("\t");
        private final String tabStr;

        public OutputTracker()
        {
            files = new HashMap<String, TreeMap<Integer, LinkedList<String>>>();
            trimlens = new HashMap<String, Integer>();
            curfiles = new LinkedList<PriorityFile>();
            char[] spaces = new char[tabWidth];
            Arrays.fill(spaces, ' ');
            tabStr = new String(spaces);
        }

        private String processLine(String line)
        {
            return tabPat.matcher(line).replaceAll(tabStr);
        }

        private int countSpaces(String line)
        {
            String trim = line.trim();
            if (!"".equals(trim))
                return line.indexOf(trim);
            else
                return Integer.MAX_VALUE;
        }

        private void updateCount(int spaces, String file)
        {
            if (trimlens.get(file) > spaces)
            {
                trimlens.put(file, spaces);
                getLog().debug(String.format("Updating trim width of %s to %d", file, spaces));
            }
        }

        public void addLine(String line)
        {
            String file;
            int priority;
            String newline = processLine(line);
            int count = countSpaces(newline);
            for (PriorityFile pf : curfiles)
            {
                file = pf.getFilename();
                priority = pf.getPriority();
                updateCount(count, file);
                files.get(file).get(priority).add(newline);
            }
        }

        public void startCapture(String filename, int priority)
        {
            PriorityFile pf = new PriorityFile(filename, priority);
            if (curfiles.contains(pf))
            {
                getLog().warn(String.format("already capturing to %s", filename));
            }
            else
            {
                getLog().debug(String.format("starting capture to %s, priority %d", filename, priority));
                curfiles.add(pf);

                if (!trimlens.containsKey(filename))
                    trimlens.put(filename, Integer.MAX_VALUE);

                if (!files.containsKey(filename))
                    files.put(filename, new TreeMap<Integer, LinkedList<String>>());
                if (!files.get(filename).containsKey(priority))
                    files.get(filename).put(priority, new LinkedList<String>());
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
            PriorityFile pf = new PriorityFile(filename);
            if (curfiles.remove(pf))
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
                for (PriorityFile pf : curfiles)
                    getLog().warn(String.format("unclosed capture: %s", pf.getFilename()));
                curfiles.clear();
            }
        }

        private String trimLine(String line, int trim)
        {
            if (line.length() <= trim)
                return "";
            else
                return line.substring(trim);
        }

        public void writeOutputs(File dir)
        {
            TreeMap<Integer, LinkedList<String>> cur;
            int trimwidth = 0;

            for (String filename : files.keySet())
            {
                cur = files.get(filename);
                File file = new File(dir, filename);
                try (PrintWriter pw = new PrintWriter(file))
                {
                    getLog().debug(String.format("writing output to %s", filename));

                    if (trimSpaces)
                        trimwidth = trimlens.get(filename);

                    for (int priority : cur.navigableKeySet())
                        for (String line : cur.get(priority))
                            pw.println(trimLine(line, trimwidth));
                }
                catch (IOException e)
                {
                    getLog().error(e);
                }
            }
        }
    }
}
