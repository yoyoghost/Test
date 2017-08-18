package eh.tpn;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;





import org.hibernate.StatelessSession;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.entity.base.SecretkeyEntity;
import eh.entity.base.SecretkeyTokenEntity;
import eh.redis.RedisClient;

/**
 * 
 * @author hexy
 *
 */
public class SecretKeyTokenService {
	
	   private static final Log logger = LogFactory.getLog(SecretKeyTokenService.class);
	 
	   @Resource
	   private SecretkeyDAO secretkeyDAO = DAOFactory.getDAO(SecretkeyDAO.class);
	   
	   @Resource
	   private RedisClient redisClient = AppContextHolder.getBean("redisClient", RedisClient.class);
	   
	   @Resource
	   private SecretkeyTokenDAO secretkeyTokenDAO = DAOFactory.getDAO(SecretkeyTokenDAO.class);
	   
	   private static Integer LIFE_TYPE_YEAR = 1; 
	   
	   private static Integer LIFE_TYPE_MONTH = 2;
	   
	   @RpcService
	   public boolean findByToken(String token) {
		   logger.info("SecretKeyTokenService.findByToken start the param is token:"+token);
		   boolean resultFlag = false;
		   SecretkeyTokenEntity resultEntity = redisClient.get(token);
		   if (null == resultEntity) {
			   List<SecretkeyTokenEntity> list = secretkeyTokenDAO.findByKey(token);
			   if (CollectionUtils.isNotEmpty(list)) {
				   resultEntity = list.get(0);
				   Date useEndTime = resultEntity.getUseEndTime();
				   if (null != useEndTime && useEndTime.getTime() > new Date().getTime()) {
					   redisClient.setEX(token,(long)60*60*24*7,resultEntity);
					   resultFlag = true;
				   }else {
					   throw new DAOException("该秘钥已过期!");
				   }
			   }
		   }else {
			   Date useEndTime = resultEntity.getUseEndTime();
			   if (null != useEndTime && useEndTime.getTime() > new Date().getTime()) {
				   resultFlag = true;
			   }else {
				   throw new DAOException("该秘钥已过期!");
			   }
		   }
		   logger.info("SecretKeyTokenService.findByToken end the resultFlag:"+JSONUtils.toString(resultFlag));
		   return resultFlag;
	   }
	 
		/**
		 * 秘钥绑定
		 */
	    @RpcService
		public boolean bindToken(final String secretKey,final String token) {
	    	logger.info("SecretKeyTokenService.BindToken start the param is secretKey:"+secretKey +",token:"+token);
	    	//查询改秘钥是否已被绑定
	    	List<SecretkeyEntity> result = secretkeyDAO.findByKey(secretKey);
	    	if (CollectionUtils.isEmpty(result)) {
	    		 throw new DAOException("该秘钥已被其他用户绑定!");
	    	}
	    	final SecretkeyEntity secretkeyEntity = result.get(0);
	    	HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
	            @Override
	            public void execute(StatelessSession statelessSession){
	            	Date useStarTime = new Date();
	    	    	Date useEndTime = getUseEndTime(secretkeyEntity);
	            	SecretkeyTokenEntity entity = new SecretkeyTokenEntity();
	    	    	entity.setSecretKey(secretKey);
	    	    	entity.setToken(token);
	    	    	entity.setStatus(1);
	    	    	entity.setUseStarTime(useStarTime);
	    	    	entity.setUseEndTime(useEndTime);
	    	    	SecretkeyTokenEntity resultEntity = secretkeyTokenDAO.saveSecretkeyToken(entity);
	    	    	if (null != resultEntity) {
	    	    		secretkeyEntity.setStatus(1);
	    	    		SecretkeyEntity updateSecretkey = secretkeyDAO.updateSecretkey(secretkeyEntity);
	    	    		if (null == updateSecretkey) {
	    	    			logger.info("secretkeyDAO.updateSecretkey err the param:"+JSONUtils.toString(secretkeyEntity));
	    	    			throw new DAOException("更改秘钥绑定状态异常!");
	    	    		}
	    	    		redisClient.setEX(token,(long)60*60*24*7,resultEntity);
	        			setResult(true);
	    	    	}
	            }
	        };
	        HibernateSessionTemplate.instance().executeTrans(action);
	        logger.info("SecretKeyTokenService.BindToken end the resultFlag is "+JSONUtils.toString(action.getResult()));
	        return action.getResult();
		}

		private Date getUseEndTime(SecretkeyEntity secretkeyEntity) {
			Integer lifeType = secretkeyEntity.getLifeType();
	    	Integer lifeCount = secretkeyEntity.getLifeCount();
	    	Calendar cale = Calendar.getInstance(); 
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
	    	try {
				if (lifeType.equals(LIFE_TYPE_YEAR)) {//年
					cale.add(Calendar.YEAR, lifeCount);
					String sTime = format.format(cale.getTime());  
					return format.parse(sTime);
				}else if (lifeType.equals(LIFE_TYPE_MONTH)) {//月
					cale.add(Calendar.MONTH, lifeCount);
					String sTime = format.format(cale.getTime());  
					return format.parse(sTime);
				}else { //类型异常
					 throw new DAOException("该秘钥有效期限类型错误!");
				}
			} catch (ParseException e) {
				throw new DAOException("设置秘钥结束时间异常!");
			}
		}
		
}
