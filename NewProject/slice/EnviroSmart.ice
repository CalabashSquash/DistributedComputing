module EnviroSmart
{
	sequence<string> stringList;
    interface TemperatureManager
    {
        void processTemperature(string name, string temp);
    }
    
    struct Location
    {
    	string name;
		string locCode;
		stringList info;
		stringList services;
	}
    
    interface UiInteractor
    {
    	Location getInfoGivenLoc(string loc);
    	string getInfoCurrentLoc(string name);
    	void logIn(string name);
    }
    
    interface APManager
    {
    	void processAQI(string name, string aqi);
    }
    
    interface LocationManager
    {
    	void processLocation(string name, string loc);
    }
    
    interface AlarmManager
    {
    	void processAlarmMessage(string alarm);
    }
    
    interface PreLocationManager
    {
    	void processPreLocation(string name, string loc);
    	string respondToIndoorResponse(string indoorOrOutdoor);
    }
    
    interface PreferenceManager
    {
    	string processPreferenceRequest(string name, string req);
    	void shutdown();
    }
}