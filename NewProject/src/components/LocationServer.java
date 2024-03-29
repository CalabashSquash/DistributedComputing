package components;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.IceStorm.TopicPrx;

import EnviroSmart.LocationManagerPrx;
import EnviroSmart.PreLocationManager;


public class LocationServer {

	public static class LocationMiddlemanWorkerI implements PreLocationManager {
		private String filename;
		private static Map<String, String[]> inOutsideToLoc = new HashMap<>();
		private static Map<String, String> locToInOutside = new HashMap<>();
		private Communicator communicator;
		private static final String TOPIC = "locSensor";
		private LocationManagerPrx locManager;

		public LocationMiddlemanWorkerI(String file) {
			super();
			this.filename = file + ".txt";
			processLocationFile(filename, inOutsideToLoc, locToInOutside);
			this.communicator = 
	        		com.zeroc.Ice.Util.initialize(null, "configfiles\\config.pub");
			com.zeroc.IceStorm.TopicManagerPrx manager = 
					com.zeroc.IceStorm.TopicManagerPrx.checkedCast(
					communicator.propertyToProxy("TopicManager.Proxy"));
			if(manager == null) {
				System.err.println("invalid proxy");
				return;
			}
			//
			// Retrieve the topic.
			//
			com.zeroc.IceStorm.TopicPrx topic;
			try {
				topic = manager.retrieve(TOPIC);
			} catch(com.zeroc.IceStorm.NoSuchTopic e) {
				try {
					topic = manager.create(TOPIC);
				} catch(com.zeroc.IceStorm.TopicExists i) {
					System.err.println("temporary failure, try again.");
					return;
				}
			}

			com.zeroc.Ice.ObjectPrx publisher = topic.getPublisher().ice_oneway();

			locManager = LocationManagerPrx.uncheckedCast(publisher);
		}

		@Override
		public void processPreLocation(String name, String loc, Current current) {
			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
			LocalDateTime now = LocalDateTime.now();
			System.out.println(dtf.format(now) + " Received AllSensors \"" + name + " " + loc + "\"");

			System.out.println(dtf.format(now) + " Sent \"" + name + " " + loc + " " + locToInOutside.get(loc) + "\"");
			locManager.processLocation(name, loc, locToInOutside.get(loc));
		}

		@Override
		public String respondToIndoorResponse(String indoorOrOutdoor, Current current) {
			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
			LocalDateTime now = LocalDateTime.now();
			System.out.println(dtf.format(now) + " Received ContextManager \"" + indoorOrOutdoor + "\"");

			String returnValue = "";

			for (String loc: inOutsideToLoc.get(indoorOrOutdoor)) {
				returnValue.concat(loc + " ");
			}

			return returnValue;
		}

		@Override
		public String getInOrOut(String locCode, Current current) {
			return locToInOutside.get(locCode);
		}

		private static void processLocationFile(String filename, Map<String, String[]> inOutsideToLoc, Map<String, String> locToInOutside) {
				
			String[] splitLine;
			// Read all lines into LinkedList
			try {
				BufferedReader reader = null;
				try {
					reader = new BufferedReader(new FileReader(filename));
				} catch (FileNotFoundException e) {
					System.out.println("Error: Cannot access " + filename + ". Check the file name, and that the file exists. Program exiting");
					System.exit(1);
				}
				// Read the first line
				String line = reader.readLine();
				splitLine = line.replaceAll(" ", "").split(":");
				if (!splitLine[0].equals("Status") || !splitLine[1].equals("LocationCoordinates")
						|| splitLine.length != 2) {
					System.err.println("Invalid location file");
					System.exit(1);
				}

				// Process the second line
				line = reader.readLine();
				if (line == null) {
					System.err.println("Invalid location file");
					System.exit(1);
				}
				splitLine = line.replaceAll(" ", "").split(":");
				if (splitLine.length != 2 
						|| (!splitLine[0].equals("Indoor"))) {
					System.err.println("Invalid location file");
					System.exit(1);
				}
				String[] locations = splitLine[1].split(",");
				inOutsideToLoc.put("indoor", locations);
				for (String loc : locations) {
					locToInOutside.put(loc, "indoor");
				}

				// Process the third line
				line = reader.readLine();
				if (line == null) {
					System.err.println("Invalid location file");
					System.exit(1);
				}
				splitLine = line.replaceAll(" ", "").split(":");
				if (splitLine.length != 2 
						|| (!splitLine[0].equals("Outdoor"))) {
					System.err.println("Invalid location file");
					System.exit(1);
				}
				locations = splitLine[1].split(",");
				inOutsideToLoc.put("outdoor", locations);
				for (String loc : locations) {
					locToInOutside.put(loc, "outdoor");
				}
				reader.close();
			} catch (IOException e) {
				System.err.println("ioexception");
				e.printStackTrace();
			}
		}

	}

	private Communicator communicator;
    private static TopicPrx incomingProxy;
    private final static String PROXY = "TopicManager.Proxy";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: LocationServer.java [filename]");
            System.exit(1);
        }

    	LocationServer server = new LocationServer();
    	server.communicator = com.zeroc.Ice.Util.initialize(args);
    	
        com.zeroc.Ice.ObjectAdapter adapter = server.communicator.createObjectAdapterWithEndpoints("LocationServer", "default -p 10023");
        com.zeroc.Ice.Object object = new LocationMiddlemanWorkerI(args[0]);

        adapter.add(object, com.zeroc.Ice.Util.stringToIdentity("LocationMiddleman"));
        adapter.activate();
        
        server.communicator.waitForShutdown();
        
		Communicator communicator = 
				com.zeroc.Ice.Util.initialize(null, "configfiles\\config.sub");
		
		//
        // Destroy communicator during JVM shutdown
        //
        Thread destroyHook = new Thread(() -> communicator.destroy());
        // Runtime.getRuntime().addShutdownHook(destroyHook);

        int status = 0;
        try
        {
            status = run(communicator, destroyHook, args[0]);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            status = 1;
        }
    }

	public static int run(Communicator communicator, Thread destroyHook, String filename) {
		com.zeroc.IceStorm.TopicManagerPrx manager = com.zeroc.IceStorm.TopicManagerPrx.checkedCast(
	            communicator.propertyToProxy(PROXY));
        if(manager == null) {
            System.err.println("invalid proxy");
            return 1;
        }
        
        com.zeroc.Ice.ObjectAdapter adapter = 
        		communicator.createObjectAdapter("EnviroSmart.PreLocationManager");
        incomingProxy = SubscriberUtil.getTopic("preLocSensor", communicator, manager);
        ObjectPrx sub = SubscriberUtil.getSubscriber(incomingProxy, new LocationMiddlemanWorkerI(filename), communicator, adapter);
        adapter.activate();
        sub = sub.ice_twoway();
       
        //
        // Replace the shutdown hook to unsubscribe during JVM shutdown
        //
        final com.zeroc.IceStorm.TopicPrx incProxy = incomingProxy;
        final com.zeroc.Ice.ObjectPrx subscriberLoc = sub;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                incProxy.unsubscribe(subscriberLoc);
            } finally {
                communicator.destroy();
            }
        }));
        Runtime.getRuntime().removeShutdownHook(destroyHook); // remove old destroy-only shutdown hook
		return 0;
    }
}
