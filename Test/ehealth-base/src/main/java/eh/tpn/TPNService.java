package eh.tpn;

import java.util.Date;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.entity.base.Doctor;
import eh.entity.base.TpnHistory;
import eh.util.VbTpnUtils;
import eh.vb.tpn.VbModelParam;
import eh.vb.tpn.VbModelResult;

/**
 * TPN(营养健康管家算法)
 * author: hexy
 */
public class TPNService {

	private static final Logger logger = LoggerFactory.getLogger(TPNService.class);
	
	@Resource
	private SecretKeyTokenService secretKeyTokenService = AppContextHolder.getBean("secretKeyTokenService", SecretKeyTokenService.class);
	
	
	@RpcService
	public VbModelResult calculationTpnScheme(VbModelParam param){
		logger.info("TPNService.calculationTpnScheme start. param:"+JSONObject.toJSONString(param));
		if (this.saveTpnHistory(param)) {
			return VbTpnUtils.calculationTpnScheme(param);	
		}
		throw new DAOException("TpnHistoryDao.saveTpnHistory Exception");
	}
	
	@RpcService
	public VbModelResult chargeTpnScheme(VbModelParam param,String token) {
		logger.info("TPNService.chargeTpnScheme start. param:"+JSONObject.toJSONString(param),"token:"+token);
		boolean haskey = secretKeyTokenService.findByToken(token);
		if (!haskey) {
			throw new DAOException("请绑定密钥！");
		}
		VbModelResult result = VbTpnUtils.calculationTpnScheme(param);
		logger.info("TpnService.chargeTpnScheme end result:"+JSONObject.toJSONString(result));
		return result;
	}
	
	protected boolean saveTpnHistory(VbModelParam param) {
		 TpnHistoryDAO dao = DAOFactory.getDAO(TpnHistoryDAO.class);
		 DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
		 Doctor doctor = doctorDAO.getByDoctorId(param.getDoctor());
		 TpnHistory tpnHistory = new TpnHistory();
		 tpnHistory.setCreateDate(new Date());
		 tpnHistory.setDepart(param.getDepart());
		 tpnHistory.setDoctor(param.getDoctor());
		 tpnHistory.setOrgan(null == doctor?null:doctor.getOrgan());
		 TpnHistory result = dao.saveTpnHistory(tpnHistory);
		 return null == result?false:true;
	}
}
