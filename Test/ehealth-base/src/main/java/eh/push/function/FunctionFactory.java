package eh.push.function;

import org.apache.log4j.Logger;

import java.util.HashMap;


public class FunctionFactory {
    private static final Logger logger = Logger.getLogger(FunctionFactory.class);

	private  HashMap<String, Class> functionMap = defaultMapFactory();
	private static FunctionFactory factory;
    public static FunctionFactory instance(){
    	if(factory==null)factory=new FunctionFactory();
    	return factory;
    }
	public Function createFunction(String  serviceID) {
		Class klass =  functionMap.get(serviceID);
        if (klass == null){
        	klass = (Class) functionMap.get(serviceID);
        	if(klass==null)
             throw new RuntimeException(getClass() + " was unable to find"+
             	"an functionFactory named '" + serviceID + "'.");
        }
        Function hisInstance = null;
        try {
        	hisInstance = (Function) klass.newInstance();
        } catch (Exception e) {
            logger.error(e);
        }

        return hisInstance;
   }

	private HashMap<String,Class> defaultMapFactory() {
		HashMap<String,Class> map = new HashMap<>();
        map.put("PushNumInfo", CallNumFunction.class);
        map.put("PushNoticeInfo", CallNoticeFunction.class);
        map.put("PushReportURL", PacsJNoticeFunction.class);
        return map;
	}
	
     
}
