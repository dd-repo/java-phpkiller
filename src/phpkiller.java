
public class phpkiller
{
	public static void main(String[] args)
	{
		try
		{
			for( int i = 0; i < args.length; i += 2 )
			{
				if( args[i].equals("-h") || args[i].equals("--help") )
				{
					String usage = "Usage: java -jar phpkiller.jar [OPTIONS]\n" +
							"Options:\n" +
							"\t-h,\t--help\t\tShow this help message\n" +
							"\t-o,\t--overload\tMaximum load average per CPU allowed\n" +
							"\t-t,\t--timeout\tMaximum process lifetime\n" +
							"\t-s,\t--speed\t\tCheck time interval multiplicator (1 = every second, <1 = less than a second, >1 = more than a second)\n" +
							"\t-d,\t--debug\t\tEnable debug output [1 or 0]\n" +
							"\t-i,\t--ignore\t\tDo never kill the process of those UNIX UID (coma-separated list of UIDs)\n" +
							"\t-p,\t--process\t\tName of the php process to monitor (default /usr/bin/php5-cgi)";
					System.out.println(usage);
					System.exit(0);
				}

				if( i+1 >= args.length )
					break;

				if( args[i].equals("-d") || args[i].equals("--debug") )
					Killer.debug = !args[i+1].equals('0');
				else if( args[i].equals("-t") || args[i].equals("--timeout") )
					Killer.timeout = Double.parseDouble(args[i+1]);
				else if( args[i].equals("-o") || args[i].equals("--overload") )
					Killer.overload = Double.parseDouble(args[i+1]);
				else if( args[i].equals("-s") || args[i].equals("--speed") )
					Killer.speed = Double.parseDouble(args[i+1]);
				else if( args[i].equals("-i") || args[i].equals("--ignore") )
				{
					String[] ignore = args[i+1].split(",");
					for( String s : ignore )
						Killer.ignore.add(s);
				}
				else if( args[i].equals("-p") || args[i].equals("--proces") )
					Killer.grep = args[i+1];
			}

			System.out.println("Starting phpkiller with the following values:\n\tOverload: " + Killer.overload + "\n\tTimeout: " + Killer.timeout + "\n\tSpeed: " + Killer.speed + "\n\tIgnore: " + Killer.ignore.toString() + "\n\tDebug: " + Killer.debug);
			Killer killer = new Killer();

			Double sleep = Killer.speed * 1000;
			while(true)
			{
				Thread.sleep(sleep.intValue());
				killer.check();

				// update the speed (in case of)
				sleep = Killer.speed * 1000;
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}