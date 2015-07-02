package com.olympe;

import java.lang.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.ExecutionException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class Killer
{
        private static int cpu = 1;
        private static double real_speed = 1.0;
        public static double speed = 1.0; // seconds multiplier
        public static double overload = 5.0; // max load per cpu
        public static double timeout = 60; // max lifetime in seconds for a process
        public static boolean debug = false;
        public static Vector<String> ignore = new Vector<String>();
        public static String grep = "/usr/bin/php5-cgi";

        private Hashtable<Integer, Double> process = new Hashtable<Integer, Double>();
        private static final DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        public Killer()
        {
                Killer.real_speed = Killer.speed;

                try
                {
                        Killer.cpu = Integer.parseInt(this.exec("cat /proc/cpuinfo |grep ^processor |wc -l").firstElement());
                }
                catch(ExecutionException ee)
                {
                        if( debug )
                        {
                                System.out.println(">>> Error getting number of CPU");
                                ee.printStackTrace();
                        }
                }
        }

        public void check()
        {
                try
                {
                        try
                        {
                                checkLoadAverage();
                                Killer.speed = Killer.real_speed;
                        }
                        catch(InterruptedException ie)
                        {
                                if( debug )
                                        System.out.println(ie.getMessage() + "\n\t> Check interval is reduced to " + Killer.speed);

                                Killer.speed = Killer.real_speed / 2;
                                try
                                {
                                        this.exec("ps axn |awk '$5 == \"" + Killer.grep + "\" {print $1}' |xargs -r kill -9");
                                        System.out.println(dateFormat.format(new Date()) + "\t> Killed all '" + Killer.grep + "' processes");
                                }
                                catch(ExecutionException ee)
                                {
                                        System.out.println(dateFormat.format(new Date()) + "\t>>> Error killing all " + Killer.grep.substring(Killer.grep.lastIndexOf('/') + 1) + " processes");
                                        if( debug )
                                        {
                                                ee.printStackTrace();
                                        }
                                }

                        }

                        checkProcesses();
                }
                catch(ExecutionException ee)
                {
                        ee.printStackTrace();
                }
        }

        private void checkLoadAverage() throws InterruptedException
        {
                try
                {
                        String loadavg = this.exec("w").firstElement();
                        String tmpLoad = loadavg.substring(loadavg.indexOf("load average:")+13).trim().split("\\s+")[0];
                        Double currentLoad = Double.parseDouble(tmpLoad.substring(0,tmpLoad.length()-1).replaceFirst(",", "."));

                        if( currentLoad > Killer.overload * Killer.cpu )
                                throw new InterruptedException(dateFormat.format(new Date()) + "\t> Load average too high : " + currentLoad + " for " + Killer.cpu + " CPU");
                }
                catch(ExecutionException ee)
                {
                        System.out.println(dateFormat.format(new Date()) + "\t>>> Error getting load average");
                        if( debug )
                        {
                                ee.printStackTrace();
                        }
                }
        }

        private void checkProcesses() throws ExecutionException
        {
                Vector<String> ps = this.exec("ps auxn |awk '$11 == \"" + Killer.grep + "\"'");

                // Vector<Integer> current_process = new Vector<Integer>();
                Hashtable<Integer, Integer> current_process = new Hashtable<Integer, Integer>();
                for(String p : ps)
                {
                        String[] info = p.trim().replaceAll("\\s+", ";").split(";", 5);
                        if( debug )
                        {
                                System.out.println("Process : user="+info[0]+" pid="+info[1]+" cpu="+info[2]+" ram="+info[3]);
                        }
                        if( Killer.ignore.contains(info[0]) )
                                continue;

                        current_process.put(Integer.parseInt(info[1]), Integer.parseInt(info[0]));
                }

                if( debug )
                        System.out.println("\t>> " + current_process.size() + " PHP process currently running\n\t>> " + this.process.size() + " PHP process already listed");

                Enumeration<Integer> current_pids = current_process.keys();
                while( current_pids.hasMoreElements() )
                {
                        Integer pid = current_pids.nextElement();
                        if( !this.process.containsKey(pid) )
                                this.process.put(pid, Killer.timeout);
                }

                // update the process table
                Enumeration<Integer> pids = this.process.keys();
                while( pids.hasMoreElements() )
                {
                        Integer pid = pids.nextElement();
                        if( current_process.containsKey(pid) )
                        {
                                Double timeleft = this.process.get(pid) - speed;
                                if( timeleft <= 0 )
                                {
                                        try
                                        {
                                                this.exec("kill -9 " + pid);
                                                this.process.remove(pid);
                                                System.out.println(dateFormat.format(new Date()) + "\t> Killed process " + pid + " from user " + current_process.get(pid));
                                        }
                                        catch(ExecutionException ee)
                                        {
                                                System.out.println(dateFormat.format(new Date()) + "\t>>> Error killing process " + pid + " from user " + current_process.get(pid));
                                                if( debug )
                                                {
                                                        ee.printStackTrace();
                                                }
                                        }
                                }
                                else
                                {
                                        this.process.put(pid, timeleft);

                                        if( debug )
                                                System.out.println("\t> " + timeleft + "sec left for process " + pid);
                                }
                        }
                        else
                                this.process.remove(pid);
                }
        }

        private Vector<String> exec(String command) throws ExecutionException
        {
                try
                {
                        //Runtime r = Runtime.getRuntime();
                        //Process p = r.exec(command);
                        String[] commands = {"/bin/sh", "-c", command};
                        Process p = new ProcessBuilder(commands).start();

                        int status = p.waitFor();

                        BufferedReader reader = null;

                        if( status == 0 )
                                reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        else
                                reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));

                        Vector<String> result = new Vector<String>();
                        String line = reader.readLine();
                        while( line != null )
                        {
                                result.add(line);
                                line = reader.readLine();
                        }

                        if( status == 0 )
                                return result;
                        else
                        {
                                String error = "Command '" + command + "' returned error : " + status + " ";
                                for(String l : result)
                                        error += l + "\r\n";

                                throw new ExecutionException(error, null);
                        }
                }
                catch(IOException ioe)
                {
                        throw new ExecutionException("Command execution failed", ioe);
                }
                catch(InterruptedException ie)
                {
                        throw new ExecutionException("Command execution interrupted", ie);
                }
        }
}
