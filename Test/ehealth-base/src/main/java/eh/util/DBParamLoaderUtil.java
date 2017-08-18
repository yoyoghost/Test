package eh.util;

import eh.utils.ValidateUtil;
import eh.utils.params.support.DBParamLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Arrays;

public class DBParamLoaderUtil {
	private static final Log logger = LogFactory.getLog(DBParamLoaderUtil.class);
    private static DBParamLoader dbParamLoader = new DBParamLoader();
    
    public static Boolean getSwich(){
    	String keyValue = dbParamLoader.getParam("HIS_CENTER_CONFIG");
    	if (ValidateUtil.notBlankString(keyValue) || "1".equals(keyValue)){
    	     return true;
    	}else{
    	     return false;        	
    	}
    }
    
    public static Boolean getOrganSwich(Integer organID){
    	if(getSwich()){
    		String keyValue = dbParamLoader.getParam("HIS_CENTER_ORGAN");
    		if (ValidateUtil.notBlankString(keyValue)) {
    			String[] value = keyValue.split(";");
    			if(Arrays.asList(value).contains(organID.toString())){
    				return true;
    			}    			
            }   		
    	}
    	return false;
    }
    
}
