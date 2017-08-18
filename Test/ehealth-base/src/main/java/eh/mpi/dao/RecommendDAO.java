package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.Recommend;
import eh.mpi.service.RecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class RecommendDAO extends
		HibernateSupportDelegateDAO<Recommend> {
	private static final Logger log = LoggerFactory.getLogger(RecommendDAO.class);

	public RecommendDAO() {
		super();
		this.setEntityName(Recommend.class.getName());
		this.setKeyField("recommendId");
	}

	/**
	 * 推荐开通推送
	 *
	 * @desc 标题：推荐开通 正文：患者张三向您发送了一个请求，希望您可以开通特需预约业务。</br> 注：特需预约是向患者提供有偿的加号预约服务。
	 * @author zhangx
	 * @date 2016-3-3 luf 修改异常code
	 * @param type
	 *            开通业务类型--0特需预约1图文咨询2电话咨询
	 * @param mpiId
	 *            患者主键
	 * @param doctorId
	 *            被推荐医生Id
	 */
	@RpcService
	public void recommendOpenSet(int type, String mpiId, Integer doctorId) {
		RecommendService service= AppContextHolder.getBean("eh.recommendService",RecommendService.class);
		service.recommendOpenSet(type,mpiId,doctorId);
	}


	/**
	 * 获取推荐某项业务开通的次数
	 * 
	 * @author zhangx
	 * @date 2015-12-30 下午3:39:29
	 * @param mpiId
	 *            患者主键
	 * @param doctorId
	 *            医生主键
	 * @param recommendType
	 *            开通业务类型--0特需预约1图文咨询2电话咨询
	 * @return
	 */
	@DAOMethod(sql = "select count(*) from Recommend where mpiId=:mpiId and doctorId=:doctorId and recommendType=:recommendType")
	public abstract Long getRecommendNum(@DAOParam("mpiId") String mpiId,
			@DAOParam("doctorId") Integer doctorId,
			@DAOParam("recommendType") Integer recommendType);

	/**
	 * 获取推荐某项业务开通记录
	 * 
	 * @author zhangx
	 * @date 2015-12-30 下午3:39:29
	 * @param mpiId
	 *            患者主键
	 * @param doctorId
	 *            医生主键
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract List<Recommend> findByMpiIdAndDoctorId(String mpiId,
			Integer doctorId);

	/**
	 * 根据医生id、业务类型查询医生未发送微信推送消息的推荐记录列表
	 * @param doctorId
	 * @param recommendType
     * @return
     */
	@DAOMethod(sql = "from Recommend where recommendType=:recommendType and doctorId=:doctorId AND itWorks=0")
	public abstract List<Recommend> findRecommendByDoctorIdAndRecommendType(@DAOParam("doctorId") Integer doctorId,
										 @DAOParam("recommendType") Integer recommendType);

	/**
	 * 根据医生ID、业务类型更新推荐记录的微信推送消息记录状态为：1已推送
	 * @param doctorId
	 * @param recommendType
     */
	@DAOMethod(sql = "UPDATE Recommend SET itWorks=1 where recommendType=:recommendType and doctorId=:doctorId")
	public abstract void updateRecommendItWorksColumnByDoctorIdAndRecommendType(@DAOParam("doctorId") Integer doctorId,
																			@DAOParam("recommendType") Integer recommendType);
}
