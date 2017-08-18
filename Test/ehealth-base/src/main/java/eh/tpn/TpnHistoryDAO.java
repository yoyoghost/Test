package eh.tpn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.TpnHistory;

public abstract class TpnHistoryDAO extends HibernateSupportDelegateDAO<TpnHistory>{
	 private static final Logger logger = LoggerFactory.getLogger(TpnHistoryDAO.class);
	 
	 public TpnHistoryDAO(){
		 super();
         this.setEntityName(TpnHistory.class.getName());
         this.setKeyField("id");
	 }
	 
	 public TpnHistory saveTpnHistory(TpnHistory tpnHistory){
		 logger.info("TpnHistoryDao.saveTpnHistory(): param:"+JSONObject.toJSONString(tpnHistory));
		 return save(tpnHistory);
	 }
}
