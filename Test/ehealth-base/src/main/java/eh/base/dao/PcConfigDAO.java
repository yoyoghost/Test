package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.PcConfig;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public abstract class PcConfigDAO extends HibernateSupportDelegateDAO<PcConfig> {

	public PcConfigDAO() {
		super();
		this.setEntityName(PcConfig.class.getName());
		this.setKeyField("id");
	}

	/**
	 * @function 根据机构编号和服务访问地址获取配置信息
	 * 
	 * @author yaozh
	 * @param organId 机构编号
	 * @param serviceServerUrl 服务访问地址
	 * @date 2015-11-18
	 * @return void
	 */
	@RpcService
	@DAOMethod(sql = "from PcConfig where organId=:organId and serviceServerUrl=:serviceServerUrl")
	public abstract PcConfig getByOrganAndServiceUrl(
			@DAOParam("organId") int organId,
			@DAOParam("serviceServerUrl") String serviceServerUrl);
	
	/**
	 * @function 根据服务访问地址获取配置信息
	 * 
	 * @author yaozh
	 * @param serviceServerUrl 服务访问地址
	 * @date 2015-11-18
	 * @return void
	 */
	@RpcService
	@DAOMethod(sql = "from PcConfig where  serviceServerUrl=:serviceServerUrl")
	public abstract List<PcConfig> findServiceUrl(@DAOParam("serviceServerUrl") String serviceServerUrl);
	
	/**
	 * 根据机构编号和服务访问地址获取配置信息 
	 * @author  yaozh
	 * @param serviceServerUrl
	 * @param organId
	 * @return
	 */
	@RpcService
	public PcConfig getPcConfig(String serviceServerUrl,int organId){
		if (StringUtils.isEmpty(serviceServerUrl)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"serviceServerUrl is required!");
		}
		List<PcConfig> confList = findServiceUrl(serviceServerUrl);
		if (confList != null && confList.size() == 1) {
			return confList.get(0);
		}else{
			return getByOrganAndServiceUrl(organId, serviceServerUrl);
		}
	}
	
	

}
