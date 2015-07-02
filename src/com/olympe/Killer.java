package com.olympe;

import java.lang.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.ExecutionException;

public class Killer
{
        private static int cpu = 1;
        private static double real_speed = 1.0;
        public static double speed = 1.0; // seconds multiplier
        public static double overload = 5; // max load per cpu
        public static double timeout = 60; // max lifetime in seconds for a process
        public static boolean debug = false;
        public static Vector<String> ignore = new Vector<String>();
        public static String grep = "/usr/bin/php5-cgi";

        private Hashtable<Integer, Double> process = new Hashtable<Integer, Double>();

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
                                System.out.println("Error getting number of CPU");
                                ee.printStackTrace();
                        }
                }
        }

        public void check()
        {
                try
                {
                        if( debug )
                                System.out.println("Killer.check()");

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
                                System.out.println("Killing all '" + Killer.grep.substring(Killer.grep.lastIndexOf('/') + 1) + "' processes");
                                this.exec("killall -9 " + Killer.grep.substring(Killer.grep.lastIndexOf('/') + 1));
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
                                throw new InterruptedException("Load average too high : " + currentLoad + " for " + Killer.cpu + " CPU");
                }
                catch(ExecutionException ee)
                {
                        if( debug )
                        {
                                System.out.println("Error getting load average");
                                ee.printStackTrace();
                        }
                }
        }

        private void checkProcesses() throws ExecutionException
        {
                Vector<String> ps = this.exec("ps auxn |awk '$11 == \"" + Killer.grep + "\"'");

                Vector<Integer> current_process = new Vector<Integer>();
                for(String p : ps)
                {
                        String[] info = p.trim().replaceAll("\\s+", ";").split(";", 5);
                        if( debug )
                        {
                                System.out.println("Process : user="+info[0]+" pid="+info[1]+" cpu="+info[2]+" ram="+info[3]);
                        }
                        if( Killer.ignore.contains(info[0]) )
                                continue;

                        current_process.add(Integer.parseInt(info[1]));
                }

                if( debug )
                        System.out.println("Killer.checkProcesses()\n\t>> " + current_process.size() + " PHP process currently running\n\t>> " + this.process.size() + " PHP process already listed");

                for( Integer pid : current_process )
                {
                        if( !this.process.containsKey(pid) )
                                this.process.put(pid, Killer.timeout);
                }

                // update the process table
                Enumeration<Integer> pids = this.process.keys();
                while( pids.hasMoreElements() )
                {
                        Integer pid = pids.nextElement();
                        if( current_process.contains(pid) )
                        {
                                Double timeleft = this.process.get(pid) - speed;
                                if( timeleft <= 0 )
                                {
                                        try
                                        {
                                                this.exec("kill -9 " + pid);
                                                this.process.remove(pid);
                                                System.out.println("\t> Killing process " + pid);
                                        }
                                        catch(ExecutionException ee)
                                        {
                                                System.out.println("Error killing process " + pid);
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
