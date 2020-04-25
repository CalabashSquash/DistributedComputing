package components;

import java.util.HashMap;
import java.util.Map;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.IceStorm.AlreadySubscribed;
import com.zeroc.IceStorm.BadQoS;
import com.zeroc.IceStorm.InvalidSubscriber;
import com.zeroc.IceStorm.TopicPrx;

import EnviroSmart.APManager;
import EnviroSmart.AlarmManager;
import EnviroSmart.LocationManager;
import EnviroSmart.PreLocationManagerPrx;
import EnviroSmart.PreferenceManagerPrx;
import EnviroSmart.TemperatureManager;
import javafx.util.Pair;

public class ContextManager {
	
	public static class InfoProvider 

    public static class TempManagerI implements TemperatureManager {
    	
		@Override
		public void processTemperature(String name, String temp, Current current) {
			System.out.println("TEMP: " + temp);
			apoThresholds.replace("HI", "YO");
		}
    }

    public static class APManagerI implements APManager {
		@Override
		public void processAQI(String name, String aqi, Current current) {
			System.out.println("AQI: " + aqi);
			System.out.println("HELLO2: " + getLocManager().getKey().respondToIndoorResponse("C"));
			if (Math.random() * 10 > 7){
				apoThresholds.replace("HI", "There");
			}
		}
    }

    public static class LocationManagerI implements LocationManager {
		@Override
		public void processLocation(String name, String loc, Current current) {
			System.out.println("LOC: " + loc + " " + name);
			System.out.println("Checking apoThresholds: " + apoThresholds.get("HI"));
		}
    }

    public static class AlarmManagerI implements AlarmManager {

		@Override
		public void processAlarmMessage(String weather, Current current) {
			System.out.println("weather status:" + weather);
			apoThresholds.put("HI", "There");
		}
    }
    
    public static Pair<PreLocationManagerPrx, Communicator> getLocManager() {
    	Communicator communicator = com.zeroc.Ice.Util.initialize();
  
        
        com.zeroc.Ice.ObjectPrx base = communicator.stringToProxy("LocationMiddleman:default -p 10023");
        PreLocationManagerPrx locManager = PreLocationManagerPrx.checkedCast(base);
        
        if(locManager == null)
        {
            throw new Error("Invalid proxy when creating PreLocationManager in ContextManager");
        }
		return new Pair<>(locManager, communicator);
    }
    
    public static Pair<PreferenceManagerPrx, Communicator> getPrefRepo() {
    	Communicator communicator = com.zeroc.Ice.Util.initialize();
  
        
        com.zeroc.Ice.ObjectPrx base = communicator.stringToProxy("preferenceRepo:default -p 10034");
        PreferenceManagerPrx locManager = PreferenceManagerPrx.checkedCast(base);
        
        if(locManager == null)
        {
            throw new Error("Invalid proxy when creating PreferenceManager in ContextManager");
        }
		return new Pair<>(locManager, communicator);
    }

    private final static String PROXY = "TopicManager.Proxy";
    private static TopicPrx tempProxy;
    private static TopicPrx locProxy;
    private static TopicPrx aqiProxy;
    private static TopicPrx weatherProxy;
    private static Map<String, String> tempThresholds;
    private static Map<String, String> apoThresholds;
    private static Map<String, String> userLocations; // Store users locs at any given time
	
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: ContextManager.java [filename]");
            System.exit(1);
        }
        tempThresholds = new HashMap<>();
        apoThresholds = new HashMap<>();
        userLocations = new HashMap<>();

		Communicator communicator = 
				com.zeroc.Ice.Util.initialize(args, "configfiles\\config.sub");
		
		//
        // Destroy communicator during JVM shutdown
        //
        Thread destroyHook = new Thread(() -> communicator.destroy());
        // Runtime.getRuntime().addShutdownHook(destroyHook);

        int status = 0;
        try
        {
            status = run(communicator, destroyHook);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            status = 1;
        }
    }

	public static int run(Communicator communicator, Thread destroyHook) {
		com.zeroc.IceStorm.TopicManagerPrx manager = com.zeroc.IceStorm.TopicManagerPrx.checkedCast(
	            communicator.propertyToProxy(PROXY));
        if(manager == null) {
            System.err.println("invalid proxy");
            return 1;
        }
        
        com.zeroc.Ice.ObjectAdapter adapter = 
        		communicator.createObjectAdapter("EnviroSmart.APManager");
        tempProxy = SubscriberUtil.getTopic("tempSensor", communicator, manager);
        locProxy = SubscriberUtil.getTopic("locSensor", communicator, manager);
        aqiProxy = SubscriberUtil.getTopic("apSensor", communicator, manager);
        weatherProxy = SubscriberUtil.getTopic("weatherSensor", communicator, manager);

        ObjectPrx tempSub = SubscriberUtil.getSubscriber(tempProxy, new TempManagerI(), communicator, adapter);
        ObjectPrx locSub = SubscriberUtil.getSubscriber(locProxy, new LocationManagerI(), communicator, adapter);
        ObjectPrx aqiSub = SubscriberUtil.getSubscriber(aqiProxy, new APManagerI(), communicator, adapter);
        ObjectPrx weatherSub = SubscriberUtil.getSubscriber(weatherProxy, new AlarmManagerI(), communicator, adapter);

        adapter.activate();
        tempSub = tempSub.ice_oneway();
        locSub = locSub.ice_twoway();
        aqiSub = aqiSub.ice_oneway();
        weatherSub = weatherSub.ice_oneway();
       
        //
        // Replace the shutdown hook to unsubscribe during JVM shutdown
        //
        final com.zeroc.IceStorm.TopicPrx topicTemp = tempProxy;
        final com.zeroc.Ice.ObjectPrx subscriberTemp = tempSub;
        final com.zeroc.IceStorm.TopicPrx topicLoc = locProxy;
        final com.zeroc.Ice.ObjectPrx subscriberLoc = locSub;
        final com.zeroc.IceStorm.TopicPrx topicAqi = aqiProxy;
        final com.zeroc.Ice.ObjectPrx subscriberAqi = aqiSub;
        final com.zeroc.IceStorm.TopicPrx topicWeather = weatherProxy;
        final com.zeroc.Ice.ObjectPrx subscriberWeather = weatherSub;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // topicTemp.unsubscribe(subscriberTemp);
                topicLoc.unsubscribe(subscriberLoc);
                topicAqi.unsubscribe(subscriberAqi);
                topicTemp.unsubscribe(subscriberTemp);
                topicWeather.unsubscribe(subscriberWeather);
            } finally {
                communicator.destroy();
            }
        }));
        Runtime.getRuntime().removeShutdownHook(destroyHook); // remove old destroy-only shutdown hook
		return 0;
	}
}
