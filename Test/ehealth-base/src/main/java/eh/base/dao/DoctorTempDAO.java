package eh.base.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.Doctortemp;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

public abstract class DoctorTempDAO extends
		HibernateSupportDelegateDAO<Doctortemp> {

	public DoctorTempDAO() {
		super();
		this.setEntityName(Doctortemp.class.getName());
		this.setKeyField("doctorTempId");
	}

	/**
	 * 医生常用模板服务
	 * 
	 * @param doctorId
	 * @param bussType
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "FROM Doctortemp WHERE doctorId=:doctorId and bussType=:bussType Order By LastModify desc")
	public abstract List<Doctortemp> findAllDoctorTemp(
			@DAOParam("doctorId") int doctorId,
			@DAOParam("bussType") String bussType);

	/**
	 * 保存医生常用服模板务-不能添加相同内容的模板
	 * 
	 * @param doctorId
	 * @param bussType
	 * @param tempText
	 */
	@RpcService
	public void saveDoctorTemp(final int doctorId, final String bussType,
			final String tempText) {
		if (StringUtils.isEmpty(tempText)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"tempText is required!");
		}
		DoctorTempDAO dao = DAOFactory.getDAO(DoctorTempDAO.class);
		List<Doctortemp> doctortemps = dao
				.findAllDoctorTemp(doctorId, bussType);
		if (doctortemps != null) {
			for (Doctortemp doctortemp : doctortemps) {
				String text = doctortemp.getTempText();
				if (text != null && text.equals(tempText)) {
					throw new DAOException(609, "该模板内容已存在，请勿重复新增");
				}
			}
		}
		Doctortemp dt = new Doctortemp();
		dt.setDoctorId(doctorId);
		dt.setBussType(bussType);
		dt.setTempText(tempText);
		dt.setLastModify(new Date());
		save(dt);
	}

	/**
	 * 修改医生常用模板-----zsq
	 * 
	 * @param doctorTempId
	 * @param tempText
	 */
	@RpcService
	@DAOMethod(sql = "update Doctortemp set tempText=:tempText,LastModify=NOW() where doctorTempId=:doctorTempId")
	public abstract void updateDoctorTemp(
			@DAOParam("tempText") String tempText,
			@DAOParam("doctorTempId") int doctorTempId);

	/**
	 * 删除医生常用模板------zsq
	 * 
	 * @param doctorTempId
	 */
	@RpcService
	@DAOMethod
	public abstract void deleteDoctorTempByDoctorTempId(int doctorTempId);

	/**
	 * 
	 * 
	 * @Title: saveDoctorTempForIOS
	 * 
	 * @Description: TODO 保存模版信息并返回
	 * 
	 * @param @param doctorId
	 * @param @param bussType
	 * @param @param tempText
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-11-17下午4:16:18
	 * 
	 * @return void
	 * 
	 * @throws
	 */
	@RpcService
	public Doctortemp saveDoctorTempForIOS(final int doctorId,
			final String bussType, final String tempText) {
		if (StringUtils.isEmpty(tempText)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"tempText is required!");
		}
		DoctorTempDAO dao = DAOFactory.getDAO(DoctorTempDAO.class);
		List<Doctortemp> doctortemps = dao
				.findAllDoctorTemp(doctorId, bussType);
		if (doctortemps != null) {
			for (Doctortemp doctortemp : doctortemps) {
				String text = doctortemp.getTempText();
				if (text != null && text.equals(tempText)) {
					throw new DAOException(609, "该模板内容已存在，请勿重复新增");
				}
			}
		}
		Doctortemp dt = new Doctortemp();
		dt.setDoctorId(doctorId);
		dt.setBussType(bussType);
		dt.setTempText(tempText);
		dt.setLastModify(new Date());
		return save(dt);
	}
}
