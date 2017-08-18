package eh.tpn;

import java.util.Date;
import java.util.Random;

import javax.annotation.Resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.entity.base.SecretkeyEntity;

/**
 * 
 * @author hexy
 *
 */
public class SecretKeyService {
	
	   private static final Log logger = LogFactory.getLog(SecretKeyService.class);
	 
	   @Resource
	   private SecretkeyDAO secretkeyDAO = DAOFactory.getDAO(SecretkeyDAO.class);
	   
	   private static Integer LIFE_TYPE_YEAR = 1;
	   
	   private static Integer LIFE_TYPE_MONTH = 2;
	 
	   private static  String RANDOM_STR="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	   
	   
		/**
		 * 生成秘钥
		 */
	    @RpcService
		public void createSecretKey(Long number) {
	    	logger.info("开始自动生成秘钥>>Time:"+JSONObject.toJSONString(new Date()));
	    	Random random=new Random();
	    	for (int i = 0;i < number;i++ ) {
	    		StringBuffer sb = new StringBuffer();
	    		for(int j=0;j<8;j++){
    		       int index = random.nextInt(62);
    		       sb.append(RANDOM_STR.charAt(index));
    		     }
	    		SecretkeyEntity entity = new SecretkeyEntity();
	    		entity.setKeyType(0);
	    		entity.setStatus(0);
	    		entity.setLifeType(LIFE_TYPE_YEAR);
	    		entity.setLifeCount(1);
	    		entity.setSecretKey(sb.toString());
	    		SecretkeyEntity upEntity = secretkeyDAO.saveSecretkey(entity);
	    		upEntity.setSecretKey(sb.toString()+upEntity.getId());
	    		secretkeyDAO.update(upEntity);
	    	}
	    	
		}
		
}
