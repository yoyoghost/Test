package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.Advice;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;

public abstract class AdviceDAO extends HibernateSupportDelegateDAO<Advice> {
	public AdviceDAO() {
		super();
		this.setEntityName(Advice.class.getName());
		this.setKeyField("adviceId");
	}

	/**
	 * 保存意见
	 * @author ZX
	 * @date 2015-5-19  下午10:35:55
	 * @param advice
	 */
	@RpcService
	public void saveAdvice(Advice advice){
		if(StringUtils.isEmpty(advice.getAdviceContent())){
			throw new DAOException("请输入意见");
		}
		advice.setCreateDate(new Date());
		advice.setAdopt(0);
		advice.setAdviceBussType(0);
		this.save(advice);
	}
	
	/**
	 * 根据建议类型，登录手机号查询建议列表
	 * @author ZX
	 * @date 2015-5-19  下午10:41:41
	 * @param adviceType patient 病人建议，doctor 医生建议
	 * @param userId 登录手机号
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract List<Advice> findByAdviceTypeAndUserId(String adviceType,String userId);
}
